package com.bandjak.pos.ui.orders

import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bandjak.pos.databinding.ItemOrderBinding
import com.bandjak.pos.model.Order

class OrdersAdapter(
    private var orders: List<Order>,
    private val onClick: (Order) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOrderBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = orders.size

    private fun formatTime(time: String?): String {
        if (time == null) return "-"
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )

        for (format in formats) {
            try {
                val input = SimpleDateFormat(format, Locale.getDefault())
                val date = input.parse(time)
                if (date != null) {
                    val output = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID"))
                    return output.format(date)
                }
            } catch (e: Exception) {
                // Try the next known API format.
            }
        }

        return time
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        val locked = order.latestLockState.equals("Locked", ignoreCase = true)

        holder.binding.orderTime.text = formatTime(order.oStartTime)
        holder.binding.orderTable.text = order.table?.tName ?: "-"
        holder.binding.orderArea.text = order.tablesArea?.taName ?: "-"
        holder.binding.orderSection.text = order.table?.tablesSection?.tsName ?: "Section -"
        holder.binding.orderPax.text = "${order.oPax ?: "-"} pax"
        holder.binding.orderWaiter.text = "Waiter: ${order.user?.userName ?: "-"}"

        if (locked) {
            holder.binding.orderStatusChip.text = "TERKUNCI"
            holder.binding.orderStatusChip.setTextColor(Color.parseColor("#BE123C"))
            holder.binding.orderStatusChip.setBackgroundResource(com.bandjak.pos.R.drawable.bg_order_locked_chip)
            holder.binding.imgLock.visibility = View.VISIBLE
        } else {
            holder.binding.orderStatusChip.text = "AKTIF"
            holder.binding.orderStatusChip.setTextColor(Color.parseColor("#047857"))
            holder.binding.orderStatusChip.setBackgroundResource(com.bandjak.pos.R.drawable.bg_order_open_chip)
            holder.binding.imgLock.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onClick(order)
        }
    }

    fun update(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}
