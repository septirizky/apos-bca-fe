package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class ReceiptInfoResponse(
    val message: String?,
    @SerializedName("info1")
    val info1: String?,
    @SerializedName("info2")
    val info2: String?,
    @SerializedName("info3")
    val info3: String?
)
