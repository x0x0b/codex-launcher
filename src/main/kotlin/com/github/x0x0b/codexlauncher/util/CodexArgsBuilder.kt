package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

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

        // Add MCP configuration if specified
        val mcpConfigArgs = buildMcpConfigArgs(state.mcpConfigInput)
        parts += mcpConfigArgs

        return parts
    }

    /**
     * Builds MCP configuration arguments from JSON input.
     * 
     * Parses the JSON configuration and generates -c arguments for:
     * - mcp_servers.<ideaName>.command
     * - mcp_servers.<ideaName>.args
     * - mcp_servers.<ideaName>.env
     * 
     * @param mcpConfigInput JSON configuration string
     * @return List of -c arguments
     */
    private fun buildMcpConfigArgs(mcpConfigInput: String): List<String> {
        if (mcpConfigInput.trim().isEmpty()) {
            return emptyList()
        }

        return try {
            val jsonElement = JsonParser.parseString(mcpConfigInput)
            val jsonObject = jsonElement.asJsonObject
            val args = mutableListOf<String>()

            val command = jsonObject.get("command")?.asString ?: ""
            val argsArray = jsonObject.get("args")?.asJsonArray
            val env = jsonObject.get("env")?.asJsonObject

            val ideaName = extractIdeNameFromCommand(command)

            // Add command configuration
            if (command.isNotEmpty()) {
                args += listOf("-c", "'mcp_servers.$ideaName.command=$command'")
            }

            // Add args configuration
            if (argsArray != null && argsArray.size() > 0) {
                val argsList = argsArray.map { "\"${it.asString}\"" }.joinToString(",")
                args += listOf("-c", "'mcp_servers.$ideaName.args=[$argsList]'")
            }

            // Add env configuration
            if (env != null && env.size() > 0) {
                val envEntries = env.entrySet().map { "\"${it.key}\"=\"${it.value.asString}\"" }
                val envMap = "{${envEntries.joinToString(",")}}"
                args += listOf("-c", "'mcp_servers.$ideaName.env=$envMap'")
            }

            args
        } catch (e: JsonSyntaxException) {
            emptyList() // Return empty list if JSON is invalid
        } catch (e: Exception) {
            emptyList() // Return empty list for any other errors
        }
    }

    /**
     * Extracts IDE name from the command path for use in MCP configuration.
     */
    private fun extractIdeNameFromCommand(command: String): String {
        return when {
            command.contains("IntelliJ IDEA") -> "intellij"
            command.contains("PyCharm") -> "pycharm"
            command.contains("WebStorm") -> "webstorm"
            command.contains("CLion") -> "clion"
            command.contains("PhpStorm") -> "phpstorm"
            command.contains("RubyMine") -> "rubymine"
            command.contains("GoLand") -> "goland"
            command.contains("DataGrip") -> "datagrip"
            command.contains("Rider") -> "rider"
            else -> "ide"
        }
    }
}
