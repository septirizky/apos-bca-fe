package com.bandjak.pos.ui.orders

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bandjak.pos.R
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityOrdersBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.Order
import com.bandjak.pos.model.OrderDetailResponse
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var adapter: OrdersAdapter
    private var allOrders: List<Order> = listOf()
    private val splitOrderTotals = mutableMapOf<Int, Double>()
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
            handleOrderClick(order, userName, userId)
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

    private fun handleOrderClick(order: Order, userName: String, userId: Int) {
        val tableId = order.tableId
        val splitOrders = if (tableId == null) {
            listOf(order)
        } else {
            allOrders.filter { it.tableId == tableId }
                .sortedWith(compareBy<Order> { it.orderGroup ?: 1 }.thenBy { it.oId ?: 0 })
        }

        if (splitOrders.size <= 1) {
            lockAndOpenOrder(order, userName, userId)
            return
        }

        showSplitBillPickerPretty(order, splitOrders, userName, userId)
    }

    private fun showSplitBillPicker(
        tableOrder: Order,
        splitOrders: List<Order>,
        userName: String,
        userId: Int
    ) {
        val labels = splitOrders.mapIndexed { index, order ->
            val group = order.orderGroup ?: index + 1
            val pax = order.oPax ?: "-"
            val time = formatOrderTime(order.oStartTime)
            "Group $group • $pax pax • $time"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Table ${tableOrder.table?.tName ?: "-"}")
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                lockAndOpenOrder(splitOrders[which], userName, userId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showSplitBillPickerPretty(
        tableOrder: Order,
        splitOrders: List<Order>,
        userName: String,
        userId: Int
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }
        root.addView(TextView(this).apply {
            text = "Pilih Split Bill"
            textSize = 20f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Table ${tableOrder.table?.tName ?: "-"} memiliki ${splitOrders.size} group aktif"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(14)) }
        })

        val listContainer = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(listContainer)
        }
        root.addView(scroll)

        fun renderCards() {
            listContainer.removeAllViews()
            splitOrders.forEachIndexed { index, order ->
                listContainer.addView(createSplitOrderCard(order, index, dialog, userName, userId))
            }
        }

        renderCards()
        splitOrders.forEach { order ->
            val orderId = order.oId ?: return@forEach
            if (splitOrderTotals.containsKey(orderId)) return@forEach
            ApiClient.api.getOrderDetail(orderId).enqueue(object : Callback<OrderDetailResponse> {
                override fun onResponse(
                    call: Call<OrderDetailResponse>,
                    response: Response<OrderDetailResponse>
                ) {
                    response.body()?.summary?.totalBeforeDiscount?.let {
                        splitOrderTotals[orderId] = it
                        if (dialog.isShowing) renderCards()
                    }
                }

                override fun onFailure(call: Call<OrderDetailResponse>, t: Throwable) = Unit
            })
        }

        val actionRow = LinearLayout(this).apply {
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, 0) }
        }
        actionRow.addView(TextView(this).apply {
            text = "BATAL"
            textSize = 13f
            setTextColor(Color.parseColor("#1A05A3"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), dp(10), dp(4), dp(10))
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(actionRow)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createSplitOrderCard(
        order: Order,
        index: Int,
        dialog: Dialog,
        userName: String,
        userId: Int
    ): View {
        val orderId = order.oId
        val group = order.orderGroup ?: index + 1
        val total = orderId?.let { splitOrderTotals[it] }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_payment_card)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            minimumHeight = dp(92)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(8))
            }
            setOnClickListener {
                dialog.dismiss()
                lockAndOpenOrder(order, userName, userId)
            }

            val topRow = LinearLayout(this@OrdersActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            topRow.addView(TextView(this@OrdersActivity).apply {
                text = "Group $group"
                textSize = 13f
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            topRow.addView(TextView(this@OrdersActivity).apply {
                text = total?.let { formatRupiah(it) } ?: "Memuat..."
                textSize = 12f
                setTextColor(Color.parseColor("#1A05A3"))
                setTypeface(Typeface.DEFAULT_BOLD)
                gravity = android.view.Gravity.END
                maxLines = 1
            })
            addView(topRow)

            addView(TextView(this@OrdersActivity).apply {
                text = "${order.oPax ?: "-"} pax | ${formatOrderTime(order.oStartTime)}"
                textSize = 10f
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(10), 0, 0) }
            })
            addView(TextView(this@OrdersActivity).apply {
                text = "Order #${order.oId ?: "-"}"
                textSize = 10f
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(2), 0, 0) }
            })
        }
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
        val matchingOrders = if (keyword.isBlank()) {
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

        val grouped = matchingOrders
            .groupBy { it.tableId ?: it.oId ?: 0 }
            .values
            .map { group ->
                group.sortedWith(compareBy<Order> { it.orderGroup ?: 1 }.thenBy { it.oId ?: 0 }).first()
            }
            .sortedWith(compareBy<Order> { it.table?.tName?.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.table?.tName ?: "" })

        adapter.update(grouped)
        updateOrdersSummary(grouped.size, keyword)
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

    private fun formatOrderTime(time: String?): String {
        if (time == null) return "-"
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (format in formats) {
            runCatching {
                val input = SimpleDateFormat(format, Locale.getDefault())
                input.parse(time)?.let {
                    return SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(it)
                }
            }
        }
        return time
    }

    private fun formatRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
