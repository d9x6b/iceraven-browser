/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import mozilla.components.support.test.robolectric.testContext
import mozilla.telemetry.glean.Glean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.GleanMetrics.MetaAttribution
import org.mozilla.fenix.helpers.FenixGleanTestRule
import org.mozilla.fenix.nimbus.FxNimbus
import org.mozilla.fenix.utils.Settings
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallReferrerWorkerTest {
    val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule val gleanTestRule = FenixGleanTestRule(testContext)

    @After
    fun tearDown() {
        FxNimbus.features.pairingSigninPrompt.withCachedValue(null)
    }

    @Test
    fun `WHEN retrieving minimum UTM params from setting THEN result should match`() {
        val settings = Settings(context)
        val expected = UTMParams(source = "", medium = "", campaign = "", content = "", term = "")
        val observed = UTMParams.fromSettings(settings)

        assertEquals(observed, expected)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `WHEN retrieving maximum UTM params from setting THEN result should match`() {
        val expected =
            UTMParams(source = "source", medium = "medium", campaign = "campaign", content = "content", term = "term")
        val settings = Settings(context)

        expected.intoSettings(settings)
        val observed = UTMParams.fromSettings(settings)

        assertEquals(observed, expected)

        assertFalse(observed.isEmpty())
    }

    @Test
    fun `WHEN parsing referrer response with no UTM params from setting THEN UTM params in settings should set to empty strings`() {
        val settings = Settings(context)
        val params = UTMParams.parseUTMParameters("")
        params.recordInstallReferrer(settings)

        val expected = UTMParams(source = "", medium = "", campaign = "", content = "", term = "")
        val observed = UTMParams.fromSettings(settings)
        assertEquals(observed, expected)

        assertNull(PlayStoreAttribution.source.testGetValue())
        assertNull(PlayStoreAttribution.medium.testGetValue())
        assertNull(PlayStoreAttribution.campaign.testGetValue())
        assertNull(PlayStoreAttribution.content.testGetValue())
        assertNull(PlayStoreAttribution.term.testGetValue())

        assertTrue(observed.isEmpty())
    }

    @Test
    fun `WHEN parsing referrer response with partial UTM params from setting THEN UTM params in settings should match expected`() {
        val settings = Settings(context)
        val params = UTMParams.parseUTMParameters("utm_campaign=CAMPAIGN")
        params.recordInstallReferrer(settings)

        val expected = UTMParams(source = "", medium = "", campaign = "CAMPAIGN", content = "", term = "")
        val observed = UTMParams.fromSettings(settings)
        assertEquals(observed, expected)

        assertEquals("", PlayStoreAttribution.source.testGetValue())
        assertEquals("", PlayStoreAttribution.medium.testGetValue())
        assertEquals("CAMPAIGN", PlayStoreAttribution.campaign.testGetValue())
        assertEquals("", PlayStoreAttribution.content.testGetValue())
        assertEquals("", PlayStoreAttribution.term.testGetValue())

        assertFalse(observed.isEmpty())
    }

    @Test
    fun `WHEN parsing referrer response with full UTM params from setting THEN UTM params in settings should match expected`() {
        val settings = Settings(context)
        val params =
            UTMParams.parseUTMParameters(
                "utm_source=SOURCE&utm_medium=MEDIUM&utm_campaign=CAMPAIGN&utm_content=CONTENT&utm_term=TERM"
            )
        params.recordInstallReferrer(settings)

        val expected =
            UTMParams(source = "SOURCE", medium = "MEDIUM", campaign = "CAMPAIGN", content = "CONTENT", term = "TERM")
        val observed = UTMParams.fromSettings(settings)
        assertEquals(expected, observed)

        assertEquals("SOURCE", PlayStoreAttribution.source.testGetValue())
        assertEquals("MEDIUM", PlayStoreAttribution.medium.testGetValue())
        assertEquals("CAMPAIGN", PlayStoreAttribution.campaign.testGetValue())
        assertEquals("CONTENT", PlayStoreAttribution.content.testGetValue())
        assertEquals("TERM", PlayStoreAttribution.term.testGetValue())

        assertEquals("SOURCE", Glean.testGetAttribution().source)
        assertEquals("MEDIUM", Glean.testGetAttribution().medium)
        assertEquals("CAMPAIGN", Glean.testGetAttribution().campaign)
        assertEquals("CONTENT", Glean.testGetAttribution().content)
        assertEquals("TERM", Glean.testGetAttribution().term)

        assertFalse(observed.isEmpty())
    }

    @Test
    fun `WHEN recording install referrer with no UTM params THEN Glean attribution is not updated`() {
        val settings = Settings(context)
        val params = UTMParams.parseUTMParameters("")
        params.recordInstallReferrer(settings)

        assertNull(Glean.testGetAttribution().source)
    }

    @Test
    fun `WHEN receiving a Meta encrypted attribution THEN will decrypt correctly`() {
        val metaParams =
            MetaParams.extractMetaAttribution(
                """{"app":12345, "t":1234567890,"source":{"data":"DATA","nonce":"NONCE"}}"""
            )
        val expectedMetaParams = MetaParams("12345", "1234567890", "DATA", "NONCE")

        assertEquals(metaParams, expectedMetaParams)
    }

    @Test
    fun `WHEN receiving a Meta encrypted attribution in percent format THEN will decrypt correctly`() {
        val metaParams =
            MetaParams.extractMetaAttribution(
                "%7B%22app%22%3A12345%2C%22t%22%3A1234567890%2C%22source%22%3A%7B%22data%22%3A%22DATA%22%2C%22nonce%22%3A%22NONCE%22%7D%7D"
            )
        val expectedMetaParams = MetaParams("12345", "1234567890", "DATA", "NONCE")

        assertEquals(metaParams, expectedMetaParams)
    }

    @Test
    fun `WHEN receiving a Meta encrypted attribution in bad format THEN it should not crash`() {
        val metaParams =
            MetaParams.extractMetaAttribution(
                "%7B%22app%22%3A12345%2C%22t%22%3A1234567890%2C%22source%22%3A%7B%22data%22%3A%22DATA%22%2C%22nonce%22%3A%22NONCE%22%7B%7D"
            )

        assertNull(metaParams)
    }

    @Test
    fun `WHEN receiving a null or empty attribution THEN it should return null`() {
        assertNull(MetaParams.extractMetaAttribution(null))
        assertNull(MetaParams.extractMetaAttribution(""))
        assertNull(MetaParams.extractMetaAttribution("    "))
    }

    @Test
    fun `WHEN a referrer value contains an equals sign THEN it is preserved`() {
        val params = UTMParams.parseInstallReferrer("k=val=ue&other=plain")

        assertEquals("val=ue", params["k"])
        assertEquals("plain", params["other"])
    }

    @Test
    fun `WHEN a referrer carries a base64 padded value THEN the padding is preserved`() {
        val params = UTMParams.parseInstallReferrer("""utm_content={"data":"abc=="}""")

        assertEquals("""{"data":"abc=="}""", params["utm_content"])
    }

    @Test
    fun `WHEN a referrer segment is malformed THEN sibling params still parse`() {
        val params = UTMParams.parseInstallReferrer("k1=v1&malformed&k2=v2")

        assertEquals("v1", params["k1"])
        assertEquals("v2", params["k2"])
        assertFalse(params.containsKey("malformed"))
    }

    @Test
    fun `WHEN the referrer response is empty THEN an empty map is returned`() {
        assertEquals(emptyMap<String, String>(), UTMParams.parseInstallReferrer(""))
    }

    @Test
    fun `WHEN utm_content contains an equals sign THEN it is preserved on UTMParams`() {
        val utmParams = UTMParams.parseUTMParameters("utm_source=foo&utm_content=abc==")

        assertEquals("foo", utmParams.source)
        assertEquals("abc==", utmParams.content)
    }

    @Test
    fun `WHEN parsing referrer response with meta attribution THEN both UTM and Meta params should match expected`() {
        val utmParams =
            UTMParams.parseUTMParameters(
                """utm_content={"app":12345, "t":1234567890,"source":{"data":"DATA","nonce":"NONCE"}}"""
            )
        val expectedUtmParams =
            UTMParams(
                source = "",
                medium = "",
                campaign = "",
                content = """{"app":12345, "t":1234567890,"source":{"data":"DATA","nonce":"NONCE"}}""",
                term = "",
            )

        assertEquals(utmParams, expectedUtmParams)

        val metaParams = MetaParams.extractMetaAttribution(utmParams.content)
        val expectedMetaParams = MetaParams("12345", "1234567890", "DATA", "NONCE")

        assertEquals(metaParams, expectedMetaParams)
    }

    @Test
    fun `WHEN recording Meta attribution THEN correct values should be recorded to telemetry`() {
        // The data and nonce are from Meta's example https://developers.facebook.com/docs/app-ads/install-referrer/
        val metaParams =
            MetaParams(
                "12345",
                "1234567890",
                "afe56cf6228c6ea8c79da49186e718e92a579824596ae1d0d4d20d7793dca797bd4034ccf467bfae5c79a3981e7a2968c41949237e2b2db678c1c3d39c9ae564c5cafd52f2b77a3dc77bf1bae063114d0283b97417487207735da31ddc1531d5645a9c3e602c195a0ebf69c272aa5fda3a2d781cb47e117310164715a54c7a5a032740584e2789a7b4e596034c16425139a77e507c492b629c848573c714a03a2e7d25b9459b95842332b460f3682d19c35dbc7d53e3a51e0497ff6a6cbb367e760debc4194ae097498108df7b95eac2fa9bac4320077b510be3b7b823248bfe02ae501d9fe4ba179c7de6733c92bf89d523df9e31238ef497b9db719484cbab7531dbf6c5ea5a8087f95d59f5e4f89050e0f1dc03e464168ad76a64cca64b79",
                "b7203c6a6fb633d16e9cf5c1",
            )

        assertNull(MetaAttribution.app.testGetValue())
        assertNull(MetaAttribution.t.testGetValue())
        assertNull(MetaAttribution.data.testGetValue())
        assertNull(MetaAttribution.nonce.testGetValue())
        metaParams.recordMetaAttribution()

        val expectedApp = "12345"
        val expectedT = "1234567890"
        val expectedData =
            "afe56cf6228c6ea8c79da49186e718e92a579824596ae1d0d4d20d7793dca797bd4034ccf467bfae5c79a3981e7a2968c41949237e2b2db678c1c3d39c9ae564c5cafd52f2b77a3dc77bf1bae063114d0283b97417487207735da31ddc1531d5645a9c3e602c195a0ebf69c272aa5fda3a2d781cb47e117310164715a54c7a5a032740584e2789a7b4e596034c16425139a77e507c492b629c848573c714a03a2e7d25b9459b95842332b460f3682d19c35dbc7d53e3a51e0497ff6a6cbb367e760debc4194ae097498108df7b95eac2fa9bac4320077b510be3b7b823248bfe02ae501d9fe4ba179c7de6733c92bf89d523df9e31238ef497b9db719484cbab7531dbf6c5ea5a8087f95d59f5e4f89050e0f1dc03e464168ad76a64cca64b79"
        val expectedNonce = "b7203c6a6fb633d16e9cf5c1"

        val recordedApp = MetaAttribution.app.testGetValue()
        assertEquals(recordedApp, expectedApp)
        val recordedT = MetaAttribution.t.testGetValue()
        assertEquals(recordedT, expectedT)
        val recordedData = MetaAttribution.data.testGetValue()
        assertEquals(recordedData, expectedData)
        val recordedNonce = MetaAttribution.nonce.testGetValue()
        assertEquals(recordedNonce, expectedNonce)
    }

    @Test
    fun `WHEN install referrer worker runs THEN it succeeds without recording attribution`() = runTest {
        val settings = Settings(context)
        val previous = UTMParams.fromSettings(settings)
        val previousMeta = settings.isUserMetaAttributed
        val previousSource = Glean.testGetAttribution().source
        val previousApp = MetaAttribution.app.testGetValue()
        val worker = TestListenableWorkerBuilder<InstallReferrerWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(previous, UTMParams.fromSettings(settings))
        assertEquals(previousMeta, settings.isUserMetaAttributed)
        assertEquals(previousSource, Glean.testGetAttribution().source)
        assertEquals(previousApp, MetaAttribution.app.testGetValue())
    }
}
