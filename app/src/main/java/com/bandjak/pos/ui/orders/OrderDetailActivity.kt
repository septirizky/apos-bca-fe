package com.bandjak.pos.ui.orders

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.bandjak.pos.model.DiscountValidateRequest
import com.bandjak.pos.model.DiscountValidateResponse
import com.bandjak.pos.model.OrderDetailResponse
import com.bandjak.pos.model.OrderLockRequest
import com.bandjak.pos.model.OrderLockResponse
import com.bandjak.pos.model.OrderMemberCodeRequest
import com.bandjak.pos.model.OrderMemberCodeResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.login.LoginActivity
import com.bandjak.pos.util.AutoRefreshTimer
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.Inet4Address
import java.net.NetworkInterface
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
    private var currentDiscountAmount: Double = 0.0
    private var currentItemSaleId: Int = 0
    private var currentItemSaleCounter: Int = 0
    private var currentWaiterName: String? = null
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
        ApiClient.init(applicationContext)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Header Global Setup
        val userName = intent.getStringExtra("U_NAME") ?: "Admin User"
        binding.globalHeader.headerUserSection.visibility = View.VISIBLE
        binding.globalHeader.headerUserName.text = userName
        binding.globalHeader.headerBackButton.visibility = View.VISIBLE
        binding.globalHeader.headerBackButton.setOnClickListener { finish() }
        
        updateHeaderDateTime()

        binding.globalHeader.headerUserSection.setOnClickListener { view ->
            showLogoutPopup(view)
        }

        currentOrderId = intent.getIntExtra("ORDER_ID", 0)
        currentWaiterName = intent.getStringExtra("WAITER_NAME")
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

        binding.discountSummaryCard.setOnClickListener {
            showDiscountDialog()
        }

        binding.btnRemoveDiscountSummary.setOnClickListener {
            removeMemberCodeFromSummary()
        }

        binding.btnPayment.setOnClickListener {
            if (currentItemSaleId == 0) {
                Toast.makeText(this, "Transaction ID belum tersedia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, PaymentActivity::class.java).apply {
                putExtra("ORDER_ID", currentOrderId)
                putExtra("IS_ID", currentItemSaleId)
                putExtra("IS_COUNTER", currentItemSaleCounter)
                putExtra("TABLE_NAME", currentTableName ?: currentOrderId.toString())
                putExtra("TOTAL_AMOUNT", currentTotalAmount)
                putExtra("DISCOUNT_AMOUNT", currentDiscountAmount)
                putExtra("MEMBER_CODE", currentMemberCode)
                putExtra("WAITER_NAME", currentWaiterName)
                putExtra("U_NAME", getIntent().getStringExtra("U_NAME") ?: "Admin User")
                putExtra("U_ID", getIntent().getIntExtra("U_ID", 0))
            }
            startActivity(intent)
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
        val btnCheck = dialog.findViewById<Button>(R.id.btnCheckDiscount)
        val btnApply = dialog.findViewById<Button>(R.id.btnApply)
        val infoCard = dialog.findViewById<LinearLayout>(R.id.discountInfoCard)
        val txtMember = dialog.findViewById<TextView>(R.id.txtDiscountMember)
        val txtName = dialog.findViewById<TextView>(R.id.txtDiscountName)
        var validatedCode: String? = null

        editCode.setText(currentMemberCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        val hasAppliedMember = !currentMemberCode.isNullOrBlank()

        btnRemove.visibility = if (hasAppliedMember) View.VISIBLE else View.GONE
        btnApply.isEnabled = false

        editCode.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatedCode = null
                btnApply.isEnabled = false
                infoCard.visibility = View.GONE
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnRemove.setOnClickListener {
            applyMemberCode(dialog, btnRemove, null)
        }

        btnCheck.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isBlank()) {
                showDiscountInfo(infoCard, txtMember, txtName, "Kode diskon belum diisi", "Masukkan kode terlebih dahulu", false)
            } else {
                validateDiscountMember(
                    code = code,
                    actionButton = btnCheck,
                    onValid = { result ->
                        validatedCode = code
                        btnApply.isEnabled = true
                        val member = result.member
                        val discount = result.discounts.firstOrNull()
                        showDiscountInfo(
                            infoCard,
                            txtMember,
                            txtName,
                            "Member: ${member?.name ?: code}",
                            "Diskon: ${discount?.name ?: "-"}",
                            true
                        )
                    },
                    onInvalid = { message ->
                        validatedCode = null
                        btnApply.isEnabled = false
                        showDiscountInfo(infoCard, txtMember, txtName, "Diskon tidak ditemukan", message, false)
                    }
                )
            }
        }

        btnApply.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (validatedCode == code) {
                applyMemberCode(dialog, btnApply, code)
            } else {
                showDiscountInfo(infoCard, txtMember, txtName, "Cek diskon terlebih dahulu", "Tekan tombol Cek Diskon sebelum menerapkan", false)
            }
        }

        dialog.show()
    }

    private fun validateDiscountMember(
        code: String,
        actionButton: Button,
        onValid: (DiscountValidateResponse) -> Unit,
        onInvalid: (String) -> Unit
    ) {
        actionButton.isEnabled = false
        ApiClient.api.validateDiscountMember(DiscountValidateRequest(code))
            .enqueue(object : Callback<DiscountValidateResponse> {
                override fun onResponse(
                    call: Call<DiscountValidateResponse>,
                    response: Response<DiscountValidateResponse>
                ) {
                    actionButton.isEnabled = true
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        onValid(body)
                    } else {
                        onInvalid(getErrorMessage(response))
                    }
                }

                override fun onFailure(call: Call<DiscountValidateResponse>, t: Throwable) {
                    actionButton.isEnabled = true
                    onInvalid(t.message ?: "Gagal validasi diskon")
                }
            })
    }

    private fun showDiscountInfo(
        infoCard: LinearLayout,
        titleView: TextView,
        subtitleView: TextView,
        title: String,
        subtitle: String,
        success: Boolean
    ) {
        infoCard.visibility = View.VISIBLE
        infoCard.setBackgroundResource(
            if (success) R.drawable.bg_dialog_info_success else R.drawable.bg_dialog_info_error
        )
        titleView.text = title
        titleView.setTextColor(Color.parseColor(if (success) "#0F172A" else "#E11D48"))
        subtitleView.text = subtitle
        subtitleView.setTextColor(Color.parseColor("#64748B"))
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

    override fun onDestroy() {
        if (isFinishing && currentOrderId != 0) {
            releaseOrderLock()
        }
        super.onDestroy()
    }

    private fun releaseOrderLock(onDone: (() -> Unit)? = null) {
        val userId = intent.getIntExtra("U_ID", 0)
        if (userId == 0 || currentOrderId == 0) {
            onDone?.invoke()
            return
        }

        ApiClient.api.unlockOrder(
            currentOrderId,
            OrderLockRequest(
                userId = userId,
                posId = ApiClient.getPosId(applicationContext),
                posIp = localIpAddress()
            )
        ).enqueue(object : Callback<OrderLockResponse> {
            override fun onResponse(call: Call<OrderLockResponse>, response: Response<OrderLockResponse>) {
                onDone?.invoke()
            }

            override fun onFailure(call: Call<OrderLockResponse>, t: Throwable) {
                onDone?.invoke()
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
                    currentItemSaleId = data.itemSaleId ?: 0
                    currentItemSaleCounter = data.nextItemSaleCounter ?: 0
                    currentTableName = data.tName ?: orderId.toString()
                    currentWaiterName = data.waiterName ?: currentWaiterName
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
            "Tambah Diskon"
        } else {
            "Member : $memberCode"
        }
    }

    private fun updateDiscountSummary() {
        val hasDiscount = currentDiscountAmount > 0

        binding.discountSummaryCard.visibility = if (hasDiscount) View.VISIBLE else View.GONE
        binding.btnAddDiscount.visibility = if (hasDiscount) View.GONE else View.VISIBLE

        if (!hasDiscount) return

        binding.txtDiscountSummaryTitle.text = "Diskon"
        binding.txtDiscountSummarySubtitle.text = if (currentMemberCode.isNullOrBlank()) {
            "Applied discount"
        } else {
            "Member - $currentMemberCode"
        }
        binding.txtDiscountSummaryAmount.text = "-${formatRupiah(currentDiscountAmount)}"
    }

    private fun removeMemberCodeFromSummary() {
        if (currentMemberCode.isNullOrBlank()) {
            Toast.makeText(this, "Diskon tidak bisa dihapus dari sini", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRemoveDiscountSummary.isEnabled = false
        ApiClient.api.updateOrderMemberCode(
            currentOrderId,
            OrderMemberCodeRequest(null)
        ).enqueue(object : Callback<OrderMemberCodeResponse> {
            override fun onResponse(
                call: Call<OrderMemberCodeResponse>,
                response: Response<OrderMemberCodeResponse>
            ) {
                binding.btnRemoveDiscountSummary.isEnabled = true

                if (response.isSuccessful) {
                    currentMemberCode = response.body()?.order?.memberCode
                    loadDetail(currentOrderId)
                } else {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        getErrorMessage(response),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<OrderMemberCodeResponse>, t: Throwable) {
                binding.btnRemoveDiscountSummary.isEnabled = true
                Toast.makeText(
                    this@OrderDetailActivity,
                    t.message ?: "Server tidak terjangkau",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
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
        currentDiscountAmount = s.discountTotal
        updateDiscountSummary()
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
