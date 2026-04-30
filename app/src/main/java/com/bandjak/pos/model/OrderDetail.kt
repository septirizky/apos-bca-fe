package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class OrderDetailResponse(
    @SerializedName("o_id")
    val orderId: Int?,
    @SerializedName("is_id")
    val itemSaleId: Int?,
    @SerializedName("next_is_counter")
    val nextItemSaleCounter: Int?,
    @SerializedName("t_name")
    val tName: String?,
    @SerializedName("u_id")
    val waiterId: Int?,
    @SerializedName("u_name")
    val waiterName: String?,
    @SerializedName("m_code")
    val memberCode: String?,
    @SerializedName("items")
    val items: List<OrderDetail>,
    @SerializedName("summary")
    val summary: OrderSummary
)

data class OrderMemberCodeRequest(
    @SerializedName("m_code")
    val memberCode: String?
)

data class OrderMemberCodeResponse(
    @SerializedName("message")
    val message: String?,
    @SerializedName("order")
    val order: OrderMemberCode?
)

data class OrderMemberCode(
    @SerializedName("o_id")
    val orderId: Int,
    @SerializedName("m_code")
    val memberCode: String?
)

data class OrderSummary(
    @SerializedName("food_total")
    val foodTotal: Double,
    @SerializedName("beverage_total")
    val beverageTotal: Double,
    @SerializedName("other_total")
    val otherTotal: Double,
    @SerializedName("total_before_discount")
    val totalBeforeDiscount: Double,
    @SerializedName("discount_total")
    val discountTotal: Double,
    @SerializedName("subtotal")
    val subtotal: Double,
    @SerializedName("cooking_charge")
    val cookingCharge: Double,
    @SerializedName("pbjt")
    val pbjt: Double,
    @SerializedName("total")
    val total: Double
)

data class OrderDetail(
    @SerializedName("od_id")
    val odId: Int,
    @SerializedName("od_name")
    val odName: String,
    @SerializedName("qty")
    val qty: Int,
    @SerializedName("sell_price")
    val sellPrice: Double,
    @SerializedName("item_total")
    val itemTotal: Double,
    @SerializedName("discount_amount")
    val discountAmount: Double,
    @SerializedName("final_price")
    val finalPrice: Double,
    @SerializedName("discounts")
    val discounts: List<DiscountDetail>?,
    @SerializedName("Item")
    val item: ItemData
)

data class DiscountDetail(
    @SerializedName("d_name")
    val dName: String,
    @SerializedName("dd_value")
    val ddValue: Double?,
    @SerializedName("discount_amount")
    val discountAmount: Double,
    @SerializedName("discount_percent")
    val discountPercent: Double,
    @SerializedName("is_max_discount_capped")
    val isMaxDiscountCapped: Boolean?,
    @SerializedName("is_applied")
    val isApplied: Boolean
)

data class ItemData(
    @SerializedName("i_id")
    val iId: Int,
    @SerializedName("i_name")
    val iName: String,
    @SerializedName("i_sell_price")
    val iSellPrice: String,
    @SerializedName("i_cooking_charge")
    val iCookingCharge: String,
    @SerializedName("i_kind")
    val iKind: String?
)
