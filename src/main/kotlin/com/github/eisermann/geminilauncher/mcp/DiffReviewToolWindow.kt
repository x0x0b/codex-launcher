package com.github.eisermann.geminilauncher.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.icons.AllIcons

/**
 * Project-level service that manages a tool window for displaying diff reviews
 * with Accept/Reject buttons.
 */
@Service(Service.Level.PROJECT)
class DiffReviewToolWindow(private val project: Project) {
    private val logger = logger<DiffReviewToolWindow>()
    private var currentPanel: AcceptRejectDiffPanel? = null

    companion object {
        const val TOOL_WINDOW_ID = "Gemini Diff Review"
    }

    fun showDiff(filePath: String, newContent: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val toolWindowManager = ToolWindowManager.getInstance(project)
            var toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)

            // Register tool window if it doesn't exist
            if (toolWindow == null) {
                toolWindow = toolWindowManager.registerToolWindow(
                    RegisterToolWindowTask(
                        id = TOOL_WINDOW_ID,
                        icon = AllIcons.Actions.Diff,
                        canCloseContent = true,
                        anchor = com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM
                    )
                )
            }

            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            if (virtualFile == null) {
                logger.warn("File not found: $filePath")
                return@invokeLater
            }

            // Dispose previous panel if exists
            currentPanel?.dispose()

            // Create custom diff panel with Accept/Reject actions
            val panel = AcceptRejectDiffPanel(
                project,
                filePath,
                newContent,
                onAccept = {
                    logger.info("User accepted changes for: $filePath")
                    sendMcpNotification("diffAccepted", filePath)
                    toolWindow.hide()
                },
                onReject = {
                    logger.info("User rejected changes for: $filePath")
                    sendMcpNotification("diffRejected", filePath)
                    toolWindow.hide()
                }
            )

            panel.createPanel()
            currentPanel = panel

            // Add content to tool window
            val content = ContentFactory.getInstance().createContent(
                panel.component,
                virtualFile.name,
                false
            )

            toolWindow.contentManager.removeAllContents(true)
            toolWindow.contentManager.addContent(content)
            toolWindow.show()

            logger.info("Showing diff review for: $filePath")
        }
    }

    private fun sendMcpNotification(eventType: String, filePath: String) {
        try {
            val mcpServer = ApplicationManager.getApplication().getService(McpServer::class.java)
            mcpServer.sendNotification(eventType, mapOf("filePath" to filePath))
        } catch (e: Exception) {
            logger.warn("Failed to send MCP notification: $eventType for $filePath", e)
        }
    }
}
