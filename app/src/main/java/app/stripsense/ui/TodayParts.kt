package app.stripsense.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.AppState
import app.stripsense.core.DoseEvent
import app.stripsense.core.Medicine
import app.stripsense.core.Part
import app.stripsense.core.Rendered
import app.stripsense.core.confirmMark
import app.stripsense.core.markButton
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

/** What was just said about a tapped row, shown at the bottom so it can also be read. */
@Composable
fun SaidCard(lines: List<Rendered>, lang: String, modifier: Modifier = Modifier, onClose: () -> Unit) {
    LaunchedEffect(lines) {
        delay(15_000)
        onClose()
    }
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).shadow(10.dp, AppShapes.card)
            .clip(AppShapes.card).background(AppColors.surface).padding(start = 18.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { Text(it.shown, style = AppType.forLanguage(AppType.body, lang)) }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Close, contentDescription = Labels.get("close", lang), tint = AppColors.ink3)
        }
    }
}

/** The Mark button of a BUTTON medicine (spec 02 section 7): MB1 with Yes and No, then R1 with Undo for 60 seconds. */
@Composable
fun MarkDialog(state: AppState, med: Medicine, onClose: () -> Unit) {
    val lang = state.language
    val first = remember(med.id) { state.engine.markButton(med, state.now(), state.store.data.value.events) }
    var parts by remember(med.id) { mutableStateOf(first.parts) }
    var asking by remember(med.id) { mutableStateOf(first.ask) }
    var taken by remember(med.id) { mutableStateOf<DoseEvent?>(null) }
    var undoOpen by remember(med.id) { mutableStateOf(false) }

    LaunchedEffect(med.id) { state.say(first.parts) }
    LaunchedEffect(taken) {
        if (taken != null) {
            delay(60_000)
            undoOpen = false
        }
    }

    fun show(p: List<Part>) {
        parts = p
        state.say(p)
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppColors.surface,
        shape = AppShapes.card,
        title = { Text(nick(med, lang), fontWeight = FontWeight.Bold, fontSize = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.render(parts).forEach { Text(it.shown, style = AppType.forLanguage(AppType.answer, lang)) }
            }
        },
        confirmButton = {
            when {
                asking -> TextButton(onClick = {
                    val r = state.engine.confirmMark(med, first.slotKey, state.now(), state.store.data.value.events, state.newId())
                    r.event?.let {
                        state.store.addEvent(it)
                        taken = it
                        undoOpen = true
                    }
                    asking = false
                    show(r.parts)
                }, modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 56.dp)) { Text(Labels.get("yes", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.brand700) }
                else -> TextButton(onClick = onClose, modifier = Modifier.widthIn(min = 140.dp).heightIn(min = 56.dp)) {
                    Text(Labels.get(if (taken != null) "done" else "close", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.brand700)
                }
            }
        },
        dismissButton = {
            when {
                asking -> TextButton(onClick = {
                    asking = false
                    show(listOf(Part("NO1")))
                }, modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 56.dp)) { Text(Labels.get("no", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.ink2) }
                undoOpen -> TextButton(onClick = {
                    val t = taken ?: return@TextButton
                    val r = state.engine.undo(t, state.now(), state.store.data.value.events, state.newId())
                    r.event?.let { state.store.addEvent(it) }
                    undoOpen = false
                    show(r.parts)
                }, modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 56.dp)) { Text(Labels.get("undo", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.stopText) }
            }
        },
    )
}
