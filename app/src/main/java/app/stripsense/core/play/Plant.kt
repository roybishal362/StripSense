package app.stripsense.core.play

import app.stripsense.core.dose.Proof
import app.stripsense.core.report.DoseMark
import app.stripsense.core.report.WeekView
import java.time.LocalDate

/**
 * The person's plant: what a week of doses has grown.
 *
 * It grows by how the phone knows about each dose, so it cannot be grown by pressing a button over and over:
 * a button press grows a bud, a strip the camera checked grows a leaf, and a tablet the phone heard come out
 * grows a flower. [streak]: full days in a row, today included once today has a mark and nothing missed.
 */
data class Plant(val buds: Int, val leaves: Int, val flowers: Int, val streak: Int, val todayMarked: Int, val todayAll: Int, val missed: Boolean) {
    /** How much has grown: a flower counts for more than a leaf, a leaf for more than a bud. */
    val growth: Int get() = buds + 2 * leaves + 3 * flowers

    /** How tall it is drawn, 0 (a seed) to 4. */
    val stage: Int get() = Plants.STAGES.count { growth >= it }

    /** How much more growth the next stage needs, or null when it is full grown. */
    val toNextStage: Int? get() = Plants.STAGES.firstOrNull { growth < it }?.let { it - growth }
}

/**
 * How the plant feels, which is how it looks and what it says. It is a character, called Pattu: "patta" is both a
 * leaf and a strip of tablets. WAITING: a dose is due and it is looking at you. PROUD: every dose of today is marked.
 */
enum class Mood { WAITING, HAPPY, PROUD, SAD, SLEEPY }

object Plants {
    val STAGES = listOf(1, 6, 14, 26)

    /** [dueNow]: a dose is due and not marked. [hour]: the hour of the day, 0 to 23. */
    fun mood(plant: Plant, dueNow: Boolean, hour: Int): Mood = when {
        dueNow -> Mood.WAITING
        plant.todayAll > 0 && plant.todayMarked == plant.todayAll -> Mood.PROUD
        plant.missed && plant.streak == 0 -> Mood.SAD
        hour >= 22 || hour < 5 -> Mood.SLEEPY
        else -> Mood.HAPPY
    }

    fun of(week: WeekView, today: LocalDate): Plant {
        val all = week.days.flatMap { it.second }
        val marked = all.filter { it.mark == DoseMark.MARKED }
        var streak = 0
        for ((date, doses) in week.days.sortedByDescending { it.first }) {
            if (date > today) continue
            val cameUp = doses.filter { it.mark != DoseMark.LATER }
            val full = cameUp.isNotEmpty() && cameUp.all { it.mark == DoseMark.MARKED }
            // A day on which no dose has come up yet (early this morning) neither adds to the row nor breaks it.
            if (cameUp.isEmpty() && date == today) continue
            if (!full) break
            streak++
        }
        val todays = week.days.firstOrNull { it.first == today }?.second.orEmpty()
        val recent = week.days.filter { it.first >= today.minusDays(1) }.flatMap { it.second }
        return Plant(
            buds = marked.count { it.proof == Proof.PRESSED },
            leaves = marked.count { it.proof == Proof.SEEN },
            flowers = marked.count { it.proof == Proof.HEARD },
            streak = streak,
            todayMarked = todays.count { it.mark == DoseMark.MARKED },
            todayAll = todays.size,
            missed = recent.any { it.mark == DoseMark.NOT_MARKED },
        )
    }
}
