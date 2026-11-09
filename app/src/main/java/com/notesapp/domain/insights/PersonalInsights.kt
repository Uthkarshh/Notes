package com.notesapp.domain.insights

import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.*
import java.time.*

data class MonthInsight(val month: Int, val added: Int, val completed: Int)
data class InsightsSummary(val added: Int, val completed: Int, val completionRate: Int, val currentStreak: Int, val longestStreak: Int,
    val months: List<MonthInsight>, val activeTags: List<Pair<String, Int>>, val forgotten: List<ItemEntity>, val mediaStreaks: Map<CategoryType, Pair<Int, Int>> = emptyMap())

object PersonalInsights {
    fun compute(items: List<ItemEntity>, collections: List<CollectionEntity>, tags: List<TagEntity>, refs: List<EntityTagCrossRef>, year: Int, month: Int?, now: Instant, zone: ZoneId): InsightsSummary {
        val start = LocalDate.of(year, month ?: 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = (if (month == null) LocalDate.of(year + 1, 1, 1) else LocalDate.of(year, month, 1).plusMonths(1)).atStartOfDay(zone).toInstant().toEpochMilli()
        val schemas = collections.associate { it.id to it.schema }
        fun done(item: ItemEntity) = item.completedAt != null && item.completedAt <= now.toEpochMilli() && schemas[item.collectionId]?.roleOf(item.status) == StatusRole.DONE
        val completed = items.filter { done(it) && it.completedAt!! in start until end }
        val added = items.filter { it.createdAt in start until end }
        val completedCohort = added.count { done(it) && it.completedAt!! < end }
        val allDays = items.filter(::done).map { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).toLocalDate() }.distinct().sorted()
        var longest = 0; var run = 0; var previous: LocalDate? = null
        allDays.forEach { date -> run = if (previous?.plusDays(1) == date) run + 1 else 1; longest = maxOf(longest, run); previous = date }
        val today = now.atZone(zone).toLocalDate()
        val daySet = allDays.toSet()
        var day = if (today in daySet) today else today.minusDays(1)
        var current = 0
        while (day in daySet) { current++; day = day.minusDays(1) }
        val tagNames = tags.associate { it.id to it.name }
        val finishedIds = completed.map { it.id }.toSet()
        val active = refs.filter { it.targetType == EntityType.ITEM && it.targetId in finishedIds }.groupingBy { tagNames[it.tagId] ?: "" }.eachCount()
            .filterKeys { it.isNotEmpty() }.toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(12)
        val cutoff = now.minus(Duration.ofDays(90)).toEpochMilli()
        val forgotten = items.filter { !it.isArchived && it.collectionId != null && it.createdAt < cutoff && schemas[it.collectionId]?.roleOf(it.status) == StatusRole.BACKLOG }
            .sortedBy { it.createdAt }
        val months = (1..12).map { m ->
            val a = LocalDate.of(year, m, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val b = LocalDate.of(year, m, 1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            MonthInsight(m, items.count { it.createdAt in a until b }, items.count { done(it) && it.completedAt!! in a until b })
        }
        val categories = collections.associate { it.id to it.categoryType }
        val mediaStreaks = listOf(CategoryType.BOOK, CategoryType.MOVIE).associateWith { category ->
            val dates = items.filter { done(it) && categories[it.collectionId] == category }.map { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).toLocalDate() }.distinct().sorted()
            var best = 0; var length = 0; var previousDate: LocalDate? = null
            dates.forEach { date -> length = if (previousDate?.plusDays(1) == date) length + 1 else 1; best = maxOf(best, length); previousDate = date }
            val set = dates.toSet(); var last = if (today in set) today else today.minusDays(1); var activeRun = 0
            while (last in set) { activeRun++; last = last.minusDays(1) }
            activeRun to best
        }
        return InsightsSummary(added.size, completed.size, if (added.isEmpty()) 0 else completedCohort * 100 / added.size, current, longest, months, active, forgotten, mediaStreaks)
    }
}
