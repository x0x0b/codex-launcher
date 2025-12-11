package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Builds terminal execution plans for Codex commands, falling back to temporary scripts for
 * long inputs that would otherwise be truncated by the terminal widget.
 *
 * Responsibilities:
 * - Choose between inline execution and script execution based on command length.
 * - Render platform-specific scripts from templates and mark them executable where applicable.
 * - Track temporary scripts for cleanup via [TempScriptCleanupService] and JVM shutdown.
 */
internal class CommandScriptFactory(
    private val project: Project,
    private val isWindows: Boolean = SystemInfoRt.isWindows,
    cleanupService: TempScriptCleanupService? = runCatching {
        project.getService(TempScriptCleanupService::class.java)
    }.getOrNull()
) {

    companion object {
        internal const val MAX_INLINE_COMMAND_LENGTH = 1024
        private const val SCRIPT_PREFIX = "codex-cmd-"
        private const val SCRIPT_SUFFIX_POSIX = ".sh"
        private const val SCRIPT_SUFFIX_WINDOWS = ".ps1"
        private const val POWERSHELL_FLAGS = "-NoLogo -NoProfile -ExecutionPolicy Bypass -File"
        private const val TEMPLATE_POSIX = "/scripts/codex_command_posix.sh"
        private const val TEMPLATE_WINDOWS = "/scripts/codex_command_windows.ps1"
    }

    private val logger = logger<CommandScriptFactory>()
    private val scriptCleanupService = cleanupService

    /**
     * Builds a terminal command plan. Returns the command to execute directly when below the
     * inline threshold; otherwise writes a platform-specific script and returns a command to run it.
     *
     * @param command full command string to run
     * @return [TerminalCommandPlan] describing how to execute the command, or null if setup failed
     */
    internal fun buildPlan(command: String): TerminalCommandPlan? {
        if (command.length <= MAX_INLINE_COMMAND_LENGTH) {
            return TerminalCommandPlan(command)
        }

        val scriptPath = createCommandScript(command) ?: return null
        val pathString = scriptPath.toAbsolutePath().toString()
        val planCommand = if (isWindows) {
            val psQuotedPath = quoteForPowershell(pathString)
            "powershell $POWERSHELL_FLAGS $psQuotedPath"
        } else {
            val escapedPath = quoteForPosix(pathString)
            "sh $escapedPath"
        }

        return TerminalCommandPlan(planCommand) {
            runCatching { Files.deleteIfExists(scriptPath) }.onFailure {
                logger.info("Failed to delete temporary Codex script after dispatch failure", it)
            }
        }
    }

    /**
     * Creates a temporary script file containing the given command, based on platform-specific templates.
     * Registers the script for cleanup and marks it executable if applicable.
     *
     * @param command full command string to include in the script
     * @return path to the created script, or null if creation failed
     */
    private fun createCommandScript(command: String): Path? {
        val suffix = if (isWindows) SCRIPT_SUFFIX_WINDOWS else SCRIPT_SUFFIX_POSIX
        val scriptPath = runCatching { Files.createTempFile(SCRIPT_PREFIX, suffix) }.getOrElse {
            logger.warn("Failed to create temporary Codex command script", it)
            return null
        }
        runCatching { scriptCleanupService?.register(scriptPath) } // Register for project cleanup
            .onFailure { logger.warn("Failed to register temporary Codex command cleanup: $scriptPath", it) }
        runCatching { scriptPath.toFile().deleteOnExit() } // Remove on JVM exit as a last resort
            .onFailure { logger.warn("Failed to mark temporary Codex script for JVM exit deletion: $scriptPath", it) }

        val writeResult = runCatching {
            val resourcePath = if (isWindows) TEMPLATE_WINDOWS else TEMPLATE_POSIX
            val script = readTemplate(resourcePath, command) ?: return null
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8)
        }

        if (writeResult.isFailure) {
            logger.warn("Failed to write temporary Codex command script", writeResult.exceptionOrNull())
            runCatching { Files.deleteIfExists(scriptPath) }
            return null
        }

        return scriptPath
    }

    /**
     * Reads a script template resource and injects the given command.
     *
     * @param resourcePath path to the template resource
     * @param command command string to inject
     * @return rendered script content, or null if reading failed
     */
    private fun readTemplate(resourcePath: String, command: String): String? {
        val stream = javaClass.getResourceAsStream(resourcePath)
        if (stream == null) {
            logger.warn("Template resource not found: $resourcePath")
            return null
        }

        return runCatching {
            stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val template = reader.readText()
                String.format(Locale.ROOT, template, command)
            }
        }.getOrElse {
            logger.warn("Failed to read template resource: $resourcePath", it)
            null
        }
    }

    private fun quoteForPosix(path: String): String = "'" + path.replace("'", "'\"'\"'") + "'"

    private fun quoteForPowershell(path: String): String = "'" + path.replace("'", "''") + "'"
}

internal data class TerminalCommandPlan(val command: String, val cleanupOnFailure: () -> Unit = {})
