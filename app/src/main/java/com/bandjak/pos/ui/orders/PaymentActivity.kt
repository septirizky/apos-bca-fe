package com.bandjak.pos.ui.orders

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bandjak.pos.R
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.apos.AposManager
import com.bandjak.pos.apos.DeepLinkEncryptionUtil
import com.bandjak.pos.databinding.ActivityPaymentBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.DownPayment
import com.bandjak.pos.model.OrderDetailResponse
import com.bandjak.pos.model.OrderMemberCodeRequest
import com.bandjak.pos.model.OrderMemberCodeResponse
import com.bandjak.pos.model.PaymentRequest
import com.bandjak.pos.model.PaymentResponse
import com.bandjak.pos.model.Voucher
import com.bandjak.pos.model.VoucherValidateRequest
import com.bandjak.pos.model.VoucherValidateResponse
import com.bandjak.pos.ui.login.LoginActivity
import com.bca.apos.FeatureType
import com.bca.apos.InquiryFlag
import com.bca.apos.TransactionStatus
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var aposManager: AposManager

    private var orderId = 0
    private var itemSaleId = 0
    private var itemSaleCounter = 0
    private var userId = 0
    private var userName: String? = null
    private var tableName = "-"
    private var memberCode: String? = null
    private var totalAmount = 0.0
    private var discountAmount = 0.0
    private var selectedDownPayment: DownPayment? = null
    private var selectedVoucherId: Int? = null
    private var voucherCode: String? = null
    private var voucherAmount = 0.0
    private var selectedFeatureType: FeatureType? = null
    private var pendingPartnerRefId: String? = null
    private var didLaunchApos = false
    private var currentBranchName = "BANDAR DJAKARTA"
    private var currentOrderDetail: OrderDetailResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getIntExtra("ORDER_ID", 0)
        itemSaleId = intent.getIntExtra("IS_ID", 0)
        itemSaleCounter = intent.getIntExtra("IS_COUNTER", 0)
        userId = intent.getIntExtra("U_ID", 0)
        userName = intent.getStringExtra("U_NAME")
        tableName = intent.getStringExtra("TABLE_NAME") ?: "-"
        memberCode = intent.getStringExtra("MEMBER_CODE")
        totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
        discountAmount = intent.getDoubleExtra("DISCOUNT_AMOUNT", 0.0)

        aposManager = AposManager(applicationContext)
        aposManager.connect()

        setupHeader()
        setupInitialView()
        setupActions()
        refreshOrderDetail()
    }

    override fun onResume() {
        super.onResume()
        if (didLaunchApos && pendingPartnerRefId != null) {
            didLaunchApos = false
            inquiryAposTransaction()
        }
    }

    override fun onDestroy() {
        aposManager.disconnect()
        super.onDestroy()
    }

    private fun setupInitialView() {
        binding.txtTransactionId.text = transactionId()
        binding.txtTable.text = tableName
        binding.txtTimestamp.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        renderTotals()
        renderDiscountButton()
    }

    private fun setupActions() {
        binding.btnDownPayment.setOnClickListener { showDownPaymentDialog() }
        binding.btnDiscount.setOnClickListener { showDiscountDialog() }
        binding.btnVoucher.setOnClickListener { showVoucherDialog() }
        binding.btnPaymentMethod.setOnClickListener { showPaymentMethodDialog() }
        binding.btnPreviewReceipt.setOnClickListener {
            showReceiptPreview()
        }
        binding.btnCompletePayment.setOnClickListener { completePayment() }
    }

    private fun setupHeader() {
        binding.globalHeader.headerBackButton.visibility = View.VISIBLE
        binding.globalHeader.headerBackButton.setOnClickListener { finish() }
        binding.globalHeader.headerUserSection.visibility = View.VISIBLE
        binding.globalHeader.headerUserName.text = userName ?: "Admin User"
        binding.globalHeader.headerUserSection.setOnClickListener { view ->
            showLogoutPopup(view)
        }
        updateHeaderDateTime()
        loadBranchName()
    }

    private fun updateHeaderDateTime() {
        val sdfTime = SimpleDateFormat("HH:mm | dd MMM", Locale.ENGLISH)
        binding.globalHeader.headerDateTime.text = sdfTime.format(Date())
    }

    private fun loadBranchName() {
        ApiClient.api.getBranchName().enqueue(object : Callback<BranchNameResponse> {
            override fun onResponse(call: Call<BranchNameResponse>, response: Response<BranchNameResponse>) {
                if (response.isSuccessful) {
                    binding.globalHeader.headerBranchName.text = response.body()?.branchName ?: "BANDAR DJAKARTA"
                    currentBranchName = response.body()?.branchName ?: "BANDAR DJAKARTA"
                }
            }

            override fun onFailure(call: Call<BranchNameResponse>, t: Throwable) {
                binding.globalHeader.headerBranchName.text = "BANDAR DJAKARTA"
                currentBranchName = "BANDAR DJAKARTA"
            }
        })
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

    private fun refreshOrderDetail() {
        ApiClient.api.getOrderDetail(orderId).enqueue(object : Callback<OrderDetailResponse> {
            override fun onResponse(call: Call<OrderDetailResponse>, response: Response<OrderDetailResponse>) {
                val data = response.body() ?: return
                currentOrderDetail = data
                itemSaleId = data.itemSaleId ?: itemSaleId
                itemSaleCounter = data.nextItemSaleCounter ?: itemSaleCounter
                tableName = data.tName ?: tableName
                memberCode = data.memberCode
                totalAmount = data.summary.total
                discountAmount = data.summary.discountTotal
                binding.txtTransactionId.text = transactionId()
                binding.txtTable.text = tableName
                renderTotals()
                renderDiscountButton()
            }

            override fun onFailure(call: Call<OrderDetailResponse>, t: Throwable) {
                Toast.makeText(this@PaymentActivity, t.message ?: "Gagal memuat detail order", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showReceiptPreview() {
        val data = currentOrderDetail
        if (data == null) {
            Toast.makeText(this, "Detail order belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A05A3"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        }
        header.addView(TextView(this).apply {
            text = "X"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { dialog.dismiss() }
        })
        header.addView(TextView(this).apply {
            text = "Receipt Preview"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(36), 0)
            }
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A05A3"))
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val paper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(18), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(12), 0, dp(12), dp(12))
            }
        }

        paper.addView(TextView(this).apply {
            text = currentBranchName
            textSize = 20f
            setTextColor(Color.parseColor("#334155"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        paper.addView(TextView(this).apply {
            text = "Jl. Pantai Indah Kapuk, Jakarta Utara\nTelp: (021) 555-0123"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(8), 0, dp(18))
            }
        })

        val timestamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        paper.addView(receiptInfoRow("ORDER #${transactionId()} | TABLE $tableName", timestamp))
        paper.addView(receiptInfoRow("CASHIER: ${userName ?: "-"}", ""))
        paper.addView(receiptDivider())

        data.items.forEach { item ->
            paper.addView(receiptRow(item.odName, formatPlain(item.itemTotal), bold = true))
            paper.addView(receiptRow("${item.qty} x ${formatPlain(item.sellPrice)}", "", small = true))
            item.discounts?.filter { it.isApplied }?.forEach { discount ->
                paper.addView(
                    receiptRow(
                        "- ${discount.dName} (${discount.discountPercent}%)",
                        "-${formatPlain(discount.discountAmount)}",
                        danger = true,
                        small = true
                    )
                )
            }
        }

        paper.addView(receiptDivider())
        if (data.summary.foodTotal > 0) {
            paper.addView(receiptRow("Food Total", formatPlain(data.summary.foodTotal)))
        }
        if (data.summary.beverageTotal > 0) {
            paper.addView(receiptRow("Beverage Total", formatPlain(data.summary.beverageTotal)))
        }
        if (data.summary.otherTotal > 0) {
            paper.addView(receiptRow("Other Total", formatPlain(data.summary.otherTotal)))
        }
        paper.addView(receiptRow("Total Bef. Disc.", formatPlain(data.summary.totalBeforeDiscount)))
        if (data.summary.discountTotal > 0) {
            paper.addView(receiptRow("Total Discount", "-${formatPlain(data.summary.discountTotal)}", danger = true))
        }
        paper.addView(receiptRow("Subtotal", formatPlain(data.summary.subtotal)))
        if (data.summary.cookingCharge > 0) {
            paper.addView(receiptRow("Cooking Charge", formatPlain(data.summary.cookingCharge)))
        }
        paper.addView(receiptRow("PBJT 10%", formatPlain(data.summary.pbjt)))

        if (paidAmount() > 0 || selectedFeatureType != null) {
            paper.addView(receiptDivider())
            paper.addView(sectionTitle("APPLIED PAYMENTS"))
            selectedDownPayment?.let {
                paper.addView(receiptRow("Downpayment ${it.name}", formatPlain(it.amount.toDoubleOrNull() ?: 0.0)))
            }
            if (voucherAmount > 0) {
                paper.addView(receiptRow("Voucher ${voucherCode.orEmpty()}", formatPlain(voucherAmount)))
            }
            selectedFeatureType?.let {
                paper.addView(receiptRow(it.title, formatPlain(remainingAmount())))
            }
        }

        paper.addView(receiptDivider(strong = true))
        paper.addView(receiptTotalRow("TOTAL", formatPlain(remainingAmount())))

        scroll.addView(paper)
        root.addView(header)
        root.addView(scroll)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        dialog.show()
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun receiptInfoRow(left: String, right: String): View =
        receiptRow(left, right, small = true, uppercase = true)

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 10f
        setTextColor(Color.parseColor("#CBD5E1"))
        setTypeface(Typeface.DEFAULT_BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(8), 0, dp(4))
        }
    }

    private fun receiptRow(
        left: String,
        right: String,
        bold: Boolean = false,
        danger: Boolean = false,
        small: Boolean = false,
        uppercase: Boolean = false
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(2), 0, dp(2))
            }
        }
        val color = if (danger) "#EF4444" else "#64748B"
        row.addView(TextView(this).apply {
            text = if (uppercase) left.uppercase(Locale.US) else left
            textSize = if (small) 10f else 12f
            setTextColor(Color.parseColor(if (bold) "#334155" else color))
            setTypeface(if (bold || danger) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = if (uppercase) right.uppercase(Locale.US) else right
            textSize = if (small) 10f else 12f
            setTextColor(Color.parseColor(if (danger) "#EF4444" else "#64748B"))
            setTypeface(if (bold || danger) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        return row
    }

    private fun receiptTotalRow(label: String, amount: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(8), 0, 0)
            }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.parseColor("#334155"))
            setTypeface(Typeface.DEFAULT_BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = amount
            textSize = 20f
            setTextColor(Color.parseColor("#1A05A3"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.END
        })
        return row
    }

    private fun receiptDivider(strong: Boolean = false): View = View(this).apply {
        setBackgroundColor(Color.parseColor(if (strong) "#334155" else "#E2E8F0"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (strong) dp(2) else dp(1)
        ).apply {
            setMargins(0, dp(10), 0, dp(8))
        }
    }

    private fun renderTotals() {
        val paid = paidAmount()
        val remaining = remainingAmount()

        binding.txtTotalBill.text = formatRupiah(totalAmount)
        binding.txtPaid.text = formatRupiah(paid)
        binding.txtRemaining.text = formatRupiah(remaining)
        binding.btnCompletePayment.text = if (remaining > 0) {
            "Selesaikan Pembayaran ${formatRupiah(remaining)}"
        } else {
            "Selesaikan Pembayaran"
        }
        renderAppliedSummary()
    }

    private fun renderDiscountButton() {
        binding.btnDiscount.text = when {
            discountAmount > 0 -> "DISKON"
            memberCode.isNullOrBlank() -> "DISKON"
            else -> "MEMBER\n$memberCode"
        }
        renderAppliedSummary()
    }

    private fun paidAmount(): Double {
        val dpAmount = selectedDownPayment?.amount?.toDoubleOrNull() ?: 0.0
        return dpAmount + voucherAmount
    }

    private fun remainingAmount(): Double = max(0.0, totalAmount - paidAmount())

    private fun showDownPaymentDialog() {
        ApiClient.api.getDownPayments().enqueue(object : Callback<List<DownPayment>> {
            override fun onResponse(call: Call<List<DownPayment>>, response: Response<List<DownPayment>>) {
                val downPayments = response.body().orEmpty()
                if (downPayments.isEmpty()) {
                    Toast.makeText(this@PaymentActivity, "Downpayment aktif tidak tersedia", Toast.LENGTH_SHORT).show()
                    return
                }

                val labels = downPayments.map { "${it.name} - ${formatRupiah(it.amount.toDoubleOrNull() ?: 0.0)}" }
                    .toTypedArray()
                AlertDialog.Builder(this@PaymentActivity)
                    .setTitle("Pilih Downpayment")
                    .setItems(labels) { dialog, which ->
                        selectedDownPayment = downPayments[which]
                        binding.btnDownPayment.text = "DP\n${formatRupiah(selectedDownPayment?.amount?.toDoubleOrNull() ?: 0.0)}"
                        renderTotals()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Hapus") { dialog, _ ->
                        selectedDownPayment = null
                        binding.btnDownPayment.text = "DOWNPAYMENT"
                        renderTotals()
                        dialog.dismiss()
                    }
                    .show()
            }

            override fun onFailure(call: Call<List<DownPayment>>, t: Throwable) {
                Toast.makeText(this@PaymentActivity, t.message ?: "Gagal memuat downpayment", Toast.LENGTH_SHORT).show()
            }
        })
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

        editCode.setText(memberCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        btnRemove.visibility = if (memberCode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnRemove.setOnClickListener { applyMemberCode(dialog, btnRemove, null) }
        btnApply.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isBlank()) {
                Toast.makeText(this, "Masukkan kode terlebih dahulu", Toast.LENGTH_SHORT).show()
            } else {
                applyMemberCode(dialog, btnApply, code)
            }
        }

        dialog.show()
    }

    private fun applyMemberCode(dialog: Dialog, actionButton: Button, code: String?) {
        actionButton.isEnabled = false
        ApiClient.api.updateOrderMemberCode(orderId, OrderMemberCodeRequest(code))
            .enqueue(object : Callback<OrderMemberCodeResponse> {
                override fun onResponse(
                    call: Call<OrderMemberCodeResponse>,
                    response: Response<OrderMemberCodeResponse>
                ) {
                    actionButton.isEnabled = true
                    if (response.isSuccessful) {
                        memberCode = response.body()?.order?.memberCode
                        dialog.dismiss()
                        refreshOrderDetail()
                    } else {
                        Toast.makeText(this@PaymentActivity, getErrorMessage(response), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<OrderMemberCodeResponse>, t: Throwable) {
                    actionButton.isEnabled = true
                    Toast.makeText(this@PaymentActivity, t.message ?: "Server tidak terjangkau", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showVoucherDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_voucher_code)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val editCode = dialog.findViewById<EditText>(R.id.editVoucherCode)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelVoucher)
        val btnRemove = dialog.findViewById<Button>(R.id.btnRemoveVoucher)
        val btnApply = dialog.findViewById<Button>(R.id.btnApplyVoucher)

        editCode.setText(voucherCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        btnRemove.visibility = if (voucherCode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnRemove.setOnClickListener {
            clearVoucher()
            dialog.dismiss()
        }
        btnApply.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isBlank()) {
                Toast.makeText(this, "Masukkan kode voucher", Toast.LENGTH_SHORT).show()
            } else {
                validateVoucher(code, allowExpired = false, dialog = dialog, actionButton = btnApply)
            }
        }

        dialog.show()
    }

    private fun validateVoucher(
        code: String,
        allowExpired: Boolean,
        dialog: Dialog,
        actionButton: Button
    ) {
        actionButton.isEnabled = false
        ApiClient.api.validateVoucher(VoucherValidateRequest(code, allowExpired))
            .enqueue(object : Callback<VoucherValidateResponse> {
                override fun onResponse(
                    call: Call<VoucherValidateResponse>,
                    response: Response<VoucherValidateResponse>
                ) {
                    actionButton.isEnabled = true

                    if (response.isSuccessful) {
                        applyVoucher(response.body()?.voucher)
                        dialog.dismiss()
                        return
                    }

                    val rawError = response.errorBody()?.string().orEmpty()
                    val errorCode = runCatching {
                        JSONObject(rawError).optString("code")
                    }.getOrDefault("")

                    if (response.code() == 409 && errorCode == "VOUCHER_EXPIRED_CONFIRM") {
                        AlertDialog.Builder(this@PaymentActivity)
                            .setTitle("Voucher Kadaluarsa")
                            .setMessage("Voucher kadaluarsa apakah akan tetap digunakan?")
                            .setPositiveButton("Ya") { _, _ ->
                                validateVoucher(code, allowExpired = true, dialog = dialog, actionButton = actionButton)
                            }
                            .setNegativeButton("Tidak", null)
                            .show()
                    } else {
                        Toast.makeText(this@PaymentActivity, getErrorMessage(rawError), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<VoucherValidateResponse>, t: Throwable) {
                    actionButton.isEnabled = true
                    Toast.makeText(this@PaymentActivity, t.message ?: "Gagal validasi voucher", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun applyVoucher(voucher: Voucher?) {
        if (voucher == null) {
            Toast.makeText(this, "Voucher tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        selectedVoucherId = voucher.id
        voucherCode = voucher.code
        voucherAmount = voucher.nominal
        binding.btnVoucher.text = "VOUCHER"
        renderTotals()
    }

    private fun clearVoucher() {
        selectedVoucherId = null
        voucherCode = null
        voucherAmount = 0.0
        binding.btnVoucher.text = "VOUCHER"
        renderTotals()
    }

    private fun showPaymentMethodDialog() {
        val methods = arrayOf("Card", "QRIS")
        AlertDialog.Builder(this)
            .setTitle("Metode Pembayaran")
            .setSingleChoiceItems(methods, if (selectedFeatureType == FeatureType.QRIS) 1 else 0) { dialog, which ->
                selectedFeatureType = if (which == 0) FeatureType.CARD else FeatureType.QRIS
                binding.btnPaymentMethod.text = methods[which]
                renderAppliedSummary()
                dialog.dismiss()
            }
            .show()
    }

    private fun renderAppliedSummary() {
        val container = binding.appliedSummaryContainer
        container.removeAllViews()

        if (discountAmount > 0) {
            addAppliedRow(
                title = "Diskon",
                subtitle = memberCode?.let { "Member - $it" } ?: "Applied discount",
                amount = "-${formatRupiah(discountAmount)}",
                iconRes = R.drawable.ic_discount,
                iconBgRes = R.drawable.bg_icon_discount,
                rowBgRes = R.drawable.bg_applied_discount,
                amountColor = "#F97316",
                onRemove = { clearDiscountMember() }
            )
        }

        if (voucherAmount > 0 && !voucherCode.isNullOrBlank()) {
            addAppliedRow(
                title = "Voucher",
                subtitle = voucherCode.orEmpty(),
                amount = formatRupiah(voucherAmount),
                iconRes = R.drawable.ic_voucher,
                iconBgRes = R.drawable.bg_icon_voucher,
                rowBgRes = R.drawable.bg_applied_voucher,
                amountColor = "#059669",
                onRemove = { clearVoucher() }
            )
        }

        container.visibility = if (container.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun addAppliedRow(
        title: String,
        subtitle: String,
        amount: String,
        iconRes: Int,
        iconBgRes: Int,
        rowBgRes: Int,
        amountColor: String,
        onRemove: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(rowBgRes)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }

        val iconFrame = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(iconBgRes)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(17), dp(17))
        }
        iconFrame.addView(icon)

        val textGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(10), 0, dp(8), 0)
            }
        }
        textGroup.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            includeFontPadding = false
        })
        textGroup.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        })

        val amountView = TextView(this).apply {
            text = amount
            setTextColor(Color.parseColor(amountColor))
            textSize = 12f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val removeView = TextView(this).apply {
            text = "x"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { onRemove() }
        }

        row.addView(iconFrame)
        row.addView(textGroup)
        row.addView(amountView)
        row.addView(removeView)
        binding.appliedSummaryContainer.addView(row)
    }

    private fun clearDiscountMember() {
        if (memberCode.isNullOrBlank()) return

        ApiClient.api.updateOrderMemberCode(orderId, OrderMemberCodeRequest(null))
            .enqueue(object : Callback<OrderMemberCodeResponse> {
                override fun onResponse(
                    call: Call<OrderMemberCodeResponse>,
                    response: Response<OrderMemberCodeResponse>
                ) {
                    if (response.isSuccessful) {
                        memberCode = null
                        refreshOrderDetail()
                    } else {
                        Toast.makeText(this@PaymentActivity, getErrorMessage(response), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<OrderMemberCodeResponse>, t: Throwable) {
                    Toast.makeText(this@PaymentActivity, t.message ?: "Gagal menghapus diskon", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun completePayment() {
        if (orderId == 0 || itemSaleId == 0) {
            Toast.makeText(this, "Data transaksi tidak lengkap", Toast.LENGTH_SHORT).show()
            return
        }

        val amountToPay = remainingAmount().roundToLong()
        if (amountToPay <= 0) {
            markPaymentCompleted()
            return
        }

        val featureType = selectedFeatureType
        if (featureType == null) {
            Toast.makeText(this, "Pilih metode pembayaran dulu", Toast.LENGTH_SHORT).show()
            return
        }

        launchAposPayment(featureType, amountToPay)
    }

    private fun launchAposPayment(featureType: FeatureType, amount: Long) {
        val service = aposManager.aposService
        if (service == null) {
            aposManager.connect()
            Toast.makeText(this, "Service APOS belum terhubung, coba lagi sebentar", Toast.LENGTH_SHORT).show()
            return
        }

        val aposState = runCatching { service.getAposState(featureType.uriSuffix) }.getOrNull()
        when (aposState) {
            APOS_READY_STATE -> Unit
            SETTLEMENT_REQUIRED_STATE -> {
                Toast.makeText(this, "APOS meminta settlement sebelum transaksi", Toast.LENGTH_LONG).show()
                return
            }
            APOS_INACTIVE_STATE -> {
                Toast.makeText(this, "APOS sedang tidak aktif", Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                Toast.makeText(this, "Status APOS tidak tersedia", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val partnerRefId = transactionId()
        val transactionData = prepareTransactionData(partnerRefId, amount)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("android-app://com.bca.apos/${featureType.uriSuffix}")
                .buildUpon()
                .appendQueryParameter("TRANSACTION_DATA", transactionData)
                .build()
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        pendingPartnerRefId = partnerRefId
        didLaunchApos = true

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            didLaunchApos = false
            Toast.makeText(this, "Aplikasi APOS BCA tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareTransactionData(partnerRefId: String, amount: Long): String? {
        val serialNumber = runCatching { aposManager.aposService?.sn }.getOrNull()
            ?: Build.DEVICE
        val signature = DeepLinkEncryptionUtil().generateSignature(serialNumber)
        val jsonString = """
            {
                "PARTNER_REF_ID": "$partnerRefId",
                "AMOUNT": "$amount",
                "SIGNATURE": "$signature"
            }
        """.trimIndent()

        return DeepLinkEncryptionUtil().encrypt(jsonString)
    }

    private fun inquiryAposTransaction() {
        val partnerRefId = pendingPartnerRefId ?: return
        val inquiry = runCatching {
            aposManager.aposService?.inquiry(partnerRefId, InquiryFlag.SINGLE.value)
        }.getOrNull()

        when (inquiry?.txStatus) {
            TransactionStatus.SUCCESS -> markPaymentCompleted()
            TransactionStatus.PENDING -> Toast.makeText(this, "Transaksi APOS masih pending", Toast.LENGTH_LONG).show()
            TransactionStatus.NOT_FOUND -> Toast.makeText(this, "Transaksi APOS belum ditemukan", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, "Transaksi APOS belum berhasil", Toast.LENGTH_LONG).show()
        }
    }

    private fun markPaymentCompleted() {
        if (userId == 0) {
            Toast.makeText(this, "User ID tidak tersedia untuk menyelesaikan payment", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCompletePayment.isEnabled = false
        ApiClient.api.completePayment(
            PaymentRequest(
                orderId = orderId,
                itemSaleId = itemSaleId,
                itemSaleCounter = itemSaleCounter,
                downPaymentId = selectedDownPayment?.id,
                paymentMethod = selectedFeatureType?.uriSuffix?.uppercase(Locale.US),
                voucherCode = voucherCode,
                voucherId = selectedVoucherId,
                voucherAmount = voucherAmount,
                aposPartnerRefId = pendingPartnerRefId,
                userId = userId,
                userName = userName
            )
        ).enqueue(object : Callback<PaymentResponse> {
            override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                binding.btnCompletePayment.isEnabled = true
                if (response.isSuccessful) {
                    Toast.makeText(this@PaymentActivity, "Payment berhasil", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@PaymentActivity, getErrorMessage(response), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                binding.btnCompletePayment.isEnabled = true
                Toast.makeText(this@PaymentActivity, t.message ?: "Payment gagal", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getErrorMessage(response: Response<*>): String {
        val rawError = response.errorBody()?.string().orEmpty()
        return getErrorMessage(rawError)
    }

    private fun getErrorMessage(rawError: String): String {
        if (rawError.isBlank()) return "Terjadi kesalahan"

        return runCatching {
            val json = JSONObject(rawError)
            json.optString("message")
                .ifBlank { json.optString("error") }
                .ifBlank { rawError }
        }.getOrDefault(rawError)
    }

    private fun formatRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ")
    }

    private fun formatPlain(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    private fun transactionId(): String {
        val counter = if (itemSaleCounter > 0) itemSaleCounter else itemSaleId
        return counter.toString()
    }

    companion object {
        private const val APOS_READY_STATE = 0
        private const val SETTLEMENT_REQUIRED_STATE = 1
        private const val APOS_INACTIVE_STATE = 2
    }
}
