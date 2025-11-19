package com.github.x0x0b.geminilauncher.settings.options

/**
 * Launch mode for `gemini`.
 */
enum class Mode {
    /** Do not pass any mode-related arguments. */
    DEFAULT,

    /** Pass --full-auto. */
    FULL_AUTO,

    CHAT;

    val argument: String
        get() = when (this) {
            DEFAULT -> ""
            FULL_AUTO -> "--full-auto"
            CHAT -> "" // Default mode is usually chat or similar
        }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default (No arguments)"
        FULL_AUTO -> "Full Auto (--full-auto)"
        CHAT -> "Chat"
    }
}