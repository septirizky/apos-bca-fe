package com.bandjak.pos.ui.orders

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bandjak.pos.databinding.ItemOrderDetailBinding
import com.bandjak.pos.model.DiscountDetail
import com.bandjak.pos.model.OrderDetail
import java.text.NumberFormat
import java.util.*

class OrderDetailAdapter(
    private var items: List<OrderDetail>
) : RecyclerView.Adapter<OrderDetailAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOrderDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.binding.itemName.text = item.odName
        holder.binding.itemQtyPrice.text = "${item.qty}x${formatRupiah(item.sellPrice)}"
        holder.binding.itemTotal.text = formatRupiah(item.itemTotal)

        // Set ukuran QtyPrice ke 15sp (naik 2 dari 13sp)
        holder.binding.itemQtyPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)

        // Handle Discounts per Item
        holder.binding.discountContainer.removeAllViews()
        val appliedDiscounts = item.discounts?.filter { it.isApplied }
        
        if (!appliedDiscounts.isNullOrEmpty()) {
            holder.binding.discountContainer.visibility = View.VISIBLE
            appliedDiscounts.forEach { disc ->
                val discView = createDiscountView(
                    holder.itemView.context,
                    formatDiscountName(disc),
                    disc.discountAmount
                )
                holder.binding.discountContainer.addView(discView)
            }
        } else {
            holder.binding.discountContainer.visibility = View.GONE
        }
    }

    private fun formatDiscountName(discount: DiscountDetail): String {
        val percent = discount.ddValue ?: discount.discountPercent.toDouble()
        val formattedPercent = if (percent % 1.0 == 0.0) {
            percent.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", percent).trimEnd('0').trimEnd('.')
        }

        return "${discount.dName} [$formattedPercent%]"
    }

    private fun createDiscountView(context: android.content.Context, name: String, amount: Double): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val nameTxt = TextView(context).apply {
            text = "- $name"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f) // Naik 2 dari 13sp
            setTextColor(Color.parseColor("#EF4444"))
            setTypeface(Typeface.DEFAULT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val amountTxt = TextView(context).apply {
            text = "-${formatRupiah(amount)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f) // Naik 2 dari 13sp
            setTextColor(Color.parseColor("#EF4444"))
            setTypeface(Typeface.DEFAULT)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(nameTxt)
        layout.addView(amountTxt)
        return layout
    }

    fun update(newItems: List<OrderDetail>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ")
    }
}
