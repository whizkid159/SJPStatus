package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    RECOMMENDATIONS("jobs", "Jobs For You", Icons.Filled.Work, Icons.Outlined.WorkOutline),
    TIMELINE("timeline", "History", Icons.Filled.History, Icons.Outlined.History),
    ANALYTICS("analytics", "Reports", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    PORTAL("portal", "Account", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { SjpNotificationHelper.ensureChannelCreated(this) }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    if (currentTab != SjpDestination.DASHBOARD) {
        BackHandler {
            currentTab = SjpDestination.DASHBOARD
        }
    }

    // Invisible Headless Background Browser Host (0.dp) that handles silent SJP login,
    // multi-page API polling, and in-app job application submission in the background
    HeadlessSjpBrowserHost(
        sessionState = uiState.sessionState,
        browserCommands = viewModel.browserCommands,
        onSessionCaptured = { token, cookies ->
            viewModel.onWebViewSessionCaptured(token, cookies)
        },
        onPayloadCaptured = { json, source ->
            viewModel.onWebViewPayloadCaptured(json, source)
        },
        onOpenJobsCaptured = viewModel::onWebViewOpenJobsCaptured,
        onProfileCaptured = viewModel::onWebViewCandidateProfileCaptured,
        onFetchError = viewModel::onWebViewFetchError
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
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
                                            } else if (dest == SjpDestination.RECOMMENDATIONS && uiState.eligibleJobsCount > 0) {
                                                Badge { Text(uiState.eligibleJobsCount.toString()) }
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
                                            } else if (dest == SjpDestination.RECOMMENDATIONS && uiState.eligibleJobsCount > 0) {
                                                Badge { Text(uiState.eligibleJobsCount.toString()) }
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
                    when (currentTab) {
                        SjpDestination.DASHBOARD -> DashboardScreen(
                            uiState = uiState,
                            onSyncNow = viewModel::syncFromPortalApi,
                            onOpenPortalTab = { currentTab = SjpDestination.PORTAL },
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onStatusFilterSelect = viewModel::selectStatusFilter,
                            onToggleRemindersFilter = viewModel::toggleRemindersFilter,
                            onToggleUnreadFilter = viewModel::toggleUnreadFilter,
                            onClearFilters = viewModel::clearAllFilters,
                            onOpenJobDetail = viewModel::openJobDetail,
                            onDismissBanner = viewModel::dismissBannerFeedback
                        )

                        SjpDestination.RECOMMENDATIONS -> JobRecommendationsScreen(
                            uiState = uiState,
                            onSyncJobs = viewModel::syncFromPortalApi,
                            onUpdateProfile = viewModel::updateCandidateEligibilityProfile,
                            onToggleOnlyEligible = viewModel::setOnlyEligibleJobsFilter,
                            onSearchQueryChange = viewModel::updateOpenJobsSearchQuery,
                            onSelectJobForApply = viewModel::openApplyConfirmationSheet,
                            onOpenLoginTab = { currentTab = SjpDestination.PORTAL }
                        )

                        SjpDestination.TIMELINE -> TimelineScreen(
                            historyList = uiState.historyList,
                            unreadCount = uiState.unreadHistoryCount,
                            onMarkAllRead = viewModel::markAllHistoryRead,
                            onSyncNow = viewModel::syncFromPortalApi
                        )

                        SjpDestination.ANALYTICS -> AnalyticsAndReportScreen(
                            allJobs = uiState.allJobs,
                            onGenerateCsv = viewModel::buildCsvReport,
                            onGenerateTextReport = viewModel::buildExecutiveTextReport
                        )

                        SjpDestination.PORTAL -> NativeLoginAndAccountScreen(
                            uiState = uiState,
                            onLoginWithCredentials = viewModel::loginWithCnicAndPassword,
                            onSyncNow = viewModel::syncFromPortalApi,
                            onPayloadCaptured = { json, source ->
                                viewModel.onWebViewPayloadCaptured(json, source)
                            },
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

    // Modal Bottom Sheet for Applied Job Details & Raw JSON
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

    // Modal Bottom Sheet for Eligibility Breakdown & One-Tap In-App Job Application
    uiState.selectedEvaluationForApply?.let { evaluation ->
        EligibilityAndApplyBottomSheet(
            evaluation = evaluation,
            onDismiss = viewModel::closeApplyConfirmationSheet,
            onConfirmApply = {
                viewModel.confirmAndApplyToJob(evaluation.job)
            }
        )
    }
}
