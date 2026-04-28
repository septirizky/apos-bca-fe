package com.bandjak.pos.realtime

import android.os.Handler
import android.os.Looper
import com.bandjak.pos.api.ApiClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PosRealtimeSocket {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val listeners = mutableSetOf<(String) -> Unit>()
    private var webSocket: WebSocket? = null
    private var reconnectRunnable: Runnable? = null
    private var manuallyClosed = false

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)

        if (webSocket == null) {
            connect()
        }
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)

        if (listeners.isEmpty()) {
            disconnect()
        }
    }

    private fun connect() {
        manuallyClosed = false
        reconnectRunnable?.let(mainHandler::removeCallbacks)

        val request = Request.Builder()
            .url(ApiClient.getSocketUrl())
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = runCatching {
                    JSONObject(text).optString("type")
                }.getOrDefault("")

                if (type.isBlank()) return

                mainHandler.post {
                    listeners.toList().forEach { listener ->
                        listener(type)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@PosRealtimeSocket.webSocket = null
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@PosRealtimeSocket.webSocket = null
                if (!manuallyClosed) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun disconnect() {
        manuallyClosed = true
        reconnectRunnable?.let(mainHandler::removeCallbacks)
        reconnectRunnable = null
        webSocket?.close(1000, "No active listeners")
        webSocket = null
    }

    fun reconnect() {
        disconnect()
        if (listeners.isNotEmpty()) {
            connect()
        }
    }

    private fun scheduleReconnect() {
        if (manuallyClosed || listeners.isEmpty()) return

        reconnectRunnable?.let(mainHandler::removeCallbacks)
        reconnectRunnable = Runnable {
            if (!manuallyClosed && listeners.isNotEmpty() && webSocket == null) {
                connect()
            }
        }
        mainHandler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MILLIS)
    }

    private const val RECONNECT_DELAY_MILLIS = 3_000L
}
