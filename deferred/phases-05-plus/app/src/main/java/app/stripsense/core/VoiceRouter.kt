package app.stripsense.core

/** What the Ask screen does with one question (spec 04 section 6). */
sealed interface VoiceAction {
    /** Show and speak these parts. [callButton] adds a Call button for the contact. */
    data class Say(val parts: List<Part>, val callButton: Boolean = false) : VoiceAction

    /** Say V5, then open the Scan screen when it has been spoken (or after 3.5 seconds). */
    data object OpenScan : VoiceAction

    /** Show the taught photo of this medicine and say V6. */
    data class ShowPhoto(val med: Medicine) : VoiceAction

    /** Open the Help card and say HLP1, or SH1 with the 14416 button first. */
    data class Help(val selfHarm: Boolean) : VoiceAction

    /** Say the last answer again (RP0 when there is none). */
    data object Repeat : VoiceAction
}

/** The table of spec 04 section 6 as one pure function. Gemma's output only picks a row. */
object VoiceRouter {

    fun route(
        req: VoiceRequest?,
        meds: List<Medicine>,
        now: Long,
        events: List<DoseEvent>,
        engine: DoseEngine,
        hasContactPhone: Boolean,
    ): VoiceAction {
        if (req == null) return VoiceAction.Say(listOf(Part("V0")))
        // Section 5: the keyword check and the emergency flag win over any request.
        when (Keywords.check(req.transcript)) {
            Keywords.Hit.SELF_HARM -> return VoiceAction.Help(selfHarm = true)
            Keywords.Hit.EMERGENCY -> return VoiceAction.Help(selfHarm = false)
            Keywords.Hit.NONE -> Unit
        }
        if (req.request == "self_harm") return VoiceAction.Help(selfHarm = true)
        if (req.emergency || req.request == "emergency") return VoiceAction.Help(selfHarm = false)

        val med = meds.getOrNull(req.medicine - 1)
        val say = { parts: List<Part> -> VoiceAction.Say(parts) }
        return when (req.request) {
            "status_of" -> if (med != null) say(engine.statusAnswer(med, now, events)) else say(engine.whatsLeft(meds, now, events))
            "whats_left" -> say(engine.whatsLeft(meds, now, events))
            "which_one" -> VoiceAction.OpenScan
            "which_is_x" -> when {
                med == null -> say(listOf(Part("V0")))
                med.type == MedType.BUTTON -> say(listOf(Part("V9")))
                else -> VoiceAction.ShowPhoto(med)
            }
            // Nothing is ever marked from the Ask screen: no question is open there.
            "taken_now", "yes_no" -> if (med != null) say(engine.statusAnswer(med, now, events)) else say(listOf(Part("V0")))
            "missed_dose" -> say(listOf(Part("MD1")))
            // CL1 offers a call, so it is only said when there is a number to call.
            "extra_dose" -> if (hasContactPhone) VoiceAction.Say(listOf(Part("MD1"), Part("CL1")), callButton = true)
            else say(listOf(Part("MD1")))
            "call_family" -> if (hasContactPhone) VoiceAction.Say(listOf(Part("CL1")), callButton = true) else VoiceAction.Help(selfHarm = false)
            "repeat" -> VoiceAction.Repeat
            "medical_question" -> say(listOf(Part("MQ1")))
            else -> when {
                Keywords.isGreeting(req.transcript) -> say(listOf(Part("HELLO")))
                Keywords.isThanks(req.transcript) -> say(listOf(Part("THX")))
                else -> say(listOf(Part("V0")))
            }
        }
    }

    /**
     * Step 1 for typed text, before any model call: the keyword check, then the greeting and thank-you lists.
     * Null means Gemma is needed.
     */
    fun beforeModel(text: String): VoiceAction? = when (Keywords.check(text)) {
        Keywords.Hit.SELF_HARM -> VoiceAction.Help(selfHarm = true)
        Keywords.Hit.EMERGENCY -> VoiceAction.Help(selfHarm = false)
        Keywords.Hit.NONE -> when {
            Keywords.isGreeting(text) -> VoiceAction.Say(listOf(Part("HELLO")))
            Keywords.isThanks(text) -> VoiceAction.Say(listOf(Part("THX")))
            else -> null
        }
    }

    /** A reply that could not be parsed: still check its raw text for emergency words before V0. */
    fun unparsed(raw: String): VoiceAction = when (Keywords.check(raw)) {
        Keywords.Hit.SELF_HARM -> VoiceAction.Help(selfHarm = true)
        Keywords.Hit.EMERGENCY -> VoiceAction.Help(selfHarm = false)
        Keywords.Hit.NONE -> VoiceAction.Say(listOf(Part("V0")))
    }
}
