package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class DownPayment(

    @SerializedName("dp_name")
    val name: String,

    @SerializedName("dp_amount")
    val amount: String
)