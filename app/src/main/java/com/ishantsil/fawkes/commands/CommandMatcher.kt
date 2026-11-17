package com.ishantsil.fawkes.commands

/**
 * Rule-based command matcher. Phase 0 is a stub: it normalises the phrase
 * and reports it as unknown. Phase 1 adds the real patterns, one command
 * at a time, flashlight first. Every unheard phrase is logged so the log
 * becomes the Phase 2 feature list.
 */
object CommandMatcher {

    private val fillerWords = setOf("please", "hey", "fawkes", "ok", "okay")

    sealed class MatchResult {
        abstract fun describe(): String

        data class Unknown(val phrase: String) : MatchResult() {
            override fun describe(): String = "unknown command (\"$phrase\" — not implemented yet)"
        }
    }

    fun match(raw: String): MatchResult {
        val normalised = raw.lowercase()
            .split(" ")
            .filter { it.isNotBlank() && it !in fillerWords }
            .joinToString(" ")
            .trim()
        return MatchResult.Unknown(normalised)
    }
}
