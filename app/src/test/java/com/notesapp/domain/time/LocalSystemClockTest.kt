package com.notesapp.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class LocalSystemClockTest {
    @Test fun anExistingClockFollowsDeviceTimezoneChangesButAnExplicitZoneStaysFixed() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val clock = LocalSystemClock()
            val explicit = clock.withZone(ZoneId.of("UTC"))
            assertEquals(ZoneId.of("UTC"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), clock.zone)
            assertEquals(ZoneId.of("UTC"), explicit.zone)
        } finally { TimeZone.setDefault(previous) }
    }
}
