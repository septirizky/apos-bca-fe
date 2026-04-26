package com.bandjak.pos.ui.orders

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandjak.pos.R
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityOrderDetailBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.OrderDetailResponse
import com.bandjak.pos.model.OrderMemberCodeRequest
import com.bandjak.pos.model.OrderMemberCodeResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.login.LoginActivity
import com.bandjak.pos.util.AutoRefreshTimer
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var adapter: OrderDetailAdapter
    private var currentOrderId: Int = 0
    private var currentMemberCode: String? = null
    private var currentTableName: String? = null
    private var currentTotalAmount: Double = 0.0
    private val autoRefreshTimer = AutoRefreshTimer {
        updateHeaderDateTime()
        loadBranchName()
        loadDetail(currentOrderId)
    }
    private val realtimeListener: (String) -> Unit = { eventType ->
        if (eventType == "connected" || eventType == "orders_changed") {
            updateHeaderDateTime()
            loadDetail(currentOrderId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Header Global Setup
        val userName = intent.getStringExtra("U_NAME") ?: "Admin User"
        binding.globalHeader.headerUserSection.visibility = View.VISIBLE
        binding.globalHeader.headerUserName.text = userName
        
        updateHeaderDateTime()

        binding.globalHeader.headerUserSection.setOnClickListener { view ->
            showLogoutPopup(view)
        }

        currentOrderId = intent.getIntExtra("ORDER_ID", 0)
        binding.txtTableNumber.text = "Table $currentOrderId"

        adapter = OrderDetailAdapter(listOf())
        binding.detailRecycler.layoutManager = LinearLayoutManager(this)
        binding.detailRecycler.adapter = adapter

        loadBranchName()
        loadDetail(currentOrderId)

        // Tombol Add Discount
        binding.btnAddDiscount.setOnClickListener {
            showDiscountDialog()
        }

        binding.btnPayment.setOnClickListener {
            Toast.makeText(this, "Payment belum aktif", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDiscountDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_discount_code)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val editCode = dialog.findViewById<EditText>(R.id.editDiscountCode)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnRemove = dialog.findViewById<Button>(R.id.btnRemove)
        val btnApply = dialog.findViewById<Button>(R.id.btnApply)

        editCode.setText(currentMemberCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        val hasAppliedMember = !currentMemberCode.isNullOrBlank()

        btnRemove.visibility = if (hasAppliedMember) View.VISIBLE else View.GONE

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnRemove.setOnClickListener {
            applyMemberCode(dialog, btnRemove, null)
        }

        btnApply.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isNotEmpty()) {
                applyMemberCode(dialog, btnApply, code)
            } else {
                Toast.makeText(this, "Masukkan kode terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
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
        val sdfTime = SimpleDateFormat("HH:mm | dd MMM", Locale.ENGLISH)
        binding.globalHeader.headerDateTime.text = sdfTime.format(Date())
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

    private fun applyMemberCode(dialog: Dialog, actionButton: Button, memberCode: String?) {
        actionButton.isEnabled = false

        ApiClient.api.updateOrderMemberCode(
            currentOrderId,
            OrderMemberCodeRequest(memberCode)
        ).enqueue(object : Callback<OrderMemberCodeResponse> {
            override fun onResponse(
                call: Call<OrderMemberCodeResponse>,
                response: Response<OrderMemberCodeResponse>
            ) {
                actionButton.isEnabled = true

                if (response.isSuccessful) {
                    currentMemberCode = response.body()?.order?.memberCode
                    loadDetail(currentOrderId)
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        getErrorMessage(response),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<OrderMemberCodeResponse>, t: Throwable) {
                actionButton.isEnabled = true
                Toast.makeText(
                    this@OrderDetailActivity,
                    t.message ?: "Server tidak terjangkau",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun loadDetail(orderId: Int, memberCode: String? = null) {
        ApiClient.api.getOrderDetail(orderId, memberCode).enqueue(object : Callback<OrderDetailResponse> {
            override fun onResponse(call: Call<OrderDetailResponse>, response: Response<OrderDetailResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    currentMemberCode = data.memberCode
                    currentTableName = data.tName ?: orderId.toString()
                    binding.txtTableNumber.text = "Table ${data.tName ?: orderId}"
                    adapter.update(data.items)
                    binding.txtItemCount.text = "${data.items.size} Items"
                    updateMemberButton()
                    updateSummary(data)
                }
            }

            override fun onFailure(call: Call<OrderDetailResponse>, t: Throwable) {
                Log.e("ORDER_DETAIL", "Detail Load Failed", t)
            }
        })
    }

    private fun updateMemberButton() {
        val memberCode = currentMemberCode

        binding.btnAddDiscount.text = if (memberCode.isNullOrBlank()) {
            "Add Discount"
        } else {
            "Member: $memberCode"
        }
    }

    private fun getErrorMessage(response: Response<*>): String {
        val rawError = response.errorBody()?.string().orEmpty()

        if (rawError.isBlank()) {
            return "Terjadi kesalahan"
        }

        return runCatching {
            val json = JSONObject(rawError)
            json.optString("message")
                .ifBlank { json.optString("error") }
                .ifBlank { rawError }
        }.getOrDefault(rawError)
    }

    private fun updateSummary(data: OrderDetailResponse) {
        val s = data.summary
        binding.summaryBreakdown.removeAllViews()

        if (s.foodTotal > 0) addSummaryRow("Food Total", formatRupiah(s.foodTotal), false)
        if (s.beverageTotal > 0) addSummaryRow("Beverage Total", formatRupiah(s.beverageTotal), false)
        if (s.otherTotal > 0) addSummaryRow("Other Total", formatRupiah(s.otherTotal), false)
        
        val divider = View(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            params.setMargins(0, 8, 0, 8)
            layoutParams = params
            setBackgroundColor(Color.parseColor("#F1F5F9"))
        }
        binding.summaryBreakdown.addView(divider)

        addSummaryRow("Total Bef. Disc.", formatRupiah(s.totalBeforeDiscount), false)
        if (s.discountTotal > 0) {
            addSummaryRow("Total Discount", formatRupiah(s.discountTotal), true)
        }
        addSummaryRow("Subtotal", formatRupiah(s.subtotal), false)
        if (s.cookingCharge > 0) {
            addSummaryRow("Cooking Charge", formatRupiah(s.cookingCharge), false)
        }
        addSummaryRow("PBJT 10%", formatRupiah(s.pbjt), false)

        binding.txtTotal.text = formatRupiah(s.total)
        currentTotalAmount = s.total
    }

    private fun addSummaryRow(label: String, value: String, isDiscount: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 2, 0, 2)
            }
        }

        val lbl = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor(if (isDiscount) "#F97316" else "#64748B"))
            setTypeface(Typeface.DEFAULT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valTxt = TextView(this).apply {
            text = if (isDiscount) "-$value" else value
            textSize = 13f
            setTypeface(Typeface.DEFAULT)
            setTextColor(Color.parseColor(if (isDiscount) "#F97316" else "#1E293B"))
            gravity = Gravity.END
        }

        row.addView(lbl)
        row.addView(valTxt)
        binding.summaryBreakdown.addView(row)
    }

    private fun formatRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ")
    }
}
