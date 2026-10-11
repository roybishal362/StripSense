package app.stripsense.core.domain

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
import kotlinx.serialization.Serializable

/** Every threshold in one place, so the Demo controls can change them. Spec 01 section 2. */
@Serializable
data class Tuning(
    val minConf: Float = 0.40f,
    val minHeightPx: Int = 14,
    val quickFrames: Int = 2,
    val strengthLines: Int = 2,
    val strengthFrames: Int = 3,
    val slowMs: Long = 1200,
    val timeoutMs: Long = 6000,
    val textLetters: Int = 8,
    val unknownLetters: Int = 20,
    val unknownLettersNoStrength: Int = 40,
    val closerMarginPx: Int = 4,
    val tLook: Float = 0.55f,
    val gemmaTimeoutMs: Long = 8000,
)
