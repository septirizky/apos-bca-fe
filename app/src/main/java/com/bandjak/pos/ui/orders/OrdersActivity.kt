package com.bandjak.pos.ui.orders

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityOrdersBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.Order
import com.bandjak.pos.model.OrdersResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.login.LoginActivity
import com.bandjak.pos.util.AutoRefreshTimer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var adapter: OrdersAdapter
    private var allOrders: List<Order> = listOf()
    private val autoRefreshTimer = AutoRefreshTimer {
        updateHeaderDateTime()
        loadBranchName()
        loadOrders()
    }
    private val realtimeListener: (String) -> Unit = { eventType ->
        if (eventType == "connected" || eventType == "orders_changed") {
            updateHeaderDateTime()
            loadOrders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Header
        val userName = intent.getStringExtra("U_NAME") ?: "Admin User"
        binding.globalHeader.headerUserSection.visibility = View.VISIBLE
        binding.globalHeader.headerUserName.text = userName
        
        updateHeaderDateTime()

        binding.globalHeader.headerUserSection.setOnClickListener { view ->
            showLogoutPopup(view)
        }

        adapter = OrdersAdapter(listOf()) { order ->
            // Cek apakah order sedang di-lock
            if (order.oLocked.equals("True", ignoreCase = true)) {
                Toast.makeText(this, "Meja sedang digunakan di device lain", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, OrderDetailActivity::class.java)
                intent.putExtra("ORDER_ID", order.oId)
                intent.putExtra("U_NAME", userName)
                startActivity(intent)
            }
        }

        binding.ordersRecycler.layoutManager = LinearLayoutManager(this)
        binding.ordersRecycler.adapter = adapter

        loadBranchName()
        loadOrders()

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val keyword = s.toString().lowercase()
                renderOrders(keyword)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
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

    private fun updateHeaderDateTime() {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.ENGLISH)
        binding.globalHeader.headerDateTime.text = sdf.format(Date())
    }

    private fun showLogoutPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Logout")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Logout") {
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            } else false
        }
        popup.show()
    }

    private fun loadBranchName() {
        ApiClient.api.getBranchName().enqueue(object : Callback<BranchNameResponse> {
            override fun onResponse(call: Call<BranchNameResponse>, response: Response<BranchNameResponse>) {
                if (response.isSuccessful) {
                    binding.globalHeader.headerBranchName.text = response.body()?.branchName ?: "BANDAR DJAKARTA"
                }
            }
            override fun onFailure(call: Call<BranchNameResponse>, t: Throwable) {
                binding.globalHeader.headerBranchName.text = "BANDAR DJAKARTA"
            }
        })
    }

    private fun loadOrders() {
        ApiClient.api.getOrders().enqueue(object : Callback<OrdersResponse> {
            override fun onResponse(call: Call<OrdersResponse>, response: Response<OrdersResponse>) {
                allOrders = response.body()?.orders ?: listOf()
                renderOrders()
            }
            override fun onFailure(call: Call<OrdersResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun renderOrders(keyword: String = binding.searchInput.text.toString().lowercase()) {
        if (keyword.isBlank()) {
            adapter.update(allOrders)
            return
        }

        val filtered = allOrders.filter {
            val table = it.table?.tName ?: ""
            val area = it.tablesArea?.taName ?: ""

            table.lowercase().contains(keyword) || area.lowercase().contains(keyword)
        }
        adapter.update(filtered)
    }
}
