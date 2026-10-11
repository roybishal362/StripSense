package app.stripsense.core.say

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import java.time.LocalDate
import java.time.LocalTime

/** Part of the day of a time, by its hour. Spec 03 section 1. */
enum class Slot {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(time: LocalTime): Slot = when (time.hour) {
            in 4..11 -> MORNING
            in 12..15 -> AFTERNOON
            in 16..19 -> EVENING
            else -> NIGHT
        }
    }
}

/** A nickname or a note in both languages. */
data class Bilingual(val en: String, val hi: String) {
    fun get(lang: String) = if (lang == "hi") hi.ifEmpty { en } else en
}

data class ListItem(val nickname: Bilingual, val time: LocalTime)

/** daysAgo = difference in dosing dates. */
data class WhenValue(val daysAgo: Int, val date: LocalDate)

/** A sentence ID plus its values. */
data class Part(val id: String, val values: Map<String, Any> = emptyMap())

/** boxed = the {printed} text of V1, drawn in a red box on screen. */
data class Rendered(val shown: String, val spoken: String, val boxed: String? = null)

/** Every sentence the phone says or shows as an answer (spec 03). No answer text exists anywhere else. */
object Sentences {
    /** The few words under a strip's name on the camera picture. Short, so the label does not cover the strip. */
    fun chipStatus(timing: MedicationTiming, nameOnly: Boolean, lang: String): String {
        val time = timing.slot?.let { Words.timeShown(it.time, lang) }.orEmpty()
        val status = if (lang == "hi") when (timing.kind) {
            TimingKind.NOW -> "अभी का समय"
            TimingKind.NEXT -> "अगली $time"
            TimingKind.LATER -> "बाद में $time"
            TimingKind.UNCONFIRMED -> "$time दर्ज नहीं"
            TimingKind.MARKED -> "✓ $time दर्ज"
            TimingKind.NOT_SURE -> "$time पक्का नहीं"
            TimingKind.NO_SCHEDULE -> "समय सेव नहीं"
            TimingKind.STOPPED -> "बंद"
        } else when (timing.kind) {
            TimingKind.NOW -> "Due now"
            TimingKind.NEXT -> "Next $time"
            TimingKind.LATER -> "Later $time"
            TimingKind.UNCONFIRMED -> "$time not marked"
            TimingKind.MARKED -> "✓ $time marked"
            TimingKind.NOT_SURE -> "$time not sure"
            TimingKind.NO_SCHEDULE -> "No time saved"
            TimingKind.STOPPED -> "Stopped"
        }
        return if (!nameOnly) status else status + if (lang == "hi") " · मात्रा नहीं पढ़ी" else " · strength not read"
    }

    /** The saved schedule of a medicine, said the way a person at the table would say it. */
    fun spokenStatus(timing: MedicationTiming, lang: String): String {
        val time = timing.slot?.let { Words.timeShown(it.time, lang) }.orEmpty()
        return if (lang == "hi") when (timing.kind) {
            TimingKind.NOW -> "इसका समय अभी है।"
            TimingKind.NEXT -> "अगली खुराक $time पर है। अभी नहीं।"
            TimingKind.LATER -> "यह बाद के लिए है, $time पर।"
            TimingKind.UNCONFIRMED -> "$time की खुराक अभी दर्ज नहीं है।"
            TimingKind.MARKED -> "$time की खुराक दर्ज हो चुकी है।"
            TimingKind.NOT_SURE -> "$time की खुराक पक्की नहीं है। परिवार से पूछिए।"
            TimingKind.NO_SCHEDULE -> "इसका कोई समय सेव नहीं है।"
            TimingKind.STOPPED -> "यह दवा आपकी सूची में बंद है।"
        } else when (timing.kind) {
            TimingKind.NOW -> "It's time for this one now."
            TimingKind.NEXT -> "The next dose is at $time. Not yet."
            TimingKind.LATER -> "That one is for later, at $time."
            TimingKind.UNCONFIRMED -> "The $time dose is not marked yet."
            TimingKind.MARKED -> "The $time dose is already marked."
            TimingKind.NOT_SURE -> "The $time dose is marked not sure. Ask your family."
            TimingKind.NO_SCHEDULE -> "No times are saved for it."
            TimingKind.STOPPED -> "It is stopped on your list."
        }
    }

    fun scanGuidance(message: ScanMessage, lang: String): String {
        val hi = lang == "hi"
        val target = message.target
        if (message.hint == ScanHint.FOUND && target != null) {
            val name = Answers.nickname(target.med).get(lang)
            val side = if (hi) when (target.side) {
                ScanSide.LEFT -> "बाईं तरफ़। "; ScanSide.RIGHT -> "दाईं तरफ़। "; ScanSide.CENTRE -> "बीच में। "; ScanSide.SINGLE -> ""
            } else when (target.side) {
                ScanSide.LEFT -> "On the left. "; ScanSide.RIGHT -> "On the right. "; ScanSide.CENTRE -> "In the middle. "; ScanSide.SINGLE -> ""
            }
            // Somebody else's strip in the same home. Said plainly and nothing more: its doses are not this person's business.
            message.owner?.let { owner ->
                val whose = owner.call(lang)
                return side + if (hi) "यह $whose की $name है। यह आपकी नहीं है।" else "This is $whose's $name. It is not yours."
            }
            val evidence = if (!target.nameOnly) "" else if (hi) " नाम पढ़ लिया है, पर मात्रा अभी नहीं पढ़ी।" else " I have read its name, but not its strength yet."
            val other = if (!message.otherConflict) "" else if (hi) " दिख रही एक और पट्टी सेव दवाओं से मेल नहीं खाती। उस पर भरोसा न करें।"
                else " Another strip in view does not match the saved medicines. Do not rely on it."
            val due = message.elsewhere?.let { (med, toward) ->
                val dueName = Answers.nickname(med).get(lang)
                if (message.asked) return@let (if (hi) " जो दवा आपने पूछी, $dueName, वह " else " The one you asked for, $dueName, is ") + (if (hi) when (toward) {
                    Toward.LEFT -> "बाईं तरफ़"; Toward.RIGHT -> "दाईं तरफ़"; Toward.AWAY -> "थोड़ा आगे"; Toward.CLOSER -> "थोड़ा आपकी तरफ़" } + " है।"
                else when (toward) {
                    Toward.LEFT -> "on the left"; Toward.RIGHT -> "on the right"; Toward.AWAY -> "a little further ahead"; Toward.CLOSER -> "closer to you" } + ".")
                if (hi) " आपकी $dueName का समय हो गया है। वह " + when (toward) {
                    Toward.LEFT -> "बाईं तरफ़"; Toward.RIGHT -> "दाईं तरफ़"; Toward.AWAY -> "थोड़ा आगे"; Toward.CLOSER -> "थोड़ा आपकी तरफ़" } + " है।"
                else " Your $dueName is the one due now. It is " + when (toward) {
                    Toward.LEFT -> "on the left"; Toward.RIGHT -> "on the right"; Toward.AWAY -> "a little further ahead"; Toward.CLOSER -> "closer to you" } + "."
            }.orEmpty()
            return side + (if (hi) "यह आपकी $name है। " else "This is your $name. ") + spokenStatus(target.timing, lang) + evidence +
                message.expired?.let { " " + expired(it, lang) }.orEmpty() + message.shared?.let { " " + sameIngredient(it, lang) }.orEmpty() + due + other
        }
        message.hand?.let { return handCue(it, lang) }
        if (message.hint == ScanHint.OVERVIEW && message.overview.isNotEmpty()) return overview(message.overview, message.farToNear, lang)
        val steer = message.steer
        if (message.hint == ScanHint.AIM && steer != null) {
            // Which way the phone should move. The first time the medicine is named; after that the direction is enough.
            if (!message.intro) return if (hi) when (steer) {
                Toward.LEFT -> "बाएँ।"; Toward.RIGHT -> "दाएँ।"; Toward.AWAY -> "आगे।"; Toward.CLOSER -> "पीछे।"
            } else when (steer) {
                Toward.LEFT -> "Left."; Toward.RIGHT -> "Right."; Toward.AWAY -> "Forward."; Toward.CLOSER -> "Back."
            }
            val name = message.steerMed?.let { Answers.nickname(it).get(lang) }.orEmpty()
            return if (hi) when (steer) {
                Toward.LEFT -> "आपकी $name बाईं तरफ़ है। फ़ोन बाईं ओर ले जाइए।"
                Toward.RIGHT -> "आपकी $name दाईं तरफ़ है। फ़ोन दाईं ओर ले जाइए।"
                Toward.AWAY -> "आपकी $name थोड़ा आगे है। फ़ोन आगे ले जाइए।"
                Toward.CLOSER -> "आपकी $name थोड़ा पीछे है। फ़ोन अपनी ओर लाइए।"
            } else when (steer) {
                Toward.LEFT -> "Your $name is on the left. Move the phone left."
                Toward.RIGHT -> "Your $name is on the right. Move the phone right."
                Toward.AWAY -> "Your $name is a little further ahead. Move the phone forward."
                Toward.CLOSER -> "Your $name is closer to you. Move the phone back."
            }
        }
        message.piece?.let { if (message.hint == ScanHint.PIECE) return referenceCandidate(it, lang) }
        if (message.hint == ScanHint.APART) return if (hi) "दो पट्टियाँ सटी हुई हैं। इन्हें थोड़ा अलग रखें।" else "Two strips are touching. Move them a little apart."
        return if (hi) when (message.hint) {
            ScanHint.START -> message.steerMed?.let { roundWords(message.roundAt, lang) + "आपकी ${Answers.nickname(it).get(lang)} ढूँढ रहा हूँ। फ़ोन धीरे-धीरे अपनी दवाओं के ऊपर घुमाइए।" } ?: "फ़ोन अपनी दवाओं की तरफ़ कीजिए।"
            ScanHint.CLOSER -> "लिखावट छोटी है। फ़ोन थोड़ा पास लाइए।"
            ScanHint.CONFLICT -> "ध्यान दीजिए। इस पट्टी की छपाई आपकी सेव दवा से अलग है। इस पर भरोसा न करें। परिवार से पूछिए।"
            ScanHint.AMBIGUOUS -> nameAmbiguous(lang)
            ScanHint.LOST -> "फिर ढूँढ रहा हूँ।"
            else -> message.steerMed?.let { "आपकी ${Answers.nickname(it).get(lang)} अभी नहीं दिखी। फ़ोन धीरे-धीरे घुमाते रहिए।" } ?: "अभी नाम नहीं पढ़ पाया। पट्टी पास लाइए, छपी तरफ़ ऊपर।"
        } else when (message.hint) {
            ScanHint.START -> message.steerMed?.let { roundWords(message.roundAt, lang) + "Looking for your ${Answers.nickname(it).get(lang)}. Move the phone slowly over your medicines." } ?: "Point the phone at your medicines."
            ScanHint.CLOSER -> "The print is small. Bring the phone a little closer."
            ScanHint.CONFLICT -> "Careful. The print on this strip is different from your saved medicine. Do not rely on it. Ask your family."
            ScanHint.AMBIGUOUS -> nameAmbiguous(lang)
            ScanHint.LOST -> "Looking for it again."
            else -> message.steerMed?.let { "I don't see your ${Answers.nickname(it).get(lang)} yet. Keep moving the phone slowly." } ?: "I can't read a name yet. Bring a strip closer, printed side up."
        }
    }

    private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val MONTHS_HI = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

    /** The printed expiry date has passed. What the strip says, and who to ask. Never an instruction about the medicine. */
    fun expired(date: ExpiryDate, lang: String): String =
        if (lang == "hi") "ध्यान दीजिए। इस पट्टी पर छपी एक्सपायरी, ${MONTHS_HI[date.month - 1]} ${date.year}, निकल चुकी है। इस्तेमाल से पहले परिवार से पूछिए।"
        else "Careful. The expiry date printed on this strip, ${MONTHS_EN[date.month - 1]} ${date.year}, has passed. Ask your family before using it."

    /** The expiry date on the strip's box: "Exp Nov 2027", or "Expired Mar 2026". */
    fun expiryChip(date: ExpiryDate, passed: Boolean, lang: String): String =
        if (lang == "hi") (if (passed) "एक्सपायर हो चुकी: " else "एक्सपायरी ") + "${MONTHS_HI[date.month - 1]} ${date.year}"
        else (if (passed) "Expired " else "Exp ") + "${MONTHS_EN[date.month - 1].take(3)} ${date.year}"

    /**
     * Every medicine on the table in the order it lies, each by what it is for where the family said so.
     * Two medicines for the same thing are told apart by their own names.
     */
    fun overview(meds: List<Medicine>, farToNear: Boolean, lang: String): String {
        val hi = lang == "hi"
        fun short(med: Medicine): String {
            val purpose = Purposes.of(med) ?: return Answers.nickname(med).get(lang)
            return if (meds.count { Purposes.of(it) == purpose } > 1) Answers.nickname(med).get(lang) else if (hi) purpose.hi else purpose.en
        }
        val names = meds.map { (if (hi) "आपकी " else "your ") + short(it) }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString("; ") + (if (hi) "; और " else "; and ") + names.last()
        return if (hi) "आपकी ${meds.size} दवाएँ दिख रही हैं। " + (if (farToNear) "दूर से पास की तरफ़: " else "बाएँ से दाएँ: ") + list + "।"
        else "I can see ${meds.size} of your medicines. " + (if (farToNear) "From the far side to the near side: " else "From left to right: ") + list + "."
    }

    /**
     * Two saved medicines with one ingredient, one of them already marked today. A fact from the family's list
     * and a way to get help. Never advice about what to take.
     */
    fun sameIngredient(shared: SharedIngredient, lang: String): String {
        val other = Answers.nickname(shared.other).get(lang)
        val at = shared.markedAt?.let { Words.timeShown(it, lang) }
        return if (lang == "hi") {
            if (at != null) "ध्यान दीजिए। इसमें भी ${shared.ingredient} है, जैसे आपकी $other में, जो आज $at पर दर्ज हुई। दोनों लेने से पहले परिवार से पूछिए।"
            else "इसमें और आपकी $other में एक ही दवा है, ${shared.ingredient}।"
        } else {
            if (at != null) "Careful. It has ${shared.ingredient}, the same as your $other, which was marked today at $at. Ask your family before taking both."
            else "It has ${shared.ingredient}, the same as your $other."
        }
    }

    /** What the phone says about the strip under the finger, and which way the due strip is. */
    fun handCue(cue: HandCue, lang: String): String {
        val hi = lang == "hi"
        fun way(toward: Toward) = if (hi) when (toward) {
            Toward.LEFT -> "बाईं तरफ़"; Toward.RIGHT -> "दाईं तरफ़"; Toward.AWAY -> "थोड़ा आगे"; Toward.CLOSER -> "थोड़ा अपनी तरफ़"
        } else when (toward) {
            Toward.LEFT -> "to the left"; Toward.RIGHT -> "to the right"; Toward.AWAY -> "further away from you"; Toward.CLOSER -> "closer to you"
        }
        fun what(med: Medicine?, kind: PlacedKind, timing: MedicationTiming?): String = when {
            kind == PlacedKind.REFUSED -> if (hi) "इस पट्टी की छपाई सेव दवा से अलग है। इस पर भरोसा न करें।" else "The print on this strip differs from the saved medicine. Do not rely on it."
            med == null || timing == null -> if (hi) "यह पट्टी आपकी सेव दवाओं में नहीं है।" else "This strip is not one of your saved medicines."
            else -> (if (hi) "यह आपकी ${Answers.nickname(med).get(lang)} है। " else "This is your ${Answers.nickname(med).get(lang)}. ") + spokenStatus(timing, lang) +
                (if (kind != PlacedKind.NAME) "" else if (hi) " इसकी मात्रा नहीं पढ़ी गई।" else " Its strength was not read.")
        }
        return when (cue) {
            is HandCue.Yes -> (if (hi) "हाँ। यही है। " else "Yes. This is the one. ") + Answers.nickname(cue.med).get(lang) + (if (hi) "।" else ".")
            is HandCue.No -> (if (hi) "यह नहीं। " else "Not this one. ") + what(cue.med, cue.kind, cue.timing) +
                (cue.toward?.let { if (hi) " आपकी दवा ${way(it)} है।" else " Yours is ${way(it)}." } ?: "")
            is HandCue.Info -> what(cue.med, cue.kind, cue.timing)
            HandCue.Closer -> if (hi) "जिस पट्टी की तरफ़ आप इशारा कर रहे हैं, उसे यहाँ से पढ़ नहीं पा रहा। फ़ोन उसके पास लाइए।"
                else "I can't read the strip you are pointing at from here. Bring the phone closer to it."
            is HandCue.Move -> if (hi) when (cue.toward) {
                Toward.LEFT -> "बाएँ।"; Toward.RIGHT -> "दाएँ।"; Toward.AWAY -> "आगे।"; Toward.CLOSER -> "अपनी तरफ़।"
            } else when (cue.toward) {
                Toward.LEFT -> "Left."; Toward.RIGHT -> "Right."; Toward.AWAY -> "Further."; Toward.CLOSER -> "Closer."
            }
        }
    }

    fun nameAmbiguous(lang: String): String = if (lang == "hi")
        "इस नाम की एक से ज़्यादा दवाएँ सेव हैं। मात्रा और दवा का प्रकार दिखाएँ।"
        else "More than one saved medicine has this name. Show the strength and formulation."

    fun nameSchedule(med: Medicine, timing: MedicationTiming, lang: String): String =
        (if (lang == "hi") "नाम मिला · " else "Name matched · ") + sceneTiming(med, timing, lang)

    fun nameCheck(lang: String): String = if (lang == "hi")
        "नाम से सेव समय दिखाया गया है। मात्रा और दवा का प्रकार जाँचा नहीं गया है। लेने से पहले परिवार की सेव दवा से मिलाएँ।"
        else "Showing the saved schedule by name. Strength and formulation are not verified. Check against your family's saved medicine before taking it."

    fun sceneConflict(verdict: Verdict.Different, lang: String): String =
        if (lang == "hi") "छपा: ${verdict.printed} · सेव: ${verdict.listPart}"
        else "Print: ${verdict.printed} · Saved: ${verdict.listPart}"
    fun sceneTiming(med: Medicine, timing: MedicationTiming, lang: String): String {
        val name = Answers.nickname(med).get(lang)
        val time = timing.slot?.let { Words.timeShown(it.time, lang) }.orEmpty()
        val status = if (lang == "hi") when (timing.kind) {
            TimingKind.NOW -> "अभी का समय $time; दर्ज नहीं है"
            TimingKind.NEXT -> "अगली खुराक $time; अभी न लें"
            TimingKind.LATER -> "बाद में $time"
            TimingKind.UNCONFIRMED -> "$time का समय बीता; पुष्टि नहीं हुई"
            TimingKind.MARKED -> "✓ $time की खुराक दर्ज है"
            TimingKind.NOT_SURE -> "$time: पक्का नहीं; परिवार से पूछें"
            TimingKind.NO_SCHEDULE -> "समय सेव नहीं है"
            TimingKind.STOPPED -> "दवा बंद है"
        } else when (timing.kind) {
            TimingKind.NOW -> "Scheduled now $time; not marked"
            TimingKind.NEXT -> "Next at $time; not due yet"
            TimingKind.LATER -> "Later at $time"
            TimingKind.UNCONFIRMED -> "$time passed; not confirmed"
            TimingKind.MARKED -> "✓ $time already marked"
            TimingKind.NOT_SURE -> "$time not sure; ask family"
            TimingKind.NO_SCHEDULE -> "No saved schedule"
            TimingKind.STOPPED -> "Stopped"
        }
        return "$name · $status"
    }
    fun reminder(med: Medicine, slot: DoseSlot, lang: String): String {
        val time = Words.timeShown(slot.time, lang)
        val name = Answers.nickname(med).get(lang)
        val strength = Strengths.display(med.strengths)
        return if (lang == "hi") "$name, $strength, $time का निर्धारित समय है। केवल दवा लेने के बाद पुष्टि करें। देर की खुराक के लिए डॉक्टर के निर्देश देखें या परिवार से पूछें।"
            else "$name, $strength, scheduled at $time. Confirm only if you have taken it. For a late dose, check the prescribed instructions or ask your family."
    }
    /**
     * The answer to a question asked of the camera (see SceneQuestion). A plain yes or no first, because that is
     * what was asked; then what the strip in the frame is; then where the one asked for lies.
     */
    fun sceneAnswer(answer: app.stripsense.core.voice.SceneAnswer, lang: String): String {
        val hi = lang == "hi"
        fun name(med: Medicine?) = med?.let { Answers.nickname(it).get(lang) }.orEmpty()
        fun where(toward: Toward?) = when (toward) {
            Toward.LEFT -> if (hi) "बाईं तरफ़" else "on the left"
            Toward.RIGHT -> if (hi) "दाईं तरफ़" else "on the right"
            Toward.AWAY -> if (hi) "थोड़ा आगे" else "a little further ahead"
            Toward.CLOSER -> if (hi) "थोड़ा आपकी तरफ़" else "closer to you"
            null -> ""
        }
        val status = answer.timing?.let { " " + spokenStatus(it, lang) }.orEmpty()
        val unread = if (!answer.nameOnly) "" else if (hi) " इसकी मात्रा अभी नहीं पढ़ी।" else " I have not read its strength yet."
        return when (answer.kind) {
            app.stripsense.core.voice.SceneAnswer.Kind.YES -> (if (hi) "हाँ। यह आपकी ${name(answer.asked)} है।" else "Yes. This is your ${name(answer.asked)}.") + status + unread
            app.stripsense.core.voice.SceneAnswer.Kind.NO_OTHER_SIDE -> if (hi) "नहीं। यह आपकी ${name(answer.shown)} है। आपकी ${name(answer.asked)} ${where(answer.toward)} है।"
                else "No. This is your ${name(answer.shown)}. Your ${name(answer.asked)} is ${where(answer.toward)}."
            app.stripsense.core.voice.SceneAnswer.Kind.NO_NOT_HERE -> if (hi) "नहीं। यह आपकी ${name(answer.shown)} है। आपकी ${name(answer.asked)} यहाँ नहीं दिख रही।"
                else "No. This is your ${name(answer.shown)}. I don't see your ${name(answer.asked)} here."
            app.stripsense.core.voice.SceneAnswer.Kind.WHERE -> if (hi) "आपकी ${name(answer.asked)} ${where(answer.toward)} है। फ़ोन उस तरफ़ ले जाइए।" else "Your ${name(answer.asked)} is ${where(answer.toward)}. Move the phone that way."
            app.stripsense.core.voice.SceneAnswer.Kind.NOT_IN_VIEW -> if (hi) "आपकी ${name(answer.asked)} अभी नहीं दिख रही। फ़ोन धीरे-धीरे दवाओं के ऊपर घुमाइए, मैं बता दूँगा।"
                else "I don't see your ${name(answer.asked)} yet. Move the phone slowly over your medicines and I will tell you."
            app.stripsense.core.voice.SceneAnswer.Kind.THIS_IS -> (if (hi) "यह आपकी ${name(answer.shown)} है।" else "This is your ${name(answer.shown)}.") + status + unread
            app.stripsense.core.voice.SceneAnswer.Kind.NOTHING_IN_FRAME -> if (hi) "बीच में कोई पट्टी पढ़ नहीं पा रहा। पट्टी को फ़्रेम में लाइए, छपी तरफ़ ऊपर।" else "I can't read a strip in the middle yet. Bring it into the frame, printed side up."
            app.stripsense.core.voice.SceneAnswer.Kind.DUE_NOW -> (if (hi) "अभी आपकी ${name(answer.asked)} का समय है।" else "Right now it is time for your ${name(answer.asked)}.") +
                (answer.toward?.let { if (hi) " वह ${where(it)} है।" else " It is ${where(it)}." } ?: if (hi) " फ़ोन दवाओं के ऊपर घुमाइए, मैं उस तक ले चलूँगा।" else " Move the phone over your medicines and I will lead you to it.")
            app.stripsense.core.voice.SceneAnswer.Kind.NOTHING_DUE -> if (hi) "अभी किसी दवा का समय नहीं है।" else "No medicine is due right now."
            app.stripsense.core.voice.SceneAnswer.Kind.HELP -> if (hi) "मदद का पेज खोल रहा हूँ।" else "Opening the help page."
            app.stripsense.core.voice.SceneAnswer.Kind.UNCLEAR -> if (hi) "समझ नहीं पाया। पूछिए, जैसे: यह कौन-सी दवा है? या मेरी बीपी की दवा कहाँ है?" else "I did not catch that. Ask, for example: which one is this? Or: where is my BP medicine?"
        }
    }

    /** "Medicine 2 of 3." in front of what the phone is looking for, so the person knows how many are left. */
    private fun roundWords(at: Pair<Int, Int>?, lang: String): String = when {
        at == null || at.second < 2 -> ""
        lang == "hi" -> "${at.second} में से दवा ${at.first}। "
        else -> "Medicine ${at.first} of ${at.second}. "
    }

    /** The end of a round: how many of its doses are marked. A skipped one is said as left, not as done. */
    fun roundDone(marked: Int, total: Int, lang: String): String = when {
        lang == "hi" && marked == total -> "इस समय की सब $total दवाइयाँ दर्ज हो गईं।"
        lang == "hi" -> "$total में से $marked दवा दर्ज हुई। बाक़ी आज के पेज पर दिख रही हैं।"
        marked == total -> if (total == 1) "This medicine is marked. That was all for this time." else "All $total medicines for this time are marked."
        else -> "$marked of $total are marked. The others are still on the Today page."
    }

    /** "Night medicines": the part of the day a dose time belongs to, as the prototype named it. */
    fun reminderTitle(time: java.time.LocalTime, lang: String): String =
        if (lang == "hi") "${dayPart(time, lang)} की दवाइयाँ" else dayPart(time, lang).replaceFirstChar { it.uppercase() } + " medicines"

    /** The one line under the title, to the person by name. With a strip among them it says what to press. */
    fun reminderLine(call: String, time: java.time.LocalTime, camera: Boolean, lang: String): String {
        val part = dayPart(time, lang)
        // No name was saved for the person: the line starts without one, not with a comma.
        val way = if (!camera) "" else if (lang == "hi") " फ़ोन उठाइए, या \"अभी कौन-सी?\" दबाइए। मैं हर पट्टी तक ले चलूँगा।"
            else " Pick up the phone, or press \"Which one now?\" I will lead you to each strip."
        if (call.isBlank()) return (if (lang == "hi") "$part की दवा का समय हो गया है।" else "It's time for your $part medicines.") + way
        return (if (lang == "hi") "$call, $part की दवा का समय हो गया है।" else "$call, it's time for your $part medicines.") + way
    }

    private fun dayPart(time: java.time.LocalTime, lang: String): String = when {
        time.hour < 12 -> if (lang == "hi") "सुबह" else "morning"
        time.hour < 16 -> if (lang == "hi") "दोपहर" else "afternoon"
        time.hour < 19 -> if (lang == "hi") "शाम" else "evening"
        else -> if (lang == "hi") "रात" else "night"
    }

    /**
     * What the alarm says aloud: the hour, how many medicines, each by the name the person knows it by, and what
     * to press. It names the dose. It never says to take anything; the strips are checked first.
     */
    fun reminderRound(meds: List<Medicine>, time: java.time.LocalTime, call: String, lang: String): String {
        val hi = lang == "hi"
        val clock = Words.timeShown(time, lang)
        val names = meds.map { (if (hi) "आपकी " else "your ") + Answers.nickname(it).get(lang) }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString("; ") + (if (hi) "; और " else "; and ") + names.last()
        val who = if (call.isBlank()) "" else "$call, "
        // Picking the phone up is the first way on: it needs no button to be found.
        return if (hi) "$who$clock हो गए हैं। यह ${meds.size} दवा का समय है: $list। फ़ोन उठाइए, या \"अभी कौन-सी\" दबाइए, मैं हर पट्टी तक ले चलूँगा।"
        else (if (call.isBlank()) "It is" else "$call, it is") + " $clock. This is the time for ${meds.size} ${if (meds.size == 1) "medicine" else "medicines"}: $list. Pick up the phone, or press \"Which one now\", and I will lead you to each strip."
    }

    /**
     * The answer to "which medicine at eight in the evening?": what the family saved for that time, each by the
     * name the person knows it by, and which of them is already marked. It reads out the saved list. It does
     * not tell her to take anything.
     */
    fun dueAt(due: app.stripsense.core.voice.DueAnswer, lang: String): String {
        val hi = lang == "hi"
        val time = due.time
        if (time == null || due.items.isEmpty())
            return if (hi) "उस समय के लिए कोई दवा सेव नहीं है।" else "No medicine is saved for that time."
        val clock = Words.timeShown(time, lang)
        val names = due.items.map { item ->
            (if (hi) "आपकी " else "your ") + Answers.nickname(item.med).get(lang) + (if (!item.marked) "" else if (hi) " (दर्ज हो चुकी)" else " (already marked)")
        }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString("; ") + (if (hi) "; और " else "; and ") + names.last()
        val n = due.items.size
        return if (hi) "$clock के लिए आपकी $n दवा सेव है: $list। ${if (n == 1) "इसकी तस्वीर" else "इनकी तस्वीरें"} नीचे है।"
        else "For $clock you have $n ${if (n == 1) "medicine" else "medicines"} saved: $list. ${if (n == 1) "Its picture is" else "Their pictures are"} below."
    }

    /** A follow-up asks. It never repeats an instruction to take the medicine. */
    fun reminderFollowUp(med: Medicine, slot: DoseSlot, lang: String): String {
        val time = Words.timeShown(slot.time, lang)
        val name = Answers.nickname(med).get(lang)
        val strength = Strengths.display(med.strengths)
        return if (lang == "hi") "$name, $strength, $time का समय। क्या आपने यह ले ली है? ली हो तभी पुष्टि करें। पक्का न हो तो परिवार से पूछें।"
            else "$name, $strength, saved for $time. Have you already taken it? Confirm only if you have. If you are not sure, ask your family."
    }
    /** A retrieved reference is a comparison request, never a confirmed dose answer. */
    fun referenceCandidate(med: Medicine, lang: String): String {
        val nickname = if (lang == "hi") med.nickHi.ifEmpty { med.nickEn } else med.nickEn
        return if (lang == "hi") "यह आपकी $nickname जैसी लगती है, पर नाम अभी नहीं पढ़ा गया। इसे पास लाइए या घुमाइए।"
            else "This looks like your $nickname, but I can't read its name yet. Bring it closer or turn it."
    }


    fun render(parts: List<Part>, lang: String, person: Person): List<Rendered> =
        parts.map { render(it, lang, person) }

    fun render(part: Part, lang: String, person: Person): Rendered {
        val template = template(part.id, lang, person) ?: return Rendered("", "")
        val withCall = SentenceValues.fillCall(template, person.call(lang))
        val shown = SentenceValues.fill(withCall, part.values, lang, person, spoken = false)
        val spoken = SentenceValues.fill(withCall, part.values, lang, person, spoken = true)
        val boxed = if (part.id == "V1") (part.values["printed"] as? String) else null
        return Rendered(shown, spoken, boxed)
    }

    /** True when the table has this ID. */
    fun has(id: String): Boolean = id in EN

    private fun template(id: String, lang: String, person: Person): String? {
        if (lang != "hi") return EN[id]
        if (person.isWoman) HI_WOMAN[id]?.let { return it }
        return HI[id]
    }

    val EN: Map<String, String> = mapOf(
        // Section 2: strip answers and the question
        "NAME" to "That's your {nickname}, {strength}.",
        "P1" to "{Call}, this is your {nickname}, {strength}, the {slot} one. Nothing's marked for {now_part} yet.",
        "P3" to "That's your {nickname}, {strength}. Nothing's marked for {now_part}.",
        "A1" to "That's your {nickname}, {strength}. It's already marked for {now_part}, at {time}{ago}.",
        "W1" to "That's your {nickname}, {strength}. It isn't time yet: the next one is the {slot} one, at {time}.",
        "P2" to "Last one was marked {when} at {time}{ago}.",
        "P2n" to "It hasn't been marked before.",
        "NX1" to "The next one is at {time}.",
        "NS1" to "Earlier, at {time}, you said you weren't sure about {this_slot}. Please check with {contact} before taking another one.",
        "CHK" to "Not sure? Ask {contact} before taking another.",
        "NOTE" to "{family}'s note: {note}.",
        "Q1" to "Are you taking it now?",
        "R1" to "Okay, I've marked your {nickname} at {time}.",
        "H1" to "I heard the tablet come out. I've marked your {nickname} at {time}.",
        "LS1" to "Or just push the tablet out. I will hear it.",
        "U2" to "Okay, I've taken the mark off. It's not marked now.",
        "NO1" to "Okay, I won't mark it.",
        "NS3" to "Okay, I've noted that you're not sure. Best to check with {contact}.",
        "NM1" to "Okay, it's not marked.",
        "AM1" to "That's already marked, at {time}{ago}.",
        "MB1" to "Shall I mark your {nickname} for the {slot}?",
        "MB0" to "It isn't time for it right now.",
        // Section 3: refusals
        "RZ1" to "Just one strip at a time, please.",
        "C1" to "I can't read this one. Could you turn it over?",
        "C2" to "Still can't read it. Try laying it flat under a light.",
        "C3" to "I can't be sure about this one. Please ask {contact} or your chemist.",
        "L1" to "I can't check this one with the camera. Please use its Mark button.",
        "L2" to "This one hasn't been taught yet. Please ask {contact} to teach it.",
        "S1" to "This one is marked as stopped on your list, since {date}.",
        "S0" to "Please ask {contact} before you take it.",
        "N1" to "This looks like your {nickname}, {strength}, but it's a strip I haven't seen before. If it's the box, show me the strip inside. If it's a new strip, ask {contact} to add it.",
        "D1" to "This isn't the medicine on your list. Please check with {contact}.",
        "V1" to "Careful: this strip says {printed}, but yours is {listpart}. This one isn't on your list.",
        "U1" to "I'm not sure about this one. Please check with {contact}.",
        "K1" to "I don't recognise this one. It isn't on anyone's list here.",
        // Section 3: voice answers and help
        "VS1" to "Your {nickname}: nothing's marked for {now_part} yet.",
        "VS2" to "Your {nickname} is marked for {now_part}, at {time}{ago}.",
        "VS3" to "Your {nickname}: at {time} you said you weren't sure about {this_slot}.",
        "VS4" to "Your {nickname}: nothing's marked for {now_part}.",
        "VS6" to "Your {nickname} isn't due yet. The next one is the {slot} one, at {time}.",
        "VS8" to "Your {nickname} is marked as stopped on your list, since {date}.",
        "V0" to "Sorry, I didn't catch that. Could you say it again?",
        "V2" to "Hold up the strip and I'll check it for you.",
        "V4" to "Voice is getting ready. The buttons work.",
        "V5" to "Please hold up the strip.",
        "V6" to "Hold up your strips one at a time and I'll tell you which is which.",
        "V7" to "Point the phone at your medicines. I will lead you to it.",
        "V9" to "That one has its own Mark button on the Today screen.",
        "WL0" to "Everything on today's list is marked.",
        "WL0b" to "There's nothing on your list for today.",
        "WL1" to "Later today: {list}.",
        "WL2" to "Not marked yet: {list}.",
        "WL2x" to "Not marked, and the time has passed: {list}.",
        "WL3" to "You weren't sure about: {list}.",
        "MQ1" to "That's one for your doctor or chemist. I don't want to guess.",
        "MD1" to "I can't advise on that. Please check with {contact} before taking it late.",
        "CL1" to "Shall I call {contact}?",
        "EM1" to "Press the green button to call 112.",
        "SH1" to "You don't have to go through this alone. You can talk to someone right now: Tele-MANAS, 14416. It's free, any time.",
        "HLP1" to "I've opened the Help screen. Tap a button to call someone.",
        "HELLO" to "Hi {Call}. What would you like to know?",
        "THX" to "You're welcome, {Call}.",
        "RP0" to "There's nothing to repeat yet.",
    )

    val HI: Map<String, String> = mapOf(
        "NAME" to "ये आपकी {nickname} है, {strength}।",
        "P1" to "{Call}, ये आपकी {nickname} है, {strength}, {slot} वाली। {now_part} की खुराक अभी दर्ज नहीं हुई है।",
        "P3" to "ये आपकी {nickname} है, {strength}। {now_part} की खुराक दर्ज नहीं हुई है।",
        "A1" to "ये आपकी {nickname} है, {strength}। {now_part} की खुराक {at_time} दर्ज हो चुकी है{ago}।",
        "W1" to "ये आपकी {nickname} है, {strength}। अभी इसका समय नहीं हुआ, अगली खुराक {slot} की है, {at_time}।",
        "P2" to "पिछली खुराक {when} {at_time} दर्ज हुई थी{ago}।",
        "P2n" to "ये पहले कभी दर्ज नहीं हुई है।",
        "NX1" to "अगली खुराक {at_time} है।",
        "NS1" to "{at_time} आपने कहा था कि {this_slot} के बारे में पक्का नहीं पता। दूसरी लेने से पहले {contact} से पूछ लीजिए।",
        "CHK" to "पक्का न हो, तो दूसरी लेने से पहले {contact} से पूछ लीजिए।",
        "NOTE" to "{family} ने लिखा है: {note}।",
        "Q1" to "अभी ले रहे हैं?",
        "R1" to "ठीक है, आपकी {nickname} {at_time} दर्ज कर दी।",
        "H1" to "गोली निकलने की आवाज़ सुनाई दी। आपकी {nickname} {at_time} दर्ज कर दी।",
        "LS1" to "या बस गोली निकाल लीजिए, मुझे सुनाई दे जाएगा।",
        "U2" to "ठीक है, निशान हटा दिया। अब ये दर्ज नहीं है।",
        "NO1" to "ठीक है, इसे दर्ज नहीं किया।",
        "NS3" to "ठीक है, लिख लिया कि पक्का नहीं पता। एक बार {contact} से पूछ लीजिए।",
        "NM1" to "ठीक है, दर्ज नहीं हुई।",
        "AM1" to "ये पहले से {at_time} दर्ज है{ago}।",
        "MB1" to "आपकी {nickname}, {slot} वाली, दर्ज कर दूँ?",
        "MB0" to "अभी इसका समय नहीं है।",
        "RZ1" to "एक बार में एक ही पट्टी दिखाइए।",
        "C1" to "ये पढ़ी नहीं जा रही। पट्टी पलट कर दिखाइए।",
        "C2" to "अब भी नहीं पढ़ी जा रही। पट्टी को रोशनी में सीधा रखिए।",
        "C3" to "इसके बारे में पक्का नहीं कह सकते। {contact} या केमिस्ट से पूछ लीजिए।",
        "L1" to "इसे कैमरे से नहीं जाँच सकते। इसके बटन से दर्ज कीजिए।",
        "L2" to "ये अभी सिखाई नहीं गई है। {contact} से सिखाने को कहिए।",
        "S1" to "आपकी सूची में ये दवा {date} से बंद है।",
        "S0" to "लेने से पहले {contact} से पूछ लीजिए।",
        "N1" to "ये आपकी {nickname}, {strength} जैसी लग रही है, पर ये पट्टी पहले कभी नहीं दिखाई गई। डिब्बा है तो अंदर की पट्टी दिखाइए। नई पट्टी है तो {contact} से इसे जुड़वा लीजिए।",
        "D1" to "ये आपकी सूची वाली दवा नहीं है। {contact} से पूछ लीजिए।",
        "V1" to "ध्यान दीजिए: इस पट्टी पर {printed} लिखा है, आपकी वाली {listpart} है। ये आपकी सूची में नहीं है।",
        "U1" to "इसके बारे में पक्का नहीं कह सकते। {contact} से पूछ लीजिए।",
        "K1" to "ये दवा पहचान में नहीं आई। ये यहाँ किसी की सूची में नहीं है।",
        "VS1" to "आपकी {nickname}: {now_part} की खुराक अभी दर्ज नहीं हुई है।",
        "VS2" to "आपकी {nickname}: {now_part} की खुराक {at_time} दर्ज है{ago}।",
        "VS3" to "आपकी {nickname}: {at_time} आपने कहा था कि {this_slot} के बारे में पक्का नहीं पता।",
        "VS4" to "आपकी {nickname}: {now_part} की खुराक दर्ज नहीं हुई है।",
        "VS6" to "आपकी {nickname} का अभी समय नहीं हुआ। अगली खुराक {slot} की है, {at_time}।",
        "VS8" to "आपकी {nickname} आपकी सूची में {date} से बंद है।",
        "V0" to "माफ़ कीजिए, ठीक से समझ नहीं आया। एक बार फिर बोलिए?",
        "V2" to "पट्टी दिखाइए, देख कर बताते हैं।",
        "V4" to "बोलकर पूछना अभी तैयार हो रहा है। बटन काम कर रहे हैं।",
        "V5" to "पट्टी दिखाइए।",
        "V6" to "एक-एक करके पट्टियाँ दिखाइए, हर एक के बारे में बता देंगे।",
        "V7" to "फ़ोन अपनी दवाओं की तरफ़ कीजिए। मैं आपको उस तक ले चलूँगा।",
        "V9" to "उसका अपना बटन \"आज\" वाली स्क्रीन पर है।",
        "WL0" to "आज की सूची की हर खुराक दर्ज है।",
        "WL0b" to "आज के लिए आपकी सूची में कुछ नहीं है।",
        "WL1" to "आज आगे: {list}।",
        "WL2" to "अभी दर्ज नहीं हुईं: {list}।",
        "WL2x" to "दर्ज नहीं हुईं, और समय निकल गया: {list}।",
        "WL3" to "इनके बारे में पक्का नहीं था: {list}।",
        "MQ1" to "ये डॉक्टर या केमिस्ट से पूछने वाली बात है। इसमें अंदाज़ा लगाना ठीक नहीं।",
        "MD1" to "इस पर सलाह नहीं दे सकते। देर से लेने से पहले {contact} से पूछ लीजिए।",
        "CL1" to "{contact} को फ़ोन लगा दूँ?",
        "EM1" to "एक-एक-दो पर फ़ोन करने के लिए हरा बटन दबाइए।",
        "SH1" to "आपको ये अकेले नहीं सहना है। आप अभी किसी से बात कर सकते हैं: टेली-मानस, एक चार चार एक छह। मुफ़्त है, किसी भी समय।",
        "HLP1" to "मदद वाली स्क्रीन खोल दी है। किसी को फ़ोन करने के लिए बटन दबाइए।",
        "HELLO" to "नमस्ते {Call}, बताइए, क्या जानना है?",
        "THX" to "कोई बात नहीं, {Call}।",
        "RP0" to "दोहराने के लिए अभी कुछ नहीं है।",
    )

    /** The second Hindi forms, used when the person is set as a woman. */
    val HI_WOMAN: Map<String, String> = mapOf(
        "Q1" to "अभी ले रही हैं?",
        "SH1" to "आपको ये अकेले नहीं सहना है। आप अभी किसी से बात कर सकती हैं: टेली-मानस, एक चार चार एक छह। मुफ़्त है, किसी भी समय।",
    )
}
