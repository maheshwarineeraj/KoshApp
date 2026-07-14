package com.neeraj.fin

import android.os.Bundle
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
import kotlinx.coroutines.flow.map

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FinApp
        setContent {
            FinTheme {
                // null = setting not loaded yet: render nothing for that first frame
                // so we neither flash the lock screen nor leak content.
                val lockEnabled by app.settings.appLock
                    .map { it as Boolean? }
                    .collectAsState(initial = null)
                val unlocked by app.unlocked.collectAsState()
                when {
                    lockEnabled == null -> Unit
                    lockEnabled == true && !unlocked ->
                        LockScreen(onRequestUnlock = { promptUnlock(app) })
                    else -> FinNav()
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
