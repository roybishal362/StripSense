package app.stripsense.core.say

import app.stripsense.core.dose.SupplyState

/**
 * What is said about how much of a medicine is left and how far along its course is. Fixed sentences, like every
 * other answer. They tell the family to buy more and to ask the doctor; they never say to take more or less.
 */
object SupplyText {
    /** The one line for this medicine, or null when the family has counted nothing and set no course. */
    fun line(s: SupplyState, lang: String): String? {
        val hi = lang == "hi"
        val left = s.left
        val days = s.daysLeft
        return when {
            !s.known -> null
            s.courseOver -> if (hi) "डॉक्टर के बताए ${s.courseDays} दिन पूरे हो गए हैं। आगे लेने से पहले डॉक्टर से पूछिए।"
                else "The ${s.courseDays} days the doctor said are over. Ask the doctor before you go on."
            s.empty -> if (hi) "गिनती के हिसाब से यह दवा ख़त्म हो गई है। परिवार को बताइए।" else "By the count, this medicine is finished. Tell your family."
            left != null && days != null && s.low -> when {
                days == 0 -> if (hi) "दवा सिर्फ़ आज के लिए बची है: $left गोलियाँ। परिवार से कहिए कि और ले आएँ।" else "Tablets for today only: $left left. Tell your family to buy more."
                else -> if (hi) "सिर्फ़ $days दिन की दवा बची है: $left गोलियाँ। परिवार से कहिए कि और ले आएँ।"
                    else "Only ${dayWord(days)} left: $left tablets. Tell your family to buy more."
            }
            left != null && days != null && s.shortForCourse -> if (hi) "$days दिन की गोलियाँ हैं, पर कोर्स के ${s.courseLeft} दिन बाकी हैं। और ले आइए।"
                else "Tablets for ${dayWord(days)}, but ${dayWord(s.courseLeft ?: 0)} of the course are left. Buy more."
            left != null && days != null -> (if (hi) "$left गोलियाँ बची हैं, $days दिन के लिए।" else "$left tablets left, enough for ${dayWord(days)}.") + course(s, hi)?.let { " $it" }.orEmpty()
            else -> course(s, hi)
        }
    }

    /** Short enough for a chip beside the medicine's name. */
    fun chip(s: SupplyState, lang: String): String? {
        val hi = lang == "hi"
        val days = s.daysLeft
        return when {
            s.courseOver -> if (hi) "कोर्स पूरा" else "Course over"
            s.empty -> if (hi) "ख़त्म" else "Finished"
            days != null && s.needsALook -> if (hi) "$days दिन की बची" else "${dayWord(days)} left"
            else -> null
        }
    }

    private fun course(s: SupplyState, hi: Boolean): String? =
        if (s.courseDay == null || s.courseDays == null) null else if (hi) "दिन ${s.courseDay}, कुल ${s.courseDays}।" else "Day ${s.courseDay} of ${s.courseDays}."

    private fun dayWord(n: Int) = if (n == 1) "1 day" else "$n days"
}
