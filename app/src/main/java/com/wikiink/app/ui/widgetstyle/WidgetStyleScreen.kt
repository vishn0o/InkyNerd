package com.wikiink.app.ui.widgetstyle

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.annotation.ColorRes
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wikiink.app.widget.WikiWidgetProvider
import com.wikiink.app.widget.WikiWidgetProviderCompact
import com.wikiink.app.widget.WikiWidgetProviderLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wikiink.app.R
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetStyle
import com.wikiink.app.data.prefs.WidgetThemeMode
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkButton
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.InkTopBar
import com.wikiink.app.ui.theme.SerifFamily
import com.wikiink.app.ui.theme.inkClickable
import com.wikiink.app.ui.theme.monoTag
import com.wikiink.app.widget.WidgetStyleSpec
import com.wikiink.app.widget.WidgetTopic
import com.wikiink.app.widget.WidgetStyles
import kotlinx.coroutines.delay

private data class StyleOption(val style: WidgetStyle, val name: String, val blurb: String)

private val STYLE_OPTIONS = listOf(
    StyleOption(WidgetStyle.CLASSIC_EINK, "Classic E-Ink", "Paper & ink. Serif hooks, monospace tags, hairline rules."),
    StyleOption(WidgetStyle.SWISS_ALABASTER, "Swiss Alabaster", "Braun-clock calm. Squircle card, quiet sans type, no shadows."),
    StyleOption(WidgetStyle.FROSTED_GLASS, "Frosted Glass", "Translucent frosted card that lets your wallpaper glow through."),
    StyleOption(WidgetStyle.DOT_MATRIX, "Dot Matrix", "Nothing-inspired. Monochrome, one red dot, dot-matrix type throughout."),
    StyleOption(WidgetStyle.ALMANAC, "Almanac", "Warm paper and bold book serif, with a calendar-style highlight."),
)

private val LAYOUT_OPTIONS = listOf(
    Triple(WidgetLayout.STACK, "STACK", "A deck of cards. Swipe up, or tap › for the next one."),
    Triple(WidgetLayout.SINGLE, "ONE AT A TIME", "One full card at a time. Tap NEXT › for the next fact."),
)

private const val SAMPLE_CATEGORY = "DID YOU KNOW"
private const val SAMPLE_HOOK =
    "… that the “Bloop”, an ultra-low-frequency sound heard across 5,000 km of Pacific Ocean in 1997, was most likely an icequake?"
private const val SAMPLE_TITLE = "→ Bloop"

@Composable
fun WidgetStyleScreen(viewModel: WidgetStyleViewModel = viewModel(factory = WidgetStyleViewModel.Factory)) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val selectedTopic by viewModel.selectedTopic.collectAsStateWithLifecycle()
    val widgetsPlaced by viewModel.widgetsPlaced.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshWidgetState() }
    val systemDark = isSystemInDarkTheme()
    val spec = WidgetStyles.specFor(prefs.style, prefs.isDark(systemDark))
    val c = Ink.colors

    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        InkTopBar("INKYNERD // WIDGET")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Sync sits up top with the preview so it never needs a scroll.
            val context = LocalContext.current
            val pinManager = remember { AppWidgetManager.getInstance(context) }
            var pickSize by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // One short word so the header stays on one line at large font sizes.
                Text("PREVIEW", style = monoTag(11.sp), color = c.inkSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                if (pinManager.isRequestPinAppWidgetSupported) {
                    InkButton(
                        text = "+ ADD",
                        selected = pickSize,
                        contentDescription = "Add a widget to the home screen",
                        onClick = { pickSize = !pickSize },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                InkButton(text = "↻ SYNC & REFRESH", onClick = viewModel::syncAndRefresh)
            }
            if (pickSize) {
                // Same three presets as onboarding; the launcher then asks where to place it.
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "3×2" to WikiWidgetProviderCompact::class.java,
                        "4×2" to WikiWidgetProvider::class.java,
                        "4×3" to WikiWidgetProviderLarge::class.java,
                    ).forEach { (label, provider) ->
                        InkButton(
                            text = "+ $label",
                            contentDescription = "Add a $label widget",
                            onClick = {
                                pinManager.requestPinAppWidget(ComponentName(context, provider), null, null)
                                pickSize = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            InkRule(thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            WidgetPreview(spec, stacked = prefs.layout == WidgetLayout.STACK)
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                message?.let { Text(it, style = monoTag(10.sp), color = c.inkSecondary) }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("STYLE")
            // Compact 2-column grid; only the selected style's description is shown.
            STYLE_OPTIONS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        StyleChip(option, selected = option.style == prefs.style, modifier = Modifier.weight(1f)) {
                            viewModel.selectStyle(option.style)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                STYLE_OPTIONS.first { it.style == prefs.style }.blurb,
                style = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 18.sp),
                color = c.inkSecondary,
                // Fixed two-line slot so the controls below don't jump when switching styles.
                minLines = 2,
            )

            Spacer(Modifier.height(14.dp))
            SectionLabel("APPEARANCE")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetThemeMode.entries.forEach { mode ->
                    InkButton(
                        text = mode.name,
                        selected = mode == prefs.themeMode,
                        onClick = { viewModel.selectMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("LAYOUT")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LAYOUT_OPTIONS.forEach { (layout, label) ->
                    InkButton(
                        text = label,
                        selected = layout == prefs.layout,
                        onClick = { viewModel.selectLayout(layout) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                LAYOUT_OPTIONS.first { it.first == prefs.layout }.third,
                style = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 18.sp),
                color = c.inkSecondary,
            )

            Spacer(Modifier.height(14.dp))
            TopicPicker(
                topics = topics,
                selected = selectedTopic,
                enabled = widgetsPlaced,
                onSelect = viewModel::selectTopic,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Topic picker, collapsed by default: a single row showing the current topic; tap it (or the
 * arrow) to drop down every topic with cached facts. Mirrors tapping the tag on the widget.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPicker(topics: List<TopicOption>, selected: WidgetTopic, enabled: Boolean, onSelect: (WidgetTopic) -> Unit) {
    val c = Ink.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .inkClickable(enabled = enabled) { open = !open }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TOPIC", style = monoTag(11.sp), color = c.inkSecondary)
        Spacer(Modifier.width(10.dp))
        Text(
            if (enabled) selected.label.uppercase() else "ADD A WIDGET FIRST",
            style = monoTag(11.sp),
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(if (open) "▴" else "▾", style = monoTag(16.sp), color = if (enabled) c.ink else c.inkSecondary)
    }
    Spacer(Modifier.height(6.dp))
    InkRule(thickness = 1.dp)
    if (open && enabled) {
        Spacer(Modifier.height(10.dp))
        Text(
            "What your widgets show. You can also tap the tag on the widget to flip through these.",
            style = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 18.sp),
            color = c.inkSecondary,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            topics.forEach { option ->
                InkButton(
                    text = "${option.topic.label.uppercase()} · ${option.count}",
                    selected = option.topic == selected,
                    onClick = {
                        onSelect(option.topic)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = Ink.colors
    Text(text, style = monoTag(11.sp), color = c.inkSecondary)
    Spacer(Modifier.height(6.dp))
    InkRule(thickness = 1.dp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StyleChip(option: StyleOption, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val fg = if (selected) c.background else c.ink
    Row(
        modifier
            .border(HairlineWidth, c.border, RoundedCornerShape(6.dp))
            .background(if (selected) c.ink else c.surface, RoundedCornerShape(6.dp))
            .inkClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shrinks to fit rather than truncating ("Swiss Alabast…" at a 1.25× font size).
        BasicText(
            option.name,
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f),
        )
        Text(if (selected) "■" else "□", style = monoTag(12.sp), color = fg)
    }
}

/**
 * Compose mirror of the RemoteViews widget, driven by the same [WidgetStyleSpec] color resources.
 * Frosted Glass sits on a colorful "wallpaper" so its translucency is visible.
 */
@Composable
private fun WidgetPreview(spec: WidgetStyleSpec, stacked: Boolean) {
    val wallpaper = when (spec.style) {
        WidgetStyle.FROSTED_GLASS -> Brush.linearGradient(
            if (spec.dark) listOf(Color(0xFF1E1B4B), Color(0xFF7C2D12), Color(0xFF064E3B))
            else listOf(Color(0xFFFBCFE8), Color(0xFFBFDBFE), Color(0xFFFDE68A)),
        )
        else -> Brush.linearGradient(
            if (spec.dark) listOf(Color(0xFF2A2D33), Color(0xFF1A1C20)) else listOf(Color(0xFFD9DDE3), Color(0xFFC7CCD4)),
        )
    }
    val corner = RoundedCornerShape(spec.cornerDp.dp)

    // Borderless, header-free — mirrors the home-screen widget: cards sit straight on the wallpaper.
    Box(
        Modifier
            .fillMaxWidth()
            .border(HairlineWidth, Ink.colors.border, RoundedCornerShape(8.dp))
            .background(wallpaper, RoundedCornerShape(8.dp))
            .padding(18.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = if (stacked) 14.dp else 0.dp, end = if (stacked) 14.dp else 0.dp)) {
            if (stacked) {
                // Ghost cards peeking up-and-right, like StackView's deck.
                listOf(2 to 0.35f, 1 to 0.6f).forEach { (depth, a) ->
                    Box(
                        Modifier
                            .matchParentSize()
                            .offset(x = (7 * depth).dp, y = (-7 * depth).dp)
                            .alpha(a)
                            .then(cardSurface(spec, corner, stacked = true)),
                    )
                }
            }
            PreviewCard(spec, corner, stacked)
        }
    }
}

@Composable
private fun cardSurface(spec: WidgetStyleSpec, corner: RoundedCornerShape, stacked: Boolean = false): Modifier = when (spec.style) {
    WidgetStyle.CLASSIC_EINK ->
        Modifier.background(colorResource(if (spec.dark) R.color.eink_surface_dark else R.color.eink_surface_light), corner)
    WidgetStyle.SWISS_ALABASTER ->
        Modifier.background(colorResource(if (spec.dark) R.color.swiss_card_dark else R.color.swiss_card_light), corner)
    WidgetStyle.DOT_MATRIX ->
        Modifier.background(colorResource(if (spec.dark) R.color.dot_card_dark else R.color.dot_card_light), corner)
    WidgetStyle.ALMANAC ->
        Modifier.background(colorResource(if (spec.dark) R.color.alm_card_dark else R.color.alm_card_light), corner)
    WidgetStyle.FROSTED_GLASS -> Modifier.background(
        Brush.verticalGradient(
            listOf(
                colorResource(
                    when {
                        stacked && spec.dark -> R.color.glass_top_dark_solid
                        stacked -> R.color.glass_top_light_solid
                        spec.dark -> R.color.glass_top_dark
                        else -> R.color.glass_top_light
                    },
                ),
                colorResource(
                    when {
                        stacked && spec.dark -> R.color.glass_bottom_dark_solid
                        stacked -> R.color.glass_bottom_light_solid
                        spec.dark -> R.color.glass_bottom_dark
                        else -> R.color.glass_bottom_light
                    },
                ),
            ),
        ),
        corner,
    )
}

/**
 * Compose mirror of the shared widget card template (tools/gen_widget_layouts.py): same padding,
 * 32dp header [tag][index | spacer][↻][bookmark], fact, optional rule, 24dp footer
 * [index (Dot / Almanac)][title][›]. Fonts mirror the widget's, not the app's reading font.
 */
@Composable
private fun PreviewCard(spec: WidgetStyleSpec, corner: RoundedCornerShape, stacked: Boolean) {
    val style = spec.style
    val ink = colorResource(spec.hookInk)
    val ink2 = colorResource(spec.titleInk)
    val meta = colorResource(spec.indexInk)
    val hookFamily = when (style) {
        WidgetStyle.CLASSIC_EINK -> FontFamily.Serif
        WidgetStyle.ALMANAC -> AlmanacFamily
        WidgetStyle.DOT_MATRIX -> DotFamily
        else -> FontFamily.SansSerif
    }
    val hookWeight = if (style == WidgetStyle.CLASSIC_EINK) FontWeight.Normal else if (style == WidgetStyle.ALMANAC) FontWeight.SemiBold else FontWeight.Medium
    val titleStyle = when (style) {
        WidgetStyle.CLASSIC_EINK -> TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.02.em)
        WidgetStyle.DOT_MATRIX -> TextStyle(fontFamily = DotFamily, fontSize = 12.sp, letterSpacing = 0.02.em)
        WidgetStyle.ALMANAC -> TextStyle(fontFamily = AlmanacFamily, fontSize = 12.sp)
        else -> TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
    }

    Column(
        cardSurface(spec, corner, stacked)
            .fillMaxWidth()
            // A standard 4×2 widget is roughly twice as wide as it is tall; a Stack card keeps
            // that shape (each card is pinned to 90% of the widget).
            .aspectRatio(2.0f)
            .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (style == WidgetStyle.DOT_MATRIX) {
                Box(Modifier.size(7.dp).background(colorResource(R.color.dot_accent), CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            PreviewTag(spec)
            if (style == WidgetStyle.DOT_MATRIX || style == WidgetStyle.ALMANAC) {
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    "01/36",
                    style = TextStyle(fontFamily = if (style == WidgetStyle.CLASSIC_EINK) FontFamily.Monospace else FontFamily.SansSerif, fontSize = 10.sp),
                    color = meta,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Text("↻", style = TextStyle(fontSize = 15.sp), color = ink2)
            }
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bookmark_outline), contentDescription = null, tint = colorResource(spec.iconTint), modifier = Modifier.size(18.dp))
            }
        }
        // Same auto-fit as the widget's hook (12–36sp, largest that fits), so preview == home screen.
        BasicText(
            SAMPLE_HOOK,
            style = TextStyle(fontFamily = hookFamily, fontWeight = hookWeight, fontSize = 15.sp, lineHeight = 1.3.em, color = ink),
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 36.sp, stepSize = 0.5.sp),
            modifier = Modifier.padding(top = 6.dp, end = 6.dp).weight(1f).fillMaxWidth(),
        )
        when (style) {
            WidgetStyle.CLASSIC_EINK ->
                Box(Modifier.padding(top = 6.dp, end = 6.dp).fillMaxWidth().height(1.dp).background(ink))
            WidgetStyle.DOT_MATRIX ->
                Row(Modifier.padding(top = 6.dp, end = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(40) { Box(Modifier.size(2.dp).background(ink2.copy(alpha = 0.5f), CircleShape)) }
                }
            else -> Unit
        }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            when (style) {
                WidgetStyle.DOT_MATRIX -> Text(
                    "01/36",
                    style = TextStyle(fontFamily = DotFamily, fontSize = 20.sp),
                    color = ink,
                    modifier = Modifier.padding(end = 10.dp),
                )
                WidgetStyle.ALMANAC -> Text(
                    "01/36",
                    style = TextStyle(fontFamily = AlmanacFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = meta,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .background(ink, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                else -> Unit
            }
            Text(SAMPLE_TITLE, style = titleStyle, color = ink2, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                if (stacked) {
                    Text("›", style = TextStyle(fontSize = 22.sp), color = ink, modifier = Modifier.padding(end = 12.dp))
                } else {
                    Text(
                        "NEXT ›",
                        style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.06.em),
                        color = ink,
                    )
                }
            }
        }
    }
}

/** The card's category tag, skinned per style exactly like the widget's. */
@Composable
private fun PreviewTag(spec: WidgetStyleSpec) {
    val color = colorResource(spec.categoryInk)
    when (spec.style) {
        WidgetStyle.CLASSIC_EINK -> Text(
            SAMPLE_CATEGORY,
            style = monoTag(10.sp),
            color = color,
            modifier = Modifier.border(1.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        WidgetStyle.DOT_MATRIX -> Text(
            SAMPLE_CATEGORY,
            style = TextStyle(fontFamily = DotFamily, fontSize = 10.sp, letterSpacing = 0.06.em),
            color = color,
            modifier = Modifier
                .border(1.dp, colorResource(if (spec.dark) R.color.dot_line_dark else R.color.dot_line_light), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        WidgetStyle.ALMANAC -> Text(
            SAMPLE_CATEGORY,
            style = TextStyle(fontFamily = AlmanacFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em),
            color = color,
        )
        else -> PreviewPill(spec, eink = false)
    }
}

private val DotFamily = FontFamily(Font(R.font.doto_black))

private val AlmanacFamily = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_semibold, FontWeight.SemiBold),
    Font(R.font.literata_bold, FontWeight.Bold),
)

@Composable
private fun PreviewPill(spec: WidgetStyleSpec, eink: Boolean) {
    val textColor = colorResource(spec.categoryInk)
    if (eink) {
        Text(
            SAMPLE_CATEGORY,
            style = monoTag(10.sp),
            color = textColor,
            modifier = Modifier.border(1.dp, textColor, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        )
    } else {
        @ColorRes val pill = when (spec.style) {
            WidgetStyle.SWISS_ALABASTER -> if (spec.dark) R.color.swiss_pill_dark else R.color.swiss_pill_light
            else -> if (spec.dark) R.color.glass_pill_dark else R.color.glass_pill_light
        }
        Text(
            SAMPLE_CATEGORY,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.08.em),
            color = textColor,
            modifier = Modifier.background(colorResource(pill), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
