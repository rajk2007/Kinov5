package com.lagradost.cloudstream3.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityProbeHlsTest {
    @Test
    fun hlsEstimateUses24PercentWithoutSeparateAudio() {
        val estimate = calculateEstimatedSize(
            videoBandwidth = 1_000_000L,
            audioBandwidth = 128_000L,
            durationSeconds = 100.0,
            isHlsStream = true,
            hasSeparateAudio = false,
        )

        assertEquals(3_000_000L, estimate)
    }

    @Test
    fun hlsEstimateIncludesAudioOnlyWhenSeparate() {
        val estimate = calculateEstimatedSize(
            videoBandwidth = 1_000_000L,
            audioBandwidth = 128_000L,
            durationSeconds = 100.0,
            isHlsStream = true,
            hasSeparateAudio = true,
        )

        assertEquals(3_384_000L, estimate)
    }

    @Test
    fun nonHlsEstimateDoesNotApplyHlsCorrection() {
        val estimate = calculateEstimatedSize(
            videoBandwidth = 1_000_000L,
            audioBandwidth = 0L,
            durationSeconds = 8.0,
        )

        assertEquals(1_000_000L, estimate)
    }
}
