package app.stripsense.core.teach

import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*
/**
 * Instructions, price and batch information are not the medicine's composition.
 * Price and stock-code lines matter most: on a real strip "MRP:26.25" was read as "2G.25", which parses as
 * 2 g, and "SKU:Parawel-32 5m" gave the name two false numbers.
 */
object LabelEvidence {
    private val instructions = Regex("(?i)\\b(dosage|doses?|maximum|daily|every|expiry|exp\\.?\\s*date|mfg\\.?|manufactured|licen[cs]e|batch|store|storage|protect|keep\\s*out|temperature|m\\.?\\s?r\\.?\\s?p|sku|price|rs|incl|taxes)\\b|mg\\s*/\\s*kg")
    fun identityLine(text: String): Boolean = !instructions.containsMatchIn(text)
    fun lines(lines: List<OcrLine>): List<OcrLine> = lines.filter { identityLine(it.text) }
}
