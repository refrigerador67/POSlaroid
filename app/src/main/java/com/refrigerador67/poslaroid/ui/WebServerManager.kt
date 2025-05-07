package com.refrigerador67.poslaroid.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

class WebServerManager private constructor(
    private val context: Context,
    initialPort: Int,
    private val onPrintRequest: (Bitmap, String?) -> Unit
) : NanoHTTPD(initialPort) {

    private val currentPort = AtomicInteger(initialPort)
    private var isRunning = false

    companion object {
        private const val TAG = "WebServerManager"
        private const val DEFAULT_PORT = 8080
        private const val MAX_PORT_ATTEMPTS = 10
        private const val PORT_INCREMENT = 1

        @Volatile
        private var instance: WebServerManager? = null

        fun getInstance(
            context: Context,
            port: Int = DEFAULT_PORT,
            onPrintRequest: (Bitmap, String?) -> Unit
        ): WebServerManager {
            return instance ?: synchronized(this) {
                instance ?: WebServerManager(context, port, onPrintRequest).also { instance = it }
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/" -> serveUploadPage(session)
            "/upload" -> handleImageUpload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun serveUploadPage(session: IHTTPSession): Response {
        val inputStream = context.assets.open("upload.html")
        return newChunkedResponse(
            Response.Status.OK,
            "text/html",
            inputStream
        )
    }

    private fun handleImageUpload(session: IHTTPSession): Response {
        if (session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "POST method required"
            )
        }

        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
            
            val tempFile = files["image"]
            val customText = session.parameters["text"]?.get(0)
            
            if (tempFile != null) {
                val bitmap = BitmapFactory.decodeFile(tempFile)
                if (bitmap != null) {
                    onPrintRequest(bitmap, customText)
                    return newFixedLengthResponse("Image received and printing started")
                }
            }
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "No image received"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing upload request", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error processing request: ${e.message}"
            )
        }
    }

    @Synchronized
    override fun start() {
        if (isRunning) {
            Log.w(TAG, "Server is already running on port ${currentPort.get()}")
            return
        }

        var attemptCount = 0
        var started = false

        while (!started && attemptCount < MAX_PORT_ATTEMPTS) {
            try {
                super.start(SOCKET_READ_TIMEOUT, false)
                started = true
                isRunning = true
                Log.i(TAG, "Server started successfully on port ${currentPort.get()}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start server on port ${currentPort.get()}, trying next port", e)
                currentPort.addAndGet(PORT_INCREMENT)
                attemptCount++
            }
        }

        if (!started) {
            throw IllegalStateException("Failed to start server after $MAX_PORT_ATTEMPTS attempts")
        }
    }

    @Synchronized
    override fun stop() {
        super.stop()
        isRunning = false
        Log.i(TAG, "Server stopped")
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return "127.0.0.1"
    }

    fun getCurrentPort(): Int = currentPort.get()

    fun isServerRunning(): Boolean = isRunning

    fun changePort(newPort: Int): Boolean {
        if (isRunning) {
            stop()
        }
        currentPort.set(newPort)
        try {
            start()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change port to $newPort", e)
            // Try to restart on the old port
            try {
                currentPort.set(currentPort.get() - PORT_INCREMENT)
                start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart server on original port", e)
            }
            return false
        }
    }
}
