package com.github.x0x0b.codexlauncher.settings.options

/**
 * Model selection for the `--model` argument.
 *
 * NOTE:
 * - GPT_5* models are kept for compatibility with environments where gpt-5.1 is not yet available.
 * - GPT_5_1* models are additional options and should not replace GPT_5* in enterprise environments.
 */
enum class Model {
    /** Do not pass --model. */
    DEFAULT,

    // Legacy / existing models (kept for enterprise compatibility)
    GPT_5,
    GPT_5_CODEX,
    CODEX_MINI_LATEST,

    // New gpt-5.1 based models (optional additions)
    GPT_5_1,
    GPT_5_1_CODEX,
    GPT_5_1_CODEX_MINI,

    /** Use customModel from settings. */
    CUSTOM;

    fun cliName(): String = when (this) {
        DEFAULT -> ""

        // Existing
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"

        // New 5.1 models
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        GPT_5_1_CODEX_MINI -> "gpt-5.1-codex-mini"

        CUSTOM -> ""
    }

    fun toDisplayName(): String = when (this) {
        DEFAULT -> "Default"

        // Existing
        GPT_5 -> "gpt-5"
        GPT_5_CODEX -> "gpt-5-codex"
        CODEX_MINI_LATEST -> "codex-mini-latest"

        // New 5.1 models
        GPT_5_1 -> "gpt-5.1"
        GPT_5_1_CODEX -> "gpt-5.1-codex"
        GPT_5_1_CODEX_MINI -> "gpt-5.1-codex-mini"

        CUSTOM -> "Custom..."
    }

    override fun toString(): String = toDisplayName()
}
