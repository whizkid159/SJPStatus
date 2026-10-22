package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*

@Composable
fun JobRecommendationsScreen(
    uiState: SjpUiState,
    onSyncJobs: () -> Unit,
    onUpdateProfile: (CandidateProfile) -> Unit,
    onToggleOnlyEligible: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectJobForApply: (EligibilityEvaluation) -> Unit,
    onOpenLoginTab: () -> Unit
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }
    val profile = uiState.sessionState.candidateProfile

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("recommendations_list"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Candidate Education & Eligibility Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Education & Eligibility Matcher",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (profile.isSyncedFromPortal) {
                                        "Auto-synced from SJP Profile • Tap Edit to customize"
                                    } else {
                                        "Matching jobs to your qualification & domicile"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.testTag("edit_eligibility_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Eligibility Criteria"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EligibilityProfileSummaryRow("Qualification", profile.highestDegreeLevel)
                            EligibilityProfileSummaryRow("Major / Field", profile.majorField)
                            EligibilityProfileSummaryRow("Domicile", "${profile.domicileDistrict} (${profile.domicileQuota})")
                            EligibilityProfileSummaryRow("Age & Exp", "${profile.ageYears} yrs • ${profile.experienceYears} yr(s) experience")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncJobs,
                            enabled = !uiState.isSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sync_open_jobs_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.isSyncing) "Syncing SJP…" else "Refresh SJP Vacancies")
                        }

                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Criteria")
                        }
                    }
                }
            }
        }

        // 2. Search & Filter Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.openJobsSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search SJP vacancies by post, department, BPS, or major…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.openJobsSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_jobs_search_input")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.onlyEligibleFilter,
                        onClick = { onToggleOnlyEligible(true) },
                        label = { Text("Matched to My Education") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = !uiState.onlyEligibleFilter,
                        onClick = { onToggleOnlyEligible(false) },
                        label = { Text("All Synced SJP Posts") }
                    )
                }
            }
        }

        // 3. Recommended Jobs List or Empty Prompt
        if (uiState.evaluatedOpenJobs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (uiState.sessionState.hasActiveSession) {
                                "No Matching SJP Vacancies Found"
                            } else {
                                "Sign In to Sync Live SJP Vacancies"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.sessionState.hasActiveSession) {
                                "Tap 'All Synced SJP Posts' or adjust your Education & Major criteria above, or tap 'Refresh SJP Vacancies' to poll usr-svc.sjp.gos.pk."
                            } else {
                                "Log in with your SJP CNIC and Password on the Account tab so the app can sync live vacancies and match them to your education."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!uiState.sessionState.hasActiveSession) {
                                Button(onClick = onOpenLoginTab) {
                                    Text("Sign In with CNIC & Password")
                                }
                            } else {
                                Button(onClick = { onToggleOnlyEligible(false) }) {
                                    Text("Show All Posts")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(
                items = uiState.evaluatedOpenJobs,
                key = { it.job.jobId }
            ) { evaluation ->
                RecommendedJobCard(
                    evaluation = evaluation,
                    isApplying = uiState.isApplyingJobId == evaluation.job.jobId,
                    onReviewAndApply = { onSelectJobForApply(evaluation) }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        EditCandidateProfileDialog(
            initialProfile = profile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updated ->
                onUpdateProfile(updated)
                showEditProfileDialog = false
            }
        )
    }
}

@Composable
private fun EligibilityProfileSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun RecommendedJobCard(
    evaluation: EligibilityEvaluation,
    isApplying: Boolean,
    onReviewAndApply: () -> Unit
) {
    val job = evaluation.job
    val (matchBg, matchFg, matchLabel) = when {
        evaluation.isAlreadyApplied -> Triple(
            Color(0xFFDBEAFE),
            Color(0xFF1D4ED8),
            "ALREADY APPLIED"
        )
        evaluation.isEligible -> Triple(
            Color(0xFFD1FAE5),
            Color(0xFF047857),
            "${evaluation.matchScorePercent}% MATCH • ELIGIBLE"
        )
        else -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFFB45309),
            "${evaluation.matchScorePercent}% MATCH • CHECK CRITERIA"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReviewAndApply)
            .testTag("open_job_card_${job.jobId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${job.bpsScale} • ${job.totalPositions} Post(s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = matchBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = matchLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = matchFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = job.jobTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = job.departmentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Required: ${job.requiredDegreeLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Discipline: ${job.requiredMajor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Domicile: ${job.domicileQuota} • Age: ${job.minAge}–${job.maxAge} yrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Closing: ${job.closingDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onReviewAndApply,
                    enabled = !isApplying,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("apply_button_${job.jobId}")
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Applying…")
                    } else {
                        Icon(
                            imageVector = if (evaluation.isAlreadyApplied) Icons.Default.CheckCircle else Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (evaluation.isAlreadyApplied) "View Match" else "Check & Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EligibilityAndApplyBottomSheet(
    evaluation: EligibilityEvaluation,
    onDismiss: () -> Unit,
    onConfirmApply: () -> Unit
) {
    val job = evaluation.job
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${job.bpsScale} • Eligibility Match ${evaluation.matchScorePercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = job.jobTitle,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = job.departmentName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Eligibility Match Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (check in evaluation.checks) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (check.passed) {
                            Color(0xFFD1FAE5).copy(alpha = 0.45f)
                        } else {
                            Color(0xFFFEE2E2).copy(alpha = 0.45f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (check.passed) Color(0xFF047857) else Color(0xFFB91C1C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = check.criterion,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Required: ${check.jobRequirement}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Your Profile: ${check.candidateValue}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (evaluation.isAlreadyApplied) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDBEAFE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You have already applied to this post (Application ID: ${evaluation.appliedApplicationId ?: "Tracked"}). Its status is being monitored on your Status Radar.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Button(
                    onClick = onConfirmApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("confirm_submit_application_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm & Submit Application on SJP")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EditCandidateProfileDialog(
    initialProfile: CandidateProfile,
    onDismiss: () -> Unit,
    onSave: (CandidateProfile) -> Unit
) {
    var selectedDegree by remember { mutableStateOf(initialProfile.highestDegreeLevel) }
    var majorField by remember { mutableStateOf(initialProfile.majorField) }
    var domicileDistrict by remember { mutableStateOf(initialProfile.domicileDistrict) }
    var domicileQuota by remember { mutableStateOf(initialProfile.domicileQuota) }
    var ageInput by remember { mutableStateOf(initialProfile.ageYears.toString()) }
    var expInput by remember { mutableStateOf(initialProfile.experienceYears.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Education & Eligibility Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Highest Education Qualification:",
                    style = MaterialTheme.typography.labelLarge
                )
                for (edu in EducationLevel.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDegree = edu.label }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedDegree == edu.label,
                            onClick = { selectedDegree = edu.label }
                        )
                        Text(
                            text = edu.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = majorField,
                    onValueChange = { majorField = it },
                    label = { Text("Major / Discipline (e.g., Computer Science / IT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = domicileDistrict,
                    onValueChange = { domicileDistrict = it },
                    label = { Text("Domicile District (e.g., Hyderabad / Karachi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = domicileQuota,
                    onValueChange = { domicileQuota = it },
                    label = { Text("Domicile Quota (Urban / Rural / All Sindh)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { ageInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Age (Years)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expInput,
                        onValueChange = { expInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Experience (Yrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            highestDegreeLevel = selectedDegree,
                            majorField = majorField.ifBlank { "General" },
                            domicileDistrict = domicileDistrict.ifBlank { "Sindh" },
                            domicileQuota = domicileQuota.ifBlank { "All Sindh" },
                            ageYears = ageInput.toIntOrNull() ?: 27,
                            experienceYears = expInput.toIntOrNull() ?: 0
                        )
                    )
                }
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
