package de.simon.dankelmann.bluetoothlespam.Services

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpPairingServer(private val context: Context, private val port: Int = 8080) {
    private val _logTag = "HttpPairingServer"
    @Volatile private var running = false
    private var serverThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        serverThread = thread(start = true) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(port))
                Log.i(_logTag, "HTTP pairing server listening on port $port")

                while (running) {
                    val client = server.accept()
                    handleClient(client)
                }

                server.close()
            } catch (ex: Exception) {
                Log.e(_logTag, "Server error", ex)
            }
        }
    }

    fun stop() {
        running = false
        serverThread?.interrupt()
    }

    private fun handleClient(client: Socket) {
        thread(start = true) {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine() ?: ""
                Log.i(_logTag, "Request: $requestLine")

                // Simple routing: GET /pairAll  or /stopPair
                val parts = requestLine.split(" ")
                var path = "/"
                if (parts.size >= 2) path = parts[1]

                val response = when (path) {
                    "/pairAll" -> {
                        PairAllReceiver().also { it.onReceive(context, null) }
                        "Triggered continuous pairing"
                    }
                    "/stopPair" -> {
                        PairAllReceiver().also { it.onReceive(context, null) }
                        "Stopped continuous pairing"
                    }
                    else -> "OK"
                }

                val out: OutputStream = client.getOutputStream()
                val body = response.toByteArray()
                val header = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\n\r\n"
                out.write(header.toByteArray())
                out.write(body)
                out.flush()
                client.close()
            } catch (ex: Exception) {
                Log.e(_logTag, "Error handling client", ex)
            }
        }
    }
}
