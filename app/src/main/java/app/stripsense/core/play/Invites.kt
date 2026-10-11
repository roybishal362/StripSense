package app.stripsense.core.play

/** The small thing Pattu offers after a dose: a chit chat with something to watch or read, a game of tic-tac-toe, or today's code. */
enum class InviteKind { CHAT, TIC_TAC_TOE, CODE }

/**
 * The alarm is the moment every person opens the app, so that is when Pattu asks them to stay a minute. The alarm's
 * own screen only says what is waiting ("after this one..."), in words: the dose comes first and nothing there can
 * be pressed instead of it. Once the dose is marked, Pattu asks, and yes opens that thing.
 *
 * Which of the three it is changes from dose to dose. It is worked out from the dose itself, so what the alarm
 * promised is what is offered afterwards.
 */
object Invites {
    /** A number for one dose, stirred well: the plain hash of two dose times differs by a multiple of three too often. */
    fun seed(medId: String, slotKey: String): Int {
        var h = "$medId|$slotKey".hashCode()
        h = (h xor (h ushr 16)) * 0x45d9f3b
        h = (h xor (h ushr 16)) * 0x45d9f3b
        return h xor (h ushr 16)
    }

    /**
     * [chatOpen]: there is a gift to choose today, or something for Pattu to ask about. [codeOpen]: today's code is
     * not cracked yet. What is already done today is not offered again; tic-tac-toe can always be played.
     */
    fun pick(seed: Int, chatOpen: Boolean, codeOpen: Boolean): InviteKind {
        val open = buildList {
            if (chatOpen) add(InviteKind.CHAT)
            add(InviteKind.TIC_TAC_TOE)
            if (codeOpen) add(InviteKind.CODE)
        }
        return open[Math.floorMod(seed, open.size)]
    }
}
