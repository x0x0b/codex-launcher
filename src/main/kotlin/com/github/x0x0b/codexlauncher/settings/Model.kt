package com.github.x0x0b.codexlauncher.settings

enum class Model {
    DEFAULT, // Do not pass --model
    GPT_5,
    CODEX_MINI_LATEST,
    CUSTOM; // Use customModel string

    fun cliName(): String = when (this) {
        DEFAULT -> ""
        GPT_5 -> "gpt-5"
        CODEX_MINI_LATEST -> "codex-mini-latest"
        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"
        GPT_5 -> "gpt-5"
        CODEX_MINI_LATEST -> "codex-mini-latest"
        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
