package com.github.x0x0b.codexlauncher.settings

/**
 * Launch mode for `codex`.
 */
enum class Mode {
    /** Do not pass any mode-related arguments. */
    DEFAULT,

    /** Pass --full-auto. */
    FULL_AUTO;

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default (No arguments)"
        FULL_AUTO -> "Full Auto (--full-auto)"
    }
}
