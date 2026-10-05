package com.vayana.core.wear

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class WearDailyProgress(val day: String, val seconds: Long, val includedIds: Set<String>) {
    fun json() = JSONObject().put("day", day).put("seconds", seconds)
        .put("ids", JSONArray(includedIds.toList())).toString()
    fun total(watch: WatchState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = LocalDate.now(java.time.Clock.fixed(java.time.Instant.ofEpochMilli(now), zone))
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val local = watch.entries.filter { it.session.id !in includedIds && it.receipt !in WearSyncRules.delivered &&
            it.receipt in setOf(null, "phone_timer_active") }.sumOf {
            clippedSeconds(it.session.startedAt, it.session.endedAt, it.session.seconds, it.session.activeIntervals, start, end)
        }
        return (if (day == date.toString()) seconds else 0) + local
    }
    companion object {
        fun parse(text: String): WearDailyProgress {
            val j = JSONObject(text); val ids = j.getJSONArray("ids")
            return WearDailyProgress(j.getString("day"), j.getLong("seconds"), (0 until ids.length()).map { ids.getString(it) }.toSet())
                .also { LocalDate.parse(it.day); require(it.seconds >= 0) }
        }
        fun clippedSeconds(started: Long, ended: Long, seconds: Long, intervals: String?, start: Long, end: Long): Long {
            if (intervals != null) return com.vayana.core.common.ReadingIntervals.decode(intervals).sumOf {
                (minOf(it.end, end) - maxOf(it.start, start)).coerceAtLeast(0)
            } / 1000
            val overlap = (minOf(ended, end) - maxOf(started, start)).coerceAtLeast(0)
            return if (ended <= started) 0 else (seconds * overlap.toDouble() / (ended - started)).toLong()
        }
    }
}
