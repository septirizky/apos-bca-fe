package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class VoucherValidateRequest(
    @SerializedName("v_code")
    val code: String,
    @SerializedName("allow_expired")
    val allowExpired: Boolean = false
)

data class VoucherValidateResponse(
    val message: String?,
    val voucher: Voucher?
)

data class Voucher(
    @SerializedName("v_id")
    val id: Int,
    @SerializedName("v_code")
    val code: String,
    @SerializedName("v_nominal")
    val nominal: Double,
    @SerializedName("v_start_date")
    val startDate: String?,
    @SerializedName("v_end_date")
    val endDate: String?,
    @SerializedName("v_status")
    val status: String?,
    val expired: Boolean
)
