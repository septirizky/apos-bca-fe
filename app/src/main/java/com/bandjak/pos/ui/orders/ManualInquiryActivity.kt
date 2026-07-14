package com.bandjak.pos.ui.orders

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.apos.AposDeepLink
import com.bandjak.pos.apos.AposManager
import com.bandjak.pos.apos.AposPendingStore
import com.bandjak.pos.apos.AposPendingTransaction
import com.bandjak.pos.databinding.ActivityManualInquiryBinding
import com.bandjak.pos.model.PiMlpLogRequest
import com.bandjak.pos.model.PiMlpLogResponse
import com.bca.apos.FeatureType
import com.bca.apos.InquiryFlag
import com.bca.apos.PartnerInquiryData
import com.bca.apos.PartnerIntegrationAidl
import com.bca.apos.TransactionStatus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Daftar transaksi APOS yang hasil akhirnya belum final, agar kasir bisa merekonsiliasinya.
 *
 * Ini melengkapi auto inquiry di PaymentActivity, yang hanya sempat jalan kalau aplikasi masih
 * hidup saat kasir kembali dari APOS. Kalau prosesnya mati di tengah jalan — atau QRIS baru lunas
 * beberapa saat kemudian — hanya layar inilah yang bisa menutup transaksinya.
 */
class ManualInquiryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualInquiryBinding
    private lateinit var adapter: PendingTransactionAdapter
    private lateinit var aposManager: AposManager
    private lateinit var pendingStore: AposPendingStore

    private var userId = 0
    private var userName: String? = null
    private var branchName: String? = null

    /** Diisi saat kasir dilempar ke layar APOS, dibaca kembali di onResume. */
    private var refIdAwaitingApos: String? = null

    private val aposConnectHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        binding = ActivityManualInquiryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("U_ID", 0)
        userName = intent.getStringExtra("U_NAME")
        branchName = intent.getStringExtra("BRANCH_NAME")

        pendingStore = AposPendingStore(applicationContext)
        aposManager = AposManager(applicationContext)
        aposManager.connect()

        binding.globalHeader.headerBackButton.visibility = View.VISIBLE
        binding.globalHeader.headerBackButton.setOnClickListener { finish() }
        branchName?.let { binding.globalHeader.headerBranchName.text = it }

        adapter = PendingTransactionAdapter(
            items = emptyList(),
            onCheckStatus = { transaction -> checkStatus(transaction) },
            onOpenApos = { transaction -> openAposManualInquiry(transaction) }
        )
        binding.pendingRecycler.layoutManager = LinearLayoutManager(this)
        binding.pendingRecycler.adapter = adapter

        binding.btnRefreshInquiry.setOnClickListener { refreshList() }

        refreshList()
    }

    override fun onResume() {
        super.onResume()

        // Kembali dari layar APOS: baca hasil otoritatifnya lewat AIDL, jangan percaya layar saja.
        val refId = refIdAwaitingApos
        refIdAwaitingApos = null
        if (refId != null) {
            pendingStore.find(refId)?.let { checkStatus(it) } ?: refreshList()
        } else {
            refreshList()
        }
    }

    override fun onDestroy() {
        aposManager.onConnected = null
        aposConnectHandler.removeCallbacksAndMessages(null)
        aposManager.disconnect()
        super.onDestroy()
    }

    private fun refreshList() {
        val pending = pendingStore.all()
        adapter.update(pending)
        binding.inquiryCountChip.text = "${pending.size} Transaksi"
        binding.emptyState.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        binding.pendingRecycler.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Manual inquiry lewat AIDL — jalur yang menentukan status sebenarnya. */
    private fun checkStatus(transaction: AposPendingTransaction) {
        withAposService { service ->
            val inquiry = runCatching {
                service.inquiry(transaction.partnerRefId, InquiryFlag.SINGLE.value)
            }.getOrNull()

            logInquiry(transaction, inquiry)

            val status = inquiry?.txStatus
            pendingStore.updateStatus(
                transaction.partnerRefId,
                status?.value ?: AposPendingStore.STATUS_UNRESOLVED
            )
            refreshList()

            if (status == TransactionStatus.SUCCESS) {
                promptContinuePayment(transaction)
            } else {
                showStatusDialog(transaction, status)
            }
        }
    }

    /**
     * Membuka layar Manual Inquiry milik APOS (deep link). Hasilnya tetap dibaca lewat AIDL saat
     * kasir kembali — layar APOS hanya alat bantu cari, bukan sumber kebenaran bagi POS.
     */
    private fun openAposManualInquiry(transaction: AposPendingTransaction) {
        withAposService { service ->
            val serialNumber = runCatching { service.sn }.getOrNull() ?: Build.DEVICE
            val transactionData = AposDeepLink.transactionData(
                serialNumber = serialNumber,
                partnerRefId = transaction.partnerRefId,
                amount = transaction.amount
            )
            val aposPackage = aposManager.aposPackageName
            if (aposPackage == null) {
                Toast.makeText(this, "Aplikasi APOS BCA tidak ditemukan", Toast.LENGTH_LONG).show()
                return@withAposService
            }

            val intent = AposDeepLink.intent(
                context = this,
                packageName = aposPackage,
                featureType = FeatureType.MANUAL_INQUIRY,
                transactionData = transactionData
            )
            if (intent == null) {
                Toast.makeText(
                    this,
                    "APOS tidak menyediakan layar manual inquiry. Pakai Cek Status.",
                    Toast.LENGTH_LONG
                ).show()
                return@withAposService
            }

            try {
                refIdAwaitingApos = transaction.partnerRefId
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                refIdAwaitingApos = null
                Toast.makeText(this, "Aplikasi APOS BCA tidak ditemukan", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Transaksi ternyata sukses di APOS tapi belum tercatat di backend. Kasir dikembalikan ke
     * PaymentActivity order tersebut, yang akan menyelesaikan pembayaran lewat jalur normalnya —
     * konteks voucher/down payment/diskon pulih sendiri karena tersimpan per-order.
     */
    private fun promptContinuePayment(transaction: AposPendingTransaction) {
        AlertDialog.Builder(this)
            .setTitle("Transaksi Berhasil di APOS")
            .setMessage(
                "Transaction ID ${transaction.partnerRefId} sudah BERHASIL di APOS, " +
                    "tetapi pembayarannya belum tercatat.\n\nLanjutkan penyelesaian pembayaran?"
            )
            .setPositiveButton("Lanjutkan") { dialog, _ ->
                dialog.dismiss()
                startActivity(
                    Intent(this, PaymentActivity::class.java).apply {
                        putExtra("ORDER_ID", transaction.orderId)
                        putExtra("IS_ID", transaction.itemSaleId)
                        putExtra("IS_COUNTER", transaction.itemSaleCounter)
                        putExtra("TABLE_NAME", transaction.tableName)
                        putExtra("U_ID", userId)
                        putExtra("U_NAME", userName)
                        putExtra(
                            PaymentActivity.EXTRA_RESUME_PARTNER_REF_ID,
                            transaction.partnerRefId
                        )
                    }
                )
                finish()
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun showStatusDialog(transaction: AposPendingTransaction, status: TransactionStatus?) {
        val message = when (status) {
            TransactionStatus.PENDING,
            TransactionStatus.CREATED,
            TransactionStatus.REVERSAL ->
                "Transaksi masih diproses di APOS. Coba cek lagi beberapa saat lagi."

            TransactionStatus.NOT_FOUND ->
                "Transaksi tidak ditemukan di APOS. Kemungkinan pembayaran tidak pernah diproses."

            TransactionStatus.FAILED ->
                "Transaksi gagal di APOS."

            TransactionStatus.VOID,
            TransactionStatus.PENDING_VOID ->
                "Transaksi sudah dibatalkan (void) di APOS."

            null ->
                "Tidak ada jawaban dari service APOS. Pastikan aplikasi APOS aktif, lalu coba lagi."

            else ->
                "Status transaksi di APOS: ${status.value}"
        }

        AlertDialog.Builder(this)
            .setTitle("Status ${transaction.partnerRefId}")
            .setMessage(message)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Hapus dari Daftar") { _, _ -> confirmRemove(transaction) }
            .show()
    }

    private fun confirmRemove(transaction: AposPendingTransaction) {
        AlertDialog.Builder(this)
            .setTitle("Hapus dari Daftar?")
            .setMessage(
                "Transaction ID ${transaction.partnerRefId} akan hilang dari daftar dan tidak bisa " +
                    "dicek ulang dari sini. Lakukan ini hanya jika transaksi sudah dipastikan selesai " +
                    "atau tidak pernah terjadi."
            )
            .setPositiveButton("Hapus") { _, _ ->
                pendingStore.remove(transaction.partnerRefId)
                refreshList()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Menjalankan aksi begitu service APOS terhubung. Binding bersifat asinkron, jadi aksi kasir
     * tepat setelah layar dibuka bisa saja tiba sebelum service siap.
     */
    private fun withAposService(action: (PartnerIntegrationAidl) -> Unit) {
        aposManager.aposService?.let {
            action(it)
            return
        }

        var completed = false
        val timeout = Runnable {
            if (completed) return@Runnable
            completed = true
            aposManager.onConnected = null
            Toast.makeText(
                this,
                "Service APOS belum terhubung, coba lagi sebentar",
                Toast.LENGTH_SHORT
            ).show()
        }

        aposManager.onConnected = {
            runOnUiThread {
                val service = aposManager.aposService
                if (!completed && service != null) {
                    completed = true
                    aposManager.onConnected = null
                    aposConnectHandler.removeCallbacks(timeout)
                    action(service)
                }
            }
        }

        aposManager.connect()
        aposConnectHandler.postDelayed(timeout, APOS_CONNECT_TIMEOUT_MS)
        Toast.makeText(this, "Menghubungkan service APOS...", Toast.LENGTH_SHORT).show()
    }

    /** Audit trail yang sama dengan auto inquiry, supaya BCA melihat kedua jalur di log. */
    private fun logInquiry(transaction: AposPendingTransaction, inquiry: PartnerInquiryData?) {
        if (userId == 0) return

        ApiClient.api.savePiMlpLog(
            PiMlpLogRequest(
                branchName = branchName,
                userId = userId,
                userName = userName,
                call = APOS_INQUIRY_CALL,
                request = mapOf(
                    "event" to "APOS_MANUAL_INQUIRY",
                    "partner_ref_id" to transaction.partnerRefId,
                    "flag" to InquiryFlag.SINGLE.value,
                    "order_id" to transaction.orderId.toString(),
                    "is_id" to transaction.itemSaleId.toString(),
                    "pos_id" to ApiClient.getPosId(applicationContext)
                ),
                response = mapOf(
                    "partner_ref_id" to inquiry?.partnerRefId,
                    "tx_status" to inquiry?.txStatus?.value,
                    "feature_type" to inquiry?.featureType?.uriSuffix,
                    "trace_no" to inquiry?.traceNo,
                    "approval_code" to inquiry?.approvalCode,
                    "ref_no" to inquiry?.refNo,
                    "merchant_id" to inquiry?.merchantId,
                    "terminal_id" to inquiry?.terminalId,
                    "acquirer_type" to inquiry?.acquirerType
                ),
                statusCode = if (inquiry == null) 500 else 200,
                errorCode = if (inquiry == null) "APOS_INQUIRY_NULL" else null,
                restMessage = inquiry?.txStatus?.value ?: "INQUIRY_NULL",
                success = inquiry?.txStatus == TransactionStatus.SUCCESS
            )
        ).enqueue(object : Callback<PiMlpLogResponse> {
            override fun onResponse(
                call: Call<PiMlpLogResponse>,
                response: Response<PiMlpLogResponse>
            ) = Unit

            override fun onFailure(call: Call<PiMlpLogResponse>, t: Throwable) = Unit
        })
    }

    companion object {
        private const val APOS_INQUIRY_CALL = "aidl://com.bca.apos.PartnerIntegrationAidl/inquiry"
        private const val APOS_CONNECT_TIMEOUT_MS = 2500L
    }
}
