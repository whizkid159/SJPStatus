package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    uiState: SjpUiState,
    onSyncNow: () -> Unit,
    onOpenPortalTab: () -> Unit,
    onSimulateStatusAlert: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterSelect: (ApplicationStatus?) -> Unit,
    onToggleRemindersFilter: () -> Unit,
    onToggleUnreadFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenJobDetail: (AppliedJobEntity, Int) -> Unit,
    onDismissBanner: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Telemetry Header
        item {
            HeroTelemetryCard(
                uiState = uiState,
                onSyncNow = onSyncNow,
                onOpenPortalTab = onOpenPortalTab,
                onSimulateStatusAlert = onSimulateStatusAlert
            )
        }

        // 2. Live Feedback Banner
        item {
            AnimatedVisibility(visible = !uiState.bannerFeedback.isNullOrBlank()) {
                uiState.bannerFeedback?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alert status",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissBanner,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification banner",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. KPI Summary Strip
        item {
            KpiSummaryRow(
                total = uiState.totalCount,
                shortlisted = uiState.shortlistedTotalCount,
                offered = uiState.offeredTotalCount,
                rejected = uiState.rejectedTotalCount,
                onStatusClick = onStatusFilterSelect
            )
        }

        // 4. Search & Filter Bar
        item {
            SearchAndFiltersSection(
                filterState = uiState.filterState,
                onSearchQueryChange = onSearchQueryChange,
                onStatusFilterSelect = onStatusFilterSelect,
                onToggleRemindersFilter = onToggleRemindersFilter,
                onToggleUnreadFilter = onToggleUnreadFilter,
                onClearFilters = onClearFilters
            )
        }

        // 5. Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hidden Backend Statuses (${uiState.filteredJobs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "api/v2/.../getAllAppliedJobsByCandidate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Empty State or Job Cards
        if (uiState.filteredJobs.isEmpty()) {
            item {
                EmptyJobsCard(
                    hasAnyJobs = uiState.allJobs.isNotEmpty(),
                    onClearFilters = onClearFilters,
                    onOpenPortalTab = onOpenPortalTab
                )
            }
        } else {
            items(
                items = uiState.filteredJobs,
                key = { it.applicationId }
            ) { job ->
                AppliedJobCard(
                    job = job,
                    isDark = isDark,
                    onCardClick = { onOpenJobDetail(job, 0) },
                    onInspectJsonClick = { onOpenJobDetail(job, 2) },
                    onReminderClick = { onOpenJobDetail(job, 0) }
                )
            }
        }
    }
}

@Composable
private fun HeroTelemetryCard(
    uiState: SjpUiState,
    onSyncNow: () -> Unit,
    onOpenPortalTab: () -> Unit,
    onSimulateStatusAlert: () -> Unit
) {
    val session = uiState.sessionState
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val lastSyncLabel = if (session.lastPollTimestamp > 0L) {
        "Last synced ${timeFormatter.format(Date(session.lastPollTimestamp))}"
    } else {
        "Not synced yet"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.ic_sjp_hero_illustration),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (session.hasActiveSession) Color(0xFF34D399)
                                    else Color(0xFFFBBF24)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (session.hasActiveSession) {
                                "SJP Session Persisted • Auto-Poll ${session.pollingIntervalMinutes}m"
                            } else {
                                "Diagnostic Mode • Auto-Poll ${session.pollingIntervalMinutes}m"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }

                    Text(
                        text = lastSyncLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sindh Job Portal Status Radar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unmasks hidden statuses (Shortlisted, Temp Shortlisted, Offered, Temp Offered) from getAllAppliedJobsByCandidate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !uiState.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color(0xFF042F2E)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sync_now_button")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF042F2E)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync API",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isSyncing) "Polling…" else "Poll API",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenPortalTab,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("portal_login_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Open SJP Portal",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SJP Login",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Notification & Diff Simulator button so user can test push notifications anytime
                TextButton(
                    onClick = onSimulateStatusAlert,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFDE68A)),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("simulate_status_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Simulate status change push notification",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Status Change & Push Notification",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiSummaryRow(
    total: Int,
    shortlisted: Int,
    offered: Int,
    rejected: Int,
    onStatusClick: (ApplicationStatus?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KpiStatTile(
            label = "Total Applied",
            value = total.toString(),
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(null) }
        )
        KpiStatTile(
            label = "Shortlisted",
            value = shortlisted.toString(),
            accentColor = Color(0xFF2563EB),
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(ApplicationStatus.SHORTLISTED) }
        )
        KpiStatTile(
            label = "Offered",
            value = offered.toString(),
            accentColor = Color(0xFF059669),
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(ApplicationStatus.OFFERED) }
        )
        KpiStatTile(
            label = "Rejected",
            value = rejected.toString(),
            accentColor = Color(0xFFDC2626),
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(ApplicationStatus.REJECTED) }
        )
    }
}

@Composable
private fun KpiStatTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchAndFiltersSection(
    filterState: SjpFilterState,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterSelect: (ApplicationStatus?) -> Unit,
    onToggleRemindersFilter: () -> Unit,
    onToggleUnreadFilter: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Search post title, department, BPS, or status…",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search jobs"
                )
            },
            trailingIcon = {
                if (filterState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filterState.selectedStatus == null &&
                    !filterState.onlyWithReminders &&
                    !filterState.onlyUnreadChanges,
                onClick = onClearFilters,
                label = { Text("All Jobs") },
                modifier = Modifier.testTag("filter_chip_all")
            )

            FilterChip(
                selected = filterState.onlyUnreadChanges,
                onClick = onToggleUnreadFilter,
                label = { Text("Updated Only") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            for (status in listOf(
                ApplicationStatus.SHORTLISTED,
                ApplicationStatus.TEMP_SHORTLISTED,
                ApplicationStatus.OFFERED,
                ApplicationStatus.TEMP_OFFERED,
                ApplicationStatus.UNDER_SCRUTINY,
                ApplicationStatus.REJECTED
            )) {
                FilterChip(
                    selected = filterState.selectedStatus == status,
                    onClick = { onStatusFilterSelect(status) },
                    label = { Text(status.shortLabel) },
                    modifier = Modifier.testTag("filter_chip_${status.code.lowercase()}")
                )
            }

            FilterChip(
                selected = filterState.onlyWithReminders,
                onClick = onToggleRemindersFilter,
                label = { Text("Has Countdown") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun AppliedJobCard(
    job: AppliedJobEntity,
    isDark: Boolean,
    onCardClick: () -> Unit,
    onInspectJsonClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    val (badgeBg, badgeFg) = job.statusEnum.badgeColors(isDark)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onCardClick)
            .testTag("job_card_${job.applicationId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: BPS Pill, App ID, and Unread Update Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = job.bpsScale,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = job.applicationId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (job.hasUnreadChange) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "NEW UPDATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Applied ${job.appliedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Job Title & Department
            Text(
                text = job.jobTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = job.departmentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Frontend Masked vs Hidden Backend API Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PORTAL UI SHOWS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = job.frontendMaskedStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Revealed as",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "HIDDEN BACKEND API",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = job.statusEnum.displayLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Hidden Scrutiny Remarks & Merit Score
            if (job.backendRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = job.backendRemarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Row: Merit Score, Interview Countdown / Notes, and Raw JSON Inspect Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (job.meritScore.isNotBlank() && job.meritScore != "N/A") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Score: ${job.meritScore}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    job.interviewTimestamp?.let { ts ->
                        val daysLeft = TimeUnit.MILLISECONDS.toDays(ts - System.currentTimeMillis())
                        val countdownLabel = when {
                            daysLeft < 0 -> "Interview Passed"
                            daysLeft == 0L -> "Interview Today!"
                            else -> "Interview in ${daysLeft}d"
                        }
                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable(onClick = onReminderClick)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = countdownLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1D4ED8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onInspectJsonClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("inspect_json_${job.applicationId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = "Inspect Raw JSON",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Raw JSON",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyJobsCard(
    hasAnyJobs: Boolean,
    onClearFilters: () -> Unit,
    onOpenPortalTab: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FindInPage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasAnyJobs) "No Applications Match Filters" else "No Applications Loaded Yet",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyJobs) {
                    "Try clearing your active status or search filters to view all tracked SJP applications."
                } else {
                    "Connect your sjp.gos.pk account in the SJP Portal tab to fetch your live applications."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = if (hasAnyJobs) onClearFilters else onOpenPortalTab
            ) {
                Text(if (hasAnyJobs) "Reset All Filters" else "Open SJP Portal Login")
            }
        }
    }
}
