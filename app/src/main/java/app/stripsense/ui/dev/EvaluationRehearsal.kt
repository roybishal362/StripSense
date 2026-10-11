package app.stripsense.ui.dev

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.stripsense.AppState
import app.stripsense.Screen

/** Explicit rehearsal only: never reads or writes a medicine, schedule or dose event. */
@Composable
fun EvaluationRehearsal(state: AppState) {
    var marked by remember { mutableStateOf(false) }
    val lang = state.language
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(Labels.get("evaluationNotice", lang), style = MaterialTheme.typography.titleMedium)
        ExampleStrip(Labels.get(if (marked) "evaluationMarked" else "evaluationNow", lang), if (marked) Color(0xFF455A64) else Color(0xFF16833B))
        ExampleStrip(Labels.get(if (marked) "evaluationNext" else "evaluationLater", lang), if (marked) Color(0xFF16833B) else Color(0xFF1565C0))
        ExampleStrip(Labels.get("evaluationPast", lang), Color(0xFFC62828))
        Button(onClick = { marked = true }, enabled = !marked, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(Labels.get("evaluationConfirm", lang)) }
        OutlinedButton(onClick = { marked = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("evaluationReset", lang)) }
        OutlinedButton(onClick = { state.go(Screen.TODAY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(Labels.get("today", lang)) }
    }
}

@Composable
private fun ExampleStrip(label: String, color: Color) {
    Column(Modifier.fillMaxWidth().border(3.dp, color).padding(10.dp)) {
        Text(label, color = Color.White, modifier = Modifier.fillMaxWidth().background(color).padding(10.dp))
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            drawRoundRect(Color(0xFFE0E3E5), cornerRadius = CornerRadius(12f, 12f))
            repeat(8) { i ->
                val w = size.width / 10
                drawOval(Color(0xFFB5BDC2), Offset(w * (i + 1) - w / 3, size.height / 4), Size(w * .7f, size.height / 2))
            }
        }
    }
}
