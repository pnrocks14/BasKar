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
import app.pratyahara.core.lock.LockState
import app.pratyahara.engine
import app.pratyahara.overlay.BlockOverlay
import app.pratyahara.overlay.BuddyChip
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
 * Watches only the apps the user chose. When their Reels/Shorts screen is visible it counts the time,
 * checks in now and then with a small chip at the top, and when the lock policy says so it shows the
 * pause screen. Screen content is scored in memory and dropped.
 */
class ReelsAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private val detector = DetectionEngine(DetectionRules.all)
    private val debouncer = Debouncer(required = 2)
    private lateinit var overlay: BlockOverlay
    private lateinit var chip: BuddyChip
    private var buddy = Buddy()
    private var buddyEvery = -1

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
        if (pkg == null && previous != null) buddy.onLeave(now)
    }

    private fun onReelsOpened(now: Long) {
        scope.launch {
            val visits = engine.recordVisit()
            val data = engine.store.current
            val state = engine.lockState(data)
            if (!data.buddyEnabled || state !is LockState.Allowed || overlay.isShowing) return@launch
            syncBuddy(data.quoteEveryMinutes)
            buddy.onEnter(now, visits, (state.remainingSeconds + 59) / 60)?.let { chip.show(it) }
        }
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
        buddy.onSecond(engine.today().toString(), left)?.let { if (!overlay.isShowing) chip.show(it) }
    }

    private fun showStatusChip() {
        val data = engine.store.current
        val usage = data.day(engine.today())
        val line = when (val state = engine.lockState(data)) {
            is LockState.Allowed -> "${(state.remainingSeconds + 59) / 60} min of reels left today"
            is LockState.BudgetLocked -> "reels are done for today 🔒"
            is LockState.TaskLocked -> "reels are waiting on your task ✍️"
            is LockState.Cooldown -> "reels open in a few seconds"
            LockState.Disabled -> "blocking is off rn"
        }
        chip.show(
            Chip(Chip.Kind.HELLO, "hii 👋 i'm on it", "$line · ${usage.blocks} stops today · tap to open"),
            durationMs = 5_000,
            onTap = { openApp("home") },
        )
    }

    private fun refreshOverlay() {
        val pkg = visiblePackage
        val now = System.currentTimeMillis()
        if (pkg == null || now < suppressOverlayUntil) {
            overlay.hide()
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
        overlay.show(content)
    }

    /** One BACK per tap, then a short pause before the overlay may return. Never sends BACK on its own. */
    private fun goBack() {
        suppressOverlayUntil = System.currentTimeMillis() + BACK_SUPPRESS_MS
        overlay.hide()
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
            if (pkg != null) {
                val stillThere = rootInActiveWindow?.packageName?.toString() == pkg
                if (!stillThere || !power.isInteractive) {
                    setVisible(null)
                } else if (!overlay.isShowing) {
                    unflushedSeconds[pkg] = (unflushedSeconds[pkg] ?: 0L) + 1
                    buddySecond(pkg)
                }
            }
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
        val isRunning: StateFlow<Boolean> get() = running
    }
}
