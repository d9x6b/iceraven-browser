/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.Activity
import com.google.android.play.core.review.ReviewManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.test.runTest
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.GleanMetrics.ReviewPrompt
import org.mozilla.fenix.components.ReviewPromptAttemptResult.Displayed
import org.mozilla.fenix.components.ReviewPromptAttemptResult.Error
import org.mozilla.fenix.components.ReviewPromptAttemptResult.NotDisplayed
import org.mozilla.fenix.components.ReviewPromptAttemptResult.Unknown
import org.mozilla.fenix.helpers.FenixGleanTestRule
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayStoreReviewPromptControllerTest {

    @get:Rule val gleanTestRule = FenixGleanTestRule(testContext)

    @Test
    fun `WHEN review is requested THEN the fork does not launch a Play Store review flow`() = runTest {
        val manager = mockk<ReviewManager>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true)
        val controller = PlayStoreReviewPromptController(manager, { error("Launch count must not be read") })

        assertEquals(NotDisplayed, controller.tryPromptReview(activity))
        verify { manager wasNot Called }
        verify { activity wasNot Called }
        assertNull(ReviewPrompt.promptAttempt.testGetValue())
    }

    @Test
    fun `WHEN Play Store review is requested THEN no external activity or tab opens`() {
        val manager = mockk<ReviewManager>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true)
        var openedInNewTab = false
        val controller = PlayStoreReviewPromptController(manager, { error("Launch count must not be read") })

        controller.tryLaunchPlayStoreReview(activity) { openedInNewTab = true }

        assertFalse(openedInNewTab)
        verify { manager wasNot Called }
        verify { activity wasNot Called }
    }

    @Test
    fun `WHEN review info contains 'isNoOp=false' THEN prompt was displayed`() {
        val displayState = ReviewPromptAttemptResult.from(createReviewInfoString("isNoOp=false"))

        assertEquals(Displayed, displayState)
    }

    @Test
    fun `WHEN review info contains 'isNoOp=true' THEN prompt wasn't displayed`() {
        val displayState = ReviewPromptAttemptResult.from(createReviewInfoString("isNoOp=true"))

        assertEquals(NotDisplayed, displayState)
    }

    @Test
    fun `WHEN review info doesn't contain 'isNoOp' THEN prompt display state is unknown`() {
        val displayState = ReviewPromptAttemptResult.from(createReviewInfoString())

        assertEquals(Unknown, displayState)
    }

    private fun createReviewInfoString(optionalArg: String? = ""): String {
        return "ReviewInfo{pendingIntent=PendingIntent{5b613b1: android.os.BinderProxy@46c8096}, $optionalArg}"
    }

    @Test
    fun reviewPromptWasDisplayed() {
        testRecordReviewPromptEventRecordsTheExpectedData(Displayed, "true")
    }

    @Test
    fun reviewPromptWasNotDisplayed() {
        testRecordReviewPromptEventRecordsTheExpectedData(NotDisplayed, "false")
    }

    @Test
    fun reviewPromptDisplayStateError() {
        testRecordReviewPromptEventRecordsTheExpectedData(Error(RuntimeException()), "error")
    }

    @Test
    fun reviewPromptDisplayStateUnknown() {
        testRecordReviewPromptEventRecordsTheExpectedData(Unknown, "error")
    }

    private fun testRecordReviewPromptEventRecordsTheExpectedData(
        promptDisplayState: ReviewPromptAttemptResult,
        promptWasDisplayed: String,
    ) {
        val numberOfAppLaunches = 1
        val datetime = Date(TEST_TIME_NOW)
        val formattedNowLocalDatetime = SIMPLE_DATE_FORMAT.format(datetime)

        assertNull(ReviewPrompt.promptAttempt.testGetValue())
        recordReviewPromptEvent(promptDisplayState, numberOfAppLaunches, datetime)

        val reviewPromptData = ReviewPrompt.promptAttempt.testGetValue()!!.last().extra!!
        assertEquals(promptWasDisplayed, reviewPromptData["prompt_was_displayed"])
        assertEquals(numberOfAppLaunches, reviewPromptData["number_of_app_launches"]!!.toInt())
        assertEquals(formattedNowLocalDatetime, reviewPromptData["local_datetime"])
    }

    companion object {
        private const val TEST_TIME_NOW = 1598416882805L
        private val SIMPLE_DATE_FORMAT by lazy {
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault(),
            )
        }
    }
}
