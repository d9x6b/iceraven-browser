/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.distributions.DistributionIdManager
import org.mozilla.fenix.utils.Settings

@RunWith(AndroidJUnit4::class)
internal class AdjustMetricsServiceTest {

    private val conversionEvents = listOf(
        Event.GrowthData.ConversionEvent1,
        Event.GrowthData.ConversionEvent2,
        Event.GrowthData.ConversionEvent3,
        Event.GrowthData.ConversionEvent4,
        Event.GrowthData.ConversionEvent5,
        Event.GrowthData.ConversionEvent6,
        Event.GrowthData.ConversionEvent7(fromSearch = false),
        Event.GrowthData.ConversionEvent7(fromSearch = true),
        Event.FirstWeekPostInstall.ConversionEvent8,
        Event.FirstWeekPostInstall.ConversionEvent9,
        Event.FirstWeekPostInstall.ConversionEvent10,
    )

    @Test
    fun `WHEN marketing service is started or stopped THEN application is not accessed and conversion events are not tracked`() {
        val application = mockk<Application>(relaxed = true)
        val service = AdjustMetricsService(application)

        assertEquals(MetricServiceType.Marketing, service.type)
        service.start()
        conversionEvents.forEach {
            assertFalse(service.shouldTrack(it))
            service.track(it)
        }
        service.stop()

        verify { application wasNot Called }
    }

    @Test
    fun `WHEN conversion events are requested THEN no conversion is recorded`() {
        val recorder = mockk<ConversionEventRecorder>(relaxed = true)
        conversionEvents.forEach { AdjustMetricsService.sendGleanEventAndPing(it, recorder) }
        verify { recorder wasNot Called }
    }

    @Test
    fun `WHEN third party sharing is requested THEN no partner sharing call occurs`() {
        val controller = mockk<ThirdPartySharingController>(relaxed = true)
        for (attributed in listOf(false, true)) {
            AdjustMetricsService.applyThirdPartySharingSettings(
                distribution = DistributionIdManager.Distribution.DEFAULT,
                isUserMetaAttributed = attributed,
                isUserTikTokAttributed = attributed,
                isUserRedditAttributed = attributed,
                isUserXTwitterAttributed = attributed,
                isUserMolocoAttributed = attributed,
                isUserRakutenAttributed = attributed,
                isUserSkyflagAttributed = attributed,
                isUserQuoraAttributed = attributed,
                controller = controller,
            )
        }
        verify { controller wasNot Called }
    }

    @Test
    fun `WHEN Adjust attribution data already exist THEN already known is true`() {
        val settings = Settings(testContext)
        assertFalse(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustCampaignId = "campaign"
        assertTrue(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustCampaignId = ""
        assertFalse(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustNetwork = "network"
        assertTrue(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustNetwork = ""
        assertFalse(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustAdGroup = "ad group"
        assertTrue(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustAdGroup = ""
        assertFalse(AdjustMetricsService.alreadyKnown(settings))

        settings.adjustCreative = "creative"
        assertTrue(AdjustMetricsService.alreadyKnown(settings))
    }
}
