package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity
import com.example.data.DocumentDownloadState
import com.example.data.SjpBackendStatusEntry
import com.example.data.SjpDocumentDownloader
import com.example.data.SjpDocumentExtractor
import com.example.data.SjpDocumentRef
import com.example.data.SjpPayloadParser
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
    documentDownloadStates: Map<String, DocumentDownloadState>,
    savedCustomDocBaseUrl: String,
    onDownloadDocument: (SjpDocumentRef, String?) -> Unit,
    onDismiss: () -> Unit,
    onSaveNotesAndReminder: (String, Long?) -> Unit
) {
    var selectedTab by remember(job.applicationId, initialTab) { mutableIntStateOf(initialTab) }
    var notesText by remember(job.applicationId, job.userNotes) { mutableStateOf(job.userNotes) }
    var interviewTs by remember(job.applicationId, job.interviewTimestamp) { mutableStateOf(job.interviewTimestamp) }
    val isDark = isSystemInDarkTheme()
    val (badgeBg, badgeFg) = job.statusEnum.badgeColors(isDark)
    val context = LocalContext.current

    val backendStatusEntries = remember(job.statusHistoryJson) {
        SjpPayloadParser.parseBackendHistoryJson(job.statusHistoryJson)
    }

    val detectedDocuments = remember(job.rawJsonPayload) {
        SjpDocumentExtractor.extractDocuments(job.rawJsonPayload)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 18.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Application ID: ${job.applicationId}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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

            // Status Pill
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
                            text = "CURRENT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg.copy(alpha = 0.8f)
                        )
                        Text(
                            text = job.rawStatusString,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                    Text(
                        text = "Job Offered: ${if (job.jobOffered) "YES" else "NO"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Tabs: Overview & Notes | Status History | Raw JSON
            val historyCount = maxOf(backendStatusEntries.size, jobHistory.size)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Details & Notes") },
                    modifier = Modifier.testTag("detail_tab_overview")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Status History ($historyCount)") },
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
                    detectedDocuments = detectedDocuments,
                    documentDownloadStates = documentDownloadStates,
                    savedCustomDocBaseUrl = savedCustomDocBaseUrl,
                    onDownloadDocument = onDownloadDocument,
                    notesText = notesText,
                    onNotesChange = { notesText = it },
                    interviewTimestamp = interviewTs,
                    onInterviewTimestampChange = { interviewTs = it },
                    onSave = {
                        onSaveNotesAndReminder(notesText, interviewTs)
                    }
                )
                1 -> DetailHistoryTab(
                    backendEntries = backendStatusEntries,
                    localHistory = jobHistory,
                    isDark = isDark
                )
                2 -> DetailRawJsonInspectorTab(
                    applicationId = job.applicationId,
                    rawJson = job.rawJsonPayload,
                    detectedDocuments = detectedDocuments,
                    documentDownloadStates = documentDownloadStates,
                    onDownloadDocument = { doc ->
                        selectedTab = 0
                        onDownloadDocument(doc, null)
                    },
                    onCopyJson = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SJP Raw JSON", job.rawJsonPayload))
                        Toast.makeText(context, "Copied raw JSON to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailOverviewAndNotesTab(
    job: AppliedJobEntity,
    detectedDocuments: List<SjpDocumentRef>,
    documentDownloadStates: Map<String, DocumentDownloadState>,
    savedCustomDocBaseUrl: String,
    onDownloadDocument: (SjpDocumentRef, String?) -> Unit,
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
        // Prominent Appointment Order & PDF Documents Section (auto-extracted from raw JSON)
        if (detectedDocuments.isNotEmpty()) {
            items(
                items = detectedDocuments,
                key = { it.uniqueKey(job.applicationId) }
            ) { doc ->
                val state = documentDownloadStates[doc.uniqueKey(job.applicationId)]
                    ?: DocumentDownloadState.Idle
                AppointmentOrderDocumentCard(
                    doc = doc,
                    downloadState = state,
                    savedCustomDocBaseUrl = savedCustomDocBaseUrl,
                    onProbeAndDownload = { customBase ->
                        onDownloadDocument(doc, customBase)
                    }
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SJP Backend Application Record",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DetailKeyValueRow("Application ID", job.applicationId)
                    DetailKeyValueRow("Current status", job.rawStatusString)
                    DetailKeyValueRow("Job offered", if (job.jobOffered) "YES" else "NO")
                    DetailKeyValueRow("Interview", job.jobInterviewStatus)
                    DetailKeyValueRow("Offer record", if (job.hasOfferRecord) "PRESENT" else "NONE")
                    DetailKeyValueRow(
                        "Appointment order",
                        detectedDocuments.firstOrNull { it.isAppointmentOrder }?.displayFileName
                            ?: if (detectedDocuments.isNotEmpty()) "${detectedDocuments.size} PDF file(s) detected" else "NONE"
                    )
                    DetailKeyValueRow("Rejection reason", job.reasonForRejected)
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
                                text = "Interview / Test Countdown Reminder",
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
                            "Set a target date to display a countdown badge on your dashboard:"
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
                    text = "Personal Preparation Notes",
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = onNotesChange,
                    placeholder = {
                        Text("Add interview preparation notes, document checklist, or remarks…")
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
private fun AppointmentOrderDocumentCard(
    doc: SjpDocumentRef,
    downloadState: DocumentDownloadState,
    savedCustomDocBaseUrl: String,
    onProbeAndDownload: (String?) -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showDiagnostics by remember { mutableStateOf(false) }
    var customBaseInput by remember(savedCustomDocBaseUrl) { mutableStateOf(savedCustomDocBaseUrl) }

    val accentColor = if (doc.isAppointmentOrder) {
        if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val containerBg = if (doc.isAppointmentOrder) {
        if (isDark) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFFECFDF5)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("appointment_order_card_${doc.fieldPath}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF Document",
                            tint = accentColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                        Text(
                            text = "JSON field: ${doc.fieldPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SJP Document Field", doc.rawValue))
                        Toast.makeText(context, "Copied PDF path from JSON", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy raw PDF value",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (doc.isBase64) "Inline Base64 PDF (${doc.rawValue.length} chars)" else doc.rawValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            when (downloadState) {
                is DocumentDownloadState.Idle -> {
                    Button(
                        onClick = { onProbeAndDownload(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = if (isDark && doc.isAppointmentOrder) Color(0xFF022C22) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_appointment_order_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (doc.isAppointmentOrder) "Probe SJP API & Download Appointment Order" else "Probe SJP API & Download PDF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is DocumentDownloadState.Probing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val progress = if (downloadState.totalSteps > 0) {
                            (downloadState.currentStep.toFloat() / downloadState.totalSteps.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accentColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Probing endpoint ${downloadState.currentStep}/${downloadState.totalSteps}: ${downloadState.currentUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is DocumentDownloadState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Saved • ${SjpDocumentDownloader.formatBytes(downloadState.sizeBytes)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                            Text(
                                text = downloadState.savedLocationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Source: ${downloadState.resolvedUrl}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val openIntent = SjpDocumentDownloader.buildOpenPdfIntent(downloadState.fileUri)
                                runCatching {
                                    context.startActivity(openIntent)
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "No PDF viewer installed. File is saved in ${downloadState.savedLocationLabel}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = if (isDark && doc.isAppointmentOrder) Color(0xFF022C22) else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("open_downloaded_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open PDF", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = SjpDocumentDownloader.buildSharePdfIntent(
                                    downloadState.fileUri,
                                    downloadState.fileName
                                )
                                runCatching { context.startActivity(shareIntent) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 48.dp)
                                .testTag("share_downloaded_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }

                is DocumentDownloadState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onProbeAndDownload(customBaseInput.ifBlank { null }) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("retry_probe_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Probe")
                        }

                        OutlinedButton(
                            onClick = { showDiagnostics = !showDiagnostics },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (showDiagnostics) "Hide Log" else "Probe Log (${downloadState.attempts.size})")
                        }
                    }
                }
            }

            // Diagnostics & Custom Base URL override drawer (available anytime or after error/success)
            val probeAttempts = when (downloadState) {
                is DocumentDownloadState.Probing -> downloadState.attempts
                is DocumentDownloadState.Success -> downloadState.attempts
                is DocumentDownloadState.Error -> downloadState.attempts
                DocumentDownloadState.Idle -> emptyList()
            }

            if (downloadState !is DocumentDownloadState.Error) {
                TextButton(
                    onClick = { showDiagnostics = !showDiagnostics },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showDiagnostics) "Hide API Probe Options" else "Custom File Base URL / Probe Log (${probeAttempts.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            AnimatedVisibility(visible = showDiagnostics) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customBaseInput,
                        onValueChange = { customBaseInput = it },
                        label = { Text("Custom SJP File / S3 Base URL (optional)") },
                        placeholder = { Text("e.g. https://usr-svc.sjp.gos.pk/uploads") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_doc_base_url_input")
                    )

                    FilledTonalButton(
                        onClick = { onProbeAndDownload(customBaseInput.trim().ifBlank { null }) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("probe_with_custom_base_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probe & Download with Base URL")
                    }

                    if (probeAttempts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "API PROBE LOG (${probeAttempts.size} tested)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                for (attempt in probeAttempts.takeLast(14)) {
                                    Text(
                                        text = "${if (attempt.succeeded) "[OK]" else "[${attempt.statusCode ?: "ERR"}]"} ${attempt.url} -> ${attempt.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (attempt.succeeded) Color(0xFF6EE7B7) else Color(0xFFCBD5E1)
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

@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun DetailHistoryTab(
    backendEntries: List<SjpBackendStatusEntry>,
    localHistory: List<StatusHistoryEntity>,
    isDark: Boolean
) {
    val fmt = remember { SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.US) }

    if (backendEntries.isEmpty() && localHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No ApplicationStatuses history entries returned for this job.",
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
            if (backendEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "SJP Backend ApplicationStatuses History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(backendEntries) { entry ->
                    val (bg, fg) = ApplicationStatus.fromRawStatus(entry.status).badgeColors(isDark)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.changedAt,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = entry.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                items(localHistory, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.changedAtFormatted.ifBlank { fmt.format(Date(item.detectedAtTimestamp)) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${item.previousRawStatus}  →  ${item.newRawStatus}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
    applicationId: String,
    rawJson: String,
    detectedDocuments: List<SjpDocumentRef>,
    documentDownloadStates: Map<String, DocumentDownloadState>,
    onDownloadDocument: (SjpDocumentRef) -> Unit,
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
        if (detectedDocuments.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Detected PDF Fields in Raw JSON (${detectedDocuments.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        for (doc in detectedDocuments) {
                            val isBusy = documentDownloadStates[doc.uniqueKey(applicationId)] is DocumentDownloadState.Probing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.fieldPath,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = doc.displayFileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onDownloadDocument(doc) },
                                    enabled = !isBusy,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("raw_json_download_pdf_${doc.fieldPath}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download PDF",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isBusy) "Probing…" else "Download PDF")
                                }
                            }
                        }
                    }
                }
            }
        }

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
                        modifier = Modifier.weight(0.38f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Full Raw Application JSON Object",
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
