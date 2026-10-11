package app.stripsense.core

import app.stripsense.core.play.InviteKind
import app.stripsense.core.play.Invites
import app.stripsense.core.say.PlayText
import org.junit.Assert.*
import org.junit.Test

/** What Pattu offers with the alarm: a chit chat, a hand of tic-tac-toe, or today's pattern. */
class InvitesTest {
    @Test fun theSameDoseIsAlwaysOfferedTheSameThingSoTheAlarmsPromiseIsKept() {
        val seed = Invites.seed("m1", "2026-10-11T08:00")
        assertEquals(seed, Invites.seed("m1", "2026-10-11T08:00"))
        assertEquals(Invites.pick(seed, chatOpen = true, codeOpen = true), Invites.pick(seed, chatOpen = true, codeOpen = true))
        assertNotEquals(seed, Invites.seed("m1", "2026-10-11T20:00"))
    }

    @Test fun itChangesFromDoseToDoseAndAllThreeComeUp() {
        val offered = (1..31).flatMap { day -> listOf("08:00", "14:00", "20:00").map { Invites.pick(Invites.seed("m1", "2026-10-%02dT%s".format(day, it)), true, true) } }
        assertEquals(InviteKind.entries.toSet(), offered.toSet())
        // None of the three is rare.
        for (kind in InviteKind.entries) assertTrue(kind.name, offered.count { it == kind } >= 15)
    }

    @Test fun whatIsAlreadyDoneTodayIsNotOfferedAgain() {
        val seeds = (0 until 200).map { Invites.seed("m$it", "2026-10-11T08:00") }
        assertTrue(seeds.none { Invites.pick(it, chatOpen = false, codeOpen = true) == InviteKind.CHAT })
        assertTrue(seeds.none { Invites.pick(it, chatOpen = true, codeOpen = false) == InviteKind.CODE })
        // With both done there is always tic-tac-toe.
        assertTrue(seeds.all { Invites.pick(it, chatOpen = false, codeOpen = false) == InviteKind.TIC_TAC_TOE })
        // A seed below nothing still picks.
        assertEquals(InviteKind.TIC_TAC_TOE, Invites.pick(Int.MIN_VALUE, chatOpen = false, codeOpen = false))
    }

    @Test fun theWordsBeforeTheDoseOnlyPromiseAndTheWordsAfterItAsk() {
        for (lang in listOf("en", "hi")) {
            assertEquals(3, InviteKind.entries.map { PlayText.teaser(it, lang) }.toSet().size)
            assertEquals(3, InviteKind.entries.map { PlayText.invite(it, lang) }.toSet().size)
        }
        for (kind in InviteKind.entries) {
            assertTrue(PlayText.teaser(kind, "en").startsWith("After this one"))
            assertTrue(PlayText.invite(kind, "en").startsWith("Want"))
        }
    }
}
