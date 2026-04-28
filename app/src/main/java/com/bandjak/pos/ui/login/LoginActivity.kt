package com.bandjak.pos.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bandjak.pos.R
import com.bandjak.pos.api.*
import com.bandjak.pos.databinding.ActivityLoginBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.DatabaseStatusResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.orders.OrdersActivity
import com.bandjak.pos.ui.settings.ApiSettingsActivity
import com.bandjak.pos.util.AutoRefreshTimer
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var pinBuffer = ""
    private val maxPinLength = 6
    private val autoRefreshTimer = AutoRefreshTimer {
        checkDatabaseStatus()
        loadBranchName()
    }
    private val realtimeListener: (String) -> Unit = { eventType ->
        if (eventType == "connected" || eventType == "database_status_changed") {
            checkDatabaseStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan User Section di halaman Login
        binding.globalHeader.headerUserSection.visibility = View.GONE
        binding.globalHeader.headerSettingsButton.visibility = View.VISIBLE
        binding.globalHeader.headerSettingsButton.setOnClickListener {
            startActivity(Intent(this, ApiSettingsActivity::class.java))
        }

        setupNumericKeypad()
        checkDatabaseStatus()
        loadBranchName()

        binding.btnLogin.setOnClickListener {
            if (pinBuffer.length == maxPinLength) {
                performLogin(pinBuffer)
            } else {
                Toast.makeText(this, "Masukkan 6 digit PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PosRealtimeSocket.addListener(realtimeListener)
        autoRefreshTimer.start()
    }

    override fun onStop() {
        autoRefreshTimer.stop()
        PosRealtimeSocket.removeListener(realtimeListener)
        super.onStop()
    }

    private fun loadBranchName() {
        ApiClient.api.getBranchName().enqueue(object : Callback<BranchNameResponse> {
            override fun onResponse(call: Call<BranchNameResponse>, response: Response<BranchNameResponse>) {
                if (response.isSuccessful) {
                    val branchName = response.body()?.branchName ?: "BANDAR DJAKARTA"
                    binding.globalHeader.headerBranchName.text = branchName
                    updateRestaurantLogo(branchName)
                }
            }
            override fun onFailure(call: Call<BranchNameResponse>, t: Throwable) {
                val branchName = "BANDAR DJAKARTA"
                binding.globalHeader.headerBranchName.text = branchName
                updateRestaurantLogo(branchName)
            }
        })
    }

    private fun updateRestaurantLogo(branchName: String) {
        val normalizedBranchName = branchName.uppercase(Locale.ROOT)
        val logoRes = if (normalizedBranchName.contains("PESISIR")) {
            R.drawable.pesisir_seafood_logo
        } else {
            R.drawable.bandar_djakarta_login_logo
        }

        binding.statusLogo.setImageResource(logoRes)
    }

    private fun checkDatabaseStatus() {
        ApiClient.api.getDatabaseStatus().enqueue(object : Callback<DatabaseStatusResponse> {
            override fun onResponse(call: Call<DatabaseStatusResponse>, response: Response<DatabaseStatusResponse>) {
                if (response.isSuccessful && response.body()?.status == "Online") {
                    binding.txtStatus.text = "Online"
                    binding.txtStatus.setTextColor(ContextCompat.getColor(this@LoginActivity, android.R.color.black))
                } else {
                    setOfflineStatus()
                }
            }
            override fun onFailure(call: Call<DatabaseStatusResponse>, t: Throwable) {
                setOfflineStatus()
            }
        })
    }

    private fun setOfflineStatus() {
        binding.txtStatus.text = "Offline"
        binding.txtStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupNumericKeypad() {
        val buttons = listOf(
            binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6,
            binding.btn7, binding.btn8, binding.btn9,
            binding.btn0
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                if (pinBuffer.length < maxPinLength) {
                    pinBuffer += (it as Button).text
                    updatePinDots()
                    if (pinBuffer.length == maxPinLength) {
                        performLogin(pinBuffer)
                    }
                }
            }
        }

        binding.btnClear.setOnClickListener {
            pinBuffer = ""
            updatePinDots()
        }

        binding.btnDelete.setOnClickListener {
            if (pinBuffer.isNotEmpty()) {
                pinBuffer = pinBuffer.substring(0, pinBuffer.length - 1)
                updatePinDots()
            }
        }
        updatePinDots()
    }

    private fun updatePinDots() {
        val dots = listOf(
            binding.dot1, binding.dot2, binding.dot3,
            binding.dot4, binding.dot5, binding.dot6
        )

        dots.forEachIndexed { index, view ->
            if (index < pinBuffer.length) {
                view.setBackgroundResource(R.drawable.dot_filled)
            } else {
                view.setBackgroundResource(R.drawable.dot_empty)
            }
        }
    }

    private fun performLogin(pin: String) {
        ApiClient.api.login(LoginRequest(pin)).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()?.user
                    val intent = Intent(this@LoginActivity, OrdersActivity::class.java)
                    intent.putExtra("U_NAME", user?.uName)
                    intent.putExtra("U_ID", user?.uId ?: 0)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "PIN Salah", Toast.LENGTH_SHORT).show()
                    pinBuffer = ""
                    updatePinDots()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(this@LoginActivity, "Server tidak terjangkau", Toast.LENGTH_SHORT).show()
                pinBuffer = ""
                updatePinDots()
            }
        })
    }
}
