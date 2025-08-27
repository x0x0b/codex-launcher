package com.github.x0x0b.codexlauncher

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings

@Service(Service.Level.PROJECT)
class FileOpenService(private val project: Project) : Disposable {

    fun processChangedFilesAndOpen() {
        val changeListManager = ChangeListManager.getInstance(project)
        
        // IntelliJの変更検知を更新
        LocalFileSystem.getInstance().refresh(false)
        
        // 変更されたファイル一覧を取得
        val allChanges = changeListManager.allChanges
        
        for (change in allChanges) {
            val virtualFile = when {
                change.afterRevision?.file?.virtualFile != null -> change.afterRevision?.file?.virtualFile
                change.beforeRevision?.file?.virtualFile != null -> change.beforeRevision?.file?.virtualFile
                else -> null
            }
            
            virtualFile?.let { file ->
                if (isProjectFile(file.path) && !file.isDirectory) {
                    openFileInEditor(file)
                }
            }
        }
    }


    private fun isProjectFile(filePath: String): Boolean {
        val projectBasePath = project.basePath ?: return false
        return filePath.startsWith(projectBasePath) && !filePath.endsWith("/")
    }

    private fun openFileInEditor(file: com.intellij.openapi.vfs.VirtualFile) {
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
