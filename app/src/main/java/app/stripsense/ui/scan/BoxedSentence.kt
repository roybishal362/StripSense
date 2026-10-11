package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A sentence with one part drawn inside a rounded box that stays on one line (spec 07 section 7). */
@Composable
fun BoxedSentence(sentence: String, boxed: String, textColor: Color, boxText: Color, boxFill: Color, fontSize: TextUnit = 20.sp) {
    val joined = boxed.replace(' ', Char(0x00A0)) // a no-break space, so the box stays on one line
    val shown = sentence.replace(boxed, joined)
    val start = shown.indexOf(joined)
    var layout by remember(sentence, boxed, fontSize) { mutableStateOf<TextLayoutResult?>(null) }
    val text = buildAnnotatedString {
        if (start < 0) {
            append(shown)
            return@buildAnnotatedString
        }
        append(shown.substring(0, start))
        withStyle(SpanStyle(color = boxText, fontWeight = FontWeight.Bold)) { append(joined) }
        append(shown.substring(start + joined.length))
    }
    Text(
        text = text,
        color = textColor,
        fontSize = fontSize,
        lineHeight = fontSize * 1.5f,
        onTextLayout = { layout = it },
        modifier = Modifier.padding(horizontal = 4.dp).drawBehind {
            val l = layout ?: return@drawBehind
            if (start < 0 || joined.isEmpty()) return@drawBehind
            val first = l.getBoundingBox(start)
            val last = l.getBoundingBox(start + joined.length - 1)
            val pad = 4.dp.toPx()
            val topLeft = Offset(first.left - pad, first.top + 2.dp.toPx())
            val size = Size(last.right - first.left + 2 * pad, first.height - 4.dp.toPx())
            val corner = CornerRadius(8.dp.toPx())
            drawRoundRect(boxFill, topLeft, size, corner)
            drawRoundRect(boxText, topLeft, size, corner, style = Stroke(width = 1.5.dp.toPx()))
        },
    )
}
