package com.friendsvibe.autopilot.rank

object Names {
    private val weakTokens = setOf(
        "the", "and", "for", "with", "feat", "featuring", "remix", "edit", "mix",
    )

    fun key(value: String): String {
        val trimmed = value.trim().lowercase()
        if (trimmed.isEmpty() || trimmed == "<unknown>" || trimmed == "unknown" || trimmed == "null") {
            return ""
        }
        return trimmed
    }

    fun tokens(title: String): Set<String> {
        return title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in weakTokens }
            .toSet()
    }
}

data class Vibe(val steps: Int) {
    val clamped: Int get() = steps.coerceIn(MIN, MAX)
    val tempoOffset: Int get() = clamped * TEMPO_STEP
    val label: String
        get() = when (clamped) {
            MIN -> "Much calmer"
            -1 -> "Calmer"
            0 -> "Holding the vibe"
            1 -> "Harder"
            else -> "Much harder"
        }

    fun calmer(): Vibe = Vibe((clamped - 1).coerceIn(MIN, MAX))
    fun harder(): Vibe = Vibe((clamped + 1).coerceIn(MIN, MAX))

    companion object {
        const val MIN = -2
        const val MAX = 2
        const val TEMPO_STEP = 8
        val Neutral = Vibe(0)
    }
}
