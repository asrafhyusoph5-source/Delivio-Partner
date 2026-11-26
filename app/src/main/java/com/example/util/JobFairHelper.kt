package com.example.util

import com.example.data.model.JobApplication
import com.example.data.model.JobApplicationStatus
import com.example.data.model.JobApplyMethod
import com.example.data.model.JobFairPlatformConfig
import com.example.data.model.JobPosting
import com.example.data.model.JobReach
import com.example.data.model.JobStatus
import com.example.data.model.JobWorkType
import com.example.data.model.MerchantSettings
import com.example.data.model.SalaryPeriod
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

sealed class JobPostEligibility {
    data object Allowed : JobPostEligibility()
    data class Blocked(val message: String) : JobPostEligibility()
    data class LimitReached(val currentCount: Int, val maxAllowed: Int, val message: String) : JobPostEligibility()
}

/**
 * Schema-tolerant parsers, business rules, and Firestore payload builders for Job Fair.
 * Matches /firestore.rules for `jobs`, `jobApplications`, and `merchants`.
 */
object JobFairHelper {

    val DEFAULT_CATEGORIES = listOf(
        "Kitchen & Culinary",
        "Cashier & Counter",
        "Delivery & Rider",
        "Barista & Cafe",
        "Store Clerk & Stock",
        "Manager & Supervisor",
        "Cleaning & Maintenance",
        "Customer Service",
        "Other"
    )

    fun parsePlatformConfig(data: Map<String, Any?>?): JobFairPlatformConfig {
        if (data == null) return JobFairPlatformConfig(enabled = true, maxActiveJobsPerShop = null)
        return try {
            val servicesMap = OrderDocumentParser.safeMap(data["services"])
            val rawJobFair = servicesMap["jobfair"]
                ?: servicesMap["jobFair"]
                ?: servicesMap["job_fair"]

            val enabled = when (rawJobFair) {
                null -> true
                is Boolean -> rawJobFair
                is Number -> rawJobFair.toDouble() != 0.0
                is String -> OrderDocumentParser.safeBoolean(rawJobFair, default = true)
                is Map<*, *> -> {
                    val m = OrderDocumentParser.safeMap(rawJobFair)
                    val flag = m["enabled"] ?: m["active"] ?: m["on"]
                    if (flag != null) OrderDocumentParser.safeBoolean(flag, default = true) else true
                }
                else -> true
            }

            val jobFairMap = OrderDocumentParser.safeMap(rawJobFair)
            val jobsSectionMap = OrderDocumentParser.safeMap(data["jobFair"])
                .ifEmpty { OrderDocumentParser.safeMap(data["jobfair"]) }
                .ifEmpty { OrderDocumentParser.safeMap(data["jobs"]) }

            val limitCandidates = listOf(
                jobFairMap["maxOpenJobs"],
                jobFairMap["maxActiveJobs"],
                jobFairMap["maxJobsPerShop"],
                jobFairMap["maxJobsPerMerchant"],
                jobFairMap["maxJobs"],
                jobFairMap["limit"],
                jobsSectionMap["maxOpenJobs"],
                jobsSectionMap["maxActiveJobs"],
                jobsSectionMap["maxJobsPerShop"],
                jobsSectionMap["maxJobsPerMerchant"],
                jobsSectionMap["maxJobs"],
                jobsSectionMap["limit"],
                data["jobFairMaxOpenJobs"],
                data["jobFairMaxActiveJobs"],
                data["jobFairMaxJobs"],
                data["jobfairMaxJobs"],
                data["maxOpenJobsPerMerchant"],
                data["maxOpenJobsPerShop"],
                data["maxActiveJobsPerShop"],
                data["maxJobsPerMerchant"],
                data["maxJobsPerShop"],
                data["jobsMaxOpen"],
                data["jobsLimit"]
            )
            val maxJobs = limitCandidates
                .mapNotNull { FormatUtils.parseInt(it) }
                .firstOrNull { it > 0 }

            JobFairPlatformConfig(
                enabled = enabled,
                maxActiveJobsPerShop = maxJobs
            )
        } catch (_: Throwable) {
            JobFairPlatformConfig(enabled = true, maxActiveJobsPerShop = null)
        }
    }

    fun parseJobSnapshotSafe(
        doc: DocumentSnapshot,
        fallbackMerchantId: String = "",
        onParseError: (docId: String, path: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): JobPosting? = parseJobDataSafe(
        docId = doc.id,
        fallbackMerchantId = fallbackMerchantId,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parseJobDataSafe(
        docId: String,
        fallbackMerchantId: String = "",
        onParseError: (docId: String, path: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): JobPosting? {
        return try {
            val data = dataProvider() ?: return null
            parseJobMap(docId = docId, data = data, fallbackMerchantId = fallbackMerchantId)
        } catch (t: Throwable) {
            onParseError(docId, "jobs/$docId", t)
            null
        }
    }

    fun parseJobMap(
        docId: String,
        data: Map<String, Any?>,
        fallbackMerchantId: String = ""
    ): JobPosting {
        val salaryMap = OrderDocumentParser.safeMap(data["salary"])
        val applyMap = OrderDocumentParser.safeMap(data["apply"])

        val salaryMin = FormatUtils.parseDouble(
            data["salaryMin"] ?: salaryMap["min"]
        )?.takeIf { it >= 0.0 }
        val salaryMax = FormatUtils.parseDouble(
            data["salaryMax"] ?: salaryMap["max"]
        )?.takeIf { it >= 0.0 }
        val salaryPeriodRaw = OrderDocumentParser.safeString(
            data["salaryPeriod"] ?: salaryMap["period"]
        )
        val hideSalary = OrderDocumentParser.safeBoolean(
            data["hideSalary"] ?: salaryMap["hide"] ?: salaryMap["hidden"],
            default = false
        )

        val howToApplyRaw = OrderDocumentParser.safeString(
            data["howToApply"] ?: data["applyMethod"] ?: applyMap["method"] ?: applyMap["type"]
        )
        val applyPhone = OrderDocumentParser.safeString(
            data["applyPhone"] ?: applyMap["phone"]
        )?.trim().orEmpty()
        val applyEmail = OrderDocumentParser.safeString(
            data["applyEmail"] ?: applyMap["email"]
        )?.trim().orEmpty()

        val createdAtMs = FormatUtils.parseTimestamp(data["createdAt"]) ?: System.currentTimeMillis()
        val updatedAtMs = FormatUtils.parseTimestamp(data["updatedAt"]) ?: createdAtMs
        val expiresAtMs = FormatUtils.parseTimestamp(data["expiresAt"] ?: data["expiryAt"])
        val rawExpiryDays = FormatUtils.parseInt(data["expiryDays"] ?: data["expiry"])
        val resolvedExpiryDays = when {
            rawExpiryDays != null && rawExpiryDays > 0 -> rawExpiryDays
            expiresAtMs != null && expiresAtMs > createdAtMs -> {
                ((expiresAtMs - createdAtMs) / 86_400_000L).toInt().coerceAtLeast(1)
            }
            else -> 30
        }

        val rejectionReason = (OrderDocumentParser.safeString(data["rejectionReason"])
            ?: OrderDocumentParser.safeString(data["rejectReason"])
            ?: OrderDocumentParser.safeString(data["adminNote"])
            ?: "").trim()

        return JobPosting(
            id = docId,
            merchantId = (OrderDocumentParser.safeString(data["merchantId"]) ?: fallbackMerchantId).trim(),
            company = (OrderDocumentParser.safeString(data["company"])
                ?: OrderDocumentParser.safeString(data["merchantName"])
                ?: "").trim(),
            cityId = (OrderDocumentParser.safeString(data["cityId"]) ?: "").trim(),
            cityName = (OrderDocumentParser.safeString(data["cityName"])
                ?: OrderDocumentParser.safeString(data["city"])
                ?: "").trim(),
            title = (OrderDocumentParser.safeString(data["title"]) ?: "").trim(),
            workType = JobWorkType.fromString(
                OrderDocumentParser.safeString(data["workType"] ?: data["employmentType"] ?: data["type"])
            ),
            category = (OrderDocumentParser.safeString(data["category"]) ?: "").trim(),
            salaryMin = salaryMin,
            salaryMax = salaryMax,
            salaryPeriod = SalaryPeriod.fromString(salaryPeriodRaw),
            hideSalary = hideSalary,
            vacancies = (FormatUtils.parseInt(data["vacancies"]) ?: 1).coerceAtLeast(1),
            description = (OrderDocumentParser.safeString(data["description"]) ?: "").trim(),
            requirements = (OrderDocumentParser.safeString(data["requirements"]) ?: "").trim(),
            benefits = (OrderDocumentParser.safeString(data["benefits"]) ?: "").trim(),
            address = (OrderDocumentParser.safeString(data["address"])
                ?: OrderDocumentParser.safeString(data["location"])
                ?: "").trim(),
            reach = JobReach.fromString(OrderDocumentParser.safeString(data["reach"] ?: data["visibility"])),
            remote = OrderDocumentParser.safeBoolean(data["remote"] ?: data["isRemote"], default = false),
            howToApply = JobApplyMethod.fromString(howToApplyRaw),
            applyPhone = applyPhone,
            applyEmail = applyEmail,
            deadline = FormatUtils.parseTimestamp(data["deadline"] ?: data["applicationDeadline"]),
            startDate = FormatUtils.parseTimestamp(data["startDate"]),
            expiryDays = resolvedExpiryDays,
            expiresAt = expiresAtMs,
            status = JobStatus.fromString(OrderDocumentParser.safeString(data["status"])),
            rejectionReason = rejectionReason,
            postedBy = (OrderDocumentParser.safeString(data["postedBy"]) ?: "merchant").trim(),
            featured = OrderDocumentParser.safeBoolean(data["featured"], default = false),
            createdAt = createdAtMs,
            updatedAt = updatedAtMs
        )
    }

    fun parseApplicationSnapshotSafe(
        doc: DocumentSnapshot,
        fallbackMerchantId: String = "",
        onParseError: (docId: String, path: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): JobApplication? = parseApplicationDataSafe(
        docId = doc.id,
        fallbackMerchantId = fallbackMerchantId,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parseApplicationDataSafe(
        docId: String,
        fallbackMerchantId: String = "",
        onParseError: (docId: String, path: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): JobApplication? {
        return try {
            val data = dataProvider() ?: return null
            parseApplicationMap(docId = docId, data = data, fallbackMerchantId = fallbackMerchantId)
        } catch (t: Throwable) {
            onParseError(docId, "jobApplications/$docId", t)
            null
        }
    }

    fun parseApplicationMap(
        docId: String,
        data: Map<String, Any?>,
        fallbackMerchantId: String = ""
    ): JobApplication {
        val rawSkills = data["skills"]
        val skills = when (rawSkills) {
            is List<*> -> rawSkills.mapNotNull { OrderDocumentParser.safeString(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is String -> rawSkills.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

        val createdAtMs = FormatUtils.parseTimestamp(data["createdAt"]) ?: System.currentTimeMillis()
        val updatedAtMs = FormatUtils.parseTimestamp(data["updatedAt"]) ?: createdAtMs

        return JobApplication(
            id = docId,
            jobId = (OrderDocumentParser.safeString(data["jobId"]) ?: "").trim(),
            jobMerchantId = (OrderDocumentParser.safeString(data["jobMerchantId"])
                ?: OrderDocumentParser.safeString(data["merchantId"])
                ?: fallbackMerchantId).trim(),
            jobTitle = (OrderDocumentParser.safeString(data["jobTitle"]) ?: "").trim(),
            applicantId = (OrderDocumentParser.safeString(data["applicantId"])
                ?: OrderDocumentParser.safeString(data["userId"])
                ?: "").trim(),
            applicantName = (OrderDocumentParser.safeString(data["applicantName"])
                ?: OrderDocumentParser.safeString(data["name"])
                ?: "Applicant").trim(),
            applicantPhone = (OrderDocumentParser.safeString(data["applicantPhone"])
                ?: OrderDocumentParser.safeString(data["phone"])
                ?: "").trim(),
            applicantEmail = (OrderDocumentParser.safeString(data["applicantEmail"])
                ?: OrderDocumentParser.safeString(data["email"])
                ?: "").trim(),
            message = (OrderDocumentParser.safeString(data["message"])
                ?: OrderDocumentParser.safeString(data["coverNote"])
                ?: OrderDocumentParser.safeString(data["note"])
                ?: "").trim(),
            skills = skills,
            experience = (OrderDocumentParser.safeString(data["experience"]) ?: "").trim(),
            resumeUrl = (OrderDocumentParser.safeString(data["resumeUrl"])
                ?: OrderDocumentParser.safeString(data["resume"])
                ?: "").trim(),
            status = JobApplicationStatus.fromString(OrderDocumentParser.safeString(data["status"])),
            employerNote = (OrderDocumentParser.safeString(data["employerNote"]) ?: "").trim(),
            createdAt = createdAtMs,
            updatedAt = updatedAtMs
        )
    }

    fun countOpenOrPendingJobs(jobs: List<JobPosting>): Int {
        return jobs.count { it.isActiveForLimit }
    }

    fun checkPostEligibility(
        settings: MerchantSettings,
        jobs: List<JobPosting>,
        config: JobFairPlatformConfig
    ): JobPostEligibility {
        if (settings.jobsBlocked) {
            return JobPostEligibility.Blocked("Posting is blocked. Contact support.")
        }
        val maxLimit = config.maxActiveJobsPerShop
        if (maxLimit != null && maxLimit > 0) {
            val currentActive = countOpenOrPendingJobs(jobs)
            if (currentActive >= maxLimit) {
                return JobPostEligibility.LimitReached(
                    currentCount = currentActive,
                    maxAllowed = maxLimit,
                    message = "You have reached the limit of $maxLimit open or pending jobs. Close an existing job first."
                )
            }
        }
        return JobPostEligibility.Allowed
    }

    fun initialJobStatus(settings: MerchantSettings): JobStatus {
        return if (settings.jobsTrusted) JobStatus.OPEN else JobStatus.PENDING
    }

    fun reopenJobStatus(settings: MerchantSettings): JobStatus {
        return if (settings.jobsTrusted) JobStatus.OPEN else JobStatus.PENDING
    }

    fun validateJobDraft(draft: JobPosting): String? {
        val cleanTitle = draft.title.trim()
        if (cleanTitle.length < 3) return "Job title must be at least 3 characters."
        if (cleanTitle.length > 120) return "Job title must be 120 characters or fewer."
        if (draft.description.isBlank()) return "Please add a job description."
        if (!draft.hideSalary && draft.salaryMin != null && draft.salaryMax != null && draft.salaryMin > draft.salaryMax) {
            return "Minimum salary cannot exceed maximum salary."
        }
        if (draft.vacancies < 1) return "Vacancies must be at least 1."
        if (draft.expiryDays < 1 || draft.expiryDays > 365) return "Expiry must be between 1 and 365 days."
        return null
    }

    fun buildCreateJobPayload(
        draft: JobPosting,
        settings: MerchantSettings,
        merchantId: String,
        cityName: String,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, Any> {
        val status = initialJobStatus(settings)
        val expiryDays = draft.expiryDays.coerceIn(1, 365)
        val expiresAtMs = nowMs + expiryDays * 86_400_000L
        val companyName = settings.displayName.ifBlank { settings.storeName }.trim()
        val resolvedCityId = settings.cityId.trim().ifBlank { "taguig" }
        val resolvedCityName = cityName.trim().ifBlank {
            resolvedCityId.replaceFirstChar { it.uppercase() }
        }

        val payload = hashMapOf<String, Any>(
            "merchantId" to merchantId,
            "postedBy" to "merchant",
            "status" to status.firestoreValue,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "company" to companyName,
            "cityId" to resolvedCityId,
            "cityName" to resolvedCityName,
            "title" to draft.title.trim().take(120),
            "workType" to draft.workType.firestoreValue,
            "category" to draft.category.trim(),
            "salaryPeriod" to draft.salaryPeriod.firestoreValue,
            "hideSalary" to draft.hideSalary,
            "vacancies" to draft.vacancies.coerceAtLeast(1),
            "description" to draft.description.trim(),
            "requirements" to draft.requirements.trim(),
            "benefits" to draft.benefits.trim(),
            "address" to draft.address.trim(),
            "reach" to draft.reach.firestoreValue,
            "remote" to draft.remote,
            "howToApply" to draft.howToApply.firestoreValue,
            "applyPhone" to draft.applyPhone.trim(),
            "applyEmail" to draft.applyEmail.trim(),
            "expiryDays" to expiryDays,
            "expiresAt" to Timestamp(Date(expiresAtMs))
        )

        val salaryMap = hashMapOf<String, Any>(
            "period" to draft.salaryPeriod.firestoreValue,
            "hide" to draft.hideSalary
        )
        if (!draft.hideSalary && draft.salaryMin != null && draft.salaryMin >= 0.0) {
            payload["salaryMin"] = draft.salaryMin
            salaryMap["min"] = draft.salaryMin
        }
        if (!draft.hideSalary && draft.salaryMax != null && draft.salaryMax >= 0.0) {
            payload["salaryMax"] = draft.salaryMax
            salaryMap["max"] = draft.salaryMax
        }
        payload["salary"] = salaryMap

        draft.deadline?.let { payload["deadline"] = Timestamp(Date(it)) }
        draft.startDate?.let { payload["startDate"] = Timestamp(Date(it)) }
        return payload
    }

    /**
     * Builds update payload for an existing job strictly obeying /firestore.rules:
     * NEVER includes ['merchantId', 'postedBy', 'createdAt', 'featured'].
     */
    fun buildUpdateJobPayload(
        draft: JobPosting,
        existingStatus: JobStatus,
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, Any?> {
        val expiryDays = draft.expiryDays.coerceIn(1, 365)
        val expiresAtMs = nowMs + expiryDays * 86_400_000L

        val payload = hashMapOf<String, Any?>(
            "title" to draft.title.trim().take(120),
            "workType" to draft.workType.firestoreValue,
            "category" to draft.category.trim(),
            "salaryMin" to if (!draft.hideSalary) draft.salaryMin else null,
            "salaryMax" to if (!draft.hideSalary) draft.salaryMax else null,
            "salaryPeriod" to draft.salaryPeriod.firestoreValue,
            "hideSalary" to draft.hideSalary,
            "salary" to hashMapOf<String, Any?>(
                "min" to if (!draft.hideSalary) draft.salaryMin else null,
                "max" to if (!draft.hideSalary) draft.salaryMax else null,
                "period" to draft.salaryPeriod.firestoreValue,
                "hide" to draft.hideSalary
            ),
            "vacancies" to draft.vacancies.coerceAtLeast(1),
            "description" to draft.description.trim(),
            "requirements" to draft.requirements.trim(),
            "benefits" to draft.benefits.trim(),
            "address" to draft.address.trim(),
            "reach" to draft.reach.firestoreValue,
            "remote" to draft.remote,
            "howToApply" to draft.howToApply.firestoreValue,
            "applyPhone" to draft.applyPhone.trim(),
            "applyEmail" to draft.applyEmail.trim(),
            "deadline" to draft.deadline?.let { Timestamp(Date(it)) },
            "startDate" to draft.startDate?.let { Timestamp(Date(it)) },
            "expiryDays" to expiryDays,
            "expiresAt" to Timestamp(Date(expiresAtMs)),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (existingStatus == JobStatus.REJECTED) {
            payload["status"] = initialJobStatus(settings).firestoreValue
            payload["rejectionReason"] = ""
        }
        return payload
    }

    /**
     * Builds update payload for jobApplications/{aid} strictly obeying /firestore.rules:
     * onlyChanges(['status', 'employerNote', 'updatedAt'])
     * status in ['viewed', 'shortlisted', 'interview', 'hired', 'rejected'].
     */
    fun buildApplicationUpdatePayload(
        targetStatus: JobApplicationStatus,
        employerNote: String
    ): Map<String, Any> {
        val safeStatus = if (targetStatus in JobApplicationStatus.MERCHANT_SETTABLE) {
            targetStatus
        } else {
            JobApplicationStatus.VIEWED
        }
        return mapOf(
            "status" to safeStatus.firestoreValue,
            "employerNote" to employerNote.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun formatSalarySummary(job: JobPosting): String {
        if (job.hideSalary) return "Salary hidden"
        val min = job.salaryMin
        val max = job.salaryMax
        val period = job.salaryPeriod.label
        return when {
            min != null && max != null && min > 0 && max > 0 ->
                "${FormatUtils.formatCurrency(min)} – ${FormatUtils.formatCurrency(max)} $period"
            min != null && min > 0 ->
                "From ${FormatUtils.formatCurrency(min)} $period"
            max != null && max > 0 ->
                "Up to ${FormatUtils.formatCurrency(max)} $period"
            else -> "Negotiable"
        }
    }

    fun isSafeWebUrl(raw: String): Boolean {
        val trimmed = raw.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }
}
