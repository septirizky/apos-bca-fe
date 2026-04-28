package com.bandjak.pos.ui.orders

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityOrdersBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.Order
import com.bandjak.pos.model.OrderLockRequest
import com.bandjak.pos.model.OrderLockResponse
import com.bandjak.pos.model.OrdersResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.login.LoginActivity
import com.bandjak.pos.util.AutoRefreshTimer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.Inet4Address
import java.net.NetworkInterface
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
        ApiClient.init(applicationContext)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Header
        val userName = intent.getStringExtra("U_NAME") ?: "Admin User"
        val userId = intent.getIntExtra("U_ID", 0)
        binding.globalHeader.headerUserSection.visibility = View.VISIBLE
        binding.globalHeader.headerUserName.text = userName
        
        updateHeaderDateTime()

        binding.globalHeader.headerUserSection.setOnClickListener { view ->
            showLogoutPopup(view)
        }

        adapter = OrdersAdapter(listOf()) { order ->
            lockAndOpenOrder(order, userName, userId)
        }

        binding.ordersRecycler.layoutManager = GridLayoutManager(this, 2)
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

    private fun lockAndOpenOrder(order: Order, userName: String, userId: Int) {
        val orderId = order.oId ?: return

        ApiClient.api.lockOrder(
            orderId,
            OrderLockRequest(
                userId = userId,
                posId = ApiClient.getPosId(applicationContext),
                posIp = localIpAddress()
            )
        ).enqueue(object : Callback<OrderLockResponse> {
            override fun onResponse(call: Call<OrderLockResponse>, response: Response<OrderLockResponse>) {
                if (response.isSuccessful) {
                    val intent = Intent(this@OrdersActivity, OrderDetailActivity::class.java)
                        .putExtra("ORDER_ID", orderId)
                        .putExtra("WAITER_NAME", order.user?.userName)
                        .putExtra("U_NAME", userName)
                        .putExtra("U_ID", userId)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@OrdersActivity, getErrorMessage(response), Toast.LENGTH_SHORT).show()
                    loadOrders()
                }
            }

            override fun onFailure(call: Call<OrderLockResponse>, t: Throwable) {
                Toast.makeText(this@OrdersActivity, t.message ?: "Gagal mengunci meja", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun localIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        }.getOrNull()
    }

    private fun getErrorMessage(response: Response<*>): String {
        val rawError = response.errorBody()?.string().orEmpty()
        if (rawError.isBlank()) return "Terjadi kesalahan"

        return runCatching {
            val json = org.json.JSONObject(rawError)
            json.optString("message")
                .ifBlank { json.optString("error") }
                .ifBlank { rawError }
        }.getOrDefault(rawError)
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
        val filtered = if (keyword.isBlank()) {
            allOrders
        } else {
            allOrders.filter {
                val table = it.table?.tName ?: ""
                val area = it.tablesArea?.taName ?: ""
                val section = it.table?.tablesSection?.tsName ?: ""
                val waiter = it.user?.userName ?: ""

                table.lowercase().contains(keyword) ||
                    area.lowercase().contains(keyword) ||
                    section.lowercase().contains(keyword) ||
                    waiter.lowercase().contains(keyword)
            }
        }

        adapter.update(filtered)
        updateOrdersSummary(filtered.size, keyword)
    }

    private fun updateOrdersSummary(visibleCount: Int, keyword: String) {
        val totalCount = allOrders.size
        binding.ordersCountChip.text = "$visibleCount Meja"

        binding.ordersSummaryText.text = if (keyword.isBlank()) {
            "$totalCount order aktif saat ini"
        } else {
            "$visibleCount dari $totalCount order cocok"
        }

        if (visibleCount == 0) {
            binding.ordersRecycler.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.emptyStateTitle.text = if (keyword.isBlank()) "Belum ada order aktif" else "Order tidak ditemukan"
            binding.emptyStateMessage.text = if (keyword.isBlank()) {
                "Order aktif akan muncul di sini."
            } else {
                "Coba gunakan nomor meja, area, atau section lain."
            }
        } else {
            binding.ordersRecycler.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }
}
