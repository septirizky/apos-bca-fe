package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class DatabaseStatusResponse(
    @SerializedName("message") val message: String,
    @SerializedName("status") val status: String
)