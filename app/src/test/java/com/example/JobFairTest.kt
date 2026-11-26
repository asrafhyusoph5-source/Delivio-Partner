package com.example

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.ui.components.MerchantRightDrawer
import com.example.ui.screens.JobFairScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.JobFairHelper
import com.example.util.JobPostEligibility
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class JobFairTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val baseSettings = MerchantSettings(
        merchantId = "shop_1",
        storeName = "Kape Delivio",
        branchName = "BGC",
        phone = "09171112222",
        email = "hr@kapedelivio.ph",
        cityId = "taguig",
        storeLocation = AddressLocation(street = "High Street, BGC", city = "Taguig")
    )

    @Test
    fun parsePlatformConfig_handlesBooleanMapAndLimits() {
        val offBool = JobFairHelper.parsePlatformConfig(
            mapOf("services" to mapOf("jobfair" to false))
        )
        assertFalse(offBool.enabled)

        val offMap = JobFairHelper.parsePlatformConfig(
            mapOf("services" to mapOf("jobfair" to mapOf("enabled" to false, "maxOpenJobs" to 5)))
        )
        assertFalse(offMap.enabled)
        assertEquals(5, offMap.maxActiveJobsPerShop)

        val onWithTopLimit = JobFairHelper.parsePlatformConfig(
            mapOf(
                "services" to mapOf("jobfair" to true),
                "jobFairMaxOpenJobs" to 3
            )
        )
        assertTrue(onWithTopLimit.enabled)
        assertEquals(3, onWithTopLimit.maxActiveJobsPerShop)

        val missingDefaultsOn = JobFairHelper.parsePlatformConfig(emptyMap())
        assertTrue(missingDefaultsOn.enabled)
        assertNull(missingDefaultsOn.maxActiveJobsPerShop)
    }

    @Test
    fun jobRulesParity_createUpdateReopenAndApplicationPayloads() {
        val untrusted = baseSettings.copy(jobsTrusted = false, jobsBlocked = false)
        val trusted = baseSettings.copy(jobsTrusted = true, jobsBlocked = false)
        val blocked = baseSettings.copy(jobsBlocked = true)

        assertEquals(JobStatus.PENDING, JobFairHelper.initialJobStatus(untrusted))
        assertEquals(JobStatus.OPEN, JobFairHelper.initialJobStatus(trusted))
        assertEquals(JobStatus.PENDING, JobFairHelper.reopenJobStatus(untrusted))
        assertEquals(JobStatus.OPEN, JobFairHelper.reopenJobStatus(trusted))

        val blockedElig = JobFairHelper.checkPostEligibility(blocked, emptyList(), JobFairPlatformConfig(enabled = true, maxActiveJobsPerShop = 5))
        assertTrue(blockedElig is JobPostEligibility.Blocked)
        assertEquals("Posting is blocked. Contact support.", (blockedElig as JobPostEligibility.Blocked).message)

        val existingJobs = listOf(
            JobPosting(id = "j1", status = JobStatus.OPEN),
            JobPosting(id = "j2", status = JobStatus.PENDING),
            JobPosting(id = "j3", status = JobStatus.CLOSED)
        )
        val limitElig = JobFairHelper.checkPostEligibility(untrusted, existingJobs, JobFairPlatformConfig(enabled = true, maxActiveJobsPerShop = 2))
        assertTrue(limitElig is JobPostEligibility.LimitReached)

        val draft = JobPosting(
            title = "Barista",
            workType = JobWorkType.FULL_TIME,
            category = "Barista & Cafe",
            salaryMin = 18000.0,
            salaryMax = 22000.0,
            salaryPeriod = SalaryPeriod.MONTH,
            vacancies = 2,
            description = "Prepare espresso and serve guests.",
            address = "High Street, BGC, Taguig",
            reach = JobReach.CITY,
            howToApply = JobApplyMethod.IN_APP
        )

        val createPayload = JobFairHelper.buildCreateJobPayload(draft, trusted, "shop_1", "Taguig City")
        assertEquals("shop_1", createPayload["merchantId"])
        assertEquals("merchant", createPayload["postedBy"])
        assertEquals("open", createPayload["status"])
        assertEquals("Kape Delivio - BGC", createPayload["company"])
        assertEquals("taguig", createPayload["cityId"])
        assertEquals("Taguig City", createPayload["cityName"])
        assertEquals(FieldValue.serverTimestamp(), createPayload["createdAt"])
        assertEquals(30, createPayload["expiryDays"])

        val updatePayload = JobFairHelper.buildUpdateJobPayload(draft, JobStatus.OPEN, untrusted)
        assertFalse(updatePayload.containsKey("merchantId"))
        assertFalse(updatePayload.containsKey("postedBy"))
        assertFalse(updatePayload.containsKey("createdAt"))
        assertFalse(updatePayload.containsKey("featured"))
        assertFalse(updatePayload.containsKey("status"))

        val rejectedEditPayload = JobFairHelper.buildUpdateJobPayload(draft, JobStatus.REJECTED, untrusted)
        assertEquals("pending", rejectedEditPayload["status"])

        val appUpdate = JobFairHelper.buildApplicationUpdatePayload(JobApplicationStatus.REJECTED, "Not available weekends")
        assertEquals(setOf("status", "employerNote", "updatedAt"), appUpdate.keys)
        assertEquals("rejected", appUpdate["status"])
        assertEquals("Not available weekends", appUpdate["employerNote"])
        assertEquals(FieldValue.serverTimestamp(), appUpdate["updatedAt"])
    }

    @Test
    fun parseDocuments_toleratesMissingOrNestedFieldsAndLogsNonFatal() {
        val job = JobFairHelper.parseJobDataSafe(docId = "job_1") {
            mapOf(
                "title" to "Cashier",
                "salary" to mapOf("min" to "15000", "max" to 18000, "period" to "month", "hide" to false),
                "apply" to mapOf("method" to "email", "email" to "jobs@shop.ph"),
                "status" to "rejected",
                "rejectionReason" to "Incomplete description",
                "reach" to "everywhere",
                "remote" to true
            )
        }
        assertNotNull(job)
        assertEquals("Cashier", job!!.title)
        assertEquals(15000.0, job.salaryMin!!, 0.01)
        assertEquals(JobApplyMethod.EMAIL, job.howToApply)
        assertEquals("jobs@shop.ph", job.applyEmail)
        assertEquals(JobStatus.REJECTED, job.status)
        assertEquals("Incomplete description", job.rejectionReason)
        assertEquals(JobReach.EVERYWHERE, job.reach)
        assertTrue(job.remote)

        val app = JobFairHelper.parseApplicationDataSafe(docId = "app_1") {
            mapOf(
                "jobId" to "job_1",
                "applicantName" to "Maria Santos",
                "skills" to "POS, Latte Art, Customer Service",
                "status" to "new",
                "createdAt" to 1781503200000L
            )
        }
        assertNotNull(app)
        assertEquals(listOf("POS", "Latte Art", "Customer Service"), app!!.skills)
        assertEquals(JobApplicationStatus.NEW, app.status)

        var loggedPath: String? = null
        val failed = JobFairHelper.parseApplicationDataSafe(
            docId = "bad_app",
            onParseError = { _, path, _ -> loggedPath = path },
            dataProvider = { throw IllegalStateException("Corrupt field") }
        )
        assertNull(failed)
        assertEquals("jobApplications/bad_app", loggedPath)
    }

    @Test
    fun drawer_hidesJobFairWhenPlatformServiceOff_andOpensWhenOn() {
        var jobFairEnabled by mutableStateOf(false)
        var clicked = false

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MerchantRightDrawer(
                    settings = baseSettings,
                    currentTime = 1781503200000L,
                    isServiceLocked = false,
                    unreadInboxCount = 0,
                    newOrdersCount = 0,
                    averageRating = 5.0,
                    reviewsCount = 0,
                    walletBalance = 0.0,
                    onToggleOpen = {},
                    onOpenReviews = {},
                    onNavigateOrders = {},
                    onNavigateMenu = {},
                    onNavigateDashboard = {},
                    onNavigateEarnings = {},
                    onOpenWallet = {},
                    onOpenInbox = {},
                    onOpenVouchers = {},
                    onRequestRider = {},
                    onOpenStoreProfile = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onLogout = {},
                    showJobFair = jobFairEnabled,
                    onOpenJobFair = { clicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("drawer_item_job_fair").fetchSemanticsNodes().size)

        jobFairEnabled = true
        composeRule.waitForIdle()

        val row = composeRule.onNodeWithTag("drawer_item_job_fair")
        row.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Job Fair").assertIsDisplayed()
        composeRule.onNodeWithText("Post jobs and see applicants").assertIsDisplayed()
        row.performClick()
        assertTrue(clicked)
    }

    @Test
    fun jobFairScreen_staffIsReadOnly_andDoesNotAutoMarkViewed() {
        var markViewedCalled = false
        val jobs = listOf(
            JobPosting(id = "j1", title = "Cashier", status = JobStatus.OPEN),
            JobPosting(id = "j2", title = "Cook", status = JobStatus.REJECTED, rejectionReason = "Missing shift hours")
        )
        val apps = listOf(
            JobApplication(
                id = "a1",
                jobId = "j1",
                jobTitle = "Cashier",
                applicantName = "Pedro Cruz",
                applicantPhone = "09171234567",
                applicantEmail = "pedro@example.com",
                status = JobApplicationStatus.NEW
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                JobFairScreen(
                    settings = baseSettings,
                    jobFairConfig = JobFairPlatformConfig(enabled = true),
                    jobs = jobs,
                    applications = apps,
                    isReadOnly = true,
                    onCreateJob = { _, cb -> cb(true, null) },
                    onUpdateJob = { _, _, _, cb -> cb(true, null) },
                    onCloseJob = {},
                    onReopenJob = {},
                    onMarkApplicationViewed = { markViewedCalled = true },
                    onUpdateApplicationStatusAndNote = { _, _, _, cb -> cb(true, null) },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("job_fair_readonly_badge").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("post_job_top_button").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("job_menu_button_j1").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Rejection reason: Missing shift hours").assertIsDisplayed()

        // Switch to Applicants tab and open New application -> must NOT mark viewed for staff
        composeRule.onNodeWithTag("job_fair_tab_applicants").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("applicant_card_a1").performClick()
        composeRule.waitForIdle()

        assertFalse(markViewedCalled)
        composeRule.onNodeWithTag("applicant_status_btn_shortlisted").assertIsNotEnabled()
        assertEquals(0, composeRule.onAllNodesWithTag("applicant_save_note_button").fetchSemanticsNodes().size)
    }

    @Test
    fun jobFairScreen_ownerPostJob_andApplicantDetailsDialEmailAndUpdateStatus() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var createdDraft: JobPosting? = null
        var markedViewedId: String? = null
        var updatedStatus: JobApplicationStatus? = null
        var updatedNote: String? = null

        val jobs = listOf(
            JobPosting(id = "j1", title = "Barista", status = JobStatus.OPEN)
        )
        val apps = listOf(
            JobApplication(
                id = "a1",
                jobId = "j1",
                jobTitle = "Barista",
                applicantName = "Ana Reyes",
                applicantPhone = "09178889999",
                applicantEmail = "ana@example.com",
                message = "2 years coffee experience",
                skills = listOf("Espresso", "POS"),
                experience = "Cafe Barista",
                resumeUrl = "https://example.com/ana-cv.pdf",
                status = JobApplicationStatus.NEW,
                createdAt = 2000L
            ),
            JobApplication(
                id = "a2",
                jobId = "j1",
                jobTitle = "Barista",
                applicantName = "Old Applicant",
                status = JobApplicationStatus.VIEWED,
                createdAt = 1000L
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                JobFairScreen(
                    settings = baseSettings,
                    jobFairConfig = JobFairPlatformConfig(enabled = true, maxActiveJobsPerShop = 5),
                    jobs = jobs,
                    applications = apps,
                    isReadOnly = false,
                    onCreateJob = { draft, cb ->
                        createdDraft = draft
                        cb(true, null)
                    },
                    onUpdateJob = { _, _, _, cb -> cb(true, null) },
                    onCloseJob = {},
                    onReopenJob = {},
                    onMarkApplicationViewed = { markedViewedId = it.id },
                    onUpdateApplicationStatusAndNote = { _, st, note, cb ->
                        updatedStatus = st
                        updatedNote = note
                        cb(true, null)
                    },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Open Post a job form and submit
        composeRule.onNodeWithTag("post_job_top_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("job_form_title_input").performTextInput("Evening Cashier")
        composeRule.onNodeWithTag("job_form_description_input").performScrollTo().performTextInput("Handle counter and POS.")
        composeRule.onNodeWithTag("job_form_submit_button").performClick()
        composeRule.waitForIdle()

        assertNotNull(createdDraft)
        assertEquals("Evening Cashier", createdDraft!!.title)
        assertEquals("High Street, BGC, Taguig", createdDraft!!.address)
        assertEquals(30, createdDraft!!.expiryDays)

        // 2. Switch to Applicants tab, tap New applicant -> marks Viewed
        composeRule.onNodeWithTag("job_fair_tab_applicants").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("applicant_card_a1").performClick()
        composeRule.waitForIdle()

        assertEquals("a1", markedViewedId)

        // 3. Call button opens real ACTION_DIAL dialer
        composeRule.onNodeWithTag("applicant_call_button").performClick()
        val dialIntent = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(dialIntent)
        assertEquals(Intent.ACTION_DIAL, dialIntent.action)
        assertEquals("tel:09178889999", dialIntent.dataString)

        // 4. Email button opens ACTION_SENDTO mailto intent
        composeRule.onNodeWithTag("applicant_email_button").performClick()
        val emailIntent = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(emailIntent)
        assertEquals(Intent.ACTION_SENDTO, emailIntent.action)
        assertTrue(emailIntent.dataString!!.startsWith("mailto:ana@example.com"))

        // 5. Enter employer note & pick "Not selected" -> maps to REJECTED
        composeRule.onNodeWithTag("applicant_employer_note_input").performScrollTo().performTextInput("Strong latte art")
        composeRule.onNodeWithTag("applicant_status_btn_rejected").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(JobApplicationStatus.REJECTED, updatedStatus)
        assertEquals("Strong latte art", updatedNote)
    }
}
