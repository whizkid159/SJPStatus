package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.StatusHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    historyList: List<StatusHistoryEntity>,
    unreadCount: Int,
    onMarkAllRead: () -> Unit,
    onSyncNow: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("timeline_list"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SJP Status History & Live Diffs",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shows ApplicationStatuses history returned by usr-svc.sjp.gos.pk and live status transitions detected during background polling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("timeline_sync_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Poll SJP Statuses Now")
                        }

                        if (unreadCount > 0) {
                            OutlinedButton(
                                onClick = onMarkAllRead,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("mark_all_read_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Read ($unreadCount)")
                            }
                        }
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Status History Loaded Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Once your applications are fetched from sjp.gos.pk, their full ApplicationStatuses history and future changes will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(historyList, key = { it.id }) { item ->
                val (oldBg, oldFg) = item.previousStatusEnum.badgeColors(isDark)
                val (newBg, newFg) = item.newStatusEnum.badgeColors(isDark)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (item.isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD97706))
                                    )
                                }
                                Text(
                                    text = "App ID: ${item.applicationId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = item.changedAtFormatted.ifBlank {
                                    dateFormatter.format(Date(item.detectedAtTimestamp))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.jobTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.departmentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = oldBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, oldFg.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = item.previousRawStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = oldFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Transitioned to",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Surface(
                                color = newBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, newFg.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = item.newRawStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = newFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (item.remarksSnapshot.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.remarksSnapshot,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
