package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode

/**
 * Builds the CLI argument list for launching `codex` based on persisted settings.
 */
object CodexArgsBuilder {
    /**
     * Returns the list of arguments to pass to `codex`.
     * Example: ["--full-auto", "--model", "gpt-5"]
     */
    fun build(state: CodexLauncherSettings.State): List<String> {
        val parts = mutableListOf<String>()

        if (state.mode == Mode.FULL_AUTO) {
            parts += "--full-auto"
        }

        val modelName: String? = when (state.model) {
            Model.DEFAULT -> null
            Model.CUSTOM -> state.customModel.trim().ifBlank { null }
            else -> state.model.cliName()
        }

        if (modelName != null) {
            parts += listOf("--model", "'${modelName}'")
        }

        return parts
    }
}

