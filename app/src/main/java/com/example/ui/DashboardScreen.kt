package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onDepartmentFilterSelect: (String?) -> Unit,
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
        // 1. Animated Aurora Glassmorphic Hero Header
        item {
            HeroTelemetryCard(
                uiState = uiState,
                onSyncNow = onSyncNow,
                onOpenPortalTab = onOpenPortalTab
            )
        }

        // 2. Live Sync Feedback Banner
        item {
            AnimatedVisibility(
                visible = !uiState.bannerFeedback.isNullOrBlank(),
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.bannerFeedback?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync status",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
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
                                modifier = Modifier.size(32.dp)
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

        // 3. Interactive Clickable KPI Status Bento Dashboard
        // Includes Total Applications, Shortlisted, Temp Shortlisted, Waiting, Temp Waiting,
        // Offered, Temp Offered, Temp Rejected, Rejected, Under Scrutiny, and Applied!
        item {
            InteractiveStatusKpiDashboard(
                uiState = uiState,
                isDark = isDark,
                onStatusClick = onStatusFilterSelect
            )
        }

        // 4. Unified Search & Modern Dropdown Filter Command Hub
        item {
            ModernSearchAndFilterHub(
                uiState = uiState,
                isDark = isDark,
                onSearchQueryChange = onSearchQueryChange,
                onStatusFilterSelect = onStatusFilterSelect,
                onDepartmentFilterSelect = onDepartmentFilterSelect,
                onToggleRemindersFilter = onToggleRemindersFilter,
                onToggleUnreadFilter = onToggleUnreadFilter,
                onClearFilters = onClearFilters
            )
        }

        // 5. Section Header with Animated Count & Active Filter Pill
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedContent(
                        targetState = uiState.filteredJobs.size,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "jobs_header_count"
                    ) { count ->
                        Text(
                            text = "Applied Jobs ($count)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    uiState.filterState.selectedStatus?.let { activeStatus ->
                        val (bg, fg) = activeStatus.badgeColors(isDark)
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { onStatusFilterSelect(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeStatus.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = fg
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear status filter",
                                    tint = fg,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "usr-svc.sjp.gos.pk",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Empty State or Smooth Animated SJP Job Cards
        if (uiState.filteredJobs.isEmpty()) {
            item {
                EmptyJobsCard(
                    hasAnyJobs = uiState.allJobs.isNotEmpty(),
                    hasActiveSession = uiState.sessionState.hasActiveSession,
                    selectedStatus = uiState.filterState.selectedStatus,
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
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                ) {
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
        "Synced ${timeFormatter.format(Date(session.lastPollTimestamp))}"
    } else {
        "Not synced"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hero_aurora")
    val orbShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_shift"
    )
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF042F2E),
                            Color(0xFF064E3B),
                            Color(0xFF0F172A)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF34D399).copy(alpha = 0.20f + 0.08f * orbShift),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.78f + 0.10f * orbShift),
                                size.height * (0.18f + 0.12f * orbShift)
                            ),
                            radius = size.width * 0.58f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.20f - 0.08f * orbShift),
                                size.height * 0.88f
                            ),
                            radius = size.width * 0.48f
                        )
                    )
                }
        ) {
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
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
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
                                "Live Session • Auto-Poll ${session.pollingIntervalMinutes}m"
                            } else {
                                "Login Required • Auto-Poll ${session.pollingIntervalMinutes}m"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = lastSyncLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SJP Pulse Tracker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Fetches all pages from usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate and notifies on status changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !uiState.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color(0xFF022C22)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sync_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync SJP Applications",
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(if (uiState.isSyncing) syncRotation else 0f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isSyncing) "Syncing…" else "Fetch SJP Data",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenPortalTab,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Clickable Status KPI Dashboard:
 * Shows Total Applications plus Shortlisted, Temp Shortlisted, Waiting, Temp Waiting,
 * Offered, Temp Offered, Temp Rejected, Rejected, Under Scrutiny, and Applied.
 * Touching any tab immediately filters and displays the matching applications!
 */
@Composable
private fun InteractiveStatusKpiDashboard(
    uiState: SjpUiState,
    isDark: Boolean,
    onStatusClick: (ApplicationStatus?) -> Unit
) {
    val selectedStatus = uiState.filterState.selectedStatus
    val isAllSelected = selectedStatus == null

    // Primary 8 status tabs always shown in the sleek 4x2 Bento Grid + optional toggle for Scrutiny/Applied
    val primaryGridStatuses = remember {
        listOf(
            ApplicationStatus.OFFERED,
            ApplicationStatus.TEMP_OFFERED,
            ApplicationStatus.SHORTLISTED,
            ApplicationStatus.TEMP_SHORTLISTED,
            ApplicationStatus.WAITING,
            ApplicationStatus.TEMP_WAITING,
            ApplicationStatus.TEMP_REJECTED,
            ApplicationStatus.REJECTED,
            ApplicationStatus.UNDER_SCRUTINY,
            ApplicationStatus.APPLIED
        )
    }

    val totalScale by animateFloatAsState(
        targetValue = if (isAllSelected) 1.0f else 0.985f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "total_card_scale"
    )
    val totalBorderColor by animateColorAsState(
        targetValue = if (isAllSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
        label = "total_border_color"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Full-Width "Total Applications" Interactive Banner Card (Tap to show all 81 applications)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = totalScale
                    scaleY = totalScale
                }
                .clip(RoundedCornerShape(18.dp))
                .clickable { onStatusClick(null) }
                .testTag("kpi_tile_all"),
            shape = RoundedCornerShape(18.dp),
            color = if (isAllSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.55f else 0.75f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(if (isAllSelected) 1.5.dp else 1.dp, totalBorderColor),
            tonalElevation = if (isAllSelected) 4.dp else 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Total Applications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAllSelected) {
                                "Showing all applications • Touch any status tab below to filter"
                            } else {
                                "Touch here to show all ${uiState.totalCount} applications"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedContent(
                    targetState = uiState.totalCount,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "total_kpi_count"
                ) { count ->
                    Surface(
                        color = if (isAllSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAllSelected) Color(0xFF022C22) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 2. Interactive Clickable Bento Grid of All Dedicated Statuses
        // (Offered, Temp Offered, Shortlisted, Temp Shortlisted, Waiting, Temp Waiting, Temp Rejected, Rejected, Under Scrutiny, Applied)
        for (rowItems in primaryGridStatuses.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (status in rowItems) {
                    val count = uiState.countForStatus(status)
                    val isSelected = selectedStatus == status
                    val (badgeBg, badgeFg) = status.badgeColors(isDark)

                    KpiStatusBentoTile(
                        status = status,
                        count = count,
                        accentBg = badgeBg,
                        accentFg = badgeFg,
                        isSelected = isSelected,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_tile_${status.code.lowercase()}"),
                        onClick = { onStatusClick(status) }
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiStatusBentoTile(
    status: ApplicationStatus,
    count: Int,
    accentBg: Color,
    accentFg: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "kpi_tile_scale_${status.code}"
    )
    val barWidth by animateDpAsState(
        targetValue = if (isSelected) 42.dp else 22.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "kpi_bar_width_${status.code}"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) accentBg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
        label = "kpi_bg_${status.code}"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentFg else MaterialTheme.colorScheme.surfaceVariant,
        label = "kpi_border_${status.code}"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentFg)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = status.shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isSelected) accentFg else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "kpi_count_${status.code}"
            ) { targetCount ->
                Surface(
                    color = if (isSelected) accentFg else accentBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = targetCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.Black else accentFg,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSearchAndFilterHub(
    uiState: SjpUiState,
    isDark: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterSelect: (ApplicationStatus?) -> Unit,
    onDepartmentFilterSelect: (String?) -> Unit,
    onToggleRemindersFilter: () -> Unit,
    onToggleUnreadFilter: () -> Unit,
    onClearFilters: () -> Unit
) {
    val filterState = uiState.filterState
    var isStatusMenuExpanded by remember { mutableStateOf(false) }
    var isDeptDropdownExpanded by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isStatusMenuExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "status_chevron"
    )

    val orderedFilterStatuses = remember {
        listOf(
            ApplicationStatus.OFFERED,
            ApplicationStatus.TEMP_OFFERED,
            ApplicationStatus.SHORTLISTED,
            ApplicationStatus.TEMP_SHORTLISTED,
            ApplicationStatus.WAITING,
            ApplicationStatus.TEMP_WAITING,
            ApplicationStatus.TEMP_REJECTED,
            ApplicationStatus.REJECTED,
            ApplicationStatus.UNDER_SCRUTINY,
            ApplicationStatus.APPLIED
        )
    }

    val hasAnyActiveFilter = filterState.selectedStatus != null ||
        filterState.selectedDepartment != null ||
        filterState.onlyUnreadChanges ||
        filterState.onlyWithReminders ||
        filterState.searchQuery.isNotBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Sleek Search Input
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search job title, department, ID, or status…",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search jobs",
                        tint = MaterialTheme.colorScheme.primary
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
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )

            // 2. Modern Status & Department Dropdown Selectors Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedStatus = filterState.selectedStatus
                val statusColors = selectedStatus?.badgeColors(isDark)
                val statusContainerColor = statusColors?.first
                    ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                val statusContentColor = statusColors?.second
                    ?: MaterialTheme.colorScheme.onSurface

                Surface(
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isStatusMenuExpanded = !isStatusMenuExpanded }
                        .testTag("status_filter_dropdown_button"),
                    shape = RoundedCornerShape(14.dp),
                    color = statusContainerColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedStatus != null || isStatusMenuExpanded) {
                            statusContentColor.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedStatus != null) statusContentColor
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "STATUS DROPDOWN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusContentColor.copy(alpha = 0.72f)
                                )
                                Text(
                                    text = selectedStatus?.displayLabel ?: "All Statuses (${uiState.totalCount})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusContentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Status Filter",
                            tint = statusContentColor,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }

                // Department Filter Dropdown Trigger Button
                Box(modifier = Modifier.weight(0.85f)) {
                    val selectedDept = filterState.selectedDepartment
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isDeptDropdownExpanded = true }
                            .testTag("department_filter_dropdown_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedDept != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedDept != null) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DEPARTMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedDept ?: "All (${uiState.availableDepartments.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedDept != null) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Department Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDeptDropdownExpanded,
                        onDismissRequest = { isDeptDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 340.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "All Departments (${uiState.availableDepartments.size})",
                                    fontWeight = if (selectedDept == null) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (selectedDept == null) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                if (selectedDept != null) onDepartmentFilterSelect(null)
                                isDeptDropdownExpanded = false
                            }
                        )
                        HorizontalDivider()
                        for (dept in uiState.availableDepartments) {
                            val isSelected = selectedDept == dept
                            val deptCount = uiState.allJobs.count { it.departmentName == dept }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$dept ($deptCount)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onDepartmentFilterSelect(dept)
                                    isDeptDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 3. Smooth Expandable Status Dropdown Menu
            AnimatedVisibility(
                visible = isStatusMenuExpanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Status to Filter Applications",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { isStatusMenuExpanded = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Close", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        val allSelected = filterState.selectedStatus == null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onStatusFilterSelect(null)
                                    isStatusMenuExpanded = false
                                }
                                .testTag("filter_chip_all"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (allSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (allSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Outlined.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Show All Applications",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = uiState.totalCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        for (rowStatuses in orderedFilterStatuses.chunked(2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (status in rowStatuses) {
                                    val isSelected = filterState.selectedStatus == status
                                    val (badgeBg, badgeFg) = status.badgeColors(isDark)
                                    val count = uiState.countForStatus(status)

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onStatusFilterSelect(status)
                                                isStatusMenuExpanded = false
                                            }
                                            .testTag("filter_chip_${status.code.lowercase()}"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) badgeBg else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) badgeFg else badgeFg.copy(alpha = 0.28f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeFg)
                                                )
                                                Spacer(modifier = Modifier.width(7.dp))
                                                Text(
                                                    text = status.shortLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isSelected) badgeFg else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = badgeBg.copy(alpha = if (isSelected) 0.9f else 0.65f),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeFg,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowStatuses.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 4. Compact Quick-Toggle Strip (Updated Only, Interview Countdown, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterState.onlyUnreadChanges,
                    onClick = onToggleUnreadFilter,
                    label = { Text("Updated Only", maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )

                FilterChip(
                    selected = filterState.onlyWithReminders,
                    onClick = onToggleRemindersFilter,
                    label = { Text("Has Countdown", maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(visible = hasAnyActiveFilter) {
                    TextButton(
                        onClick = {
                            onClearFilters()
                            isStatusMenuExpanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("clear_all_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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
    var isHistoryPreviewExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clickable(onClick = onCardClick)
            .testTag("job_card_${job.applicationId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (job.hasUnreadChange) badgeFg.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(badgeFg)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ID: ${job.applicationId}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (job.hasUnreadChange) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "UPDATED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(badgeFg)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = job.rawStatusString,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = job.jobTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = job.departmentName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
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

                if (statusHistoryEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isHistoryPreviewExpanded = !isHistoryPreviewExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS HISTORY (${statusHistoryEntries.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isHistoryPreviewExpanded) "Show Less" else "Show All",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val displayedEntries = if (isHistoryPreviewExpanded) {
                        statusHistoryEntries
                    } else {
                        statusHistoryEntries.take(3)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (entry in displayedEntries) {
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
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
    selectedStatus: ApplicationStatus?,
    onClearFilters: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenPortalTab: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
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
                    hasAnyJobs && selectedStatus != null -> "0 Applications in '${selectedStatus.displayLabel}'"
                    hasAnyJobs -> "No Applications Match Selected Filter"
                    hasActiveSession -> "Ready to Fetch Your SJP Applications"
                    else -> "Log In to SJP Portal to Load Your Applications"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    hasAnyJobs -> "Touch 'Total Applications' above or tap 'Show All Applications' below to view all your loaded SJP jobs."
                    hasActiveSession -> "Your SJP session is active. Tap 'Fetch SJP Data Now' or open the SJP Login tab to load all pages from usr-svc.sjp.gos.pk."
                    else -> "Sign in once on the SJP Login tab. As soon as you log in, the app will fetch all pages of your applied jobs from usr-svc.sjp.gos.pk."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasAnyJobs) {
                    Button(
                        onClick = onClearFilters,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Show All Applications")
                    }
                } else {
                    Button(
                        onClick = onOpenPortalTab,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open SJP Login")
                    }
                    if (hasActiveSession) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fetch SJP Data Now")
                        }
                    }
                }
            }
        }
    }
}
