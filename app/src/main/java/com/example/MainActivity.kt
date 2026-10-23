package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AndroidCompatHelper
import com.example.data.SjpNotificationHelper
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

enum class SjpDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Applied", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TIMELINE("timeline", "History", Icons.Filled.History, Icons.Outlined.History),
    ANALYTICS("analytics", "Analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    PORTAL("portal", "SJP Login", Icons.Filled.Language, Icons.Outlined.Language)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SjpNotificationHelper.ensureChannelCreated(this)
        enableEdgeToEdge()
        setContent {
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(isDark) {
                AndroidCompatHelper.configureSystemBars(this@MainActivity, isDark)
            }
            MyApplicationTheme(darkTheme = isDark) {
                SjpPulseApp()
            }
        }
    }
}

@Composable
fun SjpPulseApp(
    viewModel: SjpTrackerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(SjpDestination.DASHBOARD) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (AndroidCompatHelper.requiresRuntimeNotificationPermission()) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (currentTab != SjpDestination.DASHBOARD) {
        BackHandler {
            currentTab = SjpDestination.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        tonalElevation = 6.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        for (dest in SjpDestination.entries) {
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest == SjpDestination.TIMELINE && uiState.unreadHistoryCount > 0) {
                                                Badge { Text(uiState.unreadHistoryCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("side_nav_rail")
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        for (dest in SjpDestination.entries) {
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest == SjpDestination.TIMELINE && uiState.unreadHistoryCount > 0) {
                                                Badge { Text(uiState.unreadHistoryCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) +
                                scaleIn(
                                    initialScale = 0.98f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )).togetherWith(fadeOut(animationSpec = tween(160)))
                        },
                        label = "sjp_tab_transition"
                    ) { targetTab ->
                        when (targetTab) {
                            SjpDestination.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                onSyncNow = viewModel::syncFromPortalApi,
                                onOpenPortalTab = { currentTab = SjpDestination.PORTAL },
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onStatusFilterSelect = viewModel::selectStatusFilter,
                                onDepartmentFilterSelect = viewModel::selectDepartmentFilter,
                                onToggleRemindersFilter = viewModel::toggleRemindersFilter,
                                onToggleUnreadFilter = viewModel::toggleUnreadFilter,
                                onClearFilters = viewModel::clearAllFilters,
                                onOpenJobDetail = viewModel::openJobDetail,
                                onDismissBanner = viewModel::dismissBannerFeedback
                            )

                            SjpDestination.TIMELINE -> TimelineScreen(
                                historyList = uiState.historyList,
                                unreadCount = uiState.unreadHistoryCount,
                                onMarkAllRead = viewModel::markAllHistoryRead,
                                onSyncNow = viewModel::syncFromPortalApi
                            )

                            SjpDestination.ANALYTICS -> AnalyticsAndReportScreen(
                                allJobs = uiState.allJobs
                            )

                            SjpDestination.PORTAL -> PortalWebViewScreen(
                                sessionState = uiState.sessionState,
                                webViewFetchRequests = viewModel.webViewFetchRequests,
                                onSessionCaptured = { token, cookies ->
                                    viewModel.onWebViewSessionCaptured(token, cookies)
                                },
                                onPayloadCaptured = { json, source ->
                                    viewModel.onWebViewPayloadCaptured(json, source)
                                },
                                onFetchError = viewModel::onWebViewFetchError,
                                onSaveConfig = viewModel::savePollingAndTokenConfig,
                                onClearSession = viewModel::clearSessionAndData,
                                onNavigateToDashboard = {
                                    currentTab = SjpDestination.DASHBOARD
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.selectedJobForDetail?.let { selectedJob ->
        val jobHistory = remember(uiState.historyList, selectedJob.applicationId) {
            uiState.historyList.filter { it.applicationId == selectedJob.applicationId }
        }
        JobDetailBottomSheet(
            job = selectedJob,
            jobHistory = jobHistory,
            initialTab = uiState.initialDetailTab,
            onDismiss = viewModel::closeJobDetail,
            onSaveNotesAndReminder = { notes, interviewTs ->
                viewModel.saveJobNotesAndInterview(selectedJob.applicationId, notes, interviewTs)
            }
        )
    }
}
