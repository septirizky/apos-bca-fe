package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class PiMlpLogRequest(
    @SerializedName("br_id")
    val branchId: Int = 1,
    @SerializedName("br_name")
    val branchName: String?,
    @SerializedName("u_id")
    val userId: Int,
    @SerializedName("u_name")
    val userName: String?,
    @SerializedName("l_call")
    val call: String,
    @SerializedName("l_request")
    val request: Map<String, Any?>,
    @SerializedName("l_response")
    val response: Map<String, Any?>,
    @SerializedName("l_status_code")
    val statusCode: Int,
    @SerializedName("l_error_code")
    val errorCode: String?,
    @SerializedName("l_rest_message")
    val restMessage: String?,
    @SerializedName("l_success")
    val success: Boolean
)

data class PiMlpLogResponse(
    val message: String?
)
