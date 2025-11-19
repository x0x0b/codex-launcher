package com.github.x0x0b.geminilauncher.startup

import com.github.x0x0b.geminilauncher.files.FileOpenService
import com.github.x0x0b.geminilauncher.http.HttpTriggerService
import com.github.x0x0b.geminilauncher.mcp.IdeContextTracker
import com.github.x0x0b.geminilauncher.mcp.McpServer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity that initializes essential services when a project is opened.
 * 
 * This activity ensures that both FileOpenService and HttpTriggerService are properly
 * initialized and ready to handle file monitoring and HTTP requests.
 * 
 * It also initializes the MCP Server and Context Tracker for the Gemini CLI Companion spec.
 * 
 * @since 1.0.0
 */

class GeminiStartupActivity : StartupActivity {
    
    /**
     * Runs the startup activity for the given project.
     */
    override fun runActivity(project: Project) {
        // Explicitly initialize core services
        project.getService(FileOpenService::class.java)

        // HttpTriggerService is application-level (Legacy Launcher support)
        ApplicationManager.getApplication().getService(HttpTriggerService::class.java)

        // MCP Server (Gemini Companion Spec)
        // Use synchronized block to prevent race condition when multiple projects open simultaneously
        synchronized(startLock) {
            if (!isMcpServerStarted) {
                val mcpServer = ApplicationManager.getApplication().service<McpServer>()
                mcpServer.start()
                isMcpServerStarted = true
            }
        }

        // Attach context tracker listeners for this project
        val contextTracker = ApplicationManager.getApplication().service<IdeContextTracker>()
        contextTracker.attachProjectListeners(project)
    }

    companion object {
        private val startLock = Any()
        @Volatile
        private var isMcpServerStarted = false
    }
}
