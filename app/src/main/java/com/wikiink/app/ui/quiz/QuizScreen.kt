package com.wikiink.app.ui.quiz

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wikiink.app.data.quiz.DailyChallenge
import com.wikiink.app.data.quiz.DailyState
import com.wikiink.app.data.quiz.LevelStats
import com.wikiink.app.data.quiz.QuizLevel
import com.wikiink.app.data.quiz.QuizTopics
import com.wikiink.app.data.quiz.formatPoints
import com.wikiink.app.ui.theme.HairlineWidth
import com.wikiink.app.ui.theme.Ink
import com.wikiink.app.ui.theme.InkButton
import com.wikiink.app.ui.theme.InkRule
import com.wikiink.app.ui.theme.InkTopBar
import com.wikiink.app.ui.theme.SerifFamily
import com.wikiink.app.ui.theme.inkClickable
import com.wikiink.app.ui.theme.monoTag

/**
 * Quiz home. QUICK = casual Guess It from cached facts (offline). RANKED = Wikipedia-powered
 * rounds on a chosen topic and level, scored into lifetime Nerd Points.
 */
@Composable
fun QuizScreen(
    onRead: (String) -> Unit,
    viewModel: QuizViewModel = viewModel(factory = QuizViewModel.Factory),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val quick by viewModel.quick.collectAsStateWithLifecycle()
    val ranked by viewModel.ranked.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val dailyState by viewModel.dailyState.collectAsStateWithLifecycle()
    val dailyUi by viewModel.dailyUi.collectAsStateWithLifecycle()
    val c = Ink.colors
    val context = LocalContext.current
    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
        context.startActivity(Intent.createChooser(send, "Share your Nerd Points"))
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        InkTopBar("INKYNERD // QUIZ")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InkButton("QUICK", onClick = { viewModel.setMode(QuizMode.QUICK) }, selected = mode == QuizMode.QUICK, modifier = Modifier.weight(1f))
            InkButton("DAILY", onClick = { viewModel.setMode(QuizMode.DAILY) }, selected = mode == QuizMode.DAILY, modifier = Modifier.weight(1f))
            InkButton("RANKED", onClick = { viewModel.setMode(QuizMode.RANKED) }, selected = mode == QuizMode.RANKED, modifier = Modifier.weight(1f))
        }
        InkRule()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (mode) {
                QuizMode.QUICK -> QuickPane(quick, viewModel, onRead)
                QuizMode.DAILY -> DailyPane(dailyState, dailyUi, viewModel, onRead, share)
                QuizMode.RANKED -> if (ranked.round == null && !ranked.loading) {
                    RankedSetup(ranked, stats, viewModel, share)
                } else {
                    RankedPlay(ranked, stats[ranked.level] ?: LevelStats(), viewModel, onRead)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- Quick

@Composable
private fun QuickPane(state: QuickState, vm: QuizViewModel, onRead: (String) -> Unit) {
    val c = Ink.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("GUESS IT // WHICH ARTICLE?", style = monoTag(11.sp), color = c.inkSecondary, modifier = Modifier.weight(1f))
        Text("${state.score}/${state.asked}", style = monoTag(12.sp), color = c.ink)
    }
    Spacer(Modifier.height(4.dp))
    Text("Casual practice from your saved facts. No points, works offline.", style = body(13), color = c.inkSecondary)
    Spacer(Modifier.height(14.dp))
    val round = state.round
    when {
        round != null -> QuestionCard(
            round = round,
            onPick = vm::answerQuick,
            verdictRight = "CORRECT",
            verdictWrong = "NOPE. IT WAS ${round.question.answer.uppercase()}",
            onNext = vm::nextQuick,
            onRead = { onRead(round.question.answer) },
        )
        state.notEnough -> Text("Guess It needs at least 3 saved facts. Sync first.", style = body(15), color = c.inkSecondary)
        else -> Text("LOADING FACTS…", style = monoTag(11.sp), color = c.inkSecondary)
    }
}

// ---------------------------------------------------------------------------------------- Daily

@Composable
private fun DailyPane(state: DailyState, ui: DailyUi, vm: QuizViewModel, onRead: (String) -> Unit, onShare: () -> Unit) {
    val c = Ink.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("DAILY NERD CHALLENGE // ${state.date}", style = monoTag(11.sp), color = c.inkSecondary, modifier = Modifier.weight(1f))
        Text(
            "STREAK ${state.streak}",
            style = monoTag(12.sp),
            color = c.ink,
            modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "${DailyChallenge.SIZE} questions, fixed for the day. Finish every day to grow your streak.",
        style = body(13),
        color = c.inkSecondary,
    )
    Spacer(Modifier.height(14.dp))
    when {
        ui.loading -> Text("PREPARING TODAY'S ${DailyChallenge.SIZE}…", style = monoTag(11.sp), color = c.inkSecondary)
        ui.error != null && state.questions.isEmpty() -> {
            Text(ui.error, style = body(14), color = c.inkSecondary)
            Spacer(Modifier.height(10.dp))
            InkButton(text = "TRY AGAIN", onClick = vm::openDaily)
        }
        state.done -> {
            Text("TODAY: ${state.correct}/${state.questions.size}", style = monoTag(14.sp), color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Streak: ${state.streak} day${if (state.streak == 1) "" else "s"}. Come back tomorrow for a new set.",
                style = body(15),
                color = c.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))
            state.questions.forEachIndexed { i, q ->
                val right = state.answers.getOrNull(i) == q.answer
                Row(
                    Modifier.fillMaxWidth().inkClickable { onRead(q.answer) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (right) "✓" else "✗", style = monoTag(13.sp), color = c.ink)
                    Spacer(Modifier.width(10.dp))
                    Text(q.answer, style = body(15), color = c.ink, modifier = Modifier.weight(1f))
                    Text("READ ›", style = monoTag(10.sp), color = c.inkSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            InkButton(text = "SHARE MY NERD POINTS", onClick = onShare, modifier = Modifier.fillMaxWidth())
        }
        state.questions.isNotEmpty() -> {
            val index = state.answers.size
            val question = state.questions[index]
            // The just-answered question stays on screen until NEXT, so the verdict can be read.
            var reveal by remember(state.date, index) { mutableStateOf<String?>(null) }
            Text("QUESTION ${index + 1}/${state.questions.size}", style = monoTag(11.sp), color = c.inkSecondary)
            Spacer(Modifier.height(10.dp))
            QuestionCard(
                round = Round(question, picked = reveal),
                onPick = { reveal = it },
                verdictRight = "CORRECT",
                verdictWrong = "NOPE. IT WAS ${question.answer.uppercase()}",
                onNext = { reveal?.let { vm.answerDaily(it) } },
                onRead = { onRead(question.answer) },
            )
        }
        else -> Text("LOADING…", style = monoTag(11.sp), color = c.inkSecondary)
    }
}

// --------------------------------------------------------------------------------------- Ranked

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankedSetup(state: RankedState, stats: Map<QuizLevel, LevelStats>, vm: QuizViewModel, onShare: () -> Unit) {
    val c = Ink.colors
    Label("LEVEL")
    QuizLevel.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { level ->
                LevelTile(level, stats[level] ?: LevelStats(), selected = level == state.level, modifier = Modifier.weight(1f)) {
                    vm.setLevel(level)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Text(state.level.blurb, style = body(13), color = c.inkSecondary)
    val s = stats[state.level] ?: LevelStats()
    if (s.answered > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            "${s.answered} answered · ${s.correct * 100 / s.answered}% right",
            style = monoTag(10.sp),
            color = c.inkSecondary,
        )
    }
    if (state.level.resettable && (s.answered > 0 || s.points != 0.0)) {
        Spacer(Modifier.height(8.dp))
        InkButton(
            text = if (state.confirmReset) "TAP AGAIN TO RESET ${state.level.label.uppercase()}" else "RESET ${state.level.label.uppercase()} POINTS",
            onClick = vm::resetLevel,
            selected = state.confirmReset,
        )
    }

    Spacer(Modifier.height(22.dp))
    Label("TOPIC")
    // Breadcrumb as real buttons: AUTO is always first, so you can always jump back to it.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuizTopics.path(state.browse).forEachIndexed { i, node ->
            if (i > 0) Text("›", style = monoTag(12.sp), color = c.inkSecondary, modifier = Modifier.padding(horizontal = 2.dp))
            InkButton(
                text = if (node == QuizTopics.AUTO) "AUTO" else node.label.uppercase(),
                selected = node == state.topic,
                onClick = { vm.chooseTopic(node) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (state.topic == QuizTopics.AUTO) "Anything under the moon. Or pick a topic, then go deeper."
        else "Quizzing on ${state.topic.label}. Go deeper below, or start.",
        style = body(13),
        color = c.inkSecondary,
    )
    val children = state.browse.children
    if (children.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            children.forEach { child ->
                InkButton(
                    text = child.label.uppercase() + if (child.children.isNotEmpty()) " ›" else "",
                    onClick = { vm.chooseTopic(child) },
                )
            }
        }
    }
    if (state.browse != QuizTopics.AUTO) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InkButton(text = "‹ UP", onClick = vm::topicUp)
            InkButton(text = "BACK TO AUTO", onClick = { vm.chooseTopic(QuizTopics.AUTO) })
        }
    }

    state.error?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = body(14), color = c.inkSecondary)
    }
    Spacer(Modifier.height(22.dp))
    InkButton(
        text = "START · ${state.level.label.uppercase()} · ${state.topic.label.uppercase()}",
        selected = true,
        onClick = vm::startRanked,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    InkButton(text = "SHARE MY NERD POINTS", onClick = onShare, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LevelTile(level: QuizLevel, stats: LevelStats, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val fg = if (selected) c.background else c.ink
    Column(
        modifier
            .border(HairlineWidth, c.border, RoundedCornerShape(6.dp))
            .background(if (selected) c.ink else c.surface, RoundedCornerShape(6.dp))
            .inkClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                level.label.uppercase(),
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (!level.resettable) Text("●", style = monoTag(10.sp), color = fg)
        }
        Text(formatPoints(stats.points), style = monoTag(12.sp), color = if (selected) c.background.copy(alpha = 0.85f) else c.inkSecondary)
    }
}

@Composable
private fun RankedPlay(state: RankedState, stats: LevelStats, vm: QuizViewModel, onRead: (String) -> Unit) {
    val c = Ink.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${state.level.label.uppercase()} · ${state.topic.label.uppercase()}",
            style = monoTag(11.sp),
            color = c.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        state.secondsLeft?.takeIf { round -> state.round?.picked == null && round >= 0 }?.let { left ->
            Text(
                "0:%02d".format(left),
                style = monoTag(13.sp),
                color = if (left <= 5) c.ink else c.inkSecondary,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        // Lifetime points for this level, e.g. +7 or −2.25.
        Text(
            formatPoints(stats.points),
            style = monoTag(13.sp),
            color = c.ink,
            modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text("THIS ROUND ${formatPoints(state.session)}", style = monoTag(10.sp), color = c.inkSecondary)
    Spacer(Modifier.height(14.dp))
    val round = state.round
    if (round == null) {
        Text("FETCHING QUESTIONS…", style = monoTag(11.sp), color = c.inkSecondary)
        return
    }
    QuestionCard(
        round = round,
        onPick = vm::answerRanked,
        verdictRight = "CORRECT  ${round.delta?.let { formatPoints(it) } ?: ""}",
        verdictWrong = (if (round.picked == QuizViewModel.TIMEOUT) "TIME'S UP. IT WAS " else "NOPE. IT WAS ") +
            "${round.question.answer.uppercase()}  ${round.delta?.let { formatPoints(it) } ?: ""}",
        onNext = vm::nextRanked,
        onRead = { onRead(round.question.answer) },
        extraAction = "END" to vm::endRanked,
        nextEnabled = !state.loading,
    )
}

// ---------------------------------------------------------------------------------------- Shared

@Composable
private fun QuestionCard(
    round: Round,
    onPick: (String) -> Unit,
    verdictRight: String,
    verdictWrong: String,
    onNext: () -> Unit,
    onRead: () -> Unit,
    extraAction: Pair<String, () -> Unit>? = null,
    nextEnabled: Boolean = true,
) {
    val c = Ink.colors
    val q = round.question
    Text(q.clue, style = body(18).copy(lineHeight = 26.sp), color = c.ink)
    Spacer(Modifier.height(16.dp))
    q.options.forEach { option ->
        val picked = round.picked
        val mark = when {
            picked == null -> "▸"
            option == q.answer -> "✓"
            option == picked -> "✗"
            else -> " "
        }
        val highlight = picked != null && option == q.answer
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .border(HairlineWidth, c.border, RoundedCornerShape(4.dp))
                .background(if (highlight) c.ink else c.background, RoundedCornerShape(4.dp))
                .inkClickable(enabled = picked == null) { onPick(option) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(mark, style = monoTag(13.sp), color = if (highlight) c.background else c.ink)
            Spacer(Modifier.width(10.dp))
            Text(option, style = body(16), color = if (highlight) c.background else c.ink)
        }
    }
    if (round.picked != null) {
        Spacer(Modifier.height(12.dp))
        Text(if (round.picked == q.answer) verdictRight else verdictWrong, style = monoTag(12.sp), color = c.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            extraAction?.let { (label, action) -> InkButton(text = label, onClick = action, modifier = Modifier.weight(1f)) }
            InkButton(text = "READ IT", onClick = onRead, modifier = Modifier.weight(1f))
            InkButton(text = "NEXT ›", selected = true, enabled = nextEnabled, onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = monoTag(11.sp), color = Ink.colors.inkSecondary)
    Spacer(Modifier.height(6.dp))
    InkRule(thickness = 1.dp)
    Spacer(Modifier.height(10.dp))
}

private fun body(size: Int) = TextStyle(fontFamily = SerifFamily, fontSize = size.sp, lineHeight = (size * 1.4f).sp)
