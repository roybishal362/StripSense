package app.stripsense.core.play

import app.stripsense.core.domain.DoseEvent
import app.stripsense.core.domain.Medicine
import app.stripsense.core.domain.MedStatus
import app.stripsense.core.dose.DoseEngine
import app.stripsense.core.dose.Proof
import app.stripsense.core.dose.Proofs
import java.time.LocalDate

/** FULL: every dose of the day is marked. PART: today, on its way, nothing missed. MISSED: a dose's time passed unmarked. NONE: no dose that day. */
enum class DayState { NONE, FULL, PART, MISSED }

data class JourneyDay(val date: LocalDate, val state: DayState, val marked: Int, val all: Int)

/** A level: its number from 1, its name, the points it begins at and the points the next one begins at (null at the top). */
data class Level(val number: Int, val en: String, val hi: String, val from: Int, val next: Int?) {
    fun name(lang: String) = if (lang == "hi") hi else en
}

enum class QuestKind { ALL_TODAY, SHOW_STRIP, GIFT }

/** One of today's three small goals. */
data class Quest(val kind: QuestKind, val done: Int, val goal: Int) {
    val complete: Boolean get() = goal > 0 && done >= goal
}

enum class Badge { FIRST_DOSE, PHONE_SAW, PHONE_HEARD, THREE_DAYS, SEVEN_DAYS, CODE_CRACKED, BEAT_PATTU, COLLECTOR }

/**
 * How Pattu feels, which is how it looks and what it says. Pattu is the app's character: "patta" is both a leaf
 * and a strip of tablets. WAITING: a dose is due and it is looking at you. PROUD: every dose of today is marked.
 */
enum class Mood { WAITING, HAPPY, PROUD, SAD, SLEEPY }

/**
 * What a person has built up by taking their medicine: points, a level, a row of full days, today's goals and badges.
 * All of it is worked out from the dose records, so nothing here can be earned except by a marked dose.
 * [atRisk]: there is a row of days and today is not complete yet. [dueNow]: doses that can be marked this minute.
 */
data class Journey(
    val points: Int, val level: Level, val streak: Int, val best: Int, val atRisk: Boolean, val dueNow: Int,
    val today: JourneyDay, val week: List<JourneyDay>, val quests: List<Quest>, val badges: Set<Badge>, val missed: Boolean,
) {
    /** Every dose that has come up today is marked, and there is at least one: the day's gift may be opened. */
    val onTrack: Boolean get() = today.marked > 0 && dueNow == 0 && today.state != DayState.MISSED

    /** How far into this level, 0 to 1. */
    val levelProgress: Float get() = level.next?.let { (points - level.from).toFloat() / (it - level.from) }?.coerceIn(0f, 1f) ?: 1f

    val toNextLevel: Int? get() = level.next?.let { it - points }

    /** How grown Pattu is drawn: 0 a seed, 1 a sprout, 2 a bud, 3 a flower, 4 a flower that shines. */
    val stage: Int get() = (level.number - 1).coerceIn(0, 4)
}

/**
 * The rules of the journey. A dose earns points by how the phone knows about it, so the points cannot be had by
 * pressing a button over and over: a press earns [PRESSED], a strip the camera checked earns [SEEN], a tablet the
 * phone heard come out earns [HEARD]. A day with every dose marked earns [FULL_DAY] more.
 */
object Journeys {
    const val DAYS = 60
    const val PRESSED = 10
    const val SEEN = 20
    const val HEARD = 30
    const val FULL_DAY = 20

    private val LEVELS = listOf(
        Triple(0, "Seed", "बीज"), Triple(40, "Sprout", "अंकुर"), Triple(120, "Sapling", "पौधा"), Triple(260, "Bud", "कली"),
        Triple(480, "Bloom", "फूल"), Triple(800, "Garden", "बगिया"), Triple(1300, "Forest", "वन"),
    )

    fun level(points: Int): Level {
        val i = LEVELS.indexOfLast { points >= it.first }.coerceAtLeast(0)
        return Level(i + 1, LEVELS[i].second, LEVELS[i].third, LEVELS[i].first, LEVELS.getOrNull(i + 1)?.first)
    }

    fun points(proof: Proof?): Int = when (proof) { Proof.HEARD -> HEARD; Proof.SEEN -> SEEN; else -> PRESSED }

    /**
     * [since]: when the phone first knew each medicine; doses from before that are no part of the journey.
     * [code]: what the phone keeps about the daily code. [bonus]: points earned in the games, added to the points
     * the doses earned. [beatPattu]: games of tic-tac-toe won. [gifts]: gifts collected. [giftToday]: today's is opened.
     */
    fun of(meds: List<Medicine>, events: List<DoseEvent>, now: Long, engine: DoseEngine, since: Map<String, Long> = emptyMap(),
        code: CodeRecord = CodeRecord(), bonus: Int = 0, beatPattu: Int = 0, gifts: Int = 0, giftToday: Boolean = false): Journey {
        val today = engine.dosingDate(now)
        val active = meds.filter { it.status == MedStatus.ACTIVE }
        val days = ArrayList<JourneyDay>()
        val badges = HashSet<Badge>()
        var points = bonus.coerceAtLeast(0)
        var witnessedToday = 0
        var dueNow = 0
        for (back in DAYS - 1 downTo 0) {
            val date = today.minusDays(back.toLong())
            var all = 0
            var marked = 0
            var missed = 0
            for (med in active) for (slot in engine.slotsFor(med, date)) {
                val at = engine.slotMillis(slot)
                if (at < (since[med.id] ?: Long.MIN_VALUE)) continue
                all++
                val mark = engine.markOf(med.id, slot.key, events)
                if (mark != null) {
                    marked++
                    val proof = Proofs.of(mark.source)
                    points += points(proof)
                    badges += Badge.FIRST_DOSE
                    if (proof == Proof.SEEN || proof == Proof.HEARD) { badges += Badge.PHONE_SAW; if (date == today) witnessedToday++ }
                    if (proof == Proof.HEARD) badges += Badge.PHONE_HEARD
                } else if (now > engine.window(slot, med).last) missed++
                else if (at <= now) dueNow++
            }
            val state = when {
                all == 0 -> DayState.NONE
                marked == all -> DayState.FULL
                missed > 0 -> DayState.MISSED
                else -> DayState.PART
            }
            if (state == DayState.FULL) points += FULL_DAY
            days += JourneyDay(date, state, marked, all)
        }
        val todays = days.last()
        // The row of full days up to yesterday. A day with no dose in it neither adds to the row nor breaks it.
        var streak = 0
        for (day in days.dropLast(1).asReversed()) {
            if (day.state == DayState.FULL) streak++ else if (day.state != DayState.NONE) break
        }
        // Today joins the row once it has a mark and nothing missed. A missed dose today ends the row.
        val alive = todays.state == DayState.FULL || (todays.state == DayState.PART && todays.marked > 0)
        if (todays.state == DayState.MISSED) streak = 0 else if (alive) streak++
        var best = 0
        var run = 0
        for (day in days) {
            if (day.state == DayState.FULL) { run++; best = maxOf(best, run) } else if (day.state != DayState.NONE) run = 0
        }
        best = maxOf(best, streak)
        if (best >= 3) badges += Badge.THREE_DAYS
        if (best >= 7) badges += Badge.SEVEN_DAYS
        if (code.bestRow >= 1) badges += Badge.CODE_CRACKED
        if (beatPattu >= 1) badges += Badge.BEAT_PATTU
        if (gifts >= 5) badges += Badge.COLLECTOR
        val quests = listOf(
            Quest(QuestKind.ALL_TODAY, todays.marked, todays.all),
            Quest(QuestKind.SHOW_STRIP, witnessedToday.coerceAtMost(1), 1),
            Quest(QuestKind.GIFT, if (giftToday) 1 else 0, 1),
        )
        return Journey(points, level(points), streak, best, atRisk = streak > 0 && todays.state == DayState.PART, dueNow = dueNow,
            today = todays, week = days.takeLast(7), quests = quests, badges = badges, missed = days.takeLast(2).any { it.state == DayState.MISSED })
    }

    /** [hour]: the hour of the day, 0 to 23. A dose that is due comes before everything. */
    fun mood(j: Journey, hour: Int): Mood = when {
        j.dueNow > 0 -> Mood.WAITING
        j.today.state == DayState.FULL -> Mood.PROUD
        j.missed && j.streak == 0 -> Mood.SAD
        hour >= 22 || hour < 5 -> Mood.SLEEPY
        else -> Mood.HAPPY
    }
}
