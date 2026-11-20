package com.wikiink.app.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikiink.app.R
import com.wikiink.app.data.repository.WidgetStyle
import com.wikiink.app.data.repository.WidgetThemeMode
import com.wikiink.app.ui.theme.InkStatusHeader
import com.wikiink.app.ui.theme.noRippleClickable
import kotlinx.coroutines.delay

private data class SampleFact(val category: String, val hook: String, val title: String)

private val SAMPLE_FACT = SampleFact(
    category = "DID YOU KNOW",
    hook = "A group of flamingos is called a flamboyance — and their pink feathers come from pigments in the shrimp they eat.",
    title = "Flamingo"
)

@Composable
fun WidgetStyleScreen(
    vm: WidgetStyleViewModel,
    onOpenReader: () -> Unit,
    onOpenFolders: () -> Unit
) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var confirmation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(2_200L)
            confirmation = null
        }
    }

    val dark = when (prefs.themeMode) {
        WidgetThemeMode.DARK -> true
        WidgetThemeMode.LIGHT -> false
        WidgetThemeMode.SYSTEM -> systemDark
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        InkStatusHeader(
            section = "WIDGET",
            onReader = onOpenReader,
            onFolders = onOpenFolders,
            onWidget = {}
        )

        Text(
            text = "WIDGET STYLE // INKYNERD",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        )

        // Live preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(horizontal = 12.dp)
        ) {
            val wallpaper: Modifier = if (prefs.style == WidgetStyle.FROSTED_GLASS) {
                Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF8B5CF6),
                            Color(0xFFEC4899),
                            Color(0xFFF59E0B)
                        )
                    )
                )
            } else {
                Modifier.background(if (dark) Color(0xFF121212) else Color(0xFFF4F1EA))
            }
            Box(
                wallpaper
                    .fillMaxSize()
                    .border(1.5.dp, colors.outline)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (prefs.style) {
                    WidgetStyle.CLASSIC_EINK -> EinkPreview(dark)
                    WidgetStyle.SWISS_ALABASTER -> SwissPreview(dark)
                    WidgetStyle.FROSTED_GLASS -> GlassPreview(dark)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "LIVE PREVIEW // ${prefs.style.name.replace('_', ' ')} / ${prefs.themeMode.name}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        SectionLabel("WIDGET STYLE")
        StyleOption(
            title = "Classic E-Ink",
            description = "Sharp hairlines, serif hooks, paper or night ink.",
            selected = prefs.style == WidgetStyle.CLASSIC_EINK,
            onClick = { vm.setStyle(WidgetStyle.CLASSIC_EINK) }
        )
        StyleOption(
            title = "Swiss Alabaster",
            description = "Braun-inspired squircle with a quiet category pill.",
            selected = prefs.style == WidgetStyle.SWISS_ALABASTER,
            onClick = { vm.setStyle(WidgetStyle.SWISS_ALABASTER) }
        )
        StyleOption(
            title = "Frosted Glass",
            description = "Translucent rim and frosted fill over your wallpaper.",
            selected = prefs.style == WidgetStyle.FROSTED_GLASS,
            onClick = { vm.setStyle(WidgetStyle.FROSTED_GLASS) }
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel("APPEARANCE")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .border(1.5.dp, colors.outline)
        ) {
            listOf(
                WidgetThemeMode.SYSTEM to "SYSTEM",
                WidgetThemeMode.LIGHT to "LIGHT",
                WidgetThemeMode.DARK to "DARK"
            ).forEach { (mode, label) ->
                val selected = prefs.themeMode == mode
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) colors.surface else colors.background)
                        .noRippleClickable { vm.setThemeMode(mode) }
                        .padding(vertical = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "SYNC & REFRESH WIDGET",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .border(1.5.dp, colors.outline)
                .noRippleClickable {
                    vm.syncAndRefresh(context)
                    confirmation = "WIDGET SYNCED"
                }
                .padding(vertical = 14.dp)
        )

        if (confirmation != null) {
            Text(
                text = confirmation!!,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun StyleOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.5.dp, if (selected) colors.outline else colors.outlineVariant)
            .background(colors.surface)
            .noRippleClickable(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Text(
            text = if (selected) "[X]" else "[ ]",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun EinkPreview(dark: Boolean) {
    val bg = if (dark) Color(0xFF121212) else Color(0xFFF4F1EA)
    val borderColor = if (dark) Color(0xFFE6E3DC) else Color(0xFF141414)
    val primary = if (dark) Color(0xFFE6E3DC) else Color(0xFF141414)
    val secondary = if (dark) Color(0xFF9E9B95) else Color(0xFF4A4A4A)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.5.dp, borderColor)
            .padding(12.dp)
    ) {
        Text(
            SAMPLE_FACT.category,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
            color = secondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            SAMPLE_FACT.hook,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            color = primary,
            maxLines = 4
        )
        Spacer(Modifier.height(6.dp))
        Text(
            SAMPLE_FACT.title,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif),
            color = secondary
        )
    }
}

@Composable
private fun SwissPreview(dark: Boolean) {
    val card = if (dark) Color(0xFF14171D) else Color(0xFFF6F8FB)
    val borderColor = if (dark) Color(0xFF262C36) else Color(0xFFE2E8F0)
    val primary = if (dark) Color(0xFFF1F5F9) else Color(0xFF111827)
    val secondary = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val pill = if (dark) Color(0xFF1E232B) else Color(0xFFEBF0F6)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(card)
            .border(1.dp, borderColor)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SAMPLE_FACT.category,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.08.em
                ),
                color = secondary,
                modifier = Modifier
                    .background(pill, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_bookmark),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            SAMPLE_FACT.hook,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 19.sp
            ),
            color = primary,
            maxLines = 4
        )
        Spacer(Modifier.height(10.dp))
        Text(
            SAMPLE_FACT.title,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
            color = secondary
        )
    }
}

@Composable
private fun GlassPreview(dark: Boolean) {
    val gradient = Brush.verticalGradient(
        if (dark) listOf(Color(0xBF11151C), Color(0xA60B0E14))
        else listOf(Color(0xCCF8FAFC), Color(0xB3EEF2F7))
    )
    val rim = if (dark) Color(0x38FFFFFF) else Color(0x99FFFFFF)
    val pill = if (dark) Color(0x1FFFFFFF) else Color(0x4DFFFFFF)
    val primary = if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondary = if (dark) Color(0xFFCBD5E1) else Color(0xFF334155)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(gradient)
            .border(1.2.dp, rim)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SAMPLE_FACT.category,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.08.em
                ),
                color = secondary,
                modifier = Modifier
                    .background(pill, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_glass_light),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            SAMPLE_FACT.hook,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 19.sp
            ),
            color = primary,
            maxLines = 4
        )
        Spacer(Modifier.height(8.dp))
        Text(
            SAMPLE_FACT.title,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
            color = secondary
        )
    }
}
