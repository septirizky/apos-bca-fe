package com.bandjak.pos.ui.orders

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
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val date = input.parse(time)
            val output = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            output.format(date!!)
        } catch (e: Exception) {
            time ?: "-"
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]

        holder.binding.orderTime.text = formatTime(order.oStartTime)
        holder.binding.orderTable.text = order.table?.tName ?: "-"
        holder.binding.orderArea.text = order.tablesArea?.taName ?: "-"
        holder.binding.orderPax.text = order.oPax ?: "-"

        // Logika Gembok: Jika o_locked == "True" (String dari API)
        if (order.oLocked.equals("True", ignoreCase = true)) {
            holder.binding.imgLock.visibility = View.VISIBLE
        } else {
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