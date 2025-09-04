package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import groovy.json.StringEscapeUtils

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

            val isWindows = isWindowsOS()

            // Add command configuration
            if (command.isNotEmpty()) {
                args += createConfigArgument("mcp_servers.$ideaName.command", command, isWindows)
            }

            // Add args configuration
            if (argsArray != null && argsArray.size() > 0) {
                val argsList = formatArgsArray(argsArray, isWindows)
                args += createConfigArgument("mcp_servers.$ideaName.args", "[$argsList]", isWindows)
            }

            // Add env configuration
            if (env != null && env.size() > 0) {
                val envMap = formatEnvObject(env, isWindows)
                args += createConfigArgument("mcp_servers.$ideaName.env", envMap, isWindows)
            }

            args
        } catch (e: JsonSyntaxException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Checks if the current OS is Windows.
     */
    private fun isWindowsOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    /**
     * Creates a configuration argument with proper OS-specific formatting.
     */
    private fun createConfigArgument(key: String, value: String, isWindows: Boolean): List<String> {
        val configValue = if (isWindows) {
            "$key='$value'"
        } else {
            "'$key=$value'"
        }
        return listOf("-c", configValue)
    }

    /**
     * Formats JSON args array for the target OS.
     */
    private fun formatArgsArray(argsArray: com.google.gson.JsonArray, isWindows: Boolean): String {
        return if (isWindows) {
            argsArray.joinToString(", ") { "\\\"${StringEscapeUtils.escapeJava(it.asString)}\\\"" }
        } else {
            argsArray.joinToString(", ") { "\"${it.asString}\"" }
        }
    }

    /**
     * Formats JSON env object for the target OS.
     */
    private fun formatEnvObject(env: com.google.gson.JsonObject, isWindows: Boolean): String {
        val envEntries = if (isWindows) {
            env.entrySet().map { "\\\"${it.key}\\\"=\\\"${it.value.asString}\\\"" }
        } else {
            env.entrySet().map { "\"${it.key}\"=\"${it.value.asString}\"" }
        }
        return "{${envEntries.joinToString(",")}}"
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
