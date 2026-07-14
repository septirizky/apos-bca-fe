package com.bandjak.pos.ui.orders

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bandjak.pos.R
import com.bandjak.pos.apos.AposPendingStore
import com.bandjak.pos.apos.AposPendingTransaction
import com.bca.apos.FeatureType
import com.bca.apos.TransactionStatus
import com.bandjak.pos.databinding.ItemPendingTransactionBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PendingTransactionAdapter(
    private var items: List<AposPendingTransaction>,
    private val onCheckStatus: (AposPendingTransaction) -> Unit,
    private val onOpenApos: (AposPendingTransaction) -> Unit
) : RecyclerView.Adapter<PendingTransactionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPendingTransactionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.binding.pendingRefId.text = item.partnerRefId
        holder.binding.pendingTable.text = item.tableName
        holder.binding.pendingMethod.text = FeatureType.from(item.featureType).title
        holder.binding.pendingAmount.text = formatRupiah(item.amount)
        holder.binding.pendingTime.text = formatTime(item.createdAt)

        bindStatusChip(holder, item.lastStatus)

        holder.binding.btnCheckStatus.setOnClickListener { onCheckStatus(item) }
        holder.binding.btnOpenApos.setOnClickListener { onOpenApos(item) }
    }

    private fun bindStatusChip(holder: ViewHolder, status: String) {
        val chip = holder.binding.pendingStatusChip

        // "unresolved" bukan status dari APOS — artinya kasir sudah berangkat ke APOS tapi hasilnya
        // belum pernah terbaca sama sekali (mis. aplikasi mati di tengah jalan).
        if (status == AposPendingStore.STATUS_UNRESOLVED) {
            chip.text = "BELUM DICEK"
            chip.setTextColor(Color.parseColor("#9A3412"))
            chip.setBackgroundResource(R.drawable.bg_order_pax_chip)
            return
        }

        when (TransactionStatus.from(status)) {
            TransactionStatus.SUCCESS,
            TransactionStatus.REVERSAL_VOID_SUCCESS -> {
                chip.text = "BERHASIL"
                chip.setTextColor(Color.parseColor("#047857"))
                chip.setBackgroundResource(R.drawable.bg_order_open_chip)
            }

            TransactionStatus.PENDING,
            TransactionStatus.PENDING_VOID,
            TransactionStatus.REVERSAL,
            TransactionStatus.CREATED -> {
                chip.text = "PENDING"
                chip.setTextColor(Color.parseColor("#9A3412"))
                chip.setBackgroundResource(R.drawable.bg_order_pax_chip)
            }

            TransactionStatus.NOT_FOUND -> {
                chip.text = "TIDAK ADA"
                chip.setTextColor(Color.parseColor("#BE123C"))
                chip.setBackgroundResource(R.drawable.bg_order_locked_chip)
            }

            else -> {
                chip.text = "GAGAL"
                chip.setTextColor(Color.parseColor("#BE123C"))
                chip.setBackgroundResource(R.drawable.bg_order_locked_chip)
            }
        }
    }

    fun update(newItems: List<AposPendingTransaction>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatRupiah(amount: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return "-"
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(millis))
    }
}
