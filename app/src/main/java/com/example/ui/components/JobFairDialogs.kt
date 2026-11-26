package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusInfoBlue
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils
import com.example.util.JobFairHelper
import com.example.util.PhoneDialer

enum class JobFormMode { CREATE, EDIT, DUPLICATE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobPostFormDialog(
    initialJob: JobPosting,
    mode: JobFormMode,
    isTrustedShop: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (JobPosting) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initialJob) { mutableStateOf(initialJob.title) }
    var workType by remember(initialJob) { mutableStateOf(initialJob.workType) }
    var category by remember(initialJob) { mutableStateOf(initialJob.category) }
    var salaryMinText by remember(initialJob) { mutableStateOf(initialJob.salaryMin?.toLong()?.toString() ?: "") }
    var salaryMaxText by remember(initialJob) { mutableStateOf(initialJob.salaryMax?.toLong()?.toString() ?: "") }
    var salaryPeriod by remember(initialJob) { mutableStateOf(initialJob.salaryPeriod) }
    var hideSalary by remember(initialJob) { mutableStateOf(initialJob.hideSalary) }
    var vacanciesText by remember(initialJob) { mutableStateOf(initialJob.vacancies.coerceAtLeast(1).toString()) }
    var description by remember(initialJob) { mutableStateOf(initialJob.description) }
    var requirements by remember(initialJob) { mutableStateOf(initialJob.requirements) }
    var benefits by remember(initialJob) { mutableStateOf(initialJob.benefits) }
    var address by remember(initialJob) { mutableStateOf(initialJob.address) }
    var reach by remember(initialJob) { mutableStateOf(initialJob.reach) }
    var remote by remember(initialJob) { mutableStateOf(initialJob.remote) }
    var howToApply by remember(initialJob) { mutableStateOf(initialJob.howToApply) }
    var applyPhone by remember(initialJob) { mutableStateOf(initialJob.applyPhone) }
    var applyEmail by remember(initialJob) { mutableStateOf(initialJob.applyEmail) }
    var deadlineMs by remember(initialJob) { mutableStateOf(initialJob.deadline) }
    var startDateMs by remember(initialJob) { mutableStateOf(initialJob.startDate) }
    var expiryDaysText by remember(initialJob) { mutableStateOf(initialJob.expiryDays.coerceIn(1, 365).toString()) }

    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val screenTitle = when (mode) {
        JobFormMode.CREATE -> "Post a job"
        JobFormMode.EDIT -> "Edit job"
        JobFormMode.DUPLICATE -> "Duplicate job"
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(enabled = !isSubmitting) { onDismiss() }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("job_form_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(screenTitle, fontWeight = FontWeight.Bold)
                            if (mode != JobFormMode.EDIT) {
                                Text(
                                    text = if (isTrustedShop) "Trusted shop: publishes Open immediately"
                                    else "Submitted for admin approval",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isSubmitting) onDismiss() },
                            modifier = Modifier.testTag("job_form_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close form")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val activeError = localError ?: errorMessage
                        if (!activeError.isNullOrBlank()) {
                            Text(
                                text = activeError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .testTag("job_form_error_text")
                            )
                        }
                        Button(
                            onClick = {
                                val draft = initialJob.copy(
                                    title = title.trim(),
                                    workType = workType,
                                    category = category.trim(),
                                    salaryMin = if (hideSalary) null else salaryMinText.trim().toDoubleOrNull(),
                                    salaryMax = if (hideSalary) null else salaryMaxText.trim().toDoubleOrNull(),
                                    salaryPeriod = salaryPeriod,
                                    hideSalary = hideSalary,
                                    vacancies = (vacanciesText.trim().toIntOrNull() ?: 1),
                                    description = description.trim(),
                                    requirements = requirements.trim(),
                                    benefits = benefits.trim(),
                                    address = address.trim(),
                                    reach = reach,
                                    remote = remote,
                                    howToApply = howToApply,
                                    applyPhone = applyPhone.trim(),
                                    applyEmail = applyEmail.trim(),
                                    deadline = deadlineMs,
                                    startDate = startDateMs,
                                    expiryDays = expiryDaysText.trim().toIntOrNull() ?: 30
                                )
                                val err = JobFairHelper.validateJobDraft(draft)
                                if (err != null) {
                                    localError = err
                                } else {
                                    localError = null
                                    onSubmit(draft)
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DelivioYellow,
                                contentColor = Color(0xFF1A1A1A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("job_form_submit_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1A1A1A)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = when (mode) {
                                    JobFormMode.EDIT -> "Save Changes"
                                    JobFormMode.DUPLICATE -> "Post Duplicated Job"
                                    JobFormMode.CREATE -> "Post Job"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it; localError = null },
                    label = { Text("Job title * (3–120 chars)") },
                    placeholder = { Text("e.g. Barista, Kitchen Helper, Cashier") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_form_title_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Work type",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JobWorkType.entries.forEach { wt ->
                            FilterChip(
                                selected = workType == wt,
                                onClick = { workType = wt },
                                label = { Text(wt.label) },
                                modifier = Modifier.testTag("job_work_type_${wt.firestoreValue}")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        placeholder = { Text("Select below or type category") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("job_form_category_input")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        JobFairHelper.DEFAULT_CATEGORIES.forEach { cat ->
                            AssistChip(
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Salary card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(14.dp)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Salary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hide salary if negotiable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hide", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = hideSalary,
                                    onCheckedChange = { hideSalary = it },
                                    modifier = Modifier.testTag("job_form_hide_salary_switch")
                                )
                            }
                        }

                        if (!hideSalary) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = salaryMinText,
                                    onValueChange = { salaryMinText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Min (₱)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("job_form_salary_min_input")
                                )
                                OutlinedTextField(
                                    value = salaryMaxText,
                                    onValueChange = { salaryMaxText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Max (₱)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("job_form_salary_max_input")
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SalaryPeriod.entries.forEach { period ->
                                    FilterChip(
                                        selected = salaryPeriod == period,
                                        onClick = { salaryPeriod = period },
                                        label = { Text(period.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = vacanciesText,
                        onValueChange = { vacanciesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Vacancies") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("job_form_vacancies_input")
                    )
                    OutlinedTextField(
                        value = expiryDaysText,
                        onValueChange = { expiryDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Expiry (days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("job_form_expiry_input")
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; localError = null },
                    label = { Text("Description *") },
                    placeholder = { Text("Role responsibilities, shift schedule, day-to-day duties...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_form_description_input")
                )

                OutlinedTextField(
                    value = requirements,
                    onValueChange = { requirements = it },
                    label = { Text("Requirements") },
                    placeholder = { Text("Skills, documents, or experience needed...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_form_requirements_input")
                )

                OutlinedTextField(
                    value = benefits,
                    onValueChange = { benefits = it },
                    label = { Text("Benefits") },
                    placeholder = { Text("Free meals, SSS/PhilHealth, tips, 13th month...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_form_benefits_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Work address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_form_address_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Reach",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JobReach.entries.forEach { r ->
                            FilterChip(
                                selected = reach == r,
                                onClick = { reach = r },
                                label = { Text(r.label) },
                                modifier = Modifier.testTag("job_form_reach_${r.firestoreValue}")
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remote / Work from home", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Allow applicants from outside your branch location",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = remote,
                            onCheckedChange = { remote = it },
                            modifier = Modifier.testTag("job_form_remote_switch")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "How to apply",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JobApplyMethod.entries.forEach { method ->
                            FilterChip(
                                selected = howToApply == method,
                                onClick = { howToApply = method },
                                label = { Text(method.label) },
                                modifier = Modifier.testTag("job_form_apply_${method.firestoreValue}")
                            )
                        }
                    }
                    if (howToApply == JobApplyMethod.PHONE) {
                        OutlinedTextField(
                            value = applyPhone,
                            onValueChange = { applyPhone = it },
                            label = { Text("Contact phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (howToApply == JobApplyMethod.EMAIL) {
                        OutlinedTextField(
                            value = applyEmail,
                            onValueChange = { applyEmail = it },
                            label = { Text("Contact email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedCard(
                        onClick = { showDeadlinePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("job_form_deadline_btn")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Deadline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = deadlineMs?.let { FormatUtils.formatDateOnly(it) } ?: "Optional",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (deadlineMs != null) {
                                TextButton(
                                    onClick = { deadlineMs = null },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    OutlinedCard(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("job_form_start_date_btn")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Start date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startDateMs?.let { FormatUtils.formatDateOnly(it) } ?: "Immediate / Flexible",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (startDateMs != null) {
                                TextButton(
                                    onClick = { startDateMs = null },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadlineMs = pickerState.selectedDateMillis
                    showDeadlinePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDeadlinePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDateMs = pickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApplicantDetailDialog(
    application: JobApplication,
    isReadOnly: Boolean,
    isUpdating: Boolean,
    onUpdateStatusAndNote: (JobApplicationStatus, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStatus by remember(application.id, application.status) {
        mutableStateOf(
            if (application.status == JobApplicationStatus.NEW && !isReadOnly) {
                JobApplicationStatus.VIEWED
            } else {
                application.status
            }
        )
    }
    var employerNote by remember(application.id, application.employerNote) {
        mutableStateOf(application.employerNote)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("applicant_detail_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = application.applicantName.ifBlank { "Applicant" },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = application.jobTitle.ifBlank { "Job Application" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("applicant_detail_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        ApplicationStatusChip(status = currentStatus)
                        Spacer(modifier = Modifier.width(12.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isReadOnly) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Read-only access for staff accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Contact Card: Phone (real dialer) + Email (real email app)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Contact & Applied ${FormatUtils.formatDateTime(application.createdAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (application.applicantPhone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { PhoneDialer.dial(context, application.applicantPhone) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("applicant_call_button")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call applicant", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = application.applicantPhone,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (application.applicantEmail.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { openEmailClient(context, application.applicantEmail, application.jobTitle) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("applicant_email_button")
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = "Email applicant", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = application.applicantEmail,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (application.applicantPhone.isBlank() && application.applicantEmail.isBlank()) {
                            Text(
                                text = "No phone or email provided.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Cover Message
                if (application.message.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Message", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(application.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Skills & Experience
                if (application.skills.isNotEmpty() || application.experience.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (application.skills.isNotEmpty()) {
                                Text("Skills", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    application.skills.forEach { skill ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(skill) }
                                        )
                                    }
                                }
                            }
                            if (application.experience.isNotBlank()) {
                                Text("Experience", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(application.experience, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Resume link
                if (application.resumeUrl.isNotBlank()) {
                    val isValidUrl = JobFairHelper.isSafeWebUrl(application.resumeUrl)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isValidUrl) Modifier.clickable { openResumeUrl(context, application.resumeUrl) }
                                    else Modifier
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Resume / CV", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = application.resumeUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isValidUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isValidUrl) {
                                IconButton(
                                    onClick = { openResumeUrl(context, application.resumeUrl) },
                                    modifier = Modifier.testTag("applicant_resume_link_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open resume")
                                }
                            }
                        }
                    }
                }

                // Status actions
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Application status",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (currentStatus == JobApplicationStatus.WITHDRAWN) {
                            Text(
                                text = "Applicant withdrew this application.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                JobApplicationStatus.MERCHANT_SETTABLE.forEach { st ->
                                    val selected = currentStatus == st
                                    val chipColor = when (st) {
                                        JobApplicationStatus.HIRED -> StatusOpenGreen
                                        JobApplicationStatus.REJECTED -> StatusClosedRed
                                        JobApplicationStatus.INTERVIEW -> StatusInfoBlue
                                        JobApplicationStatus.SHORTLISTED -> DelivioYellow
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    FilterChip(
                                        selected = selected,
                                        enabled = !isReadOnly && !isUpdating,
                                        onClick = {
                                            currentStatus = st
                                            onUpdateStatusAndNote(st, employerNote)
                                        },
                                        label = {
                                            Text(
                                                text = st.label,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.testTag("applicant_status_btn_${st.firestoreValue}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Private note
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Private employer note",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedTextField(
                            value = employerNote,
                            onValueChange = { if (!isReadOnly) employerNote = it },
                            enabled = !isReadOnly,
                            readOnly = isReadOnly,
                            placeholder = { Text("Visible only to your shop team...") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("applicant_employer_note_input")
                        )
                        if (!isReadOnly && currentStatus != JobApplicationStatus.WITHDRAWN) {
                            Button(
                                onClick = { onUpdateStatusAndNote(currentStatus, employerNote) },
                                enabled = !isUpdating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DelivioYellow,
                                    contentColor = Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .heightIn(min = 48.dp)
                                    .testTag("applicant_save_note_button")
                            ) {
                                Text("Save Note", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ApplicationStatusChip(status: JobApplicationStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        JobApplicationStatus.NEW -> DelivioYellow.copy(alpha = 0.35f) to Color(0xFF5D4037)
        JobApplicationStatus.VIEWED -> StatusInfoBlue.copy(alpha = 0.16f) to StatusInfoBlue
        JobApplicationStatus.SHORTLISTED -> StatusWarningAmber.copy(alpha = 0.2f) to Color(0xFFB45309)
        JobApplicationStatus.INTERVIEW -> StatusInfoBlue.copy(alpha = 0.24f) to Color(0xFF1D4ED8)
        JobApplicationStatus.HIRED -> StatusOpenGreen.copy(alpha = 0.2f) to StatusOpenGreen
        JobApplicationStatus.REJECTED -> StatusClosedRed.copy(alpha = 0.16f) to StatusClosedRed
        JobApplicationStatus.WITHDRAWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = status.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun openEmailClient(context: Context, email: String, jobTitle: String) {
    val trimmed = email.trim()
    if (trimmed.isBlank()) return
    val subject = if (jobTitle.isNotBlank()) "Your application for $jobTitle" else "Your job application"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$trimmed")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun openResumeUrl(context: Context, rawUrl: String) {
    if (!JobFairHelper.isSafeWebUrl(rawUrl)) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl.trim())).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
