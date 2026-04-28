package com.bandjak.pos.ui.settings

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityApiSettingsBinding
import com.bandjak.pos.model.DatabaseStatusResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ApiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        binding = ActivityApiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        binding.editBaseUrl.setText(ApiClient.getBaseUrl())
        updateSocketPreview()

        binding.editBaseUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSocketPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveConfig() }
        binding.btnResetDefault.setOnClickListener {
            binding.editBaseUrl.setText(ApiClient.DEFAULT_BASE_URL)
            saveConfig()
        }
    }

    private fun setupHeader() {
        binding.globalHeader.headerBackButton.visibility = View.VISIBLE
        binding.globalHeader.headerBackButton.setOnClickListener { finish() }
        binding.globalHeader.headerBranchName.text = "API Settings"
    }

    private fun updateSocketPreview() {
        val normalized = ApiClient.normalizeBaseUrl(binding.editBaseUrl.text.toString())
        val socket = when {
            normalized.startsWith("https://", ignoreCase = true) ->
                "wss://${normalized.trimEnd('/').removePrefix("https://")}/realtime"
            normalized.startsWith("http://", ignoreCase = true) ->
                "ws://${normalized.trimEnd('/').removePrefix("http://")}/realtime"
            else -> "ws://${normalized.trimEnd('/')}/realtime"
        }
        binding.txtResolvedSocket.text = "Realtime: $socket"
    }

    private fun saveConfig() {
        val rawUrl = binding.editBaseUrl.text.toString()
        if (rawUrl.isBlank()) {
            Toast.makeText(this, "Base URL tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = ApiClient.normalizeBaseUrl(rawUrl)
        val parsed = Uri.parse(normalizedUrl)
        if (parsed.host.isNullOrBlank()) {
            Toast.makeText(this, "Format Base URL tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        ApiClient.saveBaseUrl(applicationContext, normalizedUrl)
        PosRealtimeSocket.reconnect()
        binding.editBaseUrl.setText(ApiClient.getBaseUrl())
        updateSocketPreview()
        Toast.makeText(this, "Konfigurasi API disimpan", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        saveConfig()
        binding.txtConnectionStatus.text = "Mengecek koneksi..."
        binding.txtConnectionStatus.setTextColor(android.graphics.Color.parseColor("#64748B"))

        ApiClient.api.getDatabaseStatus().enqueue(object : Callback<DatabaseStatusResponse> {
            override fun onResponse(
                call: Call<DatabaseStatusResponse>,
                response: Response<DatabaseStatusResponse>
            ) {
                if (response.isSuccessful && response.body()?.status == "Online") {
                    binding.txtConnectionStatus.text = "Online - koneksi berhasil"
                    binding.txtConnectionStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                } else {
                    binding.txtConnectionStatus.text = "Server merespons, tetapi status tidak online"
                    binding.txtConnectionStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                }
            }

            override fun onFailure(call: Call<DatabaseStatusResponse>, t: Throwable) {
                binding.txtConnectionStatus.text = t.message ?: "Koneksi gagal"
                binding.txtConnectionStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            }
        })
    }
}
