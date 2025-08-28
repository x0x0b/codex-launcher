package com.github.x0x0b.codexlauncher

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.diagnostic.logger

/**
 * Service responsible for monitoring file changes and automatically opening them in the editor.
 * Tracks both version-controlled and untracked files within the project scope.
 */

@Service(Service.Level.PROJECT)
class FileOpenService(private val project: Project) : Disposable {
    
    private val logger = logger<FileOpenService>()
    
    companion object {
        /** Time to wait for VCS update detection (in milliseconds) */
        private const val VCS_UPDATE_WAIT_MS = 1500L
        
        /** Threshold for considering a file recently modified (30 seconds in milliseconds) */
        private const val FILE_LAST_EDIT_TIME_THRESHOLD_MS = 30000L
        
    }

    /**
     * Processes recently changed files and opens them in the editor if configured to do so.
     * This includes both tracked (version-controlled) and untracked (new) files.
     */
    fun processChangedFilesAndOpen() {
        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.invokeAfterUpdate({
            val thresholdTime = calculateThresholdTime()
            waitForVcsUpdate()
            
            val filesToOpen = mutableSetOf<VirtualFile>()
            collectTrackedChangedFiles(changeListManager, thresholdTime, filesToOpen)
            collectUntrackedFiles(changeListManager, thresholdTime, filesToOpen)
            openCollectedFiles(filesToOpen)
        }, InvokeAfterUpdateMode.SYNCHRONOUS_NOT_CANCELLABLE, null, null)
    }
    
    /**
     * Calculates the timestamp threshold for determining recently modified files.
     */
    private fun calculateThresholdTime(): Long {
        return System.currentTimeMillis() - FILE_LAST_EDIT_TIME_THRESHOLD_MS
    }
    
    /**
     * Waits for VCS operations to complete to ensure all changes are detected.
     */
    private fun waitForVcsUpdate() {
        try {
            Thread.sleep(VCS_UPDATE_WAIT_MS)
        } catch (e: InterruptedException) {
            logger.warn("VCS update wait was interrupted", e)
            Thread.currentThread().interrupt()
        }
    }
    
    /**
     * Collects version-controlled files that have been recently modified.
     */
    private fun collectTrackedChangedFiles(
        changeListManager: ChangeListManager,
        thresholdTime: Long,
        filesToOpen: MutableSet<VirtualFile>
    ) {
        val allChanges = changeListManager.allChanges
        for (change in allChanges) {
            val virtualFile = change.afterRevision?.file?.virtualFile
                ?: change.beforeRevision?.file?.virtualFile

            virtualFile?.let { file ->
                if (isRecentlyModifiedProjectFile(file, thresholdTime)) {
                    filesToOpen.add(file)
                }
            }
        }
    }
    
    /**
     * Collects untracked (new) files that have been recently created or modified.
     */
    private fun collectUntrackedFiles(
        changeListManager: ChangeListManager,
        thresholdTime: Long,
        filesToOpen: MutableSet<VirtualFile>
    ) {
        val untrackedFilePaths = changeListManager.unversionedFilesPaths
        for (untrackedPath in untrackedFilePaths) {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(untrackedPath.toString())
            virtualFile?.let { file ->
                if (isRecentlyModifiedProjectFile(file, thresholdTime)) {
                    filesToOpen.add(file)
                }
            }
        }
    }
    
    /**
     * Opens all collected files in the editor.
     */
    private fun openCollectedFiles(filesToOpen: Set<VirtualFile>) {
        for (file in filesToOpen) {
            openFileInEditor(file)
        }
    }
    
    /**
     * Checks if the file is a project file that has been recently modified.
     */
    private fun isRecentlyModifiedProjectFile(file: VirtualFile, thresholdTime: Long): Boolean {
        return isProjectFile(file.path) && !file.isDirectory && file.timeStamp >= thresholdTime
    }

    private fun isProjectFile(filePath: String): Boolean {
        val projectBasePath = project.basePath ?: return false
        return filePath.startsWith(projectBasePath) && !filePath.endsWith("/")
    }

    private fun openFileInEditor(file: VirtualFile) {
        try {
            val settings = service<CodexLauncherSettings>()
            if (!settings.state.openFileOnChange) {
                return
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    logger.debug("Project disposed, skipping file open for: ${file.path}")
                    return@invokeLater
                }
                try {
                    FileEditorManager.getInstance(project).openFile(file, true)
                    logger.debug("Opened file in editor: ${file.path}")
                } catch (e: Exception) {
                    logger.error("Failed to open file in editor: ${file.path}", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in openFileInEditor for file: ${file.path}", e)
        }
    }

    override fun dispose() {
    }
}
