package com.github.x0x0b.codexlauncher

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import java.net.InetSocketAddress
import java.util.concurrent.Executors

@Service(Service.Level.APP)
class HttpTriggerService : Disposable {
    
    private var server: HttpServer? = null
    private val logger = logger<HttpTriggerService>()
    
    private var actualPort: Int = 0
    
    fun getActualPort(): Int = actualPort
    
    init {
        startHttpServer()
    }
    
    private fun startHttpServer() {
        try {
            // ポート0を指定してランダムポートを使用
            server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            actualPort = server?.address?.port ?: 0
            
            // /refresh エンドポイント - ファイル一覧をリフレッシュ
            server?.createContext("/refresh") { exchange ->
                handleRefreshRequest(exchange)
            }
            
            server?.executor = Executors.newCachedThreadPool()
            server?.start()
            
            logger.info("HTTP Trigger Server started on http://localhost:$actualPort")
            logger.info("Available endpoints:")
            logger.info("  POST http://localhost:$actualPort/refresh - Refresh file list")
            
        } catch (e: Exception) {
            logger.error("Failed to start HTTP server: ${e.message}", e)
        }
    }
    
    private fun handleRefreshRequest(exchange: HttpExchange) {
        try {
            val requestMethod = exchange.requestMethod
            
            if (requestMethod == "POST") {
                ApplicationManager.getApplication().invokeLater {
                    refreshFileSystem()
                }
                
                sendResponse(exchange, 200, "")
                logger.info("File system refresh triggered via HTTP")
            } else {
                sendResponse(exchange, 405, "Method not allowed. Use POST.")
            }
            
        } catch (e: Exception) {
            logger.error("Error handling refresh request", e)
            sendResponse(exchange, 500, "Internal server error")
        }
    }

    private fun refreshFileSystem() {
        // ファイルシステム全体をリフレッシュ
        LocalFileSystem.getInstance().refresh(false)
        
        // 全ての開いているプロジェクトに対して変更されたファイルを処理
        val openProjects = ProjectManager.getInstance().openProjects
        for (project in openProjects) {
            if (!project.isDisposed) {
                try {
                    val fileOpenService = project.service<FileOpenService>()
                    fileOpenService.processChangedFilesAndOpen()
                } catch (e: Exception) {
                    logger.warn("Failed to process changed files for project ${project.name}: ${e.message}")
                }
            }
        }
        
        logger.info("File system refresh and changed files processing completed for ${openProjects.size} projects")
    }
    
    private fun sendResponse(exchange: HttpExchange, statusCode: Int, response: String) {
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, response.toByteArray(Charsets.UTF_8).size.toLong())
        exchange.responseBody.use { 
            it.write(response.toByteArray(Charsets.UTF_8)) 
        }
    }
    
    override fun dispose() {
        try {
            server?.stop(1)
            logger.info("HTTP Trigger Server stopped")
        } catch (e: Exception) {
            logger.error("Error stopping HTTP server", e)
        }
    }
}
