package com.github.x0x0b.geminilauncher.mcp

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.io.path.absolutePathString

@Service(Service.Level.APP)
class DiscoveryService : Disposable {
    private val logger = logger<DiscoveryService>()
    private val gson = Gson()
    private var discoveryFile: File? = null
    private val authToken = UUID.randomUUID().toString()

    fun getAuthToken(): String = authToken

    fun createDiscoveryFile(port: Int) {
        try {
            val pid = ProcessHandle.current().pid()
            val tmpDir = System.getProperty("java.io.tmpdir")
            val geminiIdeDir = Path.of(tmpDir, "gemini", "ide")
            
            if (!Files.exists(geminiIdeDir)) {
                Files.createDirectories(geminiIdeDir)
            }

            val fileName = "gemini-ide-server-$pid-$port.json"
            val file = geminiIdeDir.resolve(fileName).toFile()

            val workspacePaths = getWorkspacePaths()
            
            val payload = mapOf(
                "port" to port,
                "workspacePath" to workspacePaths,
                "authToken" to authToken,
                "ideInfo" to mapOf(
                    "name" to "intellij",
                    "displayName" to ApplicationInfo.getInstance().fullApplicationName
                )
            )

            val json = gson.toJson(payload)
            file.writeText(json)
            file.deleteOnExit()
            
            // Try to set permissions to 600 (only owner can read/write)
            try {
                if (System.getProperty("os.name").lowercase().contains("win")) {
                    // Windows logic if needed, typically handled by ACLs but File.setReadable works basic
                    file.setReadable(true, true)
                    file.setWritable(true, true)
                } else {
                    Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
                }
            } catch (e: Exception) {
                logger.warn("Failed to set secure file permissions on discovery file", e)
            }

            discoveryFile = file
            logger.info("Created discovery file: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to create discovery file", e)
        }
    }

    private fun getWorkspacePaths(): String {
        val openProjects = ProjectManager.getInstance().openProjects
        val paths = openProjects.mapNotNull { it.basePath }
        // Spec says: delimited by the OS-specific path separator
        return paths.joinToString(File.pathSeparator)
    }

    override fun dispose() {
        try {
            discoveryFile?.delete()
            logger.info("Deleted discovery file")
        } catch (e: Exception) {
            logger.error("Failed to delete discovery file", e)
        }
    }
}
