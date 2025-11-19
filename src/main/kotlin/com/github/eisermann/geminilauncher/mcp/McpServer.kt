package com.github.eisermann.geminilauncher.mcp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Service(Service.Level.APP)
class McpServer : Disposable {
    private val logger = logger<McpServer>()
    private var server: HttpServer? = null
    private val gson = Gson()
    private val activeSseConnections = ConcurrentHashMap.newKeySet<HttpExchange>()
    
    // Expose port
    var port: Int = 0
        private set

    companion object {
        private const val ENDPOINT = "/mcp"
        private const val THREAD_POOL_SIZE = 4  // Bounded thread pool for request handling
        private const val MAX_SSE_CONNECTIONS = 10  // Maximum concurrent SSE connections
    }

    @Synchronized
    fun start() {
        if (server != null) return

        try {
            server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            server?.createContext(ENDPOINT) { exchange ->
                handleRequest(exchange)
            }
            // Use bounded thread pool to prevent resource exhaustion
            server?.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
            server?.start()
            
            port = server?.address?.port ?: 0
            logger.info("MCP Server started on port $port")
            
            service<DiscoveryService>().createDiscoveryFile(port)
        } catch (e: Exception) {
            logger.error("Failed to start MCP Server", e)
        }
    }

    private fun handleRequest(exchange: HttpExchange) {
        try {
            if (!authorize(exchange)) {
                sendResponse(exchange, 401, "Unauthorized")
                return
            }

            when (exchange.requestMethod) {
                "GET" -> handleSseConnection(exchange)
                "POST" -> handleJsonRpc(exchange)
                else -> sendResponse(exchange, 405, "Method Not Allowed")
            }
        } catch (e: Exception) {
            logger.error("Error handling request", e)
            // If headers haven't been sent, try to send error
            try {
                sendResponse(exchange, 500, "Internal Server Error")
            } catch (ignore: Exception) {}
        }
    }

    private fun authorize(exchange: HttpExchange): Boolean {
        val authHeader = exchange.requestHeaders.getFirst("Authorization") ?: return false
        val token = service<DiscoveryService>().getAuthToken()
        return authHeader == "Bearer $token"
    }

    private fun handleSseConnection(exchange: HttpExchange) {
        // Enforce connection limit to prevent resource exhaustion
        if (activeSseConnections.size >= MAX_SSE_CONNECTIONS) {
            logger.warn("SSE connection limit reached (${MAX_SSE_CONNECTIONS}). Rejecting new connection.")
            sendResponse(exchange, 429, "Too many connections")
            return
        }

        exchange.responseHeaders.set("Content-Type", "text/event-stream")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        exchange.responseHeaders.set("Connection", "keep-alive")
        exchange.sendResponseHeaders(200, 0)

        activeSseConnections.add(exchange)
        
        // Keep connection open is handled implicitly by not closing responseBody immediately.
        // However, since we are in a cached thread pool, we must block here or 
        // else the handleRequest method returns and HttpServer might close the exchange?
        // HttpServer documentation says: "The exchange is considered to be closed if ... the handler returns without closing the exchange ... NO"
        // Actually, in com.sun.net.httpserver, if you don't close, it stays open?
        // Usually people block in a loop for SSE or use a different server model.
        // With basic HttpServer, we can just wait.
        
        // We'll just wait indefinitely until interruption or error.
        try {
             synchronized(exchange) {
                 (exchange as Object).wait()
             }
        } catch (e: InterruptedException) {
            // connection closed
        } catch (e: Exception) {
            logger.warn("SSE connection error", e)
        } finally {
            activeSseConnections.remove(exchange)
        }
    }

    private fun handleJsonRpc(exchange: HttpExchange) {
        val body = exchange.requestBody.reader(StandardCharsets.UTF_8).readText()
        logger.debug("Received JSON-RPC: $body")
        
        val request = JsonParser.parseString(body).asJsonObject
        val response = processRpc(request)
        
        val responseJson = gson.toJson(response)
        exchange.responseHeaders.set("Content-Type", "application/json")
        sendResponse(exchange, 200, responseJson)
    }

    private fun processRpc(request: JsonObject): JsonObject {
        val id = request.get("id")
        val method = request.get("method")?.asString ?: ""
        val params = request.get("params")?.asJsonObject

        // Basic JSON-RPC response structure
        val response = JsonObject()
        response.add("jsonrpc", gson.toJsonTree("2.0"))
        if (id != null) response.add("id", id)

        try {
            val result = when (method) {
                "initialize" -> handleInitialize(params)
                "notifications/initialized" -> null // No response needed usually, but we return null result
                "tools/list" -> handleToolsList()
                "tools/call" -> handleToolsCall(params)
                else -> throw IllegalArgumentException("Method not found: $method")
            }
            
            if (result != null) {
                response.add("result", result)
            }
        } catch (e: Exception) {
            val error = JsonObject()
            error.addProperty("code", -32603) // Internal error
            error.addProperty("message", e.message)
            response.add("error", error)
        }
        
        return response
    }

    private fun handleInitialize(params: JsonObject?): JsonObject {
        val result = JsonObject()
        result.addProperty("protocolVersion", "2024-11-05") // Use recent version
        val capabilities = JsonObject()
        capabilities.add("tools", JsonObject()) // We support tools
        // capabilities.add("resources", JsonObject()) 
        result.add("capabilities", capabilities)
        
        val serverInfo = JsonObject()
        serverInfo.addProperty("name", "GeminiLauncher")
        serverInfo.addProperty("version", "1.0.0")
        result.add("serverInfo", serverInfo)
        
        return result
    }

    private fun handleToolsList(): JsonObject {
        val result = JsonObject()
        val tools = com.google.gson.JsonArray()
        
        // openDiff
        val openDiff = JsonObject()
        openDiff.addProperty("name", "openDiff")
        openDiff.addProperty("description", "Open a diff view in the IDE")
        val openDiffSchema = JsonObject()
        openDiffSchema.addProperty("type", "object")
        val openDiffProps = JsonObject()
        openDiffProps.add("filePath", JsonObject().apply { addProperty("type", "string") })
        openDiffProps.add("newContent", JsonObject().apply { addProperty("type", "string") })
        openDiffSchema.add("properties", openDiffProps)
        openDiffSchema.add("required", gson.toJsonTree(listOf("filePath", "newContent")))
        openDiff.add("inputSchema", openDiffSchema)
        tools.add(openDiff)

        // closeDiff
        val closeDiff = JsonObject()
        closeDiff.addProperty("name", "closeDiff")
        closeDiff.addProperty("description", "Close a diff view in the IDE")
        val closeDiffSchema = JsonObject()
        closeDiffSchema.addProperty("type", "object")
        val closeDiffProps = JsonObject()
        closeDiffProps.add("filePath", JsonObject().apply { addProperty("type", "string") })
        closeDiffSchema.add("properties", closeDiffProps)
        closeDiffSchema.add("required", gson.toJsonTree(listOf("filePath")))
        closeDiff.add("inputSchema", closeDiffSchema)
        tools.add(closeDiff)
        
        result.add("tools", tools)
        return result
    }

    private fun handleToolsCall(params: JsonObject?): JsonObject {
        val name = params?.get("name")?.asString
        val args = params?.get("arguments")?.asJsonObject
        
        return when (name) {
            "openDiff" -> {
                val filePath = args?.get("filePath")?.asString ?: throw IllegalArgumentException("Missing filePath")
                val newContent = args?.get("newContent")?.asString ?: throw IllegalArgumentException("Missing newContent")
                service<DiffToolService>().openDiff(filePath, newContent)
            }
            "closeDiff" -> {
                val filePath = args?.get("filePath")?.asString ?: throw IllegalArgumentException("Missing filePath")
                service<DiffToolService>().closeDiff(filePath)
            }
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
    }

    fun sendNotification(method: String, params: Any) {
        val notification = JsonObject()
        notification.addProperty("jsonrpc", "2.0")
        notification.addProperty("method", method)
        notification.add("params", gson.toJsonTree(params))
        
        val data = "event: message\ndata: ${gson.toJson(notification)}\n\n"
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        
        val iterator = activeSseConnections.iterator()
        while (iterator.hasNext()) {
            val exchange = iterator.next()
            try {
                exchange.responseBody.write(bytes)
                exchange.responseBody.flush()
            } catch (e: Exception) {
                logger.warn("Failed to send notification, removing connection", e)
                iterator.remove()
                try { exchange.close() } catch (ignore: Exception) {}
            }
        }
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun dispose() {
        activeSseConnections.forEach {
            try {
                it.close()
                // Notify waiting threads
                synchronized(it) {
                    (it as Object).notifyAll()
                }
            } catch (e: Exception) {}
        }
        activeSseConnections.clear()
        server?.stop(0)
    }
}