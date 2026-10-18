package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppliedJobEntity
import com.example.data.StatusHistoryEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailBottomSheet(
    job: AppliedJobEntity,
    jobHistory: List<StatusHistoryEntity>,
    initialTab: Int,
    onDismiss: () -> Unit,
    onSaveNotesAndReminder: (String, Long?) -> Unit
) {
    var selectedTab by remember(job.applicationId, initialTab) { mutableIntStateOf(initialTab) }
    var notesText by remember(job.applicationId, job.userNotes) { mutableStateOf(job.userNotes) }
    var interviewTs by remember(job.applicationId, job.interviewTimestamp) { mutableStateOf(job.interviewTimestamp) }
    val isDark = isSystemInDarkTheme()
    val (badgeBg, badgeFg) = job.statusEnum.badgeColors(isDark)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = job.bpsScale,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${job.applicationId} • Adv: ${job.advertisementNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = job.jobTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = job.departmentName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close detail sheet"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Comparison Pill
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "UNMASKED BACKEND STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${job.statusEnum.displayLabel} (raw: \"${job.rawStatusString}\")",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                    Text(
                        text = "Score: ${job.meritScore}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Tabs: Overview & Notes | Status History | Raw JSON Inspector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview & Notes") },
                    modifier = Modifier.testTag("detail_tab_overview")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audit Trail (${jobHistory.size})") },
                    modifier = Modifier.testTag("detail_tab_history")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Raw JSON") },
                    modifier = Modifier.testTag("detail_tab_json")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> DetailOverviewAndNotesTab(
                    job = job,
                    notesText = notesText,
                    onNotesChange = { notesText = it },
                    interviewTimestamp = interviewTs,
                    onInterviewTimestampChange = { interviewTs = it },
                    onSave = {
                        onSaveNotesAndReminder(notesText, interviewTs)
                    }
                )
                1 -> DetailHistoryTab(jobHistory = jobHistory)
                2 -> DetailRawJsonInspectorTab(
                    rawJson = job.rawJsonPayload,
                    onCopyJson = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SJP Raw JSON", job.rawJsonPayload))
                        Toast.makeText(context, "Copied raw backend JSON to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailOverviewAndNotesTab(
    job: AppliedJobEntity,
    notesText: String,
    onNotesChange: (String) -> Unit,
    interviewTimestamp: Long?,
    onInterviewTimestampChange: (Long?) -> Unit,
    onSave: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }
    val dayMs = 24 * 60 * 60 * 1000L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Backend Scrutiny Remarks & Verification Notes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = job.backendRemarks.ifBlank { "No additional scrutiny remarks in payload." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Portal Frontend Status: \"${job.frontendMaskedStatus}\" • Applied: ${job.appliedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interview / Test Countdown Reminder Picker
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Interview / Written Test Countdown",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        if (interviewTimestamp != null) {
                            TextButton(onClick = { onInterviewTimestampChange(null) }) {
                                Text("Clear")
                            }
                        }
                    }

                    Text(
                        text = if (interviewTimestamp != null) {
                            "Scheduled for: ${dateFormat.format(Date(interviewTimestamp))}"
                        } else {
                            "Set a target date to display a live countdown badge on your dashboard:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { onInterviewTimestampChange(System.currentTimeMillis() + 3 * dayMs) },
                            label = { Text("In 3 Days") }
                        )
                        AssistChip(
                            onClick = { onInterviewTimestampChange(System.currentTimeMillis() + 7 * dayMs) },
                            label = { Text("In 7 Days") }
                        )
                        AssistChip(
                            onClick = { onInterviewTimestampChange(System.currentTimeMillis() + 14 * dayMs) },
                            label = { Text("In 14 Days") }
                        )
                        AssistChip(
                            onClick = { onInterviewTimestampChange(System.currentTimeMillis() + 30 * dayMs) },
                            label = { Text("In 30 Days") }
                        )
                    }
                }
            }
        }

        // Personal Preparation Notes
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Personal Preparation & Document Checklist Notes",
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = onNotesChange,
                    placeholder = {
                        Text("Add interview preparation notes, required PRC/Domicile originals, or committee room details…")
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_notes_input")
                )

                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_job_notes_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Notes & Countdown Reminder")
                }
            }
        }
    }
}

@Composable
private fun DetailHistoryTab(jobHistory: List<StatusHistoryEntity>) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy • hh:mm:ss a", Locale.US) }

    if (jobHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No status transitions recorded for this job yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            items(jobHistory, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = fmt.format(Date(item.detectedAtTimestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${item.previousStatusEnum.displayLabel}  →  ${item.newStatusEnum.displayLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.remarksSnapshot.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun DetailRawJsonInspectorTab(
    rawJson: String,
    onCopyJson: () -> Unit
) {
    val parsedFields = remember(rawJson) {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val obj = JSONObject(rawJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                list.add(k to obj.opt(k).toString())
            }
        } catch (_: Exception) {
        }
        list
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Unmasked Backend JSON Fields (${parsedFields.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                FilledTonalButton(
                    onClick = onCopyJson,
                    modifier = Modifier.testTag("copy_raw_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy JSON",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy JSON")
                }
            }
        }

        // Key-Value Inspector Rows
        items(parsedFields) { (key, value) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.6f)
                    )
                }
            }
        }

        // Full Monospace Raw Payload Block
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Raw JSON Object (api/v2/candidates/application/getAllAppliedJobsByCandidate)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rawJson,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6EE7B7),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}
