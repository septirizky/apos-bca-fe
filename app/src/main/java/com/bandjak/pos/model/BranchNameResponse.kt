package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class BranchNameResponse(
    @SerializedName("message") val message: String,
    @SerializedName("branch_name") val branchName: String
)