package app.pratyahara.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.pratyahara.engine
import app.pratyahara.ui.budget.BudgetScreen
import app.pratyahara.ui.cooldown.CooldownScreen
import app.pratyahara.ui.debug.DebugScreen
import app.pratyahara.ui.home.HomeScreen
import app.pratyahara.ui.onboarding.OnboardingScreen
import app.pratyahara.ui.settings.PrivacyScreen
import app.pratyahara.ui.settings.SettingsScreen
import app.pratyahara.ui.summary.SummaryScreen
import app.pratyahara.ui.tasks.TaskScreen
import app.pratyahara.ui.unlock.UnlockScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val BUDGET = "budget"
    const val UNLOCK = "unlock"
    const val COOLDOWN = "cooldown"
    const val TASK = "task"
    const val SUMMARY = "summary"
    const val DEBUG = "debug"
    const val PRIVACY = "privacy"
    val all = setOf(HOME, SETTINGS, BUDGET, UNLOCK, COOLDOWN, TASK, SUMMARY, DEBUG, PRIVACY)
}

@Composable
fun PratyaharaNav(requestedRoute: String?, onRouteHandled: () -> Unit) {
    val engine = LocalContext.current.engine
    val loaded by engine.store.loaded.collectAsStateWithLifecycle()
    val data by engine.store.data.collectAsStateWithLifecycle()

    if (!loaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (!data.onboarded) {
        OnboardingScreen()
        return
    }

    val nav = rememberNavController()
    LaunchedEffect(requestedRoute) {
        val r = requestedRoute ?: return@LaunchedEffect
        if (r in Routes.all && r != Routes.HOME) nav.navigate(r) { launchSingleTop = true }
        onRouteHandled()
    }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val home: () -> Unit = { nav.popBackStack(Routes.HOME, inclusive = false) }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(Routes.HOME) { HomeScreen(go) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back, go = go) }
        composable(Routes.BUDGET) { BudgetScreen(onBack = back) }
        composable(Routes.UNLOCK) { UnlockScreen(onBack = back, onUnlocked = { nav.navigate(Routes.COOLDOWN) { popUpTo(Routes.HOME) } }) }
        composable(Routes.COOLDOWN) { CooldownScreen(onDone = home) }
        composable(Routes.TASK) { TaskScreen(onBack = back, onDone = home) }
        composable(Routes.SUMMARY) { SummaryScreen(onBack = back) }
        composable(Routes.DEBUG) { DebugScreen(onBack = back) }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }
    }
}
