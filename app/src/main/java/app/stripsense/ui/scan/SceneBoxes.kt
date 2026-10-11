package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import app.stripsense.core.domain.*
import app.stripsense.core.text.*
import app.stripsense.core.scan.*
import app.stripsense.core.teach.*
import app.stripsense.core.guide.*
import app.stripsense.core.dose.*
import app.stripsense.core.say.*
import app.stripsense.core.voice.*
import app.stripsense.core.report.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.ui.theme.AppColors
import kotlin.math.roundToInt

/** One followed patch of print in the camera image and what its print adds up to so far. */
data class SceneRegion(val box: CropBox, val lines: List<OcrLine>, val trackId: Long, val result: PrintResult, val expiry: ExpiryDate? = null)

data class SceneIdentity(val med: Medicine, val nameOnly: Boolean)

/** Only a read name earns a schedule colour. A piece, a difference or an unknown strip never does. */
internal fun sceneIdentity(region: SceneRegion): SceneIdentity? = when (val result = region.result) {
    is PrintResult.Checked -> SceneIdentity(result.med, false)
    is PrintResult.NameOnly -> SceneIdentity(result.med, true)
    else -> null
}

/** [boxes]: the strips worth a box, already one per medicine (see [Scene.oneBoxEach]). */
@Composable
fun SceneBoxes(state: AppState, boxes: List<SceneRegion>, viewW: Int, viewH: Int, imageW: Int, imageH: Int,
    onPiece: (Medicine) -> Unit, onName: (Medicine) -> Unit, onSelect: (SceneRegion) -> Unit) {
    val density = LocalDensity.current
    val scale = minOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val dx = (viewW - imageW * scale) / 2
    val dy = (viewH - imageH * scale) / 2
    val lang = state.language
    @Suppress("UNUSED_VARIABLE") val tick = state.minuteTick
    val now = state.now()
    val shape = RoundedCornerShape(14.dp)
    boxes.forEach { region ->
        val result = region.result
        val identity = sceneIdentity(region)
        val timing = identity?.let { MedicationTimings.scene(it.med, state.data.medicines, now, state.data.events, state.engine) }
        val due = timing?.kind == TimingKind.NOW || timing?.kind == TimingKind.NEXT
        val color = when {
            result is PrintResult.Differs -> Color(0xFFC62828)
            identity != null && due -> Color(0xFF16833B)
            identity != null -> timingColor(timing!!)
            else -> AppColors.dueText
        }
        // Two short lines: whose strip it is, and its state. The long sentence is spoken and shown below the picture.
        val (title, status) = when (result) {
            is PrintResult.Differs -> Labels.get("boxDiffers", lang) to Sentences.sceneConflict(Verdict.Different(result.med, result.printed, result.saved), lang)
            is PrintResult.Checked -> nick(result.med, lang) to Sentences.chipStatus(timing!!, false, lang)
            is PrintResult.NameOnly -> nick(result.med, lang) to Sentences.chipStatus(timing!!, true, lang)
            is PrintResult.Piece -> Labels.fill("looksLike", lang, "n" to nick(result.med, lang)) to ""
            else -> return@forEach
        }
        // The expiry date the phone read off the strip, shown with it; in the warning colour once it has passed.
        val today = state.engine.local(now).toLocalDate()
        val expiry = region.expiry?.takeIf { identity != null }
        val label = (if (status.isEmpty()) title else "$title. $status") + expiry?.let { ". " + Sentences.expiryChip(it, it.passed(today), lang) }.orEmpty()
        // The read print is smaller than the strip it is on. The box is drawn a little larger, so it frames the strip.
        val padX = region.box.width * PAD
        val padY = region.box.height * PAD
        val x = ((region.box.left - padX) * scale + dx).roundToInt().coerceAtLeast(0)
        val y = ((region.box.top - padY) * scale + dy).roundToInt().coerceAtLeast(0)
        val w = with(density) { ((region.box.width + 2 * padX) * scale).toDp() }.coerceAtLeast(96.dp)
        val h = with(density) { ((region.box.height + 2 * padY) * scale).toDp() }.coerceAtLeast(64.dp)
        Box(Modifier.offset { IntOffset(x, y) }.width(w).height(h).border(if (due) 5.dp else 3.dp, color, shape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClickLabel = Labels.get("openScanResult", lang)) {
                when (result) {
                    is PrintResult.Checked -> onSelect(region)
                    is PrintResult.NameOnly -> onName(result.med)
                    is PrintResult.Piece -> onPiece(result.med)
                    else -> Unit
                }
            })
        // The label sits on the box's top edge, inside it, so it never runs off the picture and never hides another strip.
        // Never wider than its own box: two strips side by side had their labels written over each other.
        Column(Modifier.offset { IntOffset(x, y) }.widthIn(max = w)
            .clip(RoundedCornerShape(topStart = 14.dp, bottomEnd = 10.dp)).background(color).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (status.isNotEmpty()) Text(status, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (expiry != null) Text(Sentences.expiryChip(expiry, expiry.passed(today), lang), color = if (expiry.passed(today)) Color(0xFFFFD54F) else Color.White,
                fontWeight = if (expiry.passed(today)) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/**
 * The frame the strip is brought into, and the way to move the phone. The frame is the middle of the camera
 * picture, the same middle the spoken directions lead to. It turns green when the medicine is in it.
 * [steer]: which way to move the phone, when a medicine is known but off to one side or just out of view.
 */
@Composable
fun SceneFrame(steer: Toward?, centred: Boolean, viewW: Int, viewH: Int, imageW: Int, imageH: Int, lang: String, showHint: Boolean) {
    val density = LocalDensity.current
    val scale = minOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val left = (viewW - imageW * scale) / 2
    val top = (viewH - imageH * scale) / 2
    val frameW = imageW * scale * FRAME_W
    val frameH = imageH * scale * FRAME_H
    val x = (left + (imageW * scale - frameW) / 2).roundToInt()
    val y = (top + (imageH * scale - frameH) / 2).roundToInt()
    val color = if (centred) Color(0xFF16833B) else Color.White.copy(alpha = 0.85f)
    val arm = 34.dp
    val thick = if (centred) 6.dp else 4.dp
    val armPx = with(density) { arm.toPx() }.roundToInt()
    val thickPx = with(density) { thick.toPx() }.roundToInt()
    // Four corners only: the middle stays clear so the strip and its own box are not drawn over.
    for ((cx, cy) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
        val px = x + if (cx == 0) 0 else (frameW.roundToInt() - armPx)
        val py = y + if (cy == 0) 0 else (frameH.roundToInt() - thickPx)
        Box(Modifier.offset { IntOffset(px, py) }.width(arm).height(thick).background(color))
        val vx = x + if (cx == 0) 0 else (frameW.roundToInt() - thickPx)
        val vy = y + if (cy == 0) 0 else (frameH.roundToInt() - armPx)
        Box(Modifier.offset { IntOffset(vx, vy) }.width(thick).height(arm).background(color))
    }
    if (showHint && steer == null && !centred) Text(Labels.get("frameHint", lang), color = Color.White, fontSize = 15.sp,
        modifier = Modifier.offset { IntOffset(x, (y + frameH).roundToInt() + thickPx * 2) }.width(with(density) { frameW.toDp() })
            .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp))
    if (steer == null) return
    // One large arrow at the edge the phone should move toward, the same direction the voice is saying.
    val size = 84.dp
    val sizePx = with(density) { size.toPx() }
    val imageRight = left + imageW * scale
    val imageBottom = top + imageH * scale
    val (ax, ay, turn) = when (steer) {
        Toward.RIGHT -> Triple(imageRight - sizePx - 12, top + (imageH * scale - sizePx) / 2, 0f)
        Toward.LEFT -> Triple(left + 12, top + (imageH * scale - sizePx) / 2, 180f)
        Toward.AWAY -> Triple(left + (imageW * scale - sizePx) / 2, top + 12, 270f)
        Toward.CLOSER -> Triple(left + (imageW * scale - sizePx) / 2, imageBottom - sizePx - 12, 90f)
    }
    Box(Modifier.offset { IntOffset(ax.roundToInt(), ay.roundToInt()) }.size(size).clip(CircleShape).background(Color(0xFFF9A825)), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(56.dp).rotate(turn))
    }
}

/** The fingertip, and the remembered place of any strip the hand is covering, so a watcher sees what the phone knows. */
@Composable
fun SceneHandMarks(state: AppState, placed: List<PlacedStrip>, regions: List<SceneRegion>, finger: app.stripsense.ml.Fingertip?,
    touched: String?, viewW: Int, viewH: Int, imageW: Int, imageH: Int) {
    if (finger == null && touched == null) return
    val density = LocalDensity.current
    val scale = minOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val dx = (viewW - imageW * scale) / 2
    val dy = (viewH - imageH * scale) / 2
    val shown = regions.mapNotNullTo(HashSet()) { sceneIdentity(it)?.med?.id }
    // Only the one strip the hand is on, and only while its own box is not drawn because the hand hides its print.
    // Every remembered strip used to be outlined: a second and third box beside the real ones.
    placed.filter { it.med != null && it.med.id !in shown && it.key == touched && it.kind != PlacedKind.UNKNOWN }.forEach { strip ->
        val x = (strip.box.left * scale + dx).roundToInt().coerceAtLeast(0)
        val y = (strip.box.top * scale + dy).roundToInt().coerceAtLeast(0)
        val w = with(density) { (strip.box.width * scale).toDp() }.coerceAtLeast(96.dp)
        val h = with(density) { (strip.box.height * scale).toDp() }.coerceAtLeast(64.dp)
        // The strip the hand is on gets the thick outline, so a watcher sees which one the phone means.
        Box(Modifier.offset { IntOffset(x, y) }.width(w).height(h)
            .border(if (strip.key == touched) 5.dp else 2.dp, if (strip.key == touched) AppColors.brand500 else Color.White.copy(alpha = 0.75f), RoundedCornerShape(14.dp)))
        if (strip.med!!.id !in shown) Text(nick(strip.med, state.language), color = Color.White,
            modifier = Modifier.offset { IntOffset(x, y) }.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp))
    }
    if (finger == null) return
    val size = with(density) { 22.dp.toPx() }
    val fx = (finger.x * imageW * scale + dx - size / 2).roundToInt()
    val fy = (finger.y * imageH * scale + dy - size / 2).roundToInt()
    Box(Modifier.offset { IntOffset(fx, fy) }.size(22.dp).background(AppColors.brand500, CircleShape).border(2.dp, Color.White, CircleShape))
}

internal fun timingColor(timing: MedicationTiming): Color = when (timing.kind) {
    TimingKind.NOW, TimingKind.NEXT -> Color(0xFF16833B)
    TimingKind.LATER -> Color(0xFF1565C0)
    TimingKind.UNCONFIRMED -> Color(0xFFC62828)
    TimingKind.NOT_SURE -> AppColors.dueText
    TimingKind.STOPPED -> AppColors.stopText
    else -> Color(0xFF455A64)
}

/** How much larger than the read print the strip's box is drawn, on each side. */
private const val PAD = 0.10f

/** The frame, as a part of the camera picture. Its middle is the picture's middle. */
private const val FRAME_W = 0.72f
private const val FRAME_H = 0.42f
