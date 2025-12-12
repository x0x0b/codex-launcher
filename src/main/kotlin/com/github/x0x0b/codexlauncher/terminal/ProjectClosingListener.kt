package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Listener that triggers cleanup of temporary Codex scripts when a project is closing.
 */
class ProjectClosingListener : ProjectManagerListener {
    /**
     * Invoked when a project is closing.
     *
     * This method retrieves the TempScriptCleanupService for the given project
     * and calls its cleanup method to remove any temporary scripts created during
     * the project's lifecycle.
     *
     * @param project The project that is closing
     */
    override fun projectClosing(project: Project) {
        project.getService(TempScriptCleanupService::class.java)?.cleanup()
    }
}
