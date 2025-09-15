package com.github.x0x0b.codexlauncher

import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HTTP server service that provides endpoints for triggering file refresh operations.
 * The server runs on a random available port and provides a /refresh endpoint for external notifications.
 */

@Service(Service.Level.APP)
class HttpTriggerService : Disposable {

    companion object {
        private const val DEFAULT_PORT = 0 // Use random available port
        private const val SERVER_SHUTDOWN_TIMEOUT_SECONDS = 1
        private const val LOCALHOST = "localhost"
        private const val REFRESH_ENDPOINT = "/refresh"
        private const val HTTP_METHOD_POST = "POST"
        private const val CONTENT_TYPE_PLAIN_TEXT = "text/plain; charset=utf-8"

        // HTTP status codes
        private const val HTTP_OK = 200
        private const val HTTP_METHOD_NOT_ALLOWED = 405
        private const val HTTP_INTERNAL_SERVER_ERROR = 500

        // Response messages
        private const val MSG_METHOD_NOT_ALLOWED = "Method not allowed. Use POST."
        private const val MSG_INTERNAL_ERROR = "Internal server error"
    }

    private var server: HttpServer? = null
    private val logger = logger<HttpTriggerService>()
    private var actualPort: Int = 0

    fun getActualPort(): Int = actualPort

    init {
        startHttpServer()
    }

    private fun startHttpServer() {
        try {
            // Use random available port
            server = HttpServer.create(InetSocketAddress(LOCALHOST, DEFAULT_PORT), 0)
            actualPort = server?.address?.port ?: 0

            // Endpoint to refresh file list
            server?.createContext(REFRESH_ENDPOINT) { exchange ->
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

            if (requestMethod == HTTP_METHOD_POST) {
                // Read request body
                val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                
                // Parse JSON and extract last-assistant-message
                val notificationMessage = try {
                    if (requestBody.isNotEmpty()) {
                        val json = Json.parseToJsonElement(requestBody) as JsonObject
                        json["last-assistant-message"]?.jsonPrimitive?.content ?: "Codex CLI processing completed."
                    } else {
                        "Codex CLI processing completed."
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse request body as JSON: ${e.message}")
                    "Codex CLI processing completed."
                }

                // Check for specific notification type in JSON
                if (requestBody.isNotEmpty()) {
                    try {
                        val json = Json.parseToJsonElement(requestBody) as JsonObject
                        val type = json["type"]?.jsonPrimitive?.content
                        if (type == "agent-turn-complete") {
                            // Process refresh in the main thread
                            ApplicationManager.getApplication().invokeLater {
                                processRefreshRequest(notificationMessage)
                            }
                        } else {
                            logger.warn("Ignoring notification with unsupported type: $type")
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }

                sendResponse(exchange, HTTP_OK, "")
                logger.info("File system refresh triggered via HTTP")
            } else {
                sendResponse(exchange, HTTP_METHOD_NOT_ALLOWED, MSG_METHOD_NOT_ALLOWED)
            }

        } catch (e: Exception) {
            logger.error("Error handling refresh request", e)
            sendResponse(exchange, HTTP_INTERNAL_SERVER_ERROR, MSG_INTERNAL_ERROR)
        }
    }

    private fun processRefreshRequest(notificationMessage: String) {
        // Refresh entire file system
        LocalFileSystem.getInstance().refresh(false)

        // Process changed files for all open projects
        val settings = service<CodexLauncherSettings>()
        val openProjects = ProjectManager.getInstance().openProjects
        for (project in openProjects) {
            if (!project.isDisposed) {
                try {
                    val fileOpenService = project.service<FileOpenService>()
                    val notificationService = project.service<NotificationService>()

                    // Send notification through IntelliJ
                    if (settings.state.enableNotification) {
                        notificationService.notifyRefreshReceived(notificationMessage)
                    }

                    // Process changed files and open
                    if (settings.state.openFileOnChange) {
                        fileOpenService.processChangedFilesAndOpen()
                    }

                    // Update last refresh time
                    fileOpenService.updateLastRefreshTime()
                } catch (e: Exception) {
                    logger.warn("Failed to process changed files for project ${project.name}: ${e.message}")
                    
                    // Send error notification
                    try {
                        val notificationService = project.service<NotificationService>()
                        notificationService.notifyRefreshError(e.message ?: "Unknown error")
                    } catch (notifyError: Exception) {
                        logger.error("Failed to send error notification", notifyError)
                    }
                }
            }
        }
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, response: String) {
        exchange.responseHeaders.set("Content-Type", CONTENT_TYPE_PLAIN_TEXT)
        exchange.sendResponseHeaders(statusCode, response.toByteArray(Charsets.UTF_8).size.toLong())
        exchange.responseBody.use {
            it.write(response.toByteArray(Charsets.UTF_8))
        }
    }

    override fun dispose() {
        try {
            server?.stop(SERVER_SHUTDOWN_TIMEOUT_SECONDS)
            logger.info("HTTP Trigger Server stopped")
        } catch (e: Exception) {
            logger.error("Error stopping HTTP server", e)
        }
    }
}
