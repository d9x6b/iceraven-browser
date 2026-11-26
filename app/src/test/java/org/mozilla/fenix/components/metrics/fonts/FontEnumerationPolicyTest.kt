/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics.fonts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontEnumerationPolicyTest {
    @Test
    fun `WHEN telemetry is disabled THEN font enumeration is blocked`() {
        assertFalse(shouldEnumerateFonts(false, true, 0))
    }

    @Test
    fun `WHEN channel is not diagnostic THEN font enumeration is blocked`() {
        assertFalse(shouldEnumerateFonts(true, false, 0))
    }

    @Test
    fun `WHEN submission limit has been reached THEN font enumeration is blocked`() {
        assertFalse(shouldEnumerateFonts(true, true, FontEnumerationWorker.DESIRED_SUBMISSIONS_NUMBER))
        assertFalse(shouldEnumerateFonts(true, true, FontEnumerationWorker.DESIRED_SUBMISSIONS_NUMBER + 1))
    }

    @Test
    fun `WHEN diagnostic channel and consent allow another submission THEN font enumeration is allowed`() {
        assertTrue(shouldEnumerateFonts(true, true, FontEnumerationWorker.DESIRED_SUBMISSIONS_NUMBER - 1))
    }
}
