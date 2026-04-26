package com.bandjak.pos.api

import com.bandjak.pos.model.*
import retrofit2.Call
import retrofit2.http.*

interface PosApi {

    @GET("api/database-status")
    fun getDatabaseStatus(): Call<DatabaseStatusResponse>

    @GET("api/branch-name")
    fun getBranchName(): Call<BranchNameResponse>

    @POST("api/login-pin")
    fun login(
        @Body body: LoginRequest
    ): Call<LoginResponse>

    @GET("api/orders")
    fun getOrders(): Call<OrdersResponse>

    @GET("api/orders/{id}/detail")
    fun getOrderDetail(
        @Path("id") id: Int,
        @Query("member_code") memberCode: String? = null
    ): Call<OrderDetailResponse>

    @PATCH("api/orders/{id}/member-code")
    fun updateOrderMemberCode(
        @Path("id") id: Int,
        @Body body: OrderMemberCodeRequest
    ): Call<OrderMemberCodeResponse>

    @GET("api/taxes")
    fun getTax(): Call<Tax>

    @GET("api/down-payments")
    fun getDownPayments(): Call<List<DownPayment>>
}
