package com.bandjak.pos.ui.orders

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.button.MaterialButton
import com.bandjak.pos.R
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.apos.AposManager
import com.bandjak.pos.apos.DeepLinkEncryptionUtil
import com.bandjak.pos.databinding.ActivityPaymentBinding
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.DiscountValidateRequest
import com.bandjak.pos.model.DiscountValidateResponse
import com.bandjak.pos.model.DownPayment
import com.bandjak.pos.model.OrderDetailResponse
import com.bandjak.pos.model.OrderLockRequest
import com.bandjak.pos.model.OrderLockResponse
import com.bandjak.pos.model.OrderMemberCodeRequest
import com.bandjak.pos.model.OrderMemberCodeResponse
import com.bandjak.pos.model.PaymentRequest
import com.bandjak.pos.model.PaymentResponse
import com.bandjak.pos.model.PiMlpLogRequest
import com.bandjak.pos.model.PiMlpLogResponse
import com.bandjak.pos.model.ReceiptInfoResponse
import com.bandjak.pos.model.Voucher
import com.bandjak.pos.model.VoucherValidateRequest
import com.bandjak.pos.model.VoucherValidateResponse
import com.bandjak.pos.ui.login.LoginActivity
import com.bca.apos.FeatureType
import com.bca.apos.InquiryFlag
import com.bca.apos.PartnerInquiryData
import com.bca.apos.TransactionStatus
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var aposManager: AposManager
    private lateinit var voucherPrefs: SharedPreferences

    private var orderId = 0
    private var itemSaleId = 0
    private var itemSaleCounter = 0
    private var userId = 0
    private var userName: String? = null
    private var waiterName: String? = null
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
    private var lastAposInquiry: PartnerInquiryData? = null
    private var didLaunchApos = false
    private var isCompletingPayment = false
    private var lastActionClickAt = 0L
    private var currentBranchName = "BANDAR DJAKARTA"
    private var receiptInfoLines = listOf<String>()
    private var currentOrderDetail: OrderDetailResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getIntExtra("ORDER_ID", 0)
        itemSaleId = intent.getIntExtra("IS_ID", 0)
        itemSaleCounter = intent.getIntExtra("IS_COUNTER", 0)
        userId = intent.getIntExtra("U_ID", 0)
        userName = intent.getStringExtra("U_NAME")
        waiterName = intent.getStringExtra("WAITER_NAME")
        tableName = intent.getStringExtra("TABLE_NAME") ?: "-"
        memberCode = intent.getStringExtra("MEMBER_CODE")
        totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
        discountAmount = intent.getDoubleExtra("DISCOUNT_AMOUNT", 0.0)
        voucherPrefs = getSharedPreferences(VOUCHER_PREFS_NAME, MODE_PRIVATE)
        restoreVoucherState()

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
        binding.btnDownPayment.setSafeClickListener { showDownPaymentDialog() }
        binding.btnDiscount.setSafeClickListener { showDiscountDialog() }
        binding.btnVoucher.setSafeClickListener { showVoucherDialog() }
        binding.btnPaymentMethod.setSafeClickListener { showPaymentMethodDialog() }
        binding.btnPreviewReceipt.setSafeClickListener {
            showReceiptPreview()
        }
        binding.btnCompletePayment.setSafeClickListener { completePayment() }
    }

    private fun View.setSafeClickListener(onClick: (View) -> Unit) {
        setOnClickListener { view ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastActionClickAt < 700L) return@setOnClickListener
            lastActionClickAt = now
            onClick(view)
        }
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
        loadReceiptInfo()
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

    private fun loadReceiptInfo() {
        ApiClient.api.getReceiptInfo().enqueue(object : Callback<ReceiptInfoResponse> {
            override fun onResponse(call: Call<ReceiptInfoResponse>, response: Response<ReceiptInfoResponse>) {
                if (!response.isSuccessful) return

                val info = response.body() ?: return
                receiptInfoLines = listOfNotNull(
                    info.info1?.takeIf { it.isNotBlank() },
                    info.info2?.takeIf { it.isNotBlank() },
                    info.info3?.takeIf { it.isNotBlank() }
                )
            }

            override fun onFailure(call: Call<ReceiptInfoResponse>, t: Throwable) {
                receiptInfoLines = emptyList()
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
                waiterName = data.waiterName ?: waiterName
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
            text = receiptInfoLines.joinToString("\n")
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
        paper.addView(receiptInfoRow("WAITER: ${waiterName ?: "-"}", "CASHIER: ${userName ?: "-"}"))
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
        binding.btnCompletePayment.text = "Selesaikan Pembayaran"
        renderDownPaymentButton()
        renderAppliedSummary()
    }

    private fun renderDownPaymentButton() {
        val downPayment = selectedDownPayment
        if (downPayment == null) {
            binding.btnDownPayment.text = "DOWNPAYMENT"
            binding.btnDownPayment.maxLines = 1
            binding.btnDownPayment.ellipsize = TextUtils.TruncateAt.END
            binding.btnDownPayment.textSize = 9f
            binding.btnDownPayment.icon = getDrawable(R.drawable.ic_downpayment)
            binding.btnDownPayment.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TOP
            binding.btnDownPayment.iconPadding = dp(4)
            return
        }

        val title = downPayment.name.trim().ifBlank { "Downpayment" }
        val contact = downPayment.contact?.trim().orEmpty()
        binding.btnDownPayment.text = if (contact.isBlank()) title else "$title\n$contact"
        binding.btnDownPayment.maxLines = 2
        binding.btnDownPayment.ellipsize = TextUtils.TruncateAt.END
        binding.btnDownPayment.textSize = 9f
        binding.btnDownPayment.icon = null
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

                showDownPaymentPicker(downPayments)
            }

            override fun onFailure(call: Call<List<DownPayment>>, t: Throwable) {
                Toast.makeText(this@PaymentActivity, t.message ?: "Gagal memuat downpayment", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDownPaymentPicker(downPayments: List<DownPayment>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "Pilih Downpayment"
            textSize = 19f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Cari berdasarkan nama atau kontak tamu"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(3), 0, dp(12))
            }
        })

        val searchInput = EditText(this).apply {
            hint = "Cari nama / kontak..."
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.bg_down_payment_search)
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        }
        root.addView(searchInput)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, dp(12), 0, dp(10))
            }
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val emptyView = TextView(this).apply {
            text = "Ketik nama, kontak, atau nominal downpayment"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
            visibility = View.VISIBLE
        }
        scroll.addView(listContainer)
        root.addView(scroll)
        root.addView(emptyView)

        val actionRow = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val removeButton = TextView(this).apply {
            text = "HAPUS"
            textSize = 13f
            setTextColor(Color.parseColor("#E11D48"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            visibility = if (selectedDownPayment == null) View.GONE else View.VISIBLE
            setOnClickListener {
                selectedDownPayment = null
                renderTotals()
                dialog.dismiss()
            }
        }
        val cancelButton = TextView(this).apply {
            text = "TUTUP"
            textSize = 13f
            setTextColor(Color.parseColor("#1A05A3"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(14), dp(10), dp(4), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        actionRow.addView(removeButton)
        actionRow.addView(cancelButton)
        root.addView(actionRow)

        fun render(keyword: String = "") {
            val query = keyword.trim()
            if (query.isBlank()) {
                listContainer.removeAllViews()
                emptyView.text = "Ketik nama, kontak, atau nominal downpayment"
                emptyView.visibility = View.VISIBLE
                scroll.visibility = View.GONE
                return
            }

            val needle = query.lowercase(Locale.ROOT)
            val filtered = downPayments.filter {
                it.name.lowercase(Locale.ROOT).contains(needle) ||
                    it.contact.orEmpty().lowercase(Locale.ROOT).contains(needle) ||
                    it.amount.lowercase(Locale.ROOT).contains(needle) ||
                    formatPlain(it.amount.toDoubleOrNull() ?: 0.0).lowercase(Locale.ROOT).contains(needle)
            }

            listContainer.removeAllViews()
            emptyView.text = "Downpayment tidak ditemukan"
            emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            scroll.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE

            filtered.forEach { downPayment ->
                listContainer.addView(createDownPaymentRow(downPayment, dialog))
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        render()

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.78f).toInt()
        )
    }

    private fun createDownPaymentRow(downPayment: DownPayment, dialog: Dialog): View {
        val isSelected = selectedDownPayment?.id != null && selectedDownPayment?.id == downPayment.id
        val amount = downPayment.amount.toDoubleOrNull() ?: 0.0

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(
                if (isSelected) R.drawable.bg_down_payment_row_selected else R.drawable.bg_down_payment_row
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
            setOnClickListener {
                selectedDownPayment = downPayment
                renderTotals()
                dialog.dismiss()
            }

            val textGroup = LinearLayout(this@PaymentActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            }
            textGroup.addView(TextView(this@PaymentActivity).apply {
                text = downPayment.name
                textSize = 14f
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            textGroup.addView(TextView(this@PaymentActivity).apply {
                text = downPayment.contact?.takeIf { it.isNotBlank() } ?: "Kontak tidak tersedia"
                textSize = 11f
                setTextColor(Color.parseColor("#64748B"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            addView(textGroup)
            addView(TextView(this@PaymentActivity).apply {
                text = formatRupiah(amount)
                textSize = 13f
                setTextColor(Color.parseColor(if (isSelected) "#1A05A3" else "#334155"))
                setTypeface(Typeface.DEFAULT_BOLD)
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
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

        editCode.setText(memberCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        btnRemove.visibility = if (memberCode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
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
        btnRemove.setOnClickListener { applyMemberCode(dialog, btnRemove, null) }
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
        val btnCheck = dialog.findViewById<Button>(R.id.btnCheckVoucher)
        val btnApply = dialog.findViewById<Button>(R.id.btnApplyVoucher)
        val infoCard = dialog.findViewById<LinearLayout>(R.id.voucherInfoCard)
        val txtGroup = dialog.findViewById<TextView>(R.id.txtVoucherGroup)
        val txtStatus = dialog.findViewById<TextView>(R.id.txtVoucherStatus)
        val txtPeriod = dialog.findViewById<TextView>(R.id.txtVoucherPeriod)
        val txtValue = dialog.findViewById<TextView>(R.id.txtVoucherValue)
        var checkedVoucher: Voucher? = null

        editCode.setText(voucherCode.orEmpty())
        editCode.setSelection(editCode.text.length)
        btnRemove.visibility = if (voucherCode.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        btnApply.isEnabled = false

        editCode.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                checkedVoucher = null
                btnApply.isEnabled = false
                infoCard.visibility = View.GONE
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnRemove.setOnClickListener {
            clearVoucher()
            dialog.dismiss()
        }
        btnCheck.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isBlank()) {
                showVoucherInfo(infoCard, txtGroup, txtStatus, txtPeriod, txtValue, "Kode voucher belum diisi", "Masukkan kode voucher", "-", "-", false)
            } else {
                validateVoucher(
                    code = code,
                    allowExpired = false,
                    actionButton = btnCheck,
                    onValid = { voucher ->
                        checkedVoucher = voucher
                        btnApply.isEnabled = true
                        showVoucherInfo(
                            infoCard,
                            txtGroup,
                            txtStatus,
                            txtPeriod,
                            txtValue,
                            "Kelompok: ${voucher.setName ?: "-"}",
                            "Status: ${voucher.status ?: "-"}${if (voucher.expired) " (Expired)" else ""}",
                            "Berlaku: ${voucher.startDate ?: "-"} s/d ${voucher.endDate ?: "-"}",
                            formatRupiah(voucher.nominal),
                            true
                        )
                    },
                    onInvalid = { message ->
                        checkedVoucher = null
                        btnApply.isEnabled = false
                        showVoucherInfo(infoCard, txtGroup, txtStatus, txtPeriod, txtValue, "Voucher tidak ditemukan", message, "-", "-", false)
                    }
                )
            }
        }
        btnApply.setOnClickListener {
            val voucher = checkedVoucher
            if (voucher == null) {
                showVoucherInfo(infoCard, txtGroup, txtStatus, txtPeriod, txtValue, "Cek voucher terlebih dahulu", "Tekan tombol Cek Voucher sebelum menerapkan", "-", "-", false)
            } else {
                applyVoucher(voucher)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun validateVoucher(
        code: String,
        allowExpired: Boolean,
        actionButton: Button,
        onValid: (Voucher) -> Unit,
        onInvalid: (String) -> Unit
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
                        response.body()?.voucher?.let(onValid)
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
                                validateVoucher(code, allowExpired = true, actionButton = actionButton, onValid = onValid, onInvalid = onInvalid)
                            }
                            .setNegativeButton("Tidak", null)
                            .show()
                    } else {
                        onInvalid(getErrorMessage(rawError))
                    }
                }

                override fun onFailure(call: Call<VoucherValidateResponse>, t: Throwable) {
                    actionButton.isEnabled = true
                    onInvalid(t.message ?: "Gagal validasi voucher")
                }
            })
    }

    private fun showVoucherInfo(
        infoCard: LinearLayout,
        groupView: TextView,
        statusView: TextView,
        periodView: TextView,
        valueView: TextView,
        group: String,
        status: String,
        period: String,
        value: String,
        success: Boolean
    ) {
        infoCard.visibility = View.VISIBLE
        infoCard.setBackgroundResource(
            if (success) R.drawable.bg_down_payment_row_selected else R.drawable.bg_dialog_info_error
        )
        groupView.text = group
        groupView.setTextColor(Color.parseColor(if (success) "#0F172A" else "#E11D48"))
        statusView.text = status
        periodView.text = period
        valueView.text = value
        valueView.visibility = if (success) View.VISIBLE else View.GONE
    }

    private fun applyVoucher(voucher: Voucher?) {
        if (voucher == null) {
            Toast.makeText(this, "Voucher tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        selectedVoucherId = voucher.id
        voucherCode = voucher.code
        voucherAmount = voucher.nominal
        persistVoucherState()
        binding.btnVoucher.text = "VOUCHER"
        renderTotals()
    }

    private fun clearVoucher() {
        selectedVoucherId = null
        voucherCode = null
        voucherAmount = 0.0
        clearPersistedVoucherState()
        binding.btnVoucher.text = "VOUCHER"
        renderTotals()
    }

    private fun restoreVoucherState() {
        selectedVoucherId = voucherPrefs.getInt(voucherPrefKey("id"), 0).takeIf { it > 0 }
        voucherCode = voucherPrefs.getString(voucherPrefKey("code"), null)
        voucherAmount = Double.fromBits(voucherPrefs.getLong(voucherPrefKey("amount"), 0L))

        if (voucherCode.isNullOrBlank() || voucherAmount <= 0.0) {
            selectedVoucherId = null
            voucherCode = null
            voucherAmount = 0.0
        }
    }

    private fun persistVoucherState() {
        voucherPrefs.edit()
            .putInt(voucherPrefKey("id"), selectedVoucherId ?: 0)
            .putString(voucherPrefKey("code"), voucherCode)
            .putLong(voucherPrefKey("amount"), voucherAmount.toBits())
            .apply()
    }

    private fun clearPersistedVoucherState() {
        voucherPrefs.edit()
            .remove(voucherPrefKey("id"))
            .remove(voucherPrefKey("code"))
            .remove(voucherPrefKey("amount"))
            .apply()
    }

    private fun voucherPrefKey(field: String): String = "order_${orderId}_voucher_$field"

    private fun showPaymentMethodDialog() {
        val methods = arrayOf("Card", "QRIS")
        val checkedIndex = when (selectedFeatureType) {
            FeatureType.CARD -> 0
            FeatureType.QRIS -> 1
            else -> -1
        }
        AlertDialog.Builder(this)
            .setTitle("Metode Pembayaran")
            .setSingleChoiceItems(methods, checkedIndex) { dialog, which ->
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
        if (isCompletingPayment) return

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
        isCompletingPayment = true
        binding.btnCompletePayment.isEnabled = false

        val service = aposManager.aposService
        if (service == null) {
            aposManager.connect()
            isCompletingPayment = false
            binding.btnCompletePayment.isEnabled = true
            logAposEvent(
                call = APOS_SERVICE_CALL,
                request = mapOf(
                    "event" to "APOS_SERVICE_BIND",
                    "feature" to featureType.uriSuffix,
                    "amount" to amount.toString()
                ),
                response = mapOf("service" to null),
                statusCode = 503,
                errorCode = "APOS_SERVICE_NOT_CONNECTED",
                message = "Service APOS belum terhubung",
                success = false
            )
            Toast.makeText(this, "Service APOS belum terhubung, coba lagi sebentar", Toast.LENGTH_SHORT).show()
            return
        }

        val aposState = runCatching { service.getAposState(featureType.uriSuffix) }.getOrNull()
        logAposEvent(
            call = APOS_GET_STATE_CALL,
            request = mapOf("event" to "APOS_GET_STATE", "feature" to featureType.uriSuffix),
            response = mapOf("apos_state" to aposState),
            statusCode = if (aposState == null) 500 else 200,
            errorCode = if (aposState == null) "APOS_STATE_NULL" else null,
            message = aposStateLabel(aposState),
            success = aposState == APOS_READY_STATE
        )
        when (aposState) {
            APOS_READY_STATE -> Unit
            SETTLEMENT_REQUIRED_STATE -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                Toast.makeText(this, "APOS meminta settlement sebelum transaksi", Toast.LENGTH_LONG).show()
                return
            }
            APOS_INACTIVE_STATE -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                Toast.makeText(this, "APOS sedang tidak aktif", Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                Toast.makeText(this, "Status APOS tidak tersedia", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val partnerRefId = transactionId()
        val transactionData = prepareTransactionData(partnerRefId, amount)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("android-app://$DEFAULT_APOS_PACKAGE/${featureType.uriSuffix}")
                .buildUpon()
                .appendQueryParameter("TRANSACTION_DATA", transactionData)
                .build()
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        pendingPartnerRefId = partnerRefId
        didLaunchApos = true
        logAposEvent(
            call = "android-app://$DEFAULT_APOS_PACKAGE/${featureType.uriSuffix}",
            request = mapOf(
                "event" to "APOS_DEEP_LINK",
                "partner_ref_id" to partnerRefId,
                "amount" to amount.toString(),
                "feature" to featureType.uriSuffix,
                "transaction_data_length" to transactionData?.length?.toString()
            ),
            response = mapOf("deeplink" to "android-app://$DEFAULT_APOS_PACKAGE/${featureType.uriSuffix}"),
            statusCode = 200,
            message = "Launch APOS deep link",
            success = true
        )

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            didLaunchApos = false
            isCompletingPayment = false
            binding.btnCompletePayment.isEnabled = true
            logAposEvent(
                call = "android-app://$DEFAULT_APOS_PACKAGE/${featureType.uriSuffix}",
                request = mapOf(
                    "event" to "APOS_DEEP_LINK",
                    "partner_ref_id" to partnerRefId,
                    "feature" to featureType.uriSuffix
                ),
                response = mapOf("error" to e.message),
                statusCode = 404,
                errorCode = "APOS_APP_NOT_FOUND",
                message = "Aplikasi APOS BCA tidak ditemukan",
                success = false
            )
            showTransactionStatus(
                success = false,
                title = "Pembayaran Gagal",
                message = "Aplikasi APOS BCA tidak ditemukan"
            )
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
        lastAposInquiry = inquiry
        logAposEvent(
            call = APOS_INQUIRY_CALL,
            request = mapOf(
                "event" to "APOS_INQUIRY",
                "partner_ref_id" to partnerRefId,
                "flag" to InquiryFlag.SINGLE.value.toString()
            ),
            response = inquiry.toAposResponseMap(),
            statusCode = if (inquiry == null) 500 else 200,
            errorCode = if (inquiry == null) "APOS_INQUIRY_NULL" else null,
            message = inquiry?.txStatus?.value ?: "INQUIRY_NULL",
            success = inquiry?.txStatus == TransactionStatus.SUCCESS
        )

        when (inquiry?.txStatus) {
            TransactionStatus.SUCCESS -> markPaymentCompleted()
            TransactionStatus.PENDING -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                showTransactionStatus(
                    success = false,
                    title = "Pembayaran Pending",
                    message = "Transaksi APOS masih pending"
                )
            }
            TransactionStatus.NOT_FOUND -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                showTransactionStatus(
                    success = false,
                    title = "Pembayaran Gagal",
                    message = "Transaksi APOS belum ditemukan"
                )
            }
            else -> {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                showTransactionStatus(
                    success = false,
                    title = "Pembayaran Gagal",
                    message = "Transaksi APOS belum berhasil"
                )
            }
        }
    }

    private fun markPaymentCompleted() {
        if (isCompletingPayment && binding.btnCompletePayment.isEnabled) {
            binding.btnCompletePayment.isEnabled = false
        }
        isCompletingPayment = true

        if (userId == 0) {
            isCompletingPayment = false
            binding.btnCompletePayment.isEnabled = true
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
                aposTxStatus = lastAposInquiry?.txStatus?.value,
                aposFeatureType = lastAposInquiry?.featureType?.uriSuffix,
                aposTraceNo = lastAposInquiry?.traceNo,
                aposApprovalCode = lastAposInquiry?.approvalCode,
                aposRefNo = lastAposInquiry?.refNo,
                aposMerchantId = lastAposInquiry?.merchantId,
                aposTerminalId = lastAposInquiry?.terminalId,
                aposAcquirerType = lastAposInquiry?.acquirerType,
                posId = ApiClient.getPosId(applicationContext),
                posIp = localIpAddress(),
                userId = userId,
                userName = userName
            )
        ).enqueue(object : Callback<PaymentResponse> {
            override fun onResponse(call: Call<PaymentResponse>, response: Response<PaymentResponse>) {
                if (response.isSuccessful) {
                    clearPersistedVoucherState()
                    val responseCounter = response.body()?.data?.itemSaleCounter
                    if (responseCounter != null && responseCounter > 0) {
                        itemSaleCounter = responseCounter
                    }
                    showTransactionStatus(
                        success = true,
                        title = "Pembayaran Berhasil!",
                        message = "Payment berhasil"
                    )
                } else {
                    isCompletingPayment = false
                    binding.btnCompletePayment.isEnabled = true
                    showTransactionStatus(
                        success = false,
                        title = "Pembayaran Gagal",
                        message = getErrorMessage(response)
                    )
                }
            }

            override fun onFailure(call: Call<PaymentResponse>, t: Throwable) {
                isCompletingPayment = false
                binding.btnCompletePayment.isEnabled = true
                showTransactionStatus(
                    success = false,
                    title = "Pembayaran Gagal",
                    message = t.message ?: "Payment gagal"
                )
            }
        })
    }

    private fun showTransactionStatus(success: Boolean, title: String, message: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(24), dp(24), dp(22))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val header = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }
        val close = TextView(this).apply {
            text = "X"
            textSize = 30f
            setTextColor(Color.parseColor("#0F172A"))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
            setOnClickListener {
                if (success) {
                    dialog.dismiss()
                    returnToOrders()
                } else {
                    dialog.dismiss()
                }
            }
        }
        val headerTitle = TextView(this).apply {
            text = "Status Transaksi"
            textSize = 22f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        header.addView(headerTitle)
        header.addView(close)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(48))
        })
        content.addView(statusIcon(success))
        content.addView(TextView(this).apply {
            text = title
            textSize = 32f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(24), 0, dp(8)) }
        })
        content.addView(TextView(this).apply {
            text = "Total: ${formatRupiah(totalAmount)}"
            textSize = 23f
            setTextColor(Color.parseColor("#1A05A3"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.CENTER
        })

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_payment_card)
            setPadding(dp(22), dp(18), dp(22), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(28), 0, 0) }
        }
        infoCard.addView(statusInfoRow("Transaction ID", transactionId()))
        infoCard.addView(statusDivider())
        infoCard.addView(statusInfoRow("Metode", paymentMethodSummary()))
        infoCard.addView(statusDivider())
        infoCard.addView(statusInfoRow("Meja", tableName))
        infoCard.addView(statusDivider())
        infoCard.addView(statusInfoRow("Waktu", SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date())))
        if (!success && message.isNotBlank()) {
            infoCard.addView(statusDivider())
            infoCard.addView(statusInfoRow("Status", message))
        }
        content.addView(infoCard)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        actions.addView(MaterialButton(this).apply {
            text = if (success) "Cetak Ulang Struk" else "Lihat Preview Struk"
            isAllCaps = false
            textSize = 18f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#1A05A3"))
            icon = getDrawable(R.drawable.print)
            iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A05A3"))
            iconPadding = dp(10)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A05A3"))
            strokeWidth = dp(2)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply { setMargins(0, 0, 0, dp(14)) }
            setOnClickListener { showReceiptPreview() }
        })
        actions.addView(MaterialButton(this).apply {
            text = if (success) "Kembali ke Menu Utama" else "Kembali ke Pembayaran"
            isAllCaps = false
            textSize = 18f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A05A3"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
            setOnClickListener {
                dialog.dismiss()
                if (success) returnToOrders()
            }
        })
        actions.addView(TextView(this).apply {
            text = "${currentBranchName.uppercase(Locale.US)} POS"
            textSize = 16f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(28), 0, 0) }
        })

        root.addView(header)
        root.addView(content)
        root.addView(actions)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.show()
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun statusIcon(success: Boolean): View {
        val outer = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
        }
        val halo = TextView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(if (success) "#DCFCE7" else "#FEE2E2"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(120), dp(120), android.view.Gravity.CENTER)
        }
        val circle = TextView(this).apply {
            text = if (success) "OK" else "!"
            textSize = if (success) 26f else 48f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(if (success) "#16A34A" else "#DC2626"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(68), dp(68), android.view.Gravity.CENTER)
        }
        outer.addView(halo)
        outer.addView(circle)
        return outer
    }

    private fun statusInfoRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(38)
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = android.view.Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        })
        return row
    }

    private fun statusDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#E2E8F0"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )
    }

    private fun paymentMethodSummary(): String {
        val parts = mutableListOf<String>()
        if (selectedDownPayment != null) parts.add("DP")
        if (voucherAmount > 0) parts.add("Voucher")
        selectedFeatureType?.let { parts.add(it.displayName) }
        return when {
            parts.isEmpty() -> "-"
            parts.size == 1 -> parts.first()
            else -> "Split (${parts.joinToString(" + ")})"
        }
    }

    private fun returnToOrders() {
        val intent = Intent(this, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("U_ID", userId)
            putExtra("U_NAME", userName)
        }
        startActivity(intent)
        finish()
    }

    private fun releaseOrderLock(onDone: () -> Unit) {
        if (orderId == 0 || userId == 0) {
            onDone()
            return
        }

        ApiClient.api.unlockOrder(
            orderId,
            OrderLockRequest(
                userId = userId,
                posId = ApiClient.getPosId(applicationContext),
                posIp = localIpAddress()
            )
        ).enqueue(object : Callback<OrderLockResponse> {
            override fun onResponse(call: Call<OrderLockResponse>, response: Response<OrderLockResponse>) {
                onDone()
            }

            override fun onFailure(call: Call<OrderLockResponse>, t: Throwable) {
                Toast.makeText(this@PaymentActivity, "Payment berhasil, tetapi unlock meja gagal", Toast.LENGTH_SHORT).show()
                onDone()
            }
        })
    }

    private fun logAposEvent(
        call: String,
        request: Map<String, Any?>,
        response: Map<String, Any?>,
        statusCode: Int,
        errorCode: String? = null,
        message: String? = null,
        success: Boolean
    ) {
        if (userId == 0) return

        ApiClient.api.savePiMlpLog(
            PiMlpLogRequest(
                branchName = currentBranchName,
                userId = userId,
                userName = userName,
                call = call,
                request = request + mapOf(
                    "order_id" to orderId.toString(),
                    "is_id" to itemSaleId.toString(),
                    "pos_id" to ApiClient.getPosId(applicationContext)
                ),
                response = response,
                statusCode = statusCode,
                errorCode = errorCode,
                restMessage = message,
                success = success
            )
        ).enqueue(object : Callback<PiMlpLogResponse> {
            override fun onResponse(call: Call<PiMlpLogResponse>, response: Response<PiMlpLogResponse>) = Unit
            override fun onFailure(call: Call<PiMlpLogResponse>, t: Throwable) = Unit
        })
    }

    private val FeatureType.displayName: String
        get() = when (this) {
            FeatureType.CARD -> "Card"
            FeatureType.QRIS -> "QRIS"
            else -> name
        }

    private fun PartnerInquiryData?.toAposResponseMap(): Map<String, Any?> {
        if (this == null) return mapOf("inquiry" to null)

        return mapOf(
            "partnerRefId" to partnerRefId,
            "txStatus" to txStatus.value,
            "txStatusName" to txStatus.name,
            "featureType" to featureType.uriSuffix,
            "featureTypeName" to featureType.name,
            "merchantId" to merchantId,
            "terminalId" to terminalId,
            "paymentDateTime" to paymentDateTime,
            "approvalCode" to approvalCode,
            "batchNo" to batchNo,
            "traceNo" to traceNo,
            "refNo" to refNo,
            "amount" to amount?.toPlainString(),
            "tip" to tip?.toPlainString(),
            "fee" to fee?.toPlainString(),
            "cash" to cash?.toPlainString(),
            "dccAmount" to dccAmount?.toPlainString(),
            "dccTip" to dccTip?.toPlainString(),
            "acquirerType" to acquirerType
        )
    }

    private fun aposStateLabel(state: Int?): String = when (state) {
        APOS_READY_STATE -> "APOS_READY_STATE"
        SETTLEMENT_REQUIRED_STATE -> "SETTLEMENT_REQUIRED_STATE"
        APOS_INACTIVE_STATE -> "APOS_INACTIVE_STATE"
        null -> "APOS_STATE_NULL"
        else -> "APOS_STATE_$state"
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
        private const val APOS_SERVICE_CALL =
            "android-service://com.bca.apos/com.bca.apos.service.PartnerIntegrationService"
        private const val APOS_GET_STATE_CALL =
            "aidl://com.bca.apos.PartnerIntegrationAidl/getAposState"
        private const val APOS_INQUIRY_CALL =
            "aidl://com.bca.apos.PartnerIntegrationAidl/inquiry"
        private const val DEFAULT_APOS_PACKAGE = "com.bca.apos"
        private const val VOUCHER_PREFS_NAME = "payment_voucher_state"
    }
}
