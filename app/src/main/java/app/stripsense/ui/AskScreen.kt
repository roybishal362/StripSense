package app.stripsense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Bubble
import app.stripsense.core.Part
import app.stripsense.ml.GemmaState
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType
import kotlinx.coroutines.delay

@Composable
fun AskScreen(state: AppState) {
    val lang = state.language
    val gs by state.gemma.state.collectAsState()
    val busy by state.gemma.busy.collectAsState()

    val controller = remember(state) { AskController(state) }
    var typed by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.bubbles.size) {
        if (state.bubbles.isNotEmpty()) listState.animateScrollToItem(state.bubbles.size - 1)
    }

    LaunchedEffect(state.listenOnOpen) {
        if (state.listenOnOpen) {
            state.listenOnOpen = false
            controller.startListening()
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val (chipText, chipFg, chipBg) = when {
                busy -> Triple(Labels.get("voiceLoading", lang), AppColors.dueText, AppColors.dueBg)
                gs is GemmaState.Ready -> Triple(Labels.get("voiceReady", lang), AppColors.okText, AppColors.okBg)
                gs is GemmaState.Loading -> Triple(Labels.get("voiceLoading", lang), AppColors.dueText, AppColors.dueBg)
                else -> Triple(Labels.get("voiceNoFile", lang), AppColors.stopText, AppColors.stopBg)
            }
            Text(chipText, style = AppType.caption.copy(color = chipFg, fontWeight = FontWeight.Bold), modifier = Modifier.clip(AppShapes.chip).background(chipBg).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            if (state.bubbles.isEmpty() && !controller.listening) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(Labels.get("askEmpty", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.surface.copy(alpha = 0.8f)))
                    }
                }
            }
            items(state.bubbles) { b ->
                if (b.mine) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(Labels.get("youAsked", lang), style = AppType.caption.copy(color = AppColors.surface.copy(alpha = 0.6f)), modifier = Modifier.padding(bottom = 4.dp, end = 4.dp))
                        Box(Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)).background(AppColors.brand100).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(b.text.joinToString(" ") { it.shown }, style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)).background(AppColors.surface).padding(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (b.photo != null) {
                                    val f = java.io.File(state.store.looksDir, b.photo)
                                    val bmp = rememberThumb(f)
                                    if (bmp != null) {
                                        androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(120.dp).clip(AppShapes.card))
                                    }
                                }
                                if (b.text.isNotEmpty() && b.text.first().shown == "...") {
                                    Text(Labels.get("thinking", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.ink3))
                                } else {
                                    b.text.forEach { Text(it.shown, style = AppType.forLanguage(AppType.body, lang)) }
                                }
                                if (b.ms != null) {
                                    Text("${b.ms} ms", style = AppType.caption.copy(color = AppColors.ink3))
                                }
                            }
                        }
                        if (b.parts.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { state.say(b.parts) }, contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Speaker, contentDescription = Labels.get("again", lang), tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (controller.listening) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(Labels.get("listening", lang), style = AppType.forLanguage(AppType.body, lang).copy(color = AppColors.brand100, fontWeight = FontWeight.Bold))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun askBtn(text: String, question: String) {
                Box(
                    Modifier.height(44.dp).clip(AppShapes.chip).background(Color.White.copy(alpha = 0.15f)).clickable { controller.askTyped(question) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(text, style = AppType.forLanguage(AppType.label, lang).copy(color = Color.White)) }
            }
            askBtn(Labels.get("askLeft", lang), "What's left today?")
            state.data.medicines.forEach {
                askBtn(Labels.fill("askDidTake", lang, "n" to nick(it, lang)), "Did I take my ${it.nickEn}?")
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val micBg = if (controller.listening) AppColors.sos else AppColors.brand500
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(micBg)
                    // We don't have a robust pointerInteropFilter for press/release without Compose UI 1.6+ gestures,
                    // so we use a simple click to toggle recording for this environment.
                    .clickable { if (controller.listening) controller.stopListening() else controller.startListening() },
            ) {
                Icon(AppIcons.Microphone, contentDescription = Labels.get("holdToTalk", lang), tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                placeholder = { Text(Labels.get("askTyped", lang), color = Color.White.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (typed.isNotBlank()) { controller.askTyped(typed); typed = "" } }),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = AppColors.brand100,
                ),
                modifier = Modifier.weight(1f).height(56.dp).clip(AppShapes.chip).background(Color.White.copy(alpha = 0.1f)),
            )
            if (typed.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(AppColors.surface).clickable { controller.askTyped(typed); typed = "" },
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = Labels.get("send", lang), tint = AppColors.brand700) }
            }
        }
    }
}
