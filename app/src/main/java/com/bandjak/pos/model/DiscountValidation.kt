package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class DiscountValidateRequest(
    @SerializedName("m_code")
    val memberCode: String
)

data class DiscountValidateResponse(
    val message: String?,
    val member: DiscountMember?,
    val discounts: List<ValidatedDiscount>
)

data class DiscountMember(
    @SerializedName("m_id")
    val id: Int,
    @SerializedName("m_code")
    val code: String,
    @SerializedName("m_name")
    val name: String?,
    @SerializedName("mt_id")
    val memberTypeId: Int?
)

data class ValidatedDiscount(
    @SerializedName("d_id")
    val id: Int,
    @SerializedName("d_name")
    val name: String,
    @SerializedName("d_type")
    val type: String?,
    @SerializedName("dd_value")
    val value: Double,
    @SerializedName("d_max_disc")
    val maxDiscount: Double
)
