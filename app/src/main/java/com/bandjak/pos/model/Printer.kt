package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class EpsonPrintTestRequest(
    @SerializedName("printer_ip")
    val printerIp: String,
    @SerializedName("printer_port")
    val printerPort: Int,
    @SerializedName("printer_name")
    val printerName: String? = null,
    @SerializedName("content")
    val content: String? = null
)

data class BillInitiationPrintRequest(
    @SerializedName("o_id")
    val orderId: Int,
    @SerializedName("u_id")
    val userId: Int,
    @SerializedName("u_name")
    val userName: String?,
    @SerializedName("printer_target")
    val printerTarget: String,
    @SerializedName("printer_ip")
    val printerIp: String?,
    @SerializedName("printer_port")
    val printerPort: Int,
    @SerializedName("pos_id")
    val posId: String,
    @SerializedName("pos_ip")
    val posIp: String?
)

data class BillInitiationPrintResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("lp_id")
    val logPrintId: Int?,
    @SerializedName("is_id")
    val itemSaleId: Int?,
    @SerializedName("lp_message")
    val lpMessage: String?,
    @SerializedName("lp_message_source")
    val lpMessageSource: String?
)

data class EpsonPrintResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String
)

data class EpsonFinalBillPrintRequest(
    @SerializedName("is_id")
    val itemSaleId: Int,
    @SerializedName("printer_ip")
    val printerIp: String,
    @SerializedName("printer_port")
    val printerPort: Int
)

data class FinalBillContentResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("lp_message")
    val lpMessage: String?,
    @SerializedName("lp_message_source")
    val lpMessageSource: String?,
    @SerializedName("message")
    val message: String?
)
