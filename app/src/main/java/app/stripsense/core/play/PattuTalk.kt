package app.stripsense.core.play

/**
 * Pattu's free chit chat, answered by the model on the phone from a small book of knowledge: who Pattu is, what it
 * has given this person and what they said about it, the other things it may suggest, and how the journey stands.
 *
 * This is the one place where a model's own words are shown, and it is small talk only. Two things keep it there.
 * A message about medicine or health is never sent to the model: Pattu says where to ask instead. And a reply is
 * shown only after [clean] has passed it: short, and with nothing about medicine in it. When the model is busy or
 * its reply does not pass, a fixed sentence is used. Every answer about a medicine still comes from fixed sentences.
 */
object PattuTalk {
    private val HEALTH_ASKED = listOf("medicine", "medication", "tablet", "pill", "capsule", "dose", "dosage", "drug", "syrup", "injection", "pain", "fever",
        "sick", "illness", "disease", "symptom", "treatment", "cure", "diabet", "blood pressure", "prescription", "side effect", "overdose", "headache", "infection",
        "दवा", "गोली", "खुराक", "बुख़ार", "बुखार", "दर्द", "बीमार", "इलाज", "शुगर", "बीपी", "नुस्ख", "सिरदर्द")

    private val HEALTH_SAID = listOf("medicine", "medication", "tablet", "pill", "capsule", " dose", "dosage", " mg", "symptom", "treatment", "diagnos", "cure",
        "prescri", "side effect", "should take", "दवा", "गोली", "खुराक", "इलाज", "नुस्ख")

    /** The person is asking about medicine or health. Pattu does not answer that, and the model is not asked. */
    fun aboutHealth(message: String): Boolean = message.lowercase().let { text -> HEALTH_ASKED.any { it in text } }

    /** What is sent to the model as the person's message. */
    fun message(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(240)

    /**
     * The book of knowledge. [kept]: the gifts given. [talked]: what the person said about them. [streak], [level]:
     * how the journey stands. [lang]: the language to answer in.
     */
    fun knowledge(kept: List<String>, talked: List<String>, streak: Int, level: String, lang: String): String {
        val answered = Chats.answers(talked)
        val given = Gifts.given(kept)
        val had = given.map { it.gift.id }.toSet()
        fun line(gift: Gift) = "- \"${gift.title}\" (${gift.kind.en.lowercase()}${if (gift.by.isNotEmpty()) ", " + gift.by else ""}): ${gift.whyEn}"
        return buildString {
            append("You are Pattu, a small cheerful sprout character who lives in a phone app. You are a friend, not an expert.\n")
            append("You chat about films, books, anime, K-dramas and how the person's day is going. Nothing else.\n")
            append("Answer in at most two short sentences. Be warm and a little playful. No lists, no emoji, no questions about health.\n")
            append(if (lang == "hi") "Answer in simple Hindi, in Devanagari script.\n" else "Answer in simple English.\n")
            append("Never talk about medicines, doses, illness, symptoms or treatment, and never give advice about health. ")
            append("If asked, say you only chat about films, books and their day.\n")
            append("Only recommend titles from the lists below. Do not invent titles or facts about them.\n\n")
            append("How this person is doing: $streak full days in a row. Their level is \"$level\".\n\n")
            if (given.isEmpty()) append("You have not given this person anything yet.\n")
            else {
                append("What you have already given this person, oldest first, and what they said:\n")
                for (g in given) append(line(g.gift)).append(when (answered[g.gift.id]) {
                    Liked.LOVED -> " They loved it."
                    Liked.OKAY -> " They said it was okay."
                    Liked.NOT_YET -> " They have not got to it yet."
                    null -> " You have not asked them about it yet."
                }).append('\n')
            }
            append("\nOther things you know and may suggest:\n")
            for (gift in Gifts.ALL) if (gift.kind != GiftKind.THOUGHT && gift.id !in had) append(line(gift)).append('\n')
        }
    }

    /**
     * What may be shown of the model's reply: its first two sentences, at most 220 characters, with marks of
     * formatting taken out. Null when there is nothing to show or it speaks of medicine: a fixed sentence is used then.
     */
    fun clean(reply: String): String? {
        val flat = reply.replace(Regex("[*_#`>]"), "").replace(Regex("\\s+"), " ").trim().trim('"', '“', '”')
        if (flat.length < 2) return null
        val sentences = Regex("[^.!?।]+[.!?।]+").findAll(flat).map { it.value.trim() }.toList()
        val short = (if (sentences.isEmpty()) flat else sentences.take(2).joinToString(" ")).take(220).trim()
        val low = " " + short.lowercase()
        if (HEALTH_SAID.any { it in low }) return null
        return short
    }
}
