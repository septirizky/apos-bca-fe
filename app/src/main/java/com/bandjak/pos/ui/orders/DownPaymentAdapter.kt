package com.bandjak.pos.ui.orders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bandjak.pos.databinding.ItemDownPaymentBinding
import com.bandjak.pos.model.DownPayment
import java.text.NumberFormat
import java.util.Locale

class DownPaymentAdapter(
    private var items: List<DownPayment>
) : RecyclerView.Adapter<DownPaymentAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDownPaymentBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val binding = ItemDownPaymentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = items[position]

        holder.binding.dpName.text = item.name

        holder.binding.dpAmount.text =
            formatRupiah(item.amount)
    }

    fun update(newItems: List<DownPayment>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatRupiah(amount:String): String {

        return try {

            val number = amount.toDouble()

            val format = NumberFormat.getCurrencyInstance(
                Locale("in","ID")
            )

            format.maximumFractionDigits = 0

            format.format(number)

        } catch (e: Exception) {
            amount
        }
    }
}