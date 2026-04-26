package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class User(

    @SerializedName("u_id")
    val uId: Int,

    @SerializedName("u_name")
    val uName: String,

    @SerializedName("ur_id")
    val urId: Int
)