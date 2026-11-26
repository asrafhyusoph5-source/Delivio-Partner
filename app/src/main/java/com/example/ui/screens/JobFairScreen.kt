package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.ApplicantDetailDialog
import com.example.ui.components.ApplicationStatusChip
import com.example.ui.components.JobFormMode
import com.example.ui.components.JobPostFormDialog
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils
import com.example.util.JobFairHelper
import com.example.util.JobPostEligibility

enum class JobFairTab(val title: String) {
    MY_JOBS("My jobs"),
    APPLICANTS("Applicants")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFairScreen(
    settings: MerchantSettings,
    jobFairConfig: JobFairPlatformConfig,
    jobs: List<JobPosting>,
    applications: List<JobApplication>,
    isReadOnly: Boolean,
    onCreateJob: (JobPosting, (Boolean, String?) -> Unit) -> Unit,
    onUpdateJob: (String, JobPosting, JobStatus, (Boolean, String?) -> Unit) -> Unit,
    onCloseJob: (String) -> Unit,
    onReopenJob: (String) -> Unit,
    onMarkApplicationViewed: (JobApplication) -> Unit,
    onUpdateApplicationStatusAndNote: (String, JobApplicationStatus, String, (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf(JobFairTab.MY_JOBS) }
    var selectedJobFilterId by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<JobApplicationStatus?>(null) }

    var formState by remember { mutableStateOf<Triple<JobPosting, JobFormMode, JobStatus?>?>(null) }
    var isSubmittingForm by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    var selectedApplicationId by remember { mutableStateOf<String?>(null) }
    var isUpdatingApplicant by remember { mutableStateOf(false) }
    var postNoticeMessage by remember { mutableStateOf<String?>(null) }

    val eligibility = remember(settings.jobsBlocked, jobs, jobFairConfig) {
        JobFairHelper.checkPostEligibility(settings, jobs, jobFairConfig)
    }

    val applicationsCountByJob = remember(applications) {
        applications.groupingBy { it.jobId }.eachCount()
    }

    val jobsById = remember(jobs) {
        jobs.associateBy { it.id }
    }

    val filteredApplications = remember(applications, selectedJobFilterId, selectedStatusFilter) {
        applications
            .filter { app ->
                (selectedJobFilterId == null || app.jobId == selectedJobFilterId) &&
                    (selectedStatusFilter == null || app.status == selectedStatusFilter)
            }
            .sortedByDescending { it.createdAt }
    }

    val selectedApplication = remember(applications, selectedApplicationId) {
        selectedApplicationId?.let { id -> applications.firstOrNull { it.id == id } }
    }

    val shopDefaultAddress = remember(settings.storeLocation) {
        settings.storeLocation.formatted.ifBlank { settings.storeLocation.street }
    }

    fun openPostNewJob() {
        when (eligibility) {
            is JobPostEligibility.Blocked -> {
                postNoticeMessage = eligibility.message
                return
            }
            is JobPostEligibility.LimitReached -> {
                postNoticeMessage = eligibility.message
                return
            }
            JobPostEligibility.Allowed -> {
                postNoticeMessage = null
                formError = null
                formState = Triple(
                    JobPosting(
                        address = shopDefaultAddress,
                        applyPhone = settings.phone,
                        applyEmail = settings.email,
                        expiryDays = 30
                    ),
                    JobFormMode.CREATE,
                    null
                )
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("job_fair_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Job Fair",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isReadOnly) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("job_fair_readonly_badge")
                                ) {
                                    Text(
                                        text = "Read-only",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = settings.displayName.ifBlank { "My Shop" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("job_fair_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isReadOnly && selectedTab == JobFairTab.MY_JOBS) {
                        FilledTonalButton(
                            onClick = { openPostNewJob() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (eligibility is JobPostEligibility.Allowed) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Color(0xFF1A1A1A)
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("post_job_top_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Post a job", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = DelivioYellow,
                        height = 3.dp
                    )
                }
            ) {
                JobFairTab.entries.forEach { tab ->
                    val count = when (tab) {
                        JobFairTab.MY_JOBS -> jobs.size
                        JobFairTab.APPLICANTS -> applications.size
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = "${tab.title} ($count)",
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("job_fair_tab_${tab.name.lowercase()}")
                    )
                }
            }

            if (settings.jobsBlocked) {
                Surface(
                    color = StatusClosedRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("jobs_blocked_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = StatusClosedRed)
                        Text(
                            text = "Posting is blocked. Contact support.",
                            color = StatusClosedRed,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (!postNoticeMessage.isNullOrBlank()) {
                Surface(
                    color = StatusWarningAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("job_post_notice_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = postNoticeMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { postNoticeMessage = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (isReadOnly) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("job_fair_readonly_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Staff view: you can browse jobs and applicants, but only owners can post or update.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when (selectedTab) {
                JobFairTab.MY_JOBS -> {
                    val activeCount = JobFairHelper.countOpenOrPendingJobs(jobs)
                    val maxLimit = jobFairConfig.maxActiveJobsPerShop
                    if (maxLimit != null && maxLimit > 0) {
                        Text(
                            text = "$activeCount of $maxLimit active postings used",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    if (jobs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "No job postings yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isReadOnly) "Job postings for this branch will appear here."
                                    else "Post an opening to start receiving applications from nearby job seekers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isReadOnly) {
                                    Button(
                                        onClick = { openPostNewJob() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DelivioYellow,
                                            contentColor = Color(0xFF1A1A1A)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("empty_post_job_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ Post a job", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(jobs, key = { it.id }) { job ->
                                JobPostingCard(
                                    job = job,
                                    applicantCount = applicationsCountByJob[job.id] ?: 0,
                                    isReadOnly = isReadOnly,
                                    onViewApplicants = {
                                        selectedJobFilterId = job.id
                                        selectedTab = JobFairTab.APPLICANTS
                                    },
                                    onEdit = {
                                        formError = null
                                        formState = Triple(job, JobFormMode.EDIT, job.status)
                                    },
                                    onClose = { onCloseJob(job.id) },
                                    onReopen = {
                                        if (eligibility is JobPostEligibility.Blocked) {
                                            postNoticeMessage = eligibility.message
                                        } else if (eligibility is JobPostEligibility.LimitReached && !job.isActiveForLimit) {
                                            postNoticeMessage = eligibility.message
                                        } else {
                                            onReopenJob(job.id)
                                        }
                                    },
                                    onDuplicate = {
                                        when (eligibility) {
                                            is JobPostEligibility.Blocked -> postNoticeMessage = eligibility.message
                                            is JobPostEligibility.LimitReached -> postNoticeMessage = eligibility.message
                                            JobPostEligibility.Allowed -> {
                                                formError = null
                                                formState = Triple(
                                                    job.copy(
                                                        id = "",
                                                        title = "${job.title} (Copy)",
                                                        status = JobStatus.PENDING,
                                                        rejectionReason = ""
                                                    ),
                                                    JobFormMode.DUPLICATE,
                                                    null
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }

                JobFairTab.APPLICANTS -> {
                    ApplicantsTabContent(
                        jobs = jobs,
                        jobsById = jobsById,
                        applications = filteredApplications,
                        totalApplicationsCount = applications.size,
                        selectedJobFilterId = selectedJobFilterId,
                        selectedStatusFilter = selectedStatusFilter,
                        onSelectJobFilter = { selectedJobFilterId = it },
                        onSelectStatusFilter = { selectedStatusFilter = it },
                        onOpenApplication = { app ->
                            selectedApplicationId = app.id
                            if (!isReadOnly && app.status == JobApplicationStatus.NEW) {
                                onMarkApplicationViewed(app)
                            }
                        }
                    )
                }
            }
        }
    }

    val activeForm = formState
    if (activeForm != null) {
        val (draftJob, mode, existingStatus) = activeForm
        JobPostFormDialog(
            initialJob = draftJob,
            mode = mode,
            isTrustedShop = settings.jobsTrusted,
            isSubmitting = isSubmittingForm,
            errorMessage = formError,
            onSubmit = { submitted ->
                isSubmittingForm = true
                formError = null
                if (mode == JobFormMode.EDIT && draftJob.id.isNotBlank() && existingStatus != null) {
                    onUpdateJob(draftJob.id, submitted, existingStatus) { ok, err ->
                        isSubmittingForm = false
                        if (ok) formState = null else formError = err ?: "Failed to update job"
                    }
                } else {
                    onCreateJob(submitted) { ok, err ->
                        isSubmittingForm = false
                        if (ok) formState = null else formError = err ?: "Failed to post job"
                    }
                }
            },
            onDismiss = {
                if (!isSubmittingForm) {
                    formState = null
                    formError = null
                }
            }
        )
    }

    if (selectedApplication != null) {
        ApplicantDetailDialog(
            application = selectedApplication,
            isReadOnly = isReadOnly,
            isUpdating = isUpdatingApplicant,
            onUpdateStatusAndNote = { targetStatus, note ->
                isUpdatingApplicant = true
                onUpdateApplicationStatusAndNote(selectedApplication.id, targetStatus, note) { _, _ ->
                    isUpdatingApplicant = false
                }
            },
            onDismiss = { selectedApplicationId = null }
        )
    }
}

@Composable
private fun JobPostingCard(
    job: JobPosting,
    applicantCount: Int,
    isReadOnly: Boolean,
    onViewApplicants: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onDuplicate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("job_card_${job.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title.ifBlank { "Untitled position" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JobStatusChip(status = job.status)
                        Text(
                            text = "${job.workType.label} • ${job.reach.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isReadOnly) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("job_menu_button_${job.id}")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Job actions")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                                modifier = Modifier.testTag("job_action_edit_${job.id}")
                            )
                            if (job.status == JobStatus.OPEN || job.status == JobStatus.PENDING) {
                                DropdownMenuItem(
                                    text = { Text("Close") },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onClose()
                                    },
                                    modifier = Modifier.testTag("job_action_close_${job.id}")
                                )
                            }
                            if (job.status == JobStatus.CLOSED) {
                                DropdownMenuItem(
                                    text = { Text("Reopen") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onReopen()
                                    },
                                    modifier = Modifier.testTag("job_action_reopen_${job.id}")
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                },
                                modifier = Modifier.testTag("job_action_duplicate_${job.id}")
                            )
                        }
                    }
                }
            }

            if (job.status == JobStatus.REJECTED && job.rejectionReason.isNotBlank()) {
                Surface(
                    color = StatusClosedRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejection reason: ${job.rejectionReason}",
                        color = StatusClosedRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = "${JobFairHelper.formatSalarySummary(job)} • ${job.vacancies} ${if (job.vacancies == 1) "vacancy" else "vacancies"}" +
                    if (job.remote) " • Remote" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewApplicants() }
                        .testTag("job_applicants_count_${job.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = "$applicantCount ${if (applicantCount == 1) "applicant" else "applicants"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Posted ${FormatUtils.formatDateOnly(job.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JobStatusChip(status: JobStatus) {
    val (bgColor, textColor) = when (status) {
        JobStatus.PENDING -> StatusWarningAmber.copy(alpha = 0.22f) to Color(0xFFB45309)
        JobStatus.OPEN -> StatusOpenGreen.copy(alpha = 0.18f) to StatusOpenGreen
        JobStatus.CLOSED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        JobStatus.REJECTED -> StatusClosedRed.copy(alpha = 0.16f) to StatusClosedRed
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = status.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ApplicantsTabContent(
    jobs: List<JobPosting>,
    jobsById: Map<String, JobPosting>,
    applications: List<JobApplication>,
    totalApplicationsCount: Int,
    selectedJobFilterId: String?,
    selectedStatusFilter: JobApplicationStatus?,
    onSelectJobFilter: (String?) -> Unit,
    onSelectStatusFilter: (JobApplicationStatus?) -> Unit,
    onOpenApplication: (JobApplication) -> Unit
) {
    var jobDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar: Job picker + Status filter chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                val selectedJobLabel = selectedJobFilterId?.let { jobsById[it]?.title } ?: "All jobs"
                OutlinedButton(
                    onClick = { jobDropdownExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("applicants_job_filter_btn")
                ) {
                    Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Job: $selectedJobLabel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = jobDropdownExpanded,
                    onDismissRequest = { jobDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All jobs") },
                        onClick = {
                            onSelectJobFilter(null)
                            jobDropdownExpanded = false
                        }
                    )
                    jobs.forEach { job ->
                        DropdownMenuItem(
                            text = { Text(job.title.ifBlank { "Untitled job" }) },
                            onClick = {
                                onSelectJobFilter(job.id)
                                jobDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStatusFilter == null,
                    onClick = { onSelectStatusFilter(null) },
                    label = { Text("All") },
                    modifier = Modifier.testTag("applicant_filter_all")
                )
                JobApplicationStatus.entries
                    .filter { it != JobApplicationStatus.WITHDRAWN }
                    .forEach { st ->
                        FilterChip(
                            selected = selectedStatusFilter == st,
                            onClick = {
                                onSelectStatusFilter(if (selectedStatusFilter == st) null else st)
                            },
                            label = { Text(st.label) },
                            modifier = Modifier.testTag("applicant_filter_${st.firestoreValue}")
                        )
                    }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

        if (applications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = if (totalApplicationsCount == 0) "No applicants yet" else "No matching applicants",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalApplicationsCount == 0) {
                            "Applications submitted to your open jobs will appear here."
                        } else {
                            "Try clearing the job or status filter."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(applications, key = { it.id }) { app ->
                    val resolvedJobTitle = app.jobTitle.ifBlank { jobsById[app.jobId]?.title ?: "Job Posting" }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenApplication(app) }
                            .testTag("applicant_card_${app.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.applicantName.ifBlank { "Applicant" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ApplicationStatusChip(status = app.status)
                            }
                            Text(
                                text = resolvedJobTitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (app.message.isNotBlank()) {
                                Text(
                                    text = app.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = FormatUtils.formatDateTime(app.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (app.employerNote.isNotBlank()) {
                                    Text(
                                        text = "Has note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
