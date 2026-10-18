package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity
import com.example.data.SjpPayloadParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    uiState: SjpUiState,
    onSyncNow: () -> Unit,
    onOpenPortalTab: () -> Unit,
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
                onOpenPortalTab = onOpenPortalTab
            )
        }

        // 2. Live Sync Feedback Banner
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
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync status",
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
                                    contentDescription = "Dismiss banner",
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
                    text = "Applied Jobs (${uiState.filteredJobs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "usr-svc.sjp.gos.pk",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Empty State or Real SJP Job Cards
        if (uiState.filteredJobs.isEmpty()) {
            item {
                EmptyJobsCard(
                    hasAnyJobs = uiState.allJobs.isNotEmpty(),
                    hasActiveSession = uiState.sessionState.hasActiveSession,
                    onClearFilters = onClearFilters,
                    onSyncNow = onSyncNow,
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
                    onHistoryClick = { onOpenJobDetail(job, 1) },
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
    onOpenPortalTab: () -> Unit
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
                            .background(Color.Black.copy(alpha = 0.35f))
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
                                "SJP Session Active • Auto-Poll ${session.pollingIntervalMinutes}m"
                            } else {
                                "Login Required • Auto-Poll ${session.pollingIntervalMinutes}m"
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
                    text = "Sindh Job Portal Application Viewer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fetches all pages from usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate and notifies on status changes.",
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
                                contentDescription = "Sync SJP Applications",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isSyncing) "Fetching SJP…" else "Fetch SJP Data",
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
                            contentDescription = "Open SJP Portal Login",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SJP Login",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
            accentColor = Color(0xFF1757B8),
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(ApplicationStatus.SHORTLISTED) }
        )
        KpiStatTile(
            label = "Offered",
            value = offered.toString(),
            accentColor = Color(0xFF16803C),
            modifier = Modifier.weight(1f),
            onClick = { onStatusClick(ApplicationStatus.OFFERED) }
        )
        KpiStatTile(
            label = "Rejected",
            value = rejected.toString(),
            accentColor = Color(0xFFC62828),
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
                    text = "Search jobs, departments, application ID, or status…",
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
    onHistoryClick: () -> Unit,
    onInspectJsonClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    val (badgeBg, badgeFg) = job.statusEnum.badgeColors(isDark)
    val statusHistoryEntries = remember(job.statusHistoryJson) {
        SjpPayloadParser.parseBackendHistoryJson(job.statusHistoryJson)
    }

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
            // Top Row: Application ID & Current Status Badge
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
                            text = "ID: ${job.applicationId}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (job.hasUnreadChange) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "STATUS UPDATED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = job.rawStatusString,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Job Title & Department (exact fields from j.jobTitle and j.Department.departmentName)
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Structured Details Table matching the user's Firefox bookmarklet
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BookmarkletFieldRow("Current status", job.rawStatusString, badgeFg, boldValue = true)
                    BookmarkletFieldRow("Job offered", if (job.jobOffered) "YES" else "NO")
                    BookmarkletFieldRow("Interview", job.jobInterviewStatus)
                    BookmarkletFieldRow("Offer record", if (job.hasOfferRecord) "PRESENT" else "NONE")
                    BookmarkletFieldRow("Rejection reason", job.reasonForRejected)
                }
            }

            // Inline preview of ApplicationStatuses History (from z.ApplicationStatuses)
            if (statusHistoryEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "STATUS HISTORY (${statusHistoryEntries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (entry in statusHistoryEntries.take(3)) {
                        val (_, entryFg) = ApplicationStatus.fromRawStatus(entry.status).badgeColors(isDark)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.changedAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.status,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = entryFg
                            )
                        }
                    }
                    if (statusHistoryEntries.size > 3) {
                        Text(
                            text = "+ ${statusHistoryEntries.size - 3} more status history entries…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onHistoryClick)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Row: Interview Countdown / Notes + History & Raw JSON Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onHistoryClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Status History",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "History (${statusHistoryEntries.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    TextButton(
                        onClick = onInspectJsonClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("inspect_json_${job.applicationId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = "Inspect Raw JSON",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "JSON",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkletFieldRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    boldValue: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun EmptyJobsCard(
    hasAnyJobs: Boolean,
    hasActiveSession: Boolean,
    onClearFilters: () -> Unit,
    onSyncNow: () -> Unit,
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
                imageVector = if (hasAnyJobs) Icons.Outlined.FilterAltOff else Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    hasAnyJobs -> "No Applications Match Filters"
                    hasActiveSession -> "Ready to Fetch Your SJP Applications"
                    else -> "Log In to SJP Portal to Load Your Applications"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    hasAnyJobs -> "Clear your active search or status filters to view your loaded SJP applications."
                    hasActiveSession -> "Your SJP session is active. Tap 'Fetch SJP Data Now' or open the SJP Portal tab to load all pages from usr-svc.sjp.gos.pk."
                    else -> "Sign in once on the SJP Portal tab. As soon as you log in, the app will automatically fetch all pages of your applied jobs from usr-svc.sjp.gos.pk."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasAnyJobs) {
                    Button(onClick = onClearFilters) {
                        Text("Reset Filters")
                    }
                } else {
                    Button(onClick = onOpenPortalTab) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open SJP Portal Login")
                    }
                    if (hasActiveSession) {
                        OutlinedButton(onClick = onSyncNow) {
                            Text("Fetch SJP Data Now")
                        }
                    }
                }
            }
        }
    }
}
