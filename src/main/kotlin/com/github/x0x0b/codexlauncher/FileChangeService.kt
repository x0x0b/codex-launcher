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
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@Service(Service.Level.PROJECT)
class FileChangeService(private val project: Project) : Disposable {

    private val connection: MessageBusConnection = project.messageBus.connect(this)
    private var watchService: WatchService? = null
    private val executor = Executors.newSingleThreadExecutor(ThreadFactory { thread ->
        Thread(thread).apply {
            isDaemon = true
            name = "FileChangeWatcher-${project.name}"
        }
    })
    private var isWatching = true

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

        // 直接ファイルシステム監視を開始
        startNativeFileWatcher()
    }

    private fun startNativeFileWatcher() {
        val projectPath = project.basePath ?: return

        try {
            watchService = FileSystems.getDefault().newWatchService()
            val projectDir = Paths.get(projectPath)

            // プロジェクトディレクトリを再帰的に監視
            registerDirectoryTree(projectDir)

            executor.submit {
                try {
                    while (isWatching && !project.isDisposed) {
                        val key = watchService?.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                        if (key != null) {
                            for (event in key.pollEvents()) {
                                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE ||
                                    event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                                ) {
                                    val fileName = event.context() as Path
                                    val fullPath = (key.watchable() as Path).resolve(fileName)

                                    // ディレクトリの場合は新しく監視に追加
                                    if (Files.isDirectory(fullPath)) {
                                        registerDirectoryTree(fullPath)
                                    } else {
                                        handleFileChange(fullPath.toString())
                                    }
                                }
                            }
                            key.reset()
                        }
                    }
                } catch (e: Exception) {
                    // 監視終了時の例外は無視
                }
            }
        } catch (e: Exception) {
            // WatchService初期化失敗時はフォールバック
        }
    }

    private fun registerDirectoryTree(start: Path) {
        try {
            Files.walk(start).use { paths ->
                paths.filter { Files.isDirectory(it) }
                    .filter { !it.fileName.toString().startsWith(".") } // 隠しディレクトリを除外
                    .forEach { dir ->
                        try {
                            dir.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY
                            )
                        } catch (e: Exception) {
                            // 個別のディレクトリ登録失敗は無視
                        }
                    }
            }
        } catch (e: Exception) {
            // 登録失敗時は無視
        }
    }

    private fun handleFileChange(filePath: String) {
        if (isProjectFile(filePath)) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    // ファイルをVirtualFileSystemで取得してエディタで開く
                    LocalFileSystem.getInstance().findFileByPath(filePath)?.let { virtualFile ->
                        openFileInEditor(virtualFile)
                    }
                }
            }
        }
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
        isWatching = false
        try {
            watchService?.close()
        } catch (e: Exception) {
            // 無視
        }
        executor.shutdown()
    }
}
