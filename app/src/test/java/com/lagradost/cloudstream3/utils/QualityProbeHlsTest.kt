package com.lagradost.cloudstream3.utils
import org.junit.Assert.assertEquals
import org.junit.Test
class QualityProbeHlsTest {
    @Test
    fun peakBandwidthIsHalvedWithoutSeparateAudio() {
        val estimate = calculateEstimatedSize(1_000_000L, 128_000L, 100.0, hasSeparateAudio = false, videoBandwidthIsAverage = false)
        assertEquals(6_250_000L, estimate)
    }
    @Test
    fun peakBandwidthIsHalvedAndIncludesAudioWhenSeparate() {
        val estimate = calculateEstimatedSize(1_000_000L, 128_000L, 100.0, hasSeparateAudio = true, videoBandwidthIsAverage = false)
        assertEquals(7_850_000L, estimate)
    }
    @Test
    fun averageBandwidthIsUsedAsIs() {
        val estimate = calculateEstimatedSize(1_000_000L, 0L, 8.0, videoBandwidthIsAverage = true)
        assertEquals(1_000_000L, estimate)
    }
}
