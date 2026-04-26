package com.bandjak.pos.util

import android.os.Handler
import android.os.Looper

class AutoRefreshTimer(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val onRefresh: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            onRefresh()
            handler.postDelayed(this, intervalMillis)
        }
    }

    fun start() {
        if (running) return

        running = true
        handler.postDelayed(refreshRunnable, intervalMillis)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(refreshRunnable)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 10_000L
    }
}
