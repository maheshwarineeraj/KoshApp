package com.neeraj.fin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.screens.BudgetsScreen
import com.neeraj.fin.ui.screens.CategoriesScreen
import com.neeraj.fin.ui.screens.EditTxnScreen
import com.neeraj.fin.ui.screens.HomeScreen
import com.neeraj.fin.ui.screens.InsightsScreen
import com.neeraj.fin.ui.screens.RecurringScreen
import com.neeraj.fin.ui.screens.ReviewScreen
import com.neeraj.fin.ui.screens.SettingsScreen
import com.neeraj.fin.ui.screens.TransactionsScreen
import com.neeraj.fin.ui.screens.WealthScreen
import com.neeraj.fin.ui.theme.FinTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FinApp
        // Keep balances and transactions out of screenshots, screen recordings,
        // and the Recents preview while the setting is on (default).
        lifecycleScope.launch {
            app.settings.blockScreenshots.collect { block ->
                if (block) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            FinTheme {
                // null = setting not loaded yet: render nothing for that first frame
                // so we neither flash the lock screen nor leak content.
                val lockEnabled by app.settings.appLock
                    .map { it as Boolean? }
                    .collectAsState(initial = null)
                val privacyAccepted by app.settings.privacyAccepted
                    .map { it as Boolean? }
                    .collectAsState(initial = null)
                val unlocked by app.unlocked.collectAsState()
                when {
                    lockEnabled == null || privacyAccepted == null -> Unit
                    privacyAccepted == false -> PrivacyConsentScreen(
                        onAgree = { lifecycleScope.launch { app.settings.setPrivacyAccepted(true) } },
                        onExit = { finish() }
                    )
                    else -> Box {
                        FinNav()
                        // Overlay (not replacement) so the navigation stack and any
                        // half-filled form survive the lock/unlock round-trip.
                        if (lockEnabled == true && !unlocked) {
                            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                                LockScreen(onRequestUnlock = { promptUnlock(app) })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptUnlock(app: FinApp) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            // No screen lock configured on this device — don't lock the user out.
            app.unlocked.value = true
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    app.unlocked.value = true
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kosh")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}

@Composable
private fun LockScreen(onRequestUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onRequestUnlock() }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Kosh is locked",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
        Button(onClick = onRequestUnlock) { Text("Unlock") }
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badged: Boolean = false
)

@Composable
fun FinNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val items = listOf(
        BottomItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomItem("transactions", "Activity", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, badged = true),
        BottomItem("insights", "Insights", Icons.Filled.PieChart, Icons.Outlined.PieChart),
        BottomItem("wealth", "Wealth", Icons.Filled.Savings, Icons.Outlined.Savings),
        BottomItem("settings", "More", Icons.Filled.MoreHoriz, Icons.Filled.MoreHoriz)
    )

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTopLevel = currentRoute in items.map { it.route }
    // Wide window (tablets, foldables, phones in landscape): navigation rail
    // on the side instead of a bottom bar, and content capped to a readable width.
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    @Composable
    fun itemIcon(item: BottomItem, selected: Boolean) {
        val icon = if (selected) item.selectedIcon else item.unselectedIcon
        if (item.badged && pendingCount > 0) {
            BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                Icon(icon, contentDescription = item.label)
            }
        } else {
            Icon(icon, contentDescription = item.label)
        }
    }

    fun navigateTo(route: String) {
        nav.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isWide && onTopLevel) {
                NavigationBar {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(item.route) },
                            icon = { itemIcon(item, selected) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (isWide && onTopLevel) {
                NavigationRail {
                    Spacer(Modifier.weight(1f))
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(item.route) },
                            icon = { itemIcon(item, selected) },
                            label = { Text(item.label) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxHeight()
                ) {
            composable("home") { HomeScreen(vm, nav) }
            composable("transactions") { TransactionsScreen(vm, nav) }
            composable("insights") { InsightsScreen(vm) }
            composable("wealth") { WealthScreen(vm) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable(
                "edit/{txnId}",
                arguments = listOf(navArgument("txnId") { type = NavType.LongType })
            ) { entry ->
                EditTxnScreen(vm, nav, entry.arguments?.getLong("txnId") ?: 0L)
            }
            composable("review") { ReviewScreen(vm, nav) }
            composable("categories") { CategoriesScreen(vm, nav) }
            composable("budgets") { BudgetsScreen(vm, nav) }
            composable("recurring") { RecurringScreen(vm, nav) }
                }
            }
        }
    }
}

/**
 * First-launch privacy notice. The app is unusable until the user agrees;
 * "Exit" closes the app. Acceptance is stored once in DataStore.
 */
@Composable
private fun PrivacyConsentScreen(onAgree: () -> Unit, onExit: () -> Unit) {
    Scaffold(
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f)
                ) { Text("Exit") }
                Button(
                    onClick = onAgree,
                    modifier = Modifier.weight(2f)
                ) { Text("I Agree — Continue") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.padding(top = 24.dp))
            Text("Your Privacy, First", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Kosh is a private, offline finance tracker. Before you start, here is " +
                    "exactly how your data is handled:",
                style = MaterialTheme.typography.bodyLarge
            )
            PrivacyPoint("📱", "Everything stays on this device", "All transactions, budgets, goals and wealth data live in a local database inside the app's private storage. There is no account, no sign-up, and no server.")
            PrivacyPoint("🚫", "No internet access — enforced", "The app does not hold the Android INTERNET permission, so it is technically incapable of sending your data anywhere.")
            PrivacyPoint("✉️", "SMS & notifications, only if you allow", "If you grant access, messages are scanned on-device to detect bank transactions. Personal messages, OTPs and offers are ignored and never stored. Every detected transaction waits for your approval. You can revoke access anytime.")
            PrivacyPoint("🔐", "Backups are encrypted", "Backup files are AES-256 encrypted with a passphrase only you know, before they leave the app. Plain-text CSV export exists only as an explicit action by you.")
            PrivacyPoint("📵", "No analytics, no tracking, no ads", "Kosh contains no analytics SDKs, no crash reporters, no advertising identifiers — nothing that phones home.")
            PrivacyPoint("🗑️", "Your data, your control", "Uninstalling the app (or clearing its data) permanently deletes everything on the device. Only backups you exported yourself survive.")
            Text(
                "By tapping \"I Agree\", you confirm you have read and accept how Kosh " +
                    "handles your data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun PrivacyPoint(emoji: String, title: String, body: String) {
    Row {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
