package app.stripsense.core.voice

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/** The voice call's system instruction and JSON shape, copied from spec 04 section 4. */
object VoicePrompt {

    private const val INSTRUCTION = """You turn one short spoken request from an older person in India into JSON. The speech may be English, Hindi or a mix. You never answer the request yourself and you never give medical advice.
Choose exactly one "request":
- "status_of": asks whether a medicine was taken or marked, or whether it is due. Example: "Did I take my fever tablet?", "क्या मैंने बुख़ार की गोली ले ली?"
- "whats_left": asks what is still left or due today. Example: "What's left today?", "aur kya bacha hai?"
- "which_one": asks which strip this is or wants a strip checked. Example: "Which one is this?", "kaunsi hai?"
- "which_is_x": asks which strip is a named medicine, where it is, or asks to find it. Example: "Which one is my BP tablet?", "Where is my fever tablet?", "meri BP ki goli kahan hai?", "find my sugar tablet"
- "due_at": asks which medicines belong to a time of day, or which one comes next. Example: "Which medicine should I take at 8 in the evening?", "raat ko kaunsi dawai leni hai?", "What comes next?"
- "taken_now": says they are taking or have just taken a medicine. Example: "I'm taking it now", "le rahi hoon"
- "yes_no": only a yes, a no, or not sure. Example: "haan", "nahin", "pata nahin"
- "missed_dose": asks what to do about a missed or late dose.
- "extra_dose": says they took an extra tablet or too many.
- "call_family": wants to call or talk to family.
- "repeat": wants the last answer again.
- "medical_question": any question about health, side effects, food, or whether to take something.
- "emergency": chest pain, cannot breathe, a fall, fainting, bleeding, or any urgent health problem.
- "self_harm": wants to hurt or kill themselves, or to take all their tablets to end it.
- "other": a greeting, a thank you, or anything that is none of the above.
The person's medicines are: {medicines}
"medicine": the number of the medicine the speaker names, or 0 if none is clearly named.
"answer": for yes_no only: "yes", "no" or "not_sure". Otherwise "none".
"time": for due_at only: the time asked about as 24-hour "HH:MM" (morning is "08:00", afternoon "14:00", evening or night "20:00"), or "" for the next one. Otherwise "".
"transcript": the words that were said. Write Hindi words in Devanagari.
"emergency": true if the speech mentions any urgent health problem, even inside another request.
Output only the JSON object."""

    const val SCHEMA = """{
  "type": "object",
  "properties": {
    "request": {"type": "string", "enum": ["status_of", "whats_left", "which_one", "which_is_x", "due_at", "taken_now", "yes_no", "missed_dose", "extra_dose", "call_family", "repeat", "medical_question", "emergency", "self_harm", "other"]},
    "time": {"type": "string"},
    "medicine": {"type": "integer", "minimum": 0, "maximum": 20},
    "answer": {"type": "string", "enum": ["yes", "no", "not_sure", "none"]},
    "transcript": {"type": "string"},
    "emergency": {"type": "boolean"}
  },
  "required": ["request", "medicine", "answer", "transcript", "emergency"]
}"""

    const val AUDIO_TEXT = "Reply with the JSON for this spoken request."

    /** "1. fever tablet / बुख़ार की गोली; 2. ..." for every medicine, active and stopped, in list order. */
    fun medicines(meds: List<Medicine>): String =
        meds.mapIndexed { i, m -> Answers.nickname(m).let { "${i + 1}. ${it.en} / ${it.hi.ifEmpty { it.en }}" } }.joinToString("; ")

    fun system(meds: List<Medicine>): String = INSTRUCTION.replace("{medicines}", medicines(meds))
}
