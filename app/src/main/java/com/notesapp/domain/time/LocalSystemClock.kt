package com.notesapp.domain.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Reads the current device zone each time, including after a timezone change in a running process. */
class LocalSystemClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}
