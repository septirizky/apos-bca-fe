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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandjak.pos.R
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.apos.AposManager
import com.bandjak.pos.databinding.ActivityOrderDetailBinding
import com.bandjak.pos.model.BillInitiationPrintRequest
import com.bandjak.pos.model.BillInitiationPrintResponse
import com.bandjak.pos.model.BranchNameResponse
import com.bandjak.pos.model.DiscountValidateRequest
import com.bandjak.pos.model.DiscountValidateResponse
import com.bandjak.pos.model.EpsonPrintResponse
import com.bandjak.pos.model.EpsonPrintTestRequest
import com.bandjak.pos.model.OrderDetail
import com.bandjak.pos.model.OrderDetailResponse
import com.bandjak.pos.model.OrderLockRequest
import com.bandjak.pos.model.OrderLockResponse
import com.bandjak.pos.model.OrderMemberCodeRequest
import com.bandjak.pos.model.OrderMemberCodeResponse
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.ui.login.LoginActivity
import com.bandjak.pos.util.AutoRefreshTimer
import com.bca.apos.PartnerPrinterListener
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
    private var currentDetailData: OrderDetailResponse? = null
    private lateinit var aposManager: AposManager
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
        aposManager = AposManager(applicationContext)
        aposManager.connect()
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

        binding.btnPrint.setOnClickListener {
            showPrintDestinationDialog()
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
        aposManager.disconnect()
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
                    currentDetailData = data
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

    private fun showPrintDestinationDialog() {
        val data = currentDetailData
        if (data == null) {
            Toast.makeText(this, "Data order belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        val epsonIp = ApiClient.getEpsonPrinterIp(applicationContext)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        content.addView(TextView(this).apply {
            text = "Cetak Bill"
            textSize = 20f
            setTextColor(Color.parseColor("#0F172A"))
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Pilih tujuan printer untuk bill inisiasi."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(16)) }
        })

        content.addView(printOptionRow(
            title = "Epson TM-U220",
            subtitle = if (epsonIp.isBlank()) "IP belum diatur" else epsonIp,
            iconColor = "#0EA5E9"
        ) {
            dialog.dismiss()
            if (epsonIp.isBlank()) {
                Toast.makeText(this, "IP printer Epson belum diisi di Settings", Toast.LENGTH_LONG).show()
            } else {
                printInitiationBill("EPSON", epsonIp)
            }
        })

        content.addView(printOptionRow(
            title = "EDC APOS",
            subtitle = "Cetak langsung di mesin EDC",
            iconColor = "#1A05A3"
        ) {
            dialog.dismiss()
            printInitiationBill("EDC", null)
        })

        content.addView(TextView(this).apply {
            text = "BATAL"
            textSize = 13f
            setTextColor(Color.parseColor("#1A05A3"))
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun printInitiationBill(printerTarget: String, printerIp: String?) {
        binding.btnPrint.isEnabled = false
        ApiClient.api.printBillInitiation(
            BillInitiationPrintRequest(
                orderId = currentOrderId,
                userId = intent.getIntExtra("U_ID", 0),
                userName = intent.getStringExtra("U_NAME"),
                printerTarget = printerTarget,
                printerIp = printerIp,
                printerPort = ApiClient.getEpsonPrinterPort(applicationContext),
                posId = ApiClient.getPosId(applicationContext),
                posIp = localIpAddress()
            )
        ).enqueue(object : Callback<BillInitiationPrintResponse> {
            override fun onResponse(
                call: Call<BillInitiationPrintResponse>,
                response: Response<BillInitiationPrintResponse>
            ) {
                binding.btnPrint.isEnabled = true
                val body = response.body()
                if (!response.isSuccessful || body?.success != true) {
                    Toast.makeText(this@OrderDetailActivity, body?.message ?: getErrorMessage(response), Toast.LENGTH_LONG).show()
                    return
                }

                if (printerTarget == "EDC") {
                    printHtmlToEdc(
                        plainTextToHtml(body.lpMessage.orEmpty()),
                        successMessage = "Bill tercetak di EDC",
                        errorPrefix = "Print EDC gagal"
                    )
                } else {
                    Toast.makeText(this@OrderDetailActivity, body.message ?: "Bill terkirim ke Epson", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BillInitiationPrintResponse>, t: Throwable) {
                binding.btnPrint.isEnabled = true
                Toast.makeText(this@OrderDetailActivity, t.message ?: "Print bill gagal", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun printOptionRow(
        title: String,
        subtitle: String,
        iconColor: String,
        onClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(R.drawable.bg_payment_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                setImageResource(R.drawable.print)
                setColorFilter(Color.parseColor(iconColor))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = title
                    textSize = 15f
                    setTextColor(Color.parseColor("#0F172A"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                })
            })
        }
    }

    private fun printBillToEpson(data: OrderDetailResponse, printerIp: String) {
        binding.btnPrint.isEnabled = false
        ApiClient.api.printEpson(
            EpsonPrintTestRequest(
                printerIp = printerIp,
                printerPort = ApiClient.getEpsonPrinterPort(applicationContext),
                printerName = "EPSON TM-U220",
                content = buildBillText(data, includeEpsonCommands = true)
            )
        ).enqueue(object : Callback<EpsonPrintResponse> {
            override fun onResponse(
                call: Call<EpsonPrintResponse>,
                response: Response<EpsonPrintResponse>
            ) {
                binding.btnPrint.isEnabled = true
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Toast.makeText(this@OrderDetailActivity, "Bill terkirim ke Epson", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OrderDetailActivity, body?.message ?: getErrorMessage(response), Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<EpsonPrintResponse>, t: Throwable) {
                binding.btnPrint.isEnabled = true
                Toast.makeText(this@OrderDetailActivity, t.message ?: "Print Epson gagal", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun printBillToEdc(data: OrderDetailResponse) {
        printHtmlToEdc(
            buildBillHtml(data),
            successMessage = "Bill tercetak di EDC",
            errorPrefix = "Print EDC gagal"
        )
    }

    private fun printHtmlToEdc(html: String, successMessage: String, errorPrefix: String) {
        val service = aposManager.aposService
        if (service == null) {
            aposManager.connect()
            Toast.makeText(this, "Service APOS belum terhubung, coba lagi sebentar", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnPrint.isEnabled = false
        runCatching {
            service.startPrint(
                html,
                object : PartnerPrinterListener.Stub() {
                    override fun onFinish() {
                        runOnUiThread {
                            binding.btnPrint.isEnabled = true
                            Toast.makeText(this@OrderDetailActivity, successMessage, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onError(code: Int, message: String?) {
                        runOnUiThread {
                            binding.btnPrint.isEnabled = true
                            Toast.makeText(
                                this@OrderDetailActivity,
                                "$errorPrefix: ${message ?: code.toString()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }.onFailure {
            binding.btnPrint.isEnabled = true
            Toast.makeText(this, it.message ?: errorPrefix, Toast.LENGTH_LONG).show()
        }
    }

    private fun plainTextToHtml(text: String): String {
        fun escape(value: String): String {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        val receiptHtml = text.lines().joinToString("\n") { line ->
            val escaped = escape(line)
            when {
                line.trim().startsWith("Table ") -> "<span class=\"table-line\">$escaped</span>"
                line.startsWith("Total ") &&
                    !line.startsWith("Total Bef.") &&
                    !line.startsWith("Total Discount") -> "<span class=\"grand-total\">$escaped</span>"
                line == "TAGIHAN SEMENTARA" -> "<span class=\"title-line\">$escaped</span>"
                else -> escaped
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    @page { size: 58mm auto; margin: 0; }
                    body {
                        margin: 0;
                        padding: 0 2mm;
                        font-family: "Droid Sans Mono", "Courier New", monospace;
                        font-size: 10px;
                        line-height: 1.08;
                        color: #000;
                    }
                    pre {
                        white-space: pre;
                        margin: 0;
                        font: inherit;
                    }
                    .title-line {
                        font-weight: 700;
                    }
                    .table-line {
                        display: block;
                        width: 100%;
                        text-align: center;
                        font-size: 18px;
                        font-weight: 700;
                        line-height: 1.05;
                    }
                    .grand-total {
                        display: block;
                        font-size: 19px;
                        font-weight: 700;
                        line-height: 1.08;
                    }
                </style>
            </head>
            <body><pre>$receiptHtml</pre></body>
            </html>
        """.trimIndent()
    }

    private fun buildBillText(data: OrderDetailResponse, includeEpsonCommands: Boolean = false): String {
        val normal = if (includeEpsonCommands) "\u001B!\u0000" else ""
        val big = if (includeEpsonCommands) "\u001B!\u0010" else ""
        val center = if (includeEpsonCommands) "\u001Ba\u0001" else ""
        val left = if (includeEpsonCommands) "\u001Ba\u0000" else ""
        val printWidth = 33
        val line = "-".repeat(printWidth)
        val doubleLine = "=".repeat(printWidth)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        val printBy = intent.getStringExtra("U_NAME") ?: "-"
        val waiter = data.waiterName ?: currentWaiterName ?: "-"
        val table = data.tName ?: currentTableName ?: currentOrderId.toString()
        val tableArea = data.tableAreaName?.takeIf { it.isNotBlank() }
        val tableDisplay = if (tableArea == null) "Table $table" else "Table $table/$tableArea"
        val initiated = formatOrderDateTime(data.orderStartTime) ?: now
        val pax = data.pax?.takeIf { it > 0 } ?: 1
        val text = StringBuilder()

        if (includeEpsonCommands) {
            text.append(center).append(big).appendLine("TAGIHAN SEMENTARA")
            text.append(left).append(normal)
        } else {
            text.appendLine("TAGIHAN SEMENTARA")
        }
        text.appendLine()
        text.appendLine("Harap menunggu Final Bill untuk")
        text.appendLine("penyelesaian pembayaran")
        text.appendLine(line)
        text.appendLine("Initiated : $initiated")
        text.appendLine("Settled   : NOT SETTLED")
        text.appendLine("Waiter    : $waiter")
        text.appendLine("Print by  : $printBy")
        text.appendLine(line)
        text.appendLine("$pax  Pax")
        if (includeEpsonCommands) {
            text.append(center).append(big).appendLine(tableDisplay)
            text.append(left).append(normal)
        } else {
            text.appendLine(tableDisplay)
        }
        text.appendLine(doubleLine)

        data.items.forEach { item ->
            if (includeEpsonCommands) text.append(left).append(normal)
            text.appendLine(item.odName.ifBlank { item.item.iName })
            text.appendLine(formatPrintItemLine(item, printWidth))
            item.discounts?.filter { it.isApplied }?.forEach { discount ->
                val percent = discount.ddValue ?: discount.discountPercent
                val percentText = if (discount.isMaxDiscountCapped == true) "" else "[${formatPercent(percent)}%] "
                val discountText = "-${formatNumber(discount.discountAmount)}"
                text.appendLine(" $percentText${discountText.padStart((printWidth - percentText.length - 1).coerceAtLeast(discountText.length))}")
            }
        }

        text.appendLine(line)
        text.appendLine(formatPrintTotalLine("Food Total", data.summary.foodTotal, printWidth))
        if (data.summary.beverageTotal > 0) text.appendLine(formatPrintTotalLine("Beverage Total", data.summary.beverageTotal, printWidth))
        text.appendLine()
        if (data.summary.discountTotal > 0) text.appendLine(formatPrintTotalLine("Total Discount", -data.summary.discountTotal, printWidth))
        text.appendLine(formatPrintTotalLine("Subtotal", data.summary.subtotal, printWidth))
        if (data.summary.cookingCharge > 0) text.appendLine(formatPrintTotalLine("Cooking Charge", data.summary.cookingCharge, printWidth))
        text.appendLine(formatPrintTotalLine("PBJT      10.00%", data.summary.pbjt, printWidth))
        if (includeEpsonCommands) {
            text.append(big).appendLine(formatPrintTotalLine("Total", data.summary.total, printWidth))
            text.append(normal).append(left)
        } else {
            text.appendLine(formatPrintTotalLine("Total", data.summary.total, printWidth))
        }
        text.appendLine(line)
        text.appendLine("TEMP BILL / TAGIHAN SEMENTARA")
        text.appendLine()
        text.appendLine("Harap periksa kembali tagihan")
        text.appendLine("anda sebelum anda membayar")
        text.appendLine()
        text.appendLine()
        text.appendLine()
        text.appendLine()
        text.appendLine()
        text.appendLine()

        return text.toString()
    }

    private fun buildBillHtml(data: OrderDetailResponse): String {
        fun escape(value: String): String {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        val receiptHtml = buildBillText(data, includeEpsonCommands = false)
            .lines()
            .joinToString("\n") { line ->
                val escaped = escape(line)
                when {
                    line.trim().startsWith("Table ") -> "<span class=\"table-line\">$escaped</span>"
                    line.startsWith("Total ") -> "<span class=\"grand-total\">$escaped</span>"
                    line == "TAGIHAN SEMENTARA" -> "<span class=\"title-line\">$escaped</span>"
                    else -> escaped
                }
            }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    @page { size: 2.8in 11in; margin: 0; }
                    body { margin: 0; padding: 8px; font-family: monospace; font-size: 13px; }
                    pre { white-space: pre-wrap; margin: 0; }
                    .title-line { font-size: 18px; font-weight: 700; }
                    .table-line {
                        display: block;
                        width: 100%;
                        text-align: center;
                        font-size: 18px;
                        font-weight: 700;
                    }
                    .grand-total { font-size: 19px; font-weight: 700; }
                </style>
            </head>
            <body><pre>$receiptHtml</pre></body>
            </html>
        """.trimIndent()
    }

    private fun formatPrintItemLine(item: OrderDetail, width: Int = 32): String {
        val left = " ${item.qty}    x ${formatNumber(item.sellPrice)}"
        val right = "= ${formatNumber(item.itemTotal)}"
        return left.padEnd((width - right.length).coerceAtLeast(left.length + 1)) + right
    }

    private fun formatPrintTotalLine(label: String, amount: Double, width: Int = 32): String {
        val right = formatNumber(amount)
        return label.take(18).padEnd((width - right.length).coerceAtLeast(label.length + 1)) + right
    }

    private fun formatNumber(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    private fun formatPercent(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatOrderDateTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    if (pattern.contains("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)
            }.getOrNull()
            if (parsed != null) {
                return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(parsed)
            }
        }
        return raw.take(19).replace("T", " ")
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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
