package com.wikiink.app.ui.onboarding

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkButton
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.SerifFamily
import com.wikiink.app.ui.theme.inkClickable
import com.wikiink.app.ui.theme.monoTag
import com.wikiink.app.widget.WikiWidgetProvider
import com.wikiink.app.widget.WikiWidgetProviderCompact
import com.wikiink.app.widget.WikiWidgetProviderLarge
import kotlinx.coroutines.delay

private data class SizeOption(
    val provider: Class<out WikiWidgetProvider>,
    val name: String,
    val cols: Int,
    val rows: Int,
    val blurb: String,
    val recommended: Boolean = false,
)

private val SIZES = listOf(
    SizeOption(WikiWidgetProviderCompact::class.java, "Compact", 3, 2, "Sits beside a column of icons. Short hooks."),
    SizeOption(WikiWidgetProvider::class.java, "Standard", 4, 2, "Full width, 3–4 lines. The sweet spot.", recommended = true),
    SizeOption(WikiWidgetProviderLarge::class.java, "Large", 4, 3, "Long reads with bigger type."),
)
private const val DEFAULT_SIZE = 1

/**
 * First-launch gate: InkyNerd lives on the home screen, so ask for the widget before anything
 * else. Uses the launcher's pin-widget flow when available, with manual steps as a fallback.
 * Continue unlocks as soon as a widget of any size is placed; "Skip for now" shows until then.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = Ink.colors
    val manager = remember { AppWidgetManager.getInstance(context) }
    val pinSupported = remember { manager.isRequestPinAppWidgetSupported }
    var selected by rememberSaveable { mutableIntStateOf(DEFAULT_SIZE) }
    var added by remember { mutableStateOf(WikiWidgetProvider.allWidgetIds(context).isNotEmpty()) }
    var pinFailed by rememberSaveable { mutableStateOf(false) }

    // The launcher's confirm sheet floats over us, so poll rather than waiting for onResume.
    LaunchedEffect(Unit) {
        while (!added) {
            delay(800)
            added = WikiWidgetProvider.allWidgetIds(context).isNotEmpty()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Text("INKYNERD // SETUP", style = monoTag(11.sp), color = c.inkSecondary)
        Spacer(Modifier.height(18.dp))
        Text(
            "A nerdy fact, right on your home screen.",
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            color = c.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "InkyNerd lives in a home-screen widget: niche trivia, oddities and deep-cut facts you can flip through. " +
                "Add the widget to continue. You can resize it any time.",
            style = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 22.sp),
            color = c.inkSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Text("PICK A SIZE", style = monoTag(11.sp), color = c.inkSecondary)
        Spacer(Modifier.height(6.dp))
        InkRule(thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
        SIZES.forEachIndexed { i, option ->
            SizeCard(option, selected = i == selected) { selected = i }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        if (added) {
            Text("✓ WIDGET ADDED", style = monoTag(12.sp), color = c.ink)
            Spacer(Modifier.height(10.dp))
            InkButton(text = "CONTINUE →", selected = true, onClick = onDone, modifier = Modifier.fillMaxWidth())
        } else {
            if (pinSupported && !pinFailed) {
                InkButton(
                    text = "+ ADD ${SIZES[selected].name.uppercase()} TO HOME SCREEN",
                    selected = true,
                    onClick = {
                        val ok = manager.requestPinAppWidget(ComponentName(context, SIZES[selected].provider), null, null)
                        if (!ok) pinFailed = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
            }
            ManualSteps(emphasised = !pinSupported || pinFailed, sizeName = SIZES[selected].name)
            Spacer(Modifier.height(14.dp))
            InkButton(text = "CONTINUE", enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth())
        }

        // Once a widget is placed, CONTINUE is the way forward; "skip" would be misleading.
        if (!added) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Skip for now",
                style = monoTag(11.sp),
                color = c.inkSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .inkClickable(onClick = onDone)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SizeCard(option: SizeOption, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val fg = if (selected) c.background else c.ink
    val fg2 = if (selected) c.background.copy(alpha = 0.8f) else c.inkSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .border(HairlineWidth, c.border, RoundedCornerShape(6.dp))
            .background(if (selected) c.ink else c.surface, RoundedCornerShape(6.dp))
            .inkClickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CellGrid(option.cols, option.rows, on = fg, off = fg.copy(alpha = 0.18f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${option.name}  ${option.cols}×${option.rows}",
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = fg,
                )
                if (option.recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RECOMMENDED",
                        style = monoTag(9.sp),
                        color = fg,
                        modifier = Modifier
                            .border(1.dp, fg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(option.blurb, style = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 18.sp), color = fg2)
        }
    }
}

/** A 4×3 home-screen grid with the widget's footprint filled in. */
@Composable
private fun CellGrid(cols: Int, rows: Int, on: androidx.compose.ui.graphics.Color, off: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(4) { col ->
                    val filled = r < rows && col < cols
                    Spacer(Modifier.size(9.dp).background(if (filled) on else off, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun ManualSteps(emphasised: Boolean, sizeName: String) {
    val c = Ink.colors
    val body = TextStyle(fontFamily = SerifFamily, fontSize = 14.sp, lineHeight = 21.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(HairlineWidth, if (emphasised) c.ink else c.border, RoundedCornerShape(6.dp))
            .padding(14.dp),
    ) {
        Text(if (emphasised) "ADD IT MANUALLY" else "OR ADD IT MANUALLY", style = monoTag(10.sp), color = c.inkSecondary)
        Spacer(Modifier.height(6.dp))
        Text("1. Long-press an empty spot on your home screen.", style = body, color = c.ink)
        Text("2. Tap Widgets and find InkyNerd.", style = body, color = c.ink)
        Text(
            if (sizeName == "Standard") "3. Drag “InkyNerd Facts” onto the screen."
            else "3. Drag “InkyNerd Facts · $sizeName” onto the screen.",
            style = body,
            color = c.ink,
        )
    }
}
