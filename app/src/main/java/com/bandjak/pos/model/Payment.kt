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
    @SerializedName("apos_tx_status")
    val aposTxStatus: String?,
    @SerializedName("apos_feature_type")
    val aposFeatureType: String?,
    @SerializedName("apos_trace_no")
    val aposTraceNo: String?,
    @SerializedName("apos_approval_code")
    val aposApprovalCode: String?,
    @SerializedName("apos_ref_no")
    val aposRefNo: String?,
    @SerializedName("apos_merchant_id")
    val aposMerchantId: String?,
    @SerializedName("apos_terminal_id")
    val aposTerminalId: String?,
    @SerializedName("apos_acquirer_type")
    val aposAcquirerType: String?,
    @SerializedName("pos_id")
    val posId: String?,
    @SerializedName("pos_ip")
    val posIp: String?,
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
