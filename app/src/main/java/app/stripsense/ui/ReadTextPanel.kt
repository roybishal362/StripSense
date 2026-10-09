package app.stripsense.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.core.OcrLine
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppShapes
import java.util.Locale

/** The debug list of what the reader saw in the latest frame, tallest line first. */
@Composable
fun ReadTextPanel(lines: List<OcrLine>, fps: Int, width: Int, height: Int, language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sorted = lines.sortedByDescending { it.heightPx }
    val rows = sorted.map { row(it) }
    val summary = "lines ${lines.size} · frames per second $fps · image ${width}x$height"
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xE60D1714), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(summary, color = AppColors.brand100, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    copy(context, (rows + summary).joinToString("\n"))
                    copied = true
                },
                shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand500),
                modifier = Modifier.height(56.dp),
            ) {
                Text(Labels.get(if (copied) "copied" else "copy", language), fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows) { line ->
                Text(line, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun row(line: OcrLine): String =
    "${line.text} · ${line.heightPx} · ${String.format(Locale.US, "%.2f", line.confidence)}"

// Office Kit shares the phone's clipboard with the laptop, so this is how read lines reach the laptop.
private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("StripSense", text))
}
