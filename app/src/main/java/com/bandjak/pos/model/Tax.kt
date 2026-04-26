package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class Tax(

    @SerializedName("tax")
    val tax: Int
)