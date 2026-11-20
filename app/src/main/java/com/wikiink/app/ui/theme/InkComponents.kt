package com.wikiink.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

val InkCorner = RoundedCornerShape(6.dp)

/** Click without indication (E-Ink: no ripple, no bounce). */
fun Modifier.inkClickable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        ),
    )

/** Hairline-bordered card. Zero elevation; separation via border only. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ink.colors
    Column(
        modifier = modifier
            .border(HairlineWidth, c.border, InkCorner)
            .background(c.surface, InkCorner)
            .let { if (onClick != null) it.inkClickable(onClick = onClick) else it }
            .padding(14.dp),
        content = content,
    )
}

/**
 * Compact bordered text button. [selected] inverts colors (ink fill), as E-Ink UIs do for state.
 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 36.dp,
    contentDescription: String? = null,
    textSize: TextUnit = 12.sp,
) {
    val c = Ink.colors
    val fg = when {
        !enabled -> c.inkSecondary
        selected -> c.background
        else -> c.ink
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = 36.dp)
            .border(HairlineWidth, if (enabled) c.border else c.inkSecondary, RoundedCornerShape(4.dp))
            .background(if (selected) c.ink else c.background, RoundedCornerShape(4.dp))
            .inkClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .let { m ->
                if (contentDescription != null) {
                    m.then(Modifier.semanticsDescription(contentDescription))
                } else m
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = monoTag(textSize),
            color = fg,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun Modifier.semanticsDescription(desc: String): Modifier =
    this.then(Modifier.semantics { this.contentDescription = desc })

/** Thin horizontal rule. */
@Composable
fun InkRule(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(Ink.colors.border))
}

/** Monospace status header bar, e.g. `INKYNERD // ARCHIVE`. */
@Composable
fun InkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ink.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = monoTag(13.sp), color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
            actions()
        }
        InkRule(thickness = HairlineWidth)
    }
}

/** Monospace category tag with a hairline box. */
@Composable
fun InkTag(text: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Text(
        text = text,
        style = monoTag(10.sp),
        color = c.ink,
        modifier = modifier
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
