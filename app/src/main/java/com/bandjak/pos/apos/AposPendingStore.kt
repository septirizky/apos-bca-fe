package com.bandjak.pos.apos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Satu transaksi APOS yang hasil akhirnya belum final.
 *
 * Field order/itemSale disimpan supaya kasir bisa dikembalikan ke PaymentActivity order tersebut
 * ketika manual inquiry akhirnya menghasilkan SUCCESS — sehingga penyelesaian pembayaran tetap
 * lewat jalur normal (markPaymentCompleted) dan tidak perlu diduplikasi.
 */
data class AposPendingTransaction(
    val partnerRefId: String,
    val orderId: Int,
    val itemSaleId: Int,
    val itemSaleCounter: Int,
    val featureType: String,
    val amount: Long,
    val tableName: String,
    val createdAt: Long,
    val lastStatus: String
)

/**
 * Penyimpanan lokal transaksi APOS yang belum final.
 *
 * Record ditulis SEBELUM aplikasi berpindah ke layar APOS, bukan sesudahnya. Kalau ditulis
 * belakangan, skenario yang justru paling perlu ditutup — proses aplikasi mati saat kasir masih
 * di layar APOS — tetap kehilangan partnerRefId dan transaksinya tidak bisa direkonsiliasi.
 *
 * Memakai SharedPreferences + JSON mengikuti pola persistensi voucher yang sudah dipakai app ini.
 * (Room ada di dependency tapi tidak pernah aktif: tidak ada kapt/ksp di build.gradle.)
 */
class AposPendingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Transaksi tertunda, terbaru lebih dulu. */
    fun all(): List<AposPendingTransaction> = read().sortedByDescending { it.createdAt }

    fun count(): Int = read().size

    fun find(partnerRefId: String): AposPendingTransaction? =
        read().firstOrNull { it.partnerRefId == partnerRefId }

    /** Simpan/timpa record berdasarkan partnerRefId. */
    fun put(transaction: AposPendingTransaction) {
        val updated = read().filterNot { it.partnerRefId == transaction.partnerRefId } + transaction
        write(updated)
    }

    fun updateStatus(partnerRefId: String, status: String) {
        val current = read()
        if (current.none { it.partnerRefId == partnerRefId }) return
        write(current.map { if (it.partnerRefId == partnerRefId) it.copy(lastStatus = status) else it })
    }

    fun remove(partnerRefId: String) {
        write(read().filterNot { it.partnerRefId == partnerRefId })
    }

    private fun read(): List<AposPendingTransaction> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val partnerRefId = item.optString(FIELD_PARTNER_REF_ID).ifBlank { return@mapNotNull null }

            AposPendingTransaction(
                partnerRefId = partnerRefId,
                orderId = item.optInt(FIELD_ORDER_ID),
                itemSaleId = item.optInt(FIELD_ITEM_SALE_ID),
                itemSaleCounter = item.optInt(FIELD_ITEM_SALE_COUNTER),
                featureType = item.optString(FIELD_FEATURE_TYPE),
                amount = item.optLong(FIELD_AMOUNT),
                tableName = item.optString(FIELD_TABLE_NAME, "-"),
                createdAt = item.optLong(FIELD_CREATED_AT),
                lastStatus = item.optString(FIELD_LAST_STATUS, STATUS_UNRESOLVED)
            )
        }
    }

    private fun write(transactions: List<AposPendingTransaction>) {
        val array = JSONArray()
        transactions.forEach { transaction ->
            array.put(
                JSONObject().apply {
                    put(FIELD_PARTNER_REF_ID, transaction.partnerRefId)
                    put(FIELD_ORDER_ID, transaction.orderId)
                    put(FIELD_ITEM_SALE_ID, transaction.itemSaleId)
                    put(FIELD_ITEM_SALE_COUNTER, transaction.itemSaleCounter)
                    put(FIELD_FEATURE_TYPE, transaction.featureType)
                    put(FIELD_AMOUNT, transaction.amount)
                    put(FIELD_TABLE_NAME, transaction.tableName)
                    put(FIELD_CREATED_AT, transaction.createdAt)
                    put(FIELD_LAST_STATUS, transaction.lastStatus)
                }
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    companion object {
        /** Status awal: kasir sudah berangkat ke APOS, hasilnya belum pernah terbaca. */
        const val STATUS_UNRESOLVED = "unresolved"

        private const val PREFS_NAME = "apos_pending_transactions"
        private const val KEY_LIST = "pending_list"
        private const val FIELD_PARTNER_REF_ID = "partner_ref_id"
        private const val FIELD_ORDER_ID = "order_id"
        private const val FIELD_ITEM_SALE_ID = "item_sale_id"
        private const val FIELD_ITEM_SALE_COUNTER = "item_sale_counter"
        private const val FIELD_FEATURE_TYPE = "feature_type"
        private const val FIELD_AMOUNT = "amount"
        private const val FIELD_TABLE_NAME = "table_name"
        private const val FIELD_CREATED_AT = "created_at"
        private const val FIELD_LAST_STATUS = "last_status"
    }
}
