package com.github.x0x0b.codexlauncher.startup

import com.github.x0x0b.codexlauncher.files.FileOpenService
import com.github.x0x0b.codexlauncher.http.HttpTriggerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that initializes essential services when a project is opened.
 * 
 * This activity ensures that both FileOpenService and HttpTriggerService are properly
 * initialized and ready to handle file monitoring and HTTP requests.
 * 
 * @since 1.0.0
 */

class CodexStartupActivity : ProjectActivity {
    
    /**
     * Runs the startup activity for the given project.
     * 
     * This method explicitly initializes the core services required for the plugin functionality:
     * - FileOpenService: Monitors file changes and opens them in the editor
     * - HttpTriggerService: Provides HTTP endpoints for external notifications
     * 
     * @param project The project being opened
     */
    override suspend fun execute(project: Project) {
        // Explicitly initialize core services
        project.getService(FileOpenService::class.java)
        // HttpTriggerService is application-level, so initialize it via ApplicationManager
        ApplicationManager.getApplication().getService(HttpTriggerService::class.java)
    }
}
