package com.wikiink.app.data.quiz

/** How the clue is chosen from an article's intro. */
enum class ClueStyle { DESCRIBED, LEAD, DEEP, OBSCURE }

/**
 * Ranked quiz difficulty. The first three are practice tiers whose points can be reset;
 * NERD is the real thing: wrong answers cost points and its total can never be reset.
 */
enum class QuizLevel(
    val label: String,
    val choices: Int,
    val clue: ClueStyle,
    val right: Double,
    val wrong: Double,
    val resettable: Boolean,
    val blurb: String,
) {
    CURIOUS("Curious", 3, ClueStyle.DESCRIBED, 1.0, 0.0, true, "3 choices. Description plus the opening line."),
    GEEK("Geek", 4, ClueStyle.LEAD, 1.0, 0.0, true, "4 choices. Just the opening line."),
    BRAINIAC("Brainiac", 4, ClueStyle.DEEP, 1.0, 0.0, true, "4 choices. A deeper, less obvious line."),
    NERD("NERD", 5, ClueStyle.OBSCURE, 1.0, -0.25, false, "5 choices. The most obscure line. Wrong answers cost 0.25. Points can never be reset."),
    ;

    fun delta(correct: Boolean): Double = if (correct) right else wrong
}

/** Signed points for display: +7, −2.25, 0, +0.75. */
fun formatPoints(points: Double, signed: Boolean = true): String {
    val rounded = Math.round(points * 100) / 100.0
    val abs = kotlin.math.abs(rounded)
    val body = if (abs % 1.0 == 0.0) abs.toLong().toString()
    else abs.toBigDecimal().stripTrailingZeros().toPlainString()
    return when {
        rounded > 0 && signed -> "+$body"
        rounded < 0 -> "−$body"
        else -> body
    }
}
