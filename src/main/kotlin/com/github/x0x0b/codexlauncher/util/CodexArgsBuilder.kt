package com.github.x0x0b.codexlauncher.util

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.settings.Model
import com.github.x0x0b.codexlauncher.settings.Mode
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import groovy.json.StringEscapeUtils
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import java.util.*

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
        var parts = mutableListOf<String>()

        if (state.mode == Mode.FULL_AUTO) {
            parts += "--full-auto"
        }

        var modelName: String? = null
        if (state.model == Model.DEFAULT) {
            modelName = null
        } else if (state.model == Model.CUSTOM) {
            modelName = state.customModel.trim().ifBlank { null }
        } else {
            modelName = state.model.cliName()
        }

        if (modelName != null) {
            parts += listOf("--model", "'${modelName}'")
        }

        if (port != null) {
            parts += buildNotifyCommand(port, state.isPowerShell73OrOver, osProvider)
        }

        parts += buildMcpConfigArgs(state.mcpConfigInput, state.isPowerShell73OrOver, osProvider)

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
    private fun buildMcpConfigArgs(mcpConfigInput: String, isPowerShell73OrOver: Boolean, osProvider: OsProvider = DefaultOsProvider): List<String> {
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

            if (command.isNotEmpty()) {
                args += createConfigArgument("mcp_servers.$ideaName.command", command, osProvider)
            }

            if (argsArray != null && argsArray.size() > 0) {
                val argsList = formatArgsArray(argsArray, isPowerShell73OrOver, osProvider)
                args += createConfigArgument("mcp_servers.$ideaName.args", "[$argsList]", osProvider)
            }

            if (env != null && env.size() > 0) {
                val envMap = formatEnvObject(env, isPowerShell73OrOver, osProvider)
                args += createConfigArgument("mcp_servers.$ideaName.env", envMap!!, osProvider)
            }

            args
        } catch (exception: JsonSyntaxException) {
            println("JSON syntax error: ${exception.message}")
            emptyList()
        } catch (exception: Exception) {
            println("Unexpected error: ${exception.message}")
            emptyList()
        }
    }

    /**
     * Creates a configuration argument with proper OS-specific formatting.
     */
    private fun createConfigArgument(key: String, value: String, osProvider: OsProvider = DefaultOsProvider): List<String> {
        val configValue = if (osProvider.isWindows) {
            // Windows
            "$key='$value'"
        } else {
            // Non-Windows
            "'$key=$value'"
        }
        return listOf("-c", configValue)
    }

    /**
     * Formats JSON args array for the target OS.
     */
    private fun formatArgsArray(argsArray: JsonArray, isPowerShell73OrOver: Boolean = false, osProvider: OsProvider = DefaultOsProvider): String {
        return if (osProvider.isWindows) {
            if (isPowerShell73OrOver) {
                // PowerShell 7.3+ on Windows
                argsArray.joinToString(", ") { "\"${StringEscapeUtils.escapeJava(it.asString)}\"" }
            } else {
                // Pre PowerShell 7.3 on Windows
                argsArray.joinToString(", ") { "\\\"${StringEscapeUtils.escapeJava(it.asString)}\\\"" }
            }
        } else {
            // Non-Windows OS
            argsArray.joinToString(", ") { "\"${it.asString}\"" }
        }
    }

    /**
     * Formats JSON env object for the target OS.
     */
    private fun formatEnvObject(env: com.google.gson.JsonObject, isPowerShell73OrOver: Boolean = false, osProvider: OsProvider = DefaultOsProvider): String? {
        val envMap = env.entrySet().associate { it.key to it.value.asString }.toMutableMap()
        
        if (osProvider.isWindows && !envMap.containsKey("SystemRoot")) {
            envMap["SystemRoot"] = "C:\\\\Windows"
        }
        
        var result: String? = null
        try {
            val envEntries = if (osProvider.isWindows && !isPowerShell73OrOver) {
                envMap.map { "\\\"${it.key}\\\"=\\\"${it.value}\\\"" }
            } else {
                envMap.map { "\"${it.key}\"=\"${it.value}\"" }
            }
            result = "{${envEntries.joinToString(",")}}"
        } catch (e: Exception) {
            result = "{}"
        }
        return result
    }

    /**
     * Builds notify command configuration with proper OS-specific formatting.
     * 
     * @param port The HTTP service port for notifications
     * @return Formatted notify command string
     */
    fun buildNotifyCommand(port: Int, isPowerShell73OrOver: Boolean, osProvider: OsProvider = DefaultOsProvider): List<String> {
        val curlArgs = JsonArray()
        curlArgs.add(JsonPrimitive("curl"))
        curlArgs.add(JsonPrimitive("-s"))
        curlArgs.add(JsonPrimitive("-X"))
        curlArgs.add(JsonPrimitive("POST"))
        curlArgs.add(JsonPrimitive("http://localhost:$port/refresh"))
        curlArgs.add(JsonPrimitive("-d"))
        
        val formattedArgs = formatArgsArray(curlArgs, isPowerShell73OrOver, osProvider)
        val configArgs = createConfigArgument("notify", "[$formattedArgs]", osProvider)
        return configArgs
    }

    /**
     * Gets the current IDE name from ApplicationInfo for use in MCP configuration.
     */
    private fun getCurrentIdeName(): String {
        val appInfo = ApplicationInfo.getInstance()
        val productName = appInfo.versionName
        
        if (productName.contains("IntelliJ IDEA")) {
            return "intellij"
        } else if (productName.contains("PyCharm")) {
            return "pycharm"
        } else if (productName.contains("WebStorm")) {
            return "webstorm"
        } else if (productName.contains("CLion")) {
            return "clion"
        } else if (productName.contains("PhpStorm")) {
            return "phpstorm"
        } else if (productName.contains("RubyMine")) {
            return "rubymine"
        } else if (productName.contains("GoLand")) {
            return "goland"
        } else if (productName.contains("DataGrip")) {
            return "datagrip"
        } else if (productName.contains("Rider")) {
            return "rider"
        } else {
            return "ide"
        }
    }
}
