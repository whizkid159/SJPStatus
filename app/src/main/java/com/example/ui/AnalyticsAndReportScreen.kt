package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity

@Composable
fun AnalyticsAndReportScreen(
    allJobs: List<AppliedJobEntity>
) {
    val isDark = isSystemInDarkTheme()

    val statusDistribution = remember(allJobs) {
        ApplicationStatus.entries
            .sortedByDescending { it.funnelOrder }
            .map { status ->
                status to allJobs.count { it.statusEnum == status }
            }
    }

    val departmentDistribution = remember(allJobs) {
        allJobs.groupBy { it.departmentName }
            .map { (dept, list) -> dept to list.size }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("analytics_list"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Visual Status Funnel Chart Card (All 10 Dedicated Statuses)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Recruitment Pipeline Funnel",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Visual breakdown across all ${allJobs.size} SJP applications (including Temp Shortlisted, Temp Rejected, Temp Waiting, Waiting, and Temp Offered)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val maxCount = (statusDistribution.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        for ((status, count) in statusDistribution) {
                            val (_, fgColor) = status.badgeColors(isDark)
                            val ratio = count.toFloat() / maxCount.toFloat()

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(fgColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = status.displayLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "$count (${if (allJobs.isNotEmpty()) (count * 100 / allJobs.size) else 0}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                ) {
                                    drawRoundRect(
                                        color = fgColor.copy(alpha = 0.16f),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(12f, 12f)
                                    )
                                    val barWidth = (size.width * ratio).coerceAtLeast(if (count > 0) 18f else 0f)
                                    if (barWidth > 0f) {
                                        drawRoundRect(
                                            color = fgColor,
                                            topLeft = Offset.Zero,
                                            size = Size(barWidth, size.height),
                                            cornerRadius = CornerRadius(12f, 12f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Department Breakdown Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Government of Sindh Departments",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (departmentDistribution.isEmpty()) {
                        Text(
                            text = "No department data available. Log in to sjp.gos.pk to view your department breakdown.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((dept, count) in departmentDistribution) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dept,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "$count post(s)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
