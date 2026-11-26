package com.example.data.model

enum class JobStatus(val firestoreValue: String, val label: String) {
    PENDING("pending", "Pending approval"),
    OPEN("open", "Open"),
    CLOSED("closed", "Closed"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun fromString(raw: String?): JobStatus {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.firestoreValue == clean } ?: PENDING
        }
    }
}

enum class JobApplicationStatus(val firestoreValue: String, val label: String) {
    NEW("new", "New"),
    VIEWED("viewed", "Viewed"),
    SHORTLISTED("shortlisted", "Shortlisted"),
    INTERVIEW("interview", "Interview"),
    HIRED("hired", "Hired"),
    REJECTED("rejected", "Not selected"),
    WITHDRAWN("withdrawn", "Withdrawn");

    companion object {
        val MERCHANT_SETTABLE: List<JobApplicationStatus> = listOf(
            VIEWED,
            SHORTLISTED,
            INTERVIEW,
            HIRED,
            REJECTED
        )

        fun fromString(raw: String?): JobApplicationStatus {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return when (clean) {
                "not_selected", "not selected", "declined" -> REJECTED
                else -> entries.firstOrNull { it.firestoreValue == clean } ?: NEW
            }
        }
    }
}

enum class JobWorkType(val firestoreValue: String, val label: String) {
    FULL_TIME("full_time", "Full-time"),
    PART_TIME("part_time", "Part-time"),
    CONTRACT("contract", "Contract"),
    TEMPORARY("temporary", "Temporary"),
    INTERNSHIP("internship", "Internship"),
    FREELANCE("freelance", "Freelance");

    companion object {
        fun fromString(raw: String?): JobWorkType {
            val clean = raw?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_").orEmpty()
            return entries.firstOrNull { it.firestoreValue == clean } ?: FULL_TIME
        }
    }
}

enum class JobReach(val firestoreValue: String, val label: String) {
    CITY("city", "My city only"),
    EVERYWHERE("everywhere", "Everywhere");

    companion object {
        fun fromString(raw: String?): JobReach {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return when (clean) {
                "everywhere", "all", "national", "nationwide" -> EVERYWHERE
                else -> CITY
            }
        }
    }
}

enum class JobApplyMethod(val firestoreValue: String, val label: String) {
    IN_APP("in_app", "In-app"),
    PHONE("phone", "Phone"),
    EMAIL("email", "Email");

    companion object {
        fun fromString(raw: String?): JobApplyMethod {
            val clean = raw?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_").orEmpty()
            return when (clean) {
                "phone", "call", "sms" -> PHONE
                "email", "mail" -> EMAIL
                else -> IN_APP
            }
        }
    }
}

enum class SalaryPeriod(val firestoreValue: String, val label: String) {
    HOUR("hour", "per hour"),
    DAY("day", "per day"),
    WEEK("week", "per week"),
    MONTH("month", "per month");

    companion object {
        fun fromString(raw: String?): SalaryPeriod {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return when (clean) {
                "hour", "hourly" -> HOUR
                "day", "daily" -> DAY
                "week", "weekly" -> WEEK
                else -> MONTH
            }
        }
    }
}

data class JobFairPlatformConfig(
    val enabled: Boolean = true,
    val maxActiveJobsPerShop: Int? = null
)

data class JobPosting(
    val id: String = "",
    val merchantId: String = "",
    val company: String = "",
    val cityId: String = "",
    val cityName: String = "",
    val title: String = "",
    val workType: JobWorkType = JobWorkType.FULL_TIME,
    val category: String = "",
    val salaryMin: Double? = null,
    val salaryMax: Double? = null,
    val salaryPeriod: SalaryPeriod = SalaryPeriod.MONTH,
    val hideSalary: Boolean = false,
    val vacancies: Int = 1,
    val description: String = "",
    val requirements: String = "",
    val benefits: String = "",
    val address: String = "",
    val reach: JobReach = JobReach.CITY,
    val remote: Boolean = false,
    val howToApply: JobApplyMethod = JobApplyMethod.IN_APP,
    val applyPhone: String = "",
    val applyEmail: String = "",
    val deadline: Long? = null,
    val startDate: Long? = null,
    val expiryDays: Int = 30,
    val expiresAt: Long? = null,
    val status: JobStatus = JobStatus.PENDING,
    val rejectionReason: String = "",
    val postedBy: String = "merchant",
    val featured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isActiveForLimit: Boolean
        get() = status == JobStatus.OPEN || status == JobStatus.PENDING
}

data class JobApplication(
    val id: String = "",
    val jobId: String = "",
    val jobMerchantId: String = "",
    val jobTitle: String = "",
    val applicantId: String = "",
    val applicantName: String = "",
    val applicantPhone: String = "",
    val applicantEmail: String = "",
    val message: String = "",
    val skills: List<String> = emptyList(),
    val experience: String = "",
    val resumeUrl: String = "",
    val status: JobApplicationStatus = JobApplicationStatus.NEW,
    val employerNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
