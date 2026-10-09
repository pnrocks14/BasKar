package app.pratyahara.detection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import app.pratyahara.MainActivity
import app.pratyahara.core.buddy.Buddy
import app.pratyahara.core.buddy.Chip
import app.pratyahara.core.detection.Debouncer
import app.pratyahara.core.detection.DetectionEngine
import app.pratyahara.core.detection.DetectionResult
import app.pratyahara.core.lock.FocusHours
import app.pratyahara.core.lock.LockState
import app.pratyahara.core.lock.MindfulGate
import app.pratyahara.data.AppData
import app.pratyahara.engine
import app.pratyahara.overlay.BlockOverlay
import app.pratyahara.overlay.BuddyChip
import app.pratyahara.overlay.FloatingBubble
import app.pratyahara.overlay.PauseContent
import app.pratyahara.overlay.PauseOverlay
import app.pratyahara.overlay.OverlayCopy
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches only the apps the user chose. When their Reels/Shorts screen opens it offers a breathing pause,
 * counts the time, checks in now and then with a small chip at the top, and when the lock policy says so
 * it shows the pause screen. A floating button appears only while a limited app is on screen.
 * Screen content is scored in memory and dropped.
 */
class ReelsAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private val detector = DetectionEngine(DetectionRules.all)
    private val debouncer = Debouncer(required = 2)
    private lateinit var overlay: BlockOverlay
    private lateinit var chip: BuddyChip
    private lateinit var pause: PauseOverlay
    private lateinit var bubble: FloatingBubble
    private var buddy = Buddy()
    private var buddyEvery = -1
    private var gate = MindfulGate()
    private var gateSettings: Pair<Int, Int>? = null

    /** Package whose Reels/Shorts screen is visible right now, or null. */
    private var visiblePackage: String? = null
    private var lastEvalAt = 0L
    private var pendingEval: Job? = null
    private var suppressOverlayUntil = 0L
    private val unflushedSeconds = HashMap<String, Long>()
    private var ticks = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        running.value = true
        overlay = BlockOverlay(this, onBack = ::goBack, onOpenRoute = ::openApp)
        chip = BuddyChip(this)
        pause = PauseOverlay(this, onContinue = ::pauseContinue, onLeave = ::goBack)
        bubble = FloatingBubble(this, onTap = ::showStatusChip)
        // The accessibility shortcut (floating button or gesture) shows how today is going.
        accessibilityButtonController.registerAccessibilityButtonCallback(object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) = showStatusChip()
        })

        scope.launch {
            engine.store.data.map { it.monitored }.distinctUntilChanged().collect { applyPackageFilter(it) }
        }
        scope.launch { tickLoop() }
    }

    /** Narrows event delivery to the chosen apps, so the service is idle everywhere else. */
    private fun applyPackageFilter(monitored: Set<String>) {
        val info = serviceInfo ?: return
        val pkgs = monitored.intersect(DetectionRules.supportedPackages)
        info.packageNames = if (pkgs.isEmpty()) arrayOf(packageName) else pkgs.toTypedArray()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        serviceInfo = info
        if (visiblePackage != null && visiblePackage !in monitored) setVisible(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in engine.store.current.monitored) return
        updateBubble(pkg)
        val now = System.currentTimeMillis()
        if (now - lastEvalAt >= THROTTLE_MS) {
            evaluate()
        } else if (pendingEval?.isActive != true) {
            // Make sure the last change in a burst is evaluated too.
            pendingEval = scope.launch {
                delay(THROTTLE_MS)
                evaluate()
            }
        }
    }

    private fun evaluate() {
        lastEvalAt = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()
        if (pkg == null || pkg !in engine.store.current.monitored) {
            setVisible(null)
            return
        }
        val metrics = resources.displayMetrics
        val snapshot = SnapshotMapper.map(root, metrics.widthPixels, metrics.heightPixels)
        if (recordLayout.value) lastLayout.value = SnapshotMapper.describe(snapshot)
        val result = detector.evaluate(snapshot) ?: return
        lastResult.value = result
        val on = debouncer.update(result.isShortForm)
        when {
            !result.isShortForm -> setVisible(null)
            on -> setVisible(pkg)
        }
    }

    private fun setVisible(pkg: String?) {
        if (pkg == null) debouncer.reset()
        if (visiblePackage == pkg) {
            refreshOverlay()
            return
        }
        val previous = visiblePackage
        visiblePackage = pkg
        refreshOverlay()
        val now = System.currentTimeMillis()
        if (pkg != null && previous == null) onReelsOpened(now)
        if (pkg == null && previous != null) {
            buddy.onLeave(now)
            gate.onLeave(now)
            pause.hide()
        }
    }

    private fun onReelsOpened(now: Long) {
        scope.launch {
            val visits = engine.recordVisit()
            val data = engine.store.current
            val state = engine.lockState(data)
            if (state !is LockState.Allowed || overlay.isShowing || visiblePackage == null) return@launch
            syncGate(data)
            // Right after a squat unlock the cooldown already was the pause.
            val justUnlocked = now - data.cooldownUntil in 0 until 120_000
            if (gate.onEnter(now) && !justUnlocked) {
                val left = (state.remainingSeconds + 59) / 60
                val section = sectionName()
                chip.hide()
                pause.show(
                    PauseContent(
                        title = "Take a breath",
                        body = (if (visits <= 1) "First time opening $section today." else "This is visit $visits to $section today.") +
                            " You have ${minutes(left)} left.",
                        continueLabel = "Open $section",
                        seconds = data.pauseSeconds,
                    )
                )
                visitsAtPause = visits
            } else {
                if (!gate.isOpen) gate.onOpened(now)
                greet(now, visits, state)
            }
        }
    }

    private var visitsAtPause = 0

    /** The user chose to watch after the pause. */
    private fun pauseContinue() {
        val now = System.currentTimeMillis()
        pause.hide()
        gate.onOpened(now)
        (engine.lockState() as? LockState.Allowed)?.let { greet(now, visitsAtPause, it) }
    }

    private fun greet(now: Long, visits: Int, state: LockState.Allowed) {
        val data = engine.store.current
        if (!data.buddyEnabled || overlay.isShowing || pause.isShowing) return
        syncBuddy(data.quoteEveryMinutes)
        buddy.onEnter(now, visits, (state.remainingSeconds + 59) / 60)?.let { chip.show(it) }
    }

    /** Asks again after a long stretch of scrolling. */
    private fun checkInIfDue(now: Long) {
        if (!gate.isCheckDue(now) || pause.isShowing || overlay.isShowing) return
        val data = engine.store.current
        val state = engine.lockState(data) as? LockState.Allowed ?: return
        val section = sectionName()
        chip.hide()
        pause.show(
            PauseContent(
                title = "${gate.minutesSinceOpened(now)} minutes of $section",
                body = "Still watching on purpose? You have ${minutes((state.remainingSeconds + 59) / 60)} left today.",
                continueLabel = "Keep watching",
                seconds = data.pauseSeconds.coerceAtLeast(3),
            )
        )
        visitsAtPause = data.day(engine.today()).visits
        // Counts from now, whatever the user picks.
        gate.onOpened(now)
    }

    private fun sectionName(): String = visiblePackage?.let { detector.ruleFor(it)?.sectionName } ?: "Reels"

    private fun minutes(n: Long) = if (n == 1L) "1 minute" else "$n minutes"

    /** Rebuilds the pause logic when its settings change. */
    private fun syncGate(data: AppData) {
        val settings = data.pauseSeconds to data.checkEveryMinutes
        if (settings == gateSettings) return
        gateSettings = settings
        gate = MindfulGate(pauseSeconds = data.pauseSeconds, checkEveryMinutes = data.checkEveryMinutes)
    }

    /** The floating button shows only inside a limited app or Pratyahara itself, and never over a pause screen. */
    private fun updateBubble(foreground: String?) {
        val d = engine.store.current
        val inScope = foreground != null && (foreground in d.monitored || foreground == packageName)
        val want = d.bubbleEnabled && d.onboarded && inScope && !overlay.isShowing && !pause.isShowing &&
            getSystemService(PowerManager::class.java).isInteractive
        if (want) bubble.show() else bubble.hide()
    }

    /** Rebuilds the buddy when the quote interval changes in Settings. */
    private fun syncBuddy(everyMinutes: Int) {
        if (everyMinutes == buddyEvery) return
        buddyEvery = everyMinutes
        buddy = Buddy(quoteEverySeconds = everyMinutes * 60L)
    }

    /** One second of watching, while nothing blocks it. */
    private fun buddySecond(pkg: String) {
        val data = engine.store.current
        if (!data.buddyEnabled) return
        val state = engine.lockState(data) as? LockState.Allowed ?: return
        syncBuddy(data.quoteEveryMinutes)
        val left = state.remainingSeconds - (unflushedSeconds[pkg] ?: 0L)
        buddy.onSecond(engine.today().toString(), left)?.let { if (!overlay.isShowing && !pause.isShowing) chip.show(it) }
    }

    private fun showStatusChip() {
        val data = engine.store.current
        val usage = data.day(engine.today())
        val line = when (val state = engine.lockState(data)) {
            is LockState.Allowed -> "${minutes((state.remainingSeconds + 59) / 60)} of Reels left today"
            is LockState.BudgetLocked -> "Reels are done for today"
            is LockState.TaskLocked -> "Reels are waiting on your task"
            is LockState.Cooldown -> "Reels open in a few seconds"
            is LockState.FocusLocked -> "Focus hours until ${FocusHours.format(state.endMinute)}"
            LockState.Disabled -> "Blocking is off"
        }
        chip.show(
            Chip(Chip.Kind.HELLO, line, "Opened ${usage.visits} times · paused ${usage.blocks} times · tap for more"),
            durationMs = 5_000,
            onTap = { openApp("home") },
        )
    }

    private fun refreshOverlay() {
        val pkg = visiblePackage
        val now = System.currentTimeMillis()
        if (pkg == null || now < suppressOverlayUntil) {
            overlay.hide()
            if (pkg == null) pause.hide()
            return
        }
        val data = engine.store.current
        val state = engine.lockState(data)
        if (!state.blocksReels) {
            overlay.hide()
            return
        }
        val rule = detector.ruleFor(pkg)
        val content = OverlayCopy.forState(state, data.budgetMinutes, rule?.sectionName ?: "Reels", data.squats, now) ?: return
        if (!overlay.isShowing) scope.launch { engine.recordBlock() }
        chip.hide()
        pause.hide()
        bubble.hide()
        overlay.show(content)
    }

    /** One BACK per tap, then a short pause before the overlay may return. Never sends BACK on its own. */
    private fun goBack() {
        suppressOverlayUntil = System.currentTimeMillis() + BACK_SUPPRESS_MS
        overlay.hide()
        pause.hide()
        performGlobalAction(GLOBAL_ACTION_BACK)
        scope.launch {
            delay(BACK_SUPPRESS_MS + 50)
            evaluate()
        }
    }

    private fun openApp(route: String) {
        overlay.hide()
        suppressOverlayUntil = System.currentTimeMillis() + BACK_SUPPRESS_MS
        startActivity(MainActivity.intent(this, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun tickLoop() {
        val power = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            delay(1_000)
            ticks++
            val pkg = visiblePackage
            val foreground = rootInActiveWindow?.packageName?.toString()
            if (pkg != null) {
                if (foreground != pkg || !power.isInteractive) {
                    setVisible(null)
                } else if (!overlay.isShowing && !pause.isShowing) {
                    unflushedSeconds[pkg] = (unflushedSeconds[pkg] ?: 0L) + 1
                    buddySecond(pkg)
                    checkInIfDue(System.currentTimeMillis())
                }
            }
            updateBubble(foreground)
            refreshOverlay()
            if (ticks % FLUSH_EVERY_S == 0 || (pkg == null && unflushedSeconds.isNotEmpty())) flush()
            if (ticks % 60 == 0) engine.applyDuePending()
        }
    }

    private suspend fun flush() {
        if (unflushedSeconds.isEmpty()) return
        val batch = HashMap(unflushedSeconds)
        unflushedSeconds.clear()
        engine.addUsage(batch)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running.value = false
        if (::overlay.isInitialized) overlay.hide()
        if (::chip.isInitialized) chip.hide()
        if (::pause.isInitialized) pause.hide()
        if (::bubble.isInitialized) bubble.hide()
        val batch = HashMap(unflushedSeconds)
        // Best effort: the process may be going away.
        (applicationContext as app.pratyahara.PratyaharaApp).appScope.launch { engine.addUsage(batch) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val THROTTLE_MS = 300L
        private const val BACK_SUPPRESS_MS = 1_500L
        private const val FLUSH_EVERY_S = 10

        /** For the in-app debug screen only: the latest score and which signals matched. No screen content. */
        val lastResult = MutableStateFlow<DetectionResult?>(null)
        val running = MutableStateFlow(false)

        /** Detection check: keep a redacted description of the latest screen, see [SnapshotMapper.describe]. */
        val recordLayout = MutableStateFlow(false)
        val lastLayout = MutableStateFlow<String?>(null)
        val isRunning: StateFlow<Boolean> get() = running
    }
}
