package org.kashmir.heatguard.core.fakes

import org.kashmir.heatguard.core.Clock

class FakeClock(private var currentMillis: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = currentMillis
    fun advance(millis: Long) { currentMillis += millis }
    fun advanceMinutes(minutes: Long) { advance(minutes * 60_000L) }
}
