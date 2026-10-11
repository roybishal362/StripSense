package app.stripsense.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.core.domain.Medicine
import app.stripsense.ui.scan.medColour
import app.stripsense.ui.theme.AppColors

/*
 * The pieces the family's side is drawn from, in the style of the prototype's Family page: a soft grey bar of
 * tabs with the chosen one white, white cards with a quiet shadow, a small tile for each medicine, and rows of
 * a grey label beside a dark value. One set of pieces, so every page of the section looks like the same app.
 */

private val Hairline = Color(0x140D1714)
private val CardShape = RoundedCornerShape(24.dp)

/** The bar of tabs: one grey pill, the chosen tab a white pill inside it. Slides sideways when it is wider than the screen. */
@Composable
fun <T> Segmented(tabs: List<Pair<T, String>>, picked: T, onPick: (T) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.greyBg).horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { (tab, label) ->
            val on = tab == picked
            Box(Modifier.heightIn(min = 44.dp).then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(14.dp)) else Modifier).clip(RoundedCornerShape(14.dp))
                .background(if (on) AppColors.surface else Color.Transparent).clickable(role = Role.Tab) { onPick(tab) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 15.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) AppColors.ink else AppColors.ink2)
            }
        }
    }
}

/** A small heading over a group, in capitals, as "MORNING" stands over the morning's doses on Today. */
@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = AppColors.ink3, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
fun FamilyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().shadow(5.dp, CardShape, ambientColor = Color(0x18000000), spotColor = Color(0x18000000)).clip(CardShape).background(AppColors.surface)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** The medicine's own tile: a soft square in its colour with a dot of that colour. */
@Composable
fun MedTile(med: Medicine, size: Int = 52) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(lerp(Color.White, medColour(med), 0.15f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size((size * 0.44f).dp).clip(CircleShape).background(medColour(med)))
    }
}

/** "Brand   FEVORA 500": a grey label and its value, the labels of a card in one column. */
@Composable
fun KeyValue(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = AppColors.ink3, fontWeight = FontWeight.Medium, modifier = Modifier.width(96.dp))
        Text(value, fontSize = 15.5.sp, color = AppColors.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** One choice in a list of choices: what it is, one line about it, and an arrow. */
@Composable
fun ChoiceRow(title: String, sub: String, icon: ImageVector? = null, first: Boolean = false, onClick: () -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.brand100), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.brand700, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
            if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, color = AppColors.ink3, lineHeight = 19.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.ink3)
    }
}

/** A quiet button with an outline, for the smaller things on a page. */
@Composable
fun QuietButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0x260D1714), RoundedCornerShape(14.dp)).background(AppColors.surface)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.ink)
    }
}
