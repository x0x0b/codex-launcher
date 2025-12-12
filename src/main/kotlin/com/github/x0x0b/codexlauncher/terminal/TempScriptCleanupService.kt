package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Service responsible for tracking and cleaning up temporary script files
 * created for Codex command execution within a project.
 *
 * Temporary scripts registered with this service will be deleted when the
 * project is closed, helping to prevent accumulation of unused files.
 */
@Service(Service.Level.PROJECT)
class TempScriptCleanupService {
    private val logger = logger<TempScriptCleanupService>()
    private val scriptPaths = Collections.synchronizedSet(mutableSetOf<Path>())

    /**
     * Registers a temporary script file for cleanup.
     *
     * @param path The path of the temporary script file to register
     */
    fun register(path: Path) {
        scriptPaths.add(path)
    }

    /**
     * Cleans up all registered temporary script files.
     *
     * This method attempts to delete each registered script file. If deletion
     * fails for any file, an informational log message is recorded.
     */
    fun cleanup() {
        val snapshot = scriptPaths.toList()
        snapshot.forEach { path ->
            runCatching { Files.deleteIfExists(path) }.onFailure {
                logger.info("Failed to delete Codex temporary script on project close: $path", it)
            }
        }
        scriptPaths.clear()
    }
}
