package app.stripsense.core.guide

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
enum class ScanSide { LEFT, CENTRE, RIGHT, SINGLE }
enum class ScanHint { START, CLOSER, SEARCHING, FOUND, CONFLICT, AMBIGUOUS, LOST, PIECE, APART, HAND, AIM, OVERVIEW }

data class ScanTarget(val trackId: Long, val med: Medicine, val timing: MedicationTiming, val side: ScanSide, val nameOnly: Boolean)
/**
 * [otherConflict]: a different strip in view was refused while this target was found.
 * [steerMed], [steer]: the medicine the phone should be moved toward, and which way. [intro]: say its name with the direction.
 * [elsewhere]: the strip in the frame is not the one that is due, and the due one is also in view: which it is and where.
 */
data class ScanMessage(val hint: ScanHint, val target: ScanTarget? = null, val otherConflict: Boolean = false, val piece: Medicine? = null,
    val hand: HandCue? = null, val steerMed: Medicine? = null, val steer: Toward? = null, val intro: Boolean = false,
    val elsewhere: Pair<Medicine, Toward>? = null,
    /** [elsewhere] is the medicine the person asked the phone to find, not one that is due. */
    val asked: Boolean = false,
    /** Another saved medicine has the same ingredient as the strip in the frame, and was marked today. */
    val shared: SharedIngredient? = null,
    /** Several medicines in view and none held in the frame: all of them, in the order they lie. */
    val overview: List<Medicine> = emptyList(),
    /** [overview] runs from the far side of the table to the near side, not from left to right. */
    val farToNear: Boolean = false,
    /** The expiry date printed on the strip in the frame has passed. */
    val expired: ExpiryDate? = null,
    /** The strip in the frame is another person's in this home: whose. */
    val owner: Profile? = null,
    /** In a round over the medicines of one dose time: which one this is, and of how many. */
    val roundAt: Pair<Int, Int>? = null)

/** Selects only from observed identities. The due medicine never supplies missing visual evidence. */
object ScanGuidance {
    /**
     * The one current message. A refused strip is never a target, so it must not hide a checked strip
     * beside it: with several strips on the table that would silence the guidance the person came for.
     */
    fun message(target: ScanTarget?, conflict: Boolean, lostTarget: Boolean, ambiguous: Boolean, starting: Boolean, smallPrint: Boolean,
        piece: Medicine? = null, tooClose: Boolean = false, hand: HandCue? = null, steer: Pair<Medicine, Toward>? = null,
        elsewhere: Pair<Medicine, Toward>? = null): ScanMessage = when {
        // A hand in view is the person asking "this one?". That question is answered before anything else.
        hand != null -> ScanMessage(ScanHint.HAND, hand = hand)
        // The medicine is known but off to one side, or has just slid out of view: say which way to move the phone.
        steer != null -> ScanMessage(ScanHint.AIM, steerMed = steer.first, steer = steer.second)
        target != null -> ScanMessage(ScanHint.FOUND, target, conflict, elsewhere = elsewhere)
        conflict -> ScanMessage(ScanHint.CONFLICT)
        lostTarget -> ScanMessage(ScanHint.LOST)
        tooClose -> ScanMessage(ScanHint.APART)
        piece != null -> ScanMessage(ScanHint.PIECE, piece = piece)
        ambiguous -> ScanMessage(ScanHint.AMBIGUOUS)
        starting -> ScanMessage(ScanHint.START)
        smallPrint -> ScanMessage(ScanHint.CLOSER)
        else -> ScanMessage(ScanHint.SEARCHING)
    }

    /**
     * Which strip the phone is talking about. The strip held in the frame is "this one": the person is showing
     * it and asking. Only when no strip is in the frame does the phone turn to the medicine that is wanted
     * (asked for by a reminder, or due now), and after that to the nearest one.
     * A due medicine at the edge used to take the voice away from the strip in the middle: the person showed
     * one strip and heard about another.
     * [closeness]: how near each strip is to the middle of the picture, 0 to 1. [current]: the strip spoken of now.
     */
    fun subject(targets: List<ScanTarget>, closeness: Map<Long, Float>, requested: String?, current: String?): ScanTarget? {
        // The present subject is a little sticky, so two strips at about the same distance do not take turns.
        fun near(t: ScanTarget) = (closeness[t.trackId] ?: 0f) + if (t.med.id == current) STICKY else 0f
        val framed = targets.filter { near(it) >= (if (it.med.id == current) CENTRED_STAY else CENTRED_ENTER) }
        return framed.maxByOrNull(::near) ?: targets.minWithOrNull(compareBy<ScanTarget>(
            { if (it.med.id == requested) 0 else 1 }, { if (it.timing.kind == TimingKind.NOW) 0 else 1 }, { -near(it) }))
    }

    /**
     * Which way the phone should move to bring this strip to the middle of the view; null once it is there.
     * [wasCentred] makes the middle a little sticky, so a strip near its edge does not flip between "right" and "here".
     */
    fun steer(box: CropBox, imageW: Int, imageH: Int, wasCentred: Boolean): Toward? =
        if (Homing.closeness(box, imageW, imageH) >= (if (wasCentred) CENTRED_STAY else CENTRED_ENTER)) null
        else HandGuide.toward(imageW / 2, imageH / 2, box)

    const val CENTRED_ENTER = 0.58f
    const val CENTRED_STAY = 0.42f
    const val STICKY = 0.12f

    fun target(targets: List<ScanTarget>, preferredMedId: String? = null): ScanTarget? = targets.minWithOrNull(
        compareBy<ScanTarget>({ if (it.med.id == preferredMedId) 0 else 1 }, { rank(it.timing.kind) }, { it.trackId }))

    fun side(box: CropBox, imageWidth: Int, count: Int): ScanSide {
        if (count == 1) return ScanSide.SINGLE
        val x = (box.left + box.right) / 2.0 / imageWidth.coerceAtLeast(1)
        return when { x < .36 -> ScanSide.LEFT; x > .64 -> ScanSide.RIGHT; else -> ScanSide.CENTRE }
    }

    private fun rank(kind: TimingKind): Int = when (kind) {
        TimingKind.NOW -> 0
        TimingKind.NEXT -> 1
        TimingKind.LATER -> 2
        TimingKind.MARKED -> 3
        TimingKind.UNCONFIRMED -> 4
        TimingKind.NOT_SURE -> 5
        TimingKind.NO_SCHEDULE -> 6
        TimingKind.STOPPED -> 7
    }
}

/**
 * What is said, and when. The voice speaks only about what the camera is reading now.
 *
 * Three things went wrong on real strips and are ruled out here. The phone finished a long sentence about a
 * strip that had already been swapped for another: a sentence whose medicine is no longer in front of the camera
 * is cut off ([stale]). The same strip was announced again every time its strength came and went, or a hand
 * flickered in and out of view: that is the same news and is said once. And the phone spoke the moment anything
 * appeared: it now waits until what it sees has held still for a moment.
 */
class ScanAnnouncements {
    private var observed: Any? = null
    private var observedAt = 0L
    private var said: ScanMessage? = null
    private var saidNews: Any? = null
    private var saidAt = Long.MIN_VALUE
    private var aimed: String? = null
    private var introAt = 0L
    private var subject: String? = null
    private var subjectAt = 0L
    private var handAt = Long.MIN_VALUE
    private var hintNow = ScanHint.START

    /** "This is your X" in one state. The same from the camera and from a pointing finger. */
    private data class Is(val med: String, val timing: MedicationTiming, val nameOnly: Boolean, val conflict: Boolean, val elsewhere: String?,
        val hand: Boolean = false)

    /** Notes what is true now, spoken or not. Call for every look; [next] does it too. */
    fun observe(message: ScanMessage, now: Long) {
        val news = news(message)
        if (news != observed) { observed = news; observedAt = now }
        val about = subject(message)
        if (about != subject) { subject = about; subjectAt = now }
        hintNow = message.hint
    }

    /** How long the present message has stood unchanged. The words on the screen wait for this too. */
    fun steadyFor(now: Long): Long = now - observedAt

    /**
     * The sentence being spoken is about a medicine the camera is no longer looking at, or it is filler
     * and a medicine has come into view. The caller stops the voice; the true sentence follows.
     */
    fun stale(now: Long): Boolean {
        val spoken = said ?: return false
        if (subject(spoken) == subject) return false
        // "Your medicines, from left to right..." is finished before the phone starts leading to one of them.
        if (spoken.hint == ScanHint.OVERVIEW && hintNow == ScanHint.AIM) return false
        return now - subjectAt >= if (subject != null) CHANGED_MS else GONE_MS
    }

    /**
     * [busy]: the phone is still speaking. Only an urgent message may cut a sentence off; anything else waits
     * and is said, if it is still true, when the sentence is over.
     */
    fun next(message: ScanMessage, now: Long, repeat: Boolean = false, busy: Boolean = false): ScanMessage? {
        observe(message, now)
        val gist = gist(message)
        val news = news(message)
        if (message.hint == ScanHint.HAND) {
            // A hand that comes back after a while is a new question and gets its answer again.
            // A hand that flickers out of view for a moment is the same hand.
            if (handAt != Long.MIN_VALUE && now - handAt > REASK_MS && said?.hint == ScanHint.HAND) saidNews = null
            // A finger pointing at the strip the phone named a while ago is asking about it again.
            // Named only a moment ago, it is the same news, and the hand may just be the one holding the strip.
            if (said?.hint != ScanHint.HAND && saidAt != Long.MIN_VALUE && now - saidAt > POINT_AGAIN_MS) saidNews = null
            handAt = now
        }
        if (message.hand is HandCue.Move) {
            // Directions keep coming while the hand is still on its way: a new one soon, the same one again after a pause.
            if (!repeat && saidAt != Long.MIN_VALUE && now - saidAt < if (news == saidNews) MOVE_AGAIN_MS else MOVE_NEW_MS) return null
            return say(message, gist, news, now)
        }
        if (message.hint == ScanHint.AIM) {
            // The first direction for a medicine names it: "Crocin is on the right. Move the phone right."
            // After that the direction alone is repeated while the phone is still on its way.
            if (busy && !repeat) return null
            val intro = message.steerMed?.id != aimed || now - introAt > INTRO_AGAIN_MS
            if (!repeat && !intro && saidAt != Long.MIN_VALUE && now - saidAt < if (news == saidNews) MOVE_AGAIN_MS else MOVE_NEW_MS) return null
            if (intro) { aimed = message.steerMed?.id; introAt = now }
            say(message, gist, news, now)
            return message.copy(intro = intro)
        }
        // Nothing is said about what has only just appeared. A finger passing over a strip on its way elsewhere is
        // not a question, a guess from a piece with no name must hold for a second, and "I cannot read a name"
        // is not said in the gap while one strip is being swapped for another.
        val stableFor = when (message.hint) {
            ScanHint.FOUND -> FOUND_MS
            ScanHint.HAND -> if (message.hand == HandCue.Closer) GUESS_MS else TOUCH_MS
            ScanHint.PIECE, ScanHint.AMBIGUOUS, ScanHint.APART, ScanHint.OVERVIEW -> GUESS_MS
            ScanHint.SEARCHING, ScanHint.CLOSER -> EMPTY_MS
            else -> 0L
        }
        if (now - observedAt < stableFor && !repeat) return null
        if (!repeat && sameNews(news)) return null
        val urgent = urgent(message)
        // "Here it is" follows the last direction at once; it is what the directions were leading to.
        val arrived = message.hint == ScanHint.FOUND && said?.hint == ScanHint.AIM
        // Another medicine in front of the camera is never kept waiting behind the usual pause.
        val changed = subject(message) != null && subject(message) != said?.let(::subject)
        if (!repeat && !urgent && (busy || (!arrived && !changed && saidAt != Long.MIN_VALUE && now - saidAt < QUIET_MS))) return null
        return say(message, gist, news, now)
    }

    private fun say(message: ScanMessage, gist: ScanMessage, news: Any, now: Long): ScanMessage {
        said = gist
        saidNews = news
        saidAt = now
        return message
    }

    /**
     * Already said. A strip whose strength was read and is hidden again for a moment is the same strip: going from
     * "checked" back to "name only" is not news. And after a pointing finger was answered, the same strip
     * in the frame is not announced a second time.
     */
    private fun sameNews(news: Any): Boolean {
        val before = saidNews
        if (news == before) return true
        if (news !is Is || before !is Is || news.med != before.med || news.timing != before.timing) return false
        // The same strip in the same state, once from the camera and once from a pointing finger, is one piece of news.
        // On a real table the two took turns for as long as a hand flickered in and out of view. Only something the
        // camera's sentence adds and the finger's answer did not have (a warning, the due strip elsewhere) is still said.
        if (news.hand) return true
        if (before.hand) return news.elsewhere == null && !news.conflict
        if (news.conflict != before.conflict || news.elsewhere != before.elsewhere) return false
        // Same medicine, same state, only the strength differs. Losing it is not news; gaining it is, once.
        return news.nameOnly
    }

    /** A refusal, a refusal appearing beside the target, a lost target and an answer to a hand do not wait. */
    fun urgent(message: ScanMessage): Boolean =
        message.hint in setOf(ScanHint.LOST, ScanHint.CONFLICT) ||
            (message.hint == ScanHint.HAND && message.hand !is HandCue.Move && message.hand != HandCue.Closer) ||
            (message.otherConflict && said?.otherConflict != true)

    /** The medicine a message is about, when it is about one. */
    fun subject(message: ScanMessage): String? = when (message.hint) {
        ScanHint.FOUND -> message.target?.med?.id
        ScanHint.AIM -> message.steerMed?.id
        ScanHint.PIECE -> message.piece?.id
        ScanHint.CONFLICT -> "refused"
        ScanHint.OVERVIEW -> "table"
        ScanHint.HAND -> when (val cue = message.hand) {
            is HandCue.Yes -> cue.med.id
            is HandCue.No -> cue.med?.id ?: "other"
            is HandCue.Info -> cue.med?.id ?: "other"
            HandCue.Closer -> "unread"
            // Directions to a moving hand are about the strip they lead to, which is whatever was spoken of.
            else -> subject
        }
        else -> null
    }

    private fun gist(message: ScanMessage) = if (message.hint == ScanHint.OVERVIEW) message else message.copy(target = message.target?.copy(trackId = 0, side = ScanSide.SINGLE),
        elsewhere = message.elsewhere?.copy(second = Toward.LEFT))

    private fun news(message: ScanMessage): Any {
        val target = message.target
        if (message.hint == ScanHint.FOUND && target != null)
            return Is(target.med.id, target.timing, target.nameOnly, message.otherConflict,
                listOfNotNull(message.elsewhere?.first?.id, message.shared?.other?.id?.let { "same:$it" }, message.expired?.let { "expired" }, message.owner?.let { "owner:${it.id}" }).joinToString("|").ifEmpty { null })
        val cue = message.hand
        if (cue is HandCue.Info && cue.med != null && cue.timing != null && cue.kind != PlacedKind.REFUSED)
            return Is(cue.med.id, cue.timing, cue.kind == PlacedKind.NAME, false, null, hand = true)
        return gist(message)
    }

    fun reset() { observed = null; said = null; saidNews = null; saidAt = Long.MIN_VALUE; aimed = null; handAt = Long.MIN_VALUE }

    /** The hand left for good. Pointing at the same strip again is a new question and gets its answer again. */
    fun handGone() { if (said?.hint == ScanHint.HAND) saidNews = null }

    companion object {
        /** How long "I lost that strip" stays current before ordinary searching help returns. */
        const val LOST_MS = 4000L

        /** With nothing in front of the camera for this long, a sentence about the strip that was there is cut off. */
        const val GONE_MS = 1200L

        /** With another medicine in front of the camera for this long, the sentence about the one before is cut off. */
        const val CHANGED_MS = 350L

        /** A strip must have been read steadily for this long before it is named aloud. */
        const val FOUND_MS = 500L

        /** Nothing readable for this long before the phone says so. Shorter gaps are a strip being swapped. */
        const val EMPTY_MS = 1800L

        /** The least time between two ordinary announcements. */
        const val QUIET_MS = 2500L

        /** A hand out of view for longer than this, and back, is asking again. */
        const val REASK_MS = 3000L

        /** A strip named longer ago than this is named again when a finger points at it. */
        const val POINT_AGAIN_MS = 5000L

        /** After this long the medicine's name is said with the direction again. */
        const val INTRO_AGAIN_MS = 12_000L
        const val TOUCH_MS = 250L
        const val GUESS_MS = 1000L
        const val MOVE_NEW_MS = 700L
        const val MOVE_AGAIN_MS = 1800L
    }
}
