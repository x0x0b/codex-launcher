package com.github.x0x0b.codexlauncher

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ApplicationManager

@Service(Service.Level.PROJECT)
class FileChangeService(private val project: Project) : Disposable {
    
    private val connection: MessageBusConnection = project.messageBus.connect(this)
    
    init {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    when (event) {
                        is VFileContentChangeEvent -> {
                            val file = event.file
                            if (isProjectFile(file.path)) {
                                openFileInEditor(file)
                            }
                        }
                        is VFileCreateEvent -> {
                            event.file?.let { file ->
                                if (isProjectFile(file.path)) {
                                    openFileInEditor(file)
                                }
                            }
                        }
                    }
                }
            }
        })
    }
    
    private fun isProjectFile(filePath: String): Boolean {
        val projectBasePath = project.basePath ?: return false
        return filePath.startsWith(projectBasePath) && !filePath.endsWith("/")
    }
    
    private fun openFileInEditor(file: com.intellij.openapi.vfs.VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
    
    override fun dispose() {
        // Connection is disposed automatically
    }
}
