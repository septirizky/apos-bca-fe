package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class PaymentRequest(
    @SerializedName("o_id")
    val orderId: Int,
    @SerializedName("is_id")
    val itemSaleId: Int,
    @SerializedName("is_counter")
    val itemSaleCounter: Int,
    @SerializedName("dp_id")
    val downPaymentId: Int?,
    @SerializedName("payment_method")
    val paymentMethod: String?,
    @SerializedName("voucher_code")
    val voucherCode: String?,
    @SerializedName("voucher_id")
    val voucherId: Int?,
    @SerializedName("voucher_amount")
    val voucherAmount: Double,
    @SerializedName("apos_partner_ref_id")
    val aposPartnerRefId: String?,
    @SerializedName("u_id")
    val userId: Int,
    @SerializedName("u_name")
    val userName: String?
)

data class PaymentResponse(
    val message: String?,
    val data: PaymentResult?
)

data class PaymentResult(
    val success: Boolean?,
    @SerializedName("is_id")
    val itemSaleId: Int?,
    @SerializedName("is_counter")
    val itemSaleCounter: Int?
)
