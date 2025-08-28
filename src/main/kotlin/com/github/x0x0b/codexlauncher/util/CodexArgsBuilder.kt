package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode

/**
 * Utility object for building command-line arguments for codex execution.
 * 
 * This builder translates the plugin's settings into appropriate command-line arguments
 * that can be passed to the codex CLI tool. It handles:
 * - Mode selection (--full-auto flag)
 * - Model specification (--model parameter)
 * - Custom model handling with proper validation
 * 
 * @since 1.0.0
 */
object CodexArgsBuilder {
    /**
     * Builds the command-line argument list for codex based on the provided settings state.
     * 
     * The method processes the settings and generates appropriate arguments:
     * - Adds --full-auto flag if the mode is set to FULL_AUTO
     * - Adds --model parameter with the selected model name if not DEFAULT
     * - Handles custom models with proper validation
     * 
     * @param state The current settings state containing user preferences
     * @return A list of command-line arguments to pass to codex
     * 
     * @example
     * For settings with mode=FULL_AUTO and model=GPT_5:
     * Returns: ["--full-auto", "--model", "gpt-5"]
     */
    fun build(state: CodexLauncherSettings.State): List<String> {
        val parts = mutableListOf<String>()

        if (state.mode == Mode.FULL_AUTO) {
            parts += "--full-auto"
        }

        // Determine the model name to use
        val modelName: String? = when (state.model) {
            Model.DEFAULT -> null // Use codex default model
            Model.CUSTOM -> state.customModel.trim().ifBlank { null }
            else -> state.model.cliName()
        }

        // Add model parameter if specified
        if (modelName != null) {
            parts += listOf("--model", "'${modelName}'")
        }

        return parts
    }
}

