package com.github.eisermann.geminilauncher.mcp

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Creates a diff panel with Accept/Reject buttons using DiffRequestPanel.
 */
class AcceptRejectDiffPanel(
    private val project: Project,
    private val filePath: String,
    private val newContent: String,
    private val onAccept: () -> Unit,
    private val onReject: () -> Unit
) {
    private val logger = logger<AcceptRejectDiffPanel>()
    private val mainPanel = JPanel(BorderLayout())
    private var diffPanel: DiffRequestPanel? = null

    val component: JPanel
        get() = mainPanel

    fun createPanel() {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (virtualFile == null) {
            logger.warn("File not found: $filePath")
            return
        }

        // Create diff request
        val contentFactory = DiffContentFactory.getInstance()
        val currentContent = contentFactory.create(project, virtualFile)
        val proposedContent = contentFactory.create(newContent)

        val request = SimpleDiffRequest(
            "Review Changes: ${virtualFile.name}",
            currentContent,
            proposedContent,
            "Current",
            "Proposed"
        )

        // Create diff panel
        diffPanel = DiffManager.getInstance().createRequestPanel(project, {
            // Disposer callback
            logger.debug("Diff panel disposed for: $filePath")
        }, null)

        diffPanel?.setRequest(request)

        // Create toolbar with Accept/Reject buttons
        val toolbar = createToolbar()

        // Add components to main panel
        mainPanel.add(toolbar.component, BorderLayout.NORTH)
        diffPanel?.component?.let { mainPanel.add(it, BorderLayout.CENTER) }
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup()

        // Add Accept action
        actionGroup.add(object : DumbAwareAction(
            "Accept Changes",
            "Apply the proposed changes to the file",
            AllIcons.Actions.Commit
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                acceptChanges()
            }
        })

        // Add Reject action
        actionGroup.add(object : DumbAwareAction(
            "Reject Changes",
            "Discard the proposed changes",
            AllIcons.Actions.Cancel
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                rejectChanges()
            }
        })

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "GeminiDiffReview",
            actionGroup,
            true
        )

        toolbar.targetComponent = mainPanel
        return toolbar
    }

    private fun acceptChanges() {
        ApplicationManager.getApplication().runWriteAction {
            try {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (virtualFile != null) {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    if (document != null) {
                        document.setText(newContent)
                        FileDocumentManager.getInstance().saveDocument(document)
                        logger.info("Applied changes to file: $filePath")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to apply changes to file: $filePath", e)
            }
        }
        onAccept()
    }

    private fun rejectChanges() {
        logger.info("Rejected changes for file: $filePath")
        onReject()
    }

    fun dispose() {
        diffPanel?.let { Disposer.dispose(it) }
    }
}
