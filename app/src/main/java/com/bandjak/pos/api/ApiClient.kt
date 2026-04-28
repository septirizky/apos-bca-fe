package com.bandjak.pos.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"
    private const val PREFS_NAME = "apos_api_config"
    private const val KEY_BASE_URL = "base_url"

    private var appContext: Context? = null
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var configuredBaseUrl: String = DEFAULT_BASE_URL

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
        val storedBaseUrl = normalizeBaseUrl(
            appContext
                ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
                ?: DEFAULT_BASE_URL
        )
        if (storedBaseUrl != configuredBaseUrl) {
            configuredBaseUrl = storedBaseUrl
            retrofit = null
        }
    }

    val api: PosApi
        get() = getRetrofit().create(PosApi::class.java)

    fun getBaseUrl(): String = configuredBaseUrl

    fun getSocketUrl(): String {
        val base = configuredBaseUrl.trimEnd('/')
        return when {
            base.startsWith("https://", ignoreCase = true) ->
                "wss://${base.removePrefix("https://")}/realtime"
            base.startsWith("http://", ignoreCase = true) ->
                "ws://${base.removePrefix("http://")}/realtime"
            else -> "ws://$base/realtime"
        }
    }

    fun saveBaseUrl(context: Context, baseUrl: String) {
        init(context)
        configuredBaseUrl = normalizeBaseUrl(baseUrl)
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_BASE_URL, configuredBaseUrl)
            ?.apply()
        retrofit = null
    }

    fun resetBaseUrl(context: Context) {
        saveBaseUrl(context, DEFAULT_BASE_URL)
    }

    fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim()
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }

        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    private fun getRetrofit(): Retrofit {
        retrofit?.let { return it }

        return synchronized(this) {
            retrofit?.let { return@synchronized it }

            Retrofit.Builder()
            .baseUrl(configuredBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
                .also { retrofit = it }
        }
    }
}
