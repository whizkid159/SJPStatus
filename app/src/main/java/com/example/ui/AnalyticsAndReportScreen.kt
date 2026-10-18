package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity

@Composable
fun AnalyticsAndReportScreen(
    allJobs: List<AppliedJobEntity>,
    onGenerateCsv: (List<AppliedJobEntity>, Boolean, Boolean) -> String,
    onGenerateTextReport: (List<AppliedJobEntity>, Boolean, Boolean) -> String
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var reportStatusFilter by remember { mutableStateOf<ApplicationStatus?>(null) }
    var includeRemarks by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(true) }
    var isCsvFormat by remember { mutableStateOf(false) }

    val filteredForReport = remember(allJobs, reportStatusFilter) {
        if (reportStatusFilter == null) allJobs
        else allJobs.filter { it.statusEnum == reportStatusFilter }
    }

    val generatedReportText = remember(
        filteredForReport,
        includeRemarks,
        includeNotes,
        isCsvFormat
    ) {
        if (isCsvFormat) {
            onGenerateCsv(filteredForReport, includeRemarks, includeNotes)
        } else {
            onGenerateTextReport(filteredForReport, includeRemarks, includeNotes)
        }
    }

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
        // 1. Visual Status Funnel Chart Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recruitment Pipeline Funnel",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Visual breakdown of hidden backend statuses across ${allJobs.size} applications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val maxCount = (statusDistribution.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((status, count) in statusDistribution) {
                            val (_, fgColor) = status.badgeColors(isDark)
                            val ratio = count.toFloat() / maxCount.toFloat()

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = status.displayLabel,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "$count (${if (allJobs.isNotEmpty()) (count * 100 / allJobs.size) else 0}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
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
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Government of Sindh Departments",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((dept, count) in departmentDistribution) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$count post(s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Customizable Reporting Tool
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customizable Status Report Builder",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Filter by status, include scrutiny remarks or personal notes, and export as CSV or Executive Text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Format Toggle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isCsvFormat,
                            onClick = { isCsvFormat = false },
                            label = { Text("Executive Summary") },
                            modifier = Modifier.testTag("report_format_summary")
                        )
                        FilterChip(
                            selected = isCsvFormat,
                            onClick = { isCsvFormat = true },
                            label = { Text("CSV Spreadsheet") },
                            modifier = Modifier.testTag("report_format_csv")
                        )
                    }

                    // Status Filter Row
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = reportStatusFilter == null,
                            onClick = { reportStatusFilter = null },
                            label = { Text("All (${allJobs.size})") }
                        )
                        for (status in listOf(
                            ApplicationStatus.SHORTLISTED,
                            ApplicationStatus.TEMP_SHORTLISTED,
                            ApplicationStatus.OFFERED,
                            ApplicationStatus.TEMP_OFFERED,
                            ApplicationStatus.REJECTED
                        )) {
                            FilterChip(
                                selected = reportStatusFilter == status,
                                onClick = {
                                    reportStatusFilter = if (reportStatusFilter == status) null else status
                                },
                                label = { Text(status.shortLabel) }
                            )
                        }
                    }

                    // Checkboxes for optional columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeRemarks,
                            onCheckedChange = { includeRemarks = it }
                        )
                        Text(
                            text = "Scrutiny Remarks",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Checkbox(
                            checked = includeNotes,
                            onCheckedChange = { includeNotes = it }
                        )
                        Text(
                            text = "Personal Notes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Monospace Preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    ) {
                        Text(
                            text = generatedReportText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6EE7B7),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (isCsvFormat) "text/csv" else "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "SJP Pulse Application Status Report")
                                    putExtra(Intent.EXTRA_TEXT, generatedReportText)
                                }
                                context.startActivity(
                                    Intent.createChooser(sendIntent, "Export SJP Status Report")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("export_report_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Report")
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("SJP Report", generatedReportText)
                                )
                                Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("copy_report_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}
