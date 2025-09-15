package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode
import com.github.x0x0b.codexlauncher.settings.WinShell
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import groovy.json.StringEscapeUtils
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo

/**
 * Interface for providing OS information, allowing for testing
 */
interface OsProvider {
    val isWindows: Boolean
}

/**
 * Default OS provider that uses SystemInfo
 */
object DefaultOsProvider : OsProvider {
    override val isWindows: Boolean get() = SystemInfo.isWindows
}

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
     * @param port Optional HTTP service port for notify command
     * @return A list of command-line arguments to pass to codex
     * 
     * @example
     * For settings with mode=FULL_AUTO and model=GPT_5:
     * Returns: ["--full-auto", "--model", "gpt-5"]
     */
    fun build(state: CodexLauncherSettings.State, port: Int? = null, osProvider: OsProvider = DefaultOsProvider): List<String> {
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

        // Add notify command if port is provided
        if (port != null) {
            parts += buildNotifyCommand(port,osProvider, state.winShell)
        }

        // Add MCP configuration if specified
        parts += buildMcpConfigArgs(state.mcpConfigInput, osProvider, state.winShell)

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
    private fun buildMcpConfigArgs(mcpConfigInput: String, osProvider: OsProvider = DefaultOsProvider, winShell: WinShell): List<String> {
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

            val ideaName = getCurrentIdeName()

            // Add command configuration
            if (command.isNotEmpty()) {
                args += createConfigArgument("mcp_servers.$ideaName.command", command, osProvider, winShell)
            }

            // Add args configuration
            if (argsArray != null && argsArray.size() > 0) {
                val argsList = formatArgsArray(argsArray, osProvider, winShell)
                args += createConfigArgument("mcp_servers.$ideaName.args", "[$argsList]", osProvider, winShell)
            }

            // Add env configuration
            if (env != null && env.size() > 0) {
                val envMap = formatEnvObject(env, osProvider, winShell)
                args += createConfigArgument("mcp_servers.$ideaName.env", envMap, osProvider, winShell)
            }

            args
        } catch (e: JsonSyntaxException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Creates a configuration argument with proper OS-specific formatting.
     */
    private fun createConfigArgument(key: String, value: String, osProvider: OsProvider = DefaultOsProvider, winShell: WinShell): List<String> {
        val configValue = if (osProvider.isWindows && winShell != WinShell.WSL) {
            // Windows/PowerShell
            "$key='$value'"
        } else {
            // Non-Windows or Windows/WSL
            "'$key=$value'"
        }
        return listOf("-c", configValue)
    }

    /**
     * Formats JSON args array for the target OS.
     */
    private fun formatArgsArray(argsArray: JsonArray, osProvider: OsProvider = DefaultOsProvider, winShell: WinShell): String {
        return if (osProvider.isWindows && winShell != WinShell.WSL) {
            if (winShell == WinShell.POWERSHELL_73_PLUS) {
                // PowerShell 7.3+ on Windows
                argsArray.joinToString(", ") { "\"${StringEscapeUtils.escapeJava(it.asString)}\"" }
            } else {
                // Pre PowerShell 7.3 on Windows
                argsArray.joinToString(", ") { "\\\"${StringEscapeUtils.escapeJava(it.asString)}\\\"" }
            }
        } else {
            // Non-Windows OS or Windows/WSL
            argsArray.joinToString(", ") { "\"${it.asString}\"" }
        }
    }

    /**
     * Formats JSON env object for the target OS.
     */
    private fun formatEnvObject(env: com.google.gson.JsonObject, osProvider: OsProvider = DefaultOsProvider, winShell: WinShell): String {
        // Create a mutable copy of the env object to add Windows-specific entries
        val envMap = env.entrySet().associate { it.key to it.value.asString }.toMutableMap()
        
        // Add SystemRoot for Windows if not already present
        // https://github.com/openai/codex/issues/3311
        if (osProvider.isWindows && !envMap.containsKey("SystemRoot")) {
            envMap["SystemRoot"] = "C:\\\\Windows"
        }
        
        val envEntries = if (osProvider.isWindows && winShell == WinShell.POWERSHELL_LT_73) {
            // Pre PS 7.3 on Windows
            envMap.map { "\\\"${it.key}\\\"=\\\"${it.value}\\\"" }
        } else {
            // Non-Windows or PS 7.3+ on Windows
            envMap.map { "\"${it.key}\"=\"${it.value}\"" }
        }
        return "{${envEntries.joinToString(",")}}"
    }

    /**
     * Builds notify command configuration with proper OS-specific formatting.
     * 
     * @param port The HTTP service port for notifications
     * @return Formatted notify command string
     */
    fun buildNotifyCommand(port: Int, osProvider: OsProvider = DefaultOsProvider, winShell: WinShell): List<String> {
        // Create JsonArray for the curl command arguments
        val curlArgs = JsonArray().apply {
            add(JsonPrimitive("curl"))
            add(JsonPrimitive("-s"))
            add(JsonPrimitive("-X"))
            add(JsonPrimitive("POST"))
            add(JsonPrimitive("http://localhost:$port/refresh"))
            add(JsonPrimitive("-d"))
        }
        
        val formattedArgs = formatArgsArray(curlArgs, osProvider, winShell)
        val configArgs = createConfigArgument("notify", "[$formattedArgs]", osProvider, winShell)
        return configArgs
    }

    /**
     * Gets the current IDE name from ApplicationInfo for use in MCP configuration.
     */
    private fun getCurrentIdeName(): String {
        val appInfo = ApplicationInfo.getInstance()
        val productName = appInfo.versionName
        
        return when {
            productName.contains("IntelliJ IDEA") -> "intellij"
            productName.contains("PyCharm") -> "pycharm"
            productName.contains("WebStorm") -> "webstorm"
            productName.contains("CLion") -> "clion"
            productName.contains("PhpStorm") -> "phpstorm"
            productName.contains("RubyMine") -> "rubymine"
            productName.contains("GoLand") -> "goland"
            productName.contains("DataGrip") -> "datagrip"
            productName.contains("Rider") -> "rider"
            else -> "ide"
        }
    }
}
