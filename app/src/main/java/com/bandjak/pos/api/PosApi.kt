package com.bandjak.pos.api

import com.bandjak.pos.model.*
import retrofit2.Call
import retrofit2.http.*

interface PosApi {

    @GET("api/database-status")
    fun getDatabaseStatus(): Call<DatabaseStatusResponse>

    @GET("api/branch-name")
    fun getBranchName(): Call<BranchNameResponse>

    @GET("api/receipt-info")
    fun getReceiptInfo(): Call<ReceiptInfoResponse>

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

    @POST("api/orders/{id}/lock")
    fun lockOrder(
        @Path("id") id: Int,
        @Body body: OrderLockRequest
    ): Call<OrderLockResponse>

    @POST("api/orders/{id}/unlock")
    fun unlockOrder(
        @Path("id") id: Int,
        @Body body: OrderLockRequest
    ): Call<OrderLockResponse>

    @GET("api/taxes")
    fun getTax(): Call<Tax>

    @GET("api/down-payments")
    fun getDownPayments(): Call<List<DownPayment>>

    @POST("api/vouchers/validate")
    fun validateVoucher(
        @Body body: VoucherValidateRequest
    ): Call<VoucherValidateResponse>

    @POST("api/discounts/validate-member")
    fun validateDiscountMember(
        @Body body: DiscountValidateRequest
    ): Call<DiscountValidateResponse>

    @POST("api/payment")
    fun completePayment(
        @Body body: PaymentRequest
    ): Call<PaymentResponse>

    @POST("api/logs/pi-mlp")
    fun savePiMlpLog(
        @Body body: PiMlpLogRequest
    ): Call<PiMlpLogResponse>

    @POST("api/print/bill-initiation")
    fun printBillInitiation(
        @Body body: BillInitiationPrintRequest
    ): Call<BillInitiationPrintResponse>

    @POST("api/print/epson/test")
    fun testEpsonPrinter(
        @Body body: EpsonPrintTestRequest
    ): Call<EpsonPrintResponse>

    @POST("api/print/epson")
    fun printEpson(
        @Body body: EpsonPrintTestRequest
    ): Call<EpsonPrintResponse>

    @POST("api/print/epson/final-bill")
    fun printFinalBillEpson(
        @Body body: EpsonFinalBillPrintRequest
    ): Call<EpsonPrintResponse>

    @GET("api/print/final-bill/{is_id}")
    fun getFinalBillContent(
        @Path("is_id") itemSaleId: Int
    ): Call<FinalBillContentResponse>
}
