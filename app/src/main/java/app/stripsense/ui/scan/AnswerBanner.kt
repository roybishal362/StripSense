package app.stripsense.ui.scan

import app.stripsense.ui.shell.*
import app.stripsense.ui.scan.*
import app.stripsense.ui.ask.*
import app.stripsense.ui.today.*
import app.stripsense.ui.family.*
import app.stripsense.ui.setup.*
import app.stripsense.ui.dev.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stripsense.core.domain.Verdict
import app.stripsense.ui.theme.AppColors
import app.stripsense.ui.theme.AppIcons
import app.stripsense.ui.theme.AppShapes
import app.stripsense.ui.theme.AppType

/** How the verdict banner looks: its tint, icon, title and caption (spec 06, Answer, item 1). */
data class BannerLook(val bg: Color, val fg: Color, val icon: ImageVector, val title: String, val caption: String?)

object Banners {
    fun due(lang: String, caption: String?) =
        BannerLook(AppColors.dueBg, AppColors.dueText, AppIcons.Clock, Labels.get("dueNowNotMarked", lang), caption)

    fun marked(lang: String, caption: String?, justNow: Boolean = false) = BannerLook(
        AppColors.okBg, AppColors.okText, Icons.Filled.Check,
        Labels.get(if (justNow) "marked" else "alreadyMarked", lang), caption,
    )

    fun named(nickname: String, caption: String?) =
        BannerLook(AppColors.greyBg, AppColors.brand700, Icons.Filled.Check, nickname, caption)

    /** Every verdict except the dose rows of a Match. */
    fun forVerdict(v: Verdict, lang: String, nickname: String?): BannerLook = when (v) {
        is Verdict.Different, is Verdict.WrongMedicine ->
            BannerLook(AppColors.stopBg, AppColors.stopText, AppIcons.Block, Labels.get("notOnList", lang), Labels.get("wontName", lang))
        Verdict.Unreadable, Verdict.NotSure ->
            BannerLook(AppColors.greyBg, AppColors.ink2, Icons.Filled.Refresh, Labels.get("cantRead", lang), Labels.get("turnOver", lang))
        Verdict.Unknown ->
            BannerLook(AppColors.greyBg, AppColors.ink2, Icons.Filled.Info, Labels.get("notRecognised", lang), Labels.get("notOnAnyList", lang))
        is Verdict.NotShown ->
            BannerLook(AppColors.dueBg, AppColors.dueText, Icons.Filled.Warning, Labels.get("notShown", lang), null)
        is Verdict.Stopped ->
            BannerLook(AppColors.stopBg, AppColors.stopText, AppIcons.Block, Labels.get("stoppedTitle", lang), null)
        Verdict.OneAtATime ->
            BannerLook(AppColors.greyBg, AppColors.ink2, Icons.Filled.Info, Labels.get("oneAtATime", lang), null)
        is Verdict.ButtonOnly ->
            BannerLook(AppColors.greyBg, AppColors.brand700, Icons.Filled.Info, nickname.orEmpty(), Labels.get("eyeDropsNote", lang))
        is Verdict.NotTaught ->
            BannerLook(AppColors.greyBg, AppColors.brand700, Icons.Filled.Info, nickname.orEmpty(), Labels.get("notTaughtYet", lang))
        is Verdict.Match -> named(nickname.orEmpty(), null)
        Verdict.Searching -> BannerLook(AppColors.greyBg, AppColors.ink2, Icons.Filled.Info, "", null)
    }
}

@Composable
fun AnswerBanner(look: BannerLook, lang: String) {
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.card).background(look.bg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(look.fg),
        ) {
            Icon(look.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(look.title, color = look.fg, fontSize = if (lang == "hi") 24.sp else 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            if (!look.caption.isNullOrEmpty()) {
                Text(look.caption, style = AppType.forLanguage(AppType.label, lang).copy(color = look.fg.copy(alpha = 0.85f), fontWeight = FontWeight.Medium))
            }
        }
    }
}
