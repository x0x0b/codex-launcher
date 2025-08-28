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

@Service(Service.Level.PROJECT)
class FileOpenService(private val project: Project) : Disposable {

    fun processChangedFilesAndOpen() {
        val changeListManager = ChangeListManager.getInstance(project)
        
        // IntelliJの変更検知を更新
        LocalFileSystem.getInstance().refresh(false)
        
        val filesToOpen = mutableSetOf<VirtualFile>()
        
        // 1. 追跡済みファイルの変更を取得
        val currentTime = System.currentTimeMillis()
        val threeSecondsAgo = currentTime - 3000
        
        val allChanges = changeListManager.allChanges
        for (change in allChanges) {
            val virtualFile = when {
                change.afterRevision?.file?.virtualFile != null -> change.afterRevision?.file?.virtualFile
                change.beforeRevision?.file?.virtualFile != null -> change.beforeRevision?.file?.virtualFile
                else -> null
            }
            
            virtualFile?.let { file ->
                if (isProjectFile(file.path) && !file.isDirectory && file.timeStamp >= threeSecondsAgo) {
                    filesToOpen.add(file)
                }
            }
        }
        
        // 2. 未追跡ファイル（新規ファイル）を取得
        val untrackedFilePaths = changeListManager.unversionedFilesPaths
        for (untrackedPath in untrackedFilePaths) {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(untrackedPath.toString())
            virtualFile?.let { file ->
                if (isProjectFile(file.path) && !file.isDirectory && file.timeStamp >= threeSecondsAgo) {
                    filesToOpen.add(file)
                }
            }
        }
        
        // 3. 検出したファイルをエディタで開く
        for (file in filesToOpen) {
            openFileInEditor(file)
        }
    }


    private fun isProjectFile(filePath: String): Boolean {
        val projectBasePath = project.basePath ?: return false
        return filePath.startsWith(projectBasePath) && !filePath.endsWith("/")
    }

    private fun openFileInEditor(file: VirtualFile) {
        val settings = service<CodexLauncherSettings>()
        if (!settings.state.openFileOnChange) {
            return
        }
        
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    override fun dispose() {
    }
}
