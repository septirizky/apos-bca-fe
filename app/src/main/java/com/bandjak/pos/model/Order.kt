package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class Order(

    @SerializedName("o_id")
    val oId: Int?,

    @SerializedName("is_id")
    val itemSaleId: Int?,

    @SerializedName("t_id")
    val tableId: Int?,

    @SerializedName("o_group")
    val orderGroup: Int?,

    @SerializedName("o_start_time")
    val oStartTime: String?,

    @SerializedName("o_pax")
    val oPax: String?,

    @SerializedName("o_locked")
    val oLocked: String?,

    @SerializedName("latest_lock_state")
    val latestLockState: String?,

    @SerializedName("latest_lock_user_id")
    val latestLockUserId: Int?,

    @SerializedName("latest_lock_user_name")
    val latestLockUserName: String?,

    @SerializedName("u_id")
    val userId: Int?,

    @SerializedName("User")
    val user: OrderUser?,

    @SerializedName("TablesArea")
    val tablesArea: TablesArea?,

    @SerializedName("Table")
    val table: Table?
)

data class OrderUser(

    @SerializedName("u_id")
    val userId: Int?,

    @SerializedName("u_name")
    val userName: String?
)

data class TablesArea(

    @SerializedName("ta_name")
    val taName: String?
)

data class Table(

    @SerializedName("t_name")
    val tName: String?,

    @SerializedName("TablesSection")
    val tablesSection: TablesSection?
)

data class TablesSection(

    @SerializedName("ts_name")
    val tsName: String?
)

data class OrdersResponse(
    val message: String?,
    val orders: List<Order>?
)

data class OrderLockRequest(
    @SerializedName("u_id")
    val userId: Int,
    @SerializedName("pos_id")
    val posId: String?,
    @SerializedName("pos_ip")
    val posIp: String?
)

data class OrderLockResponse(
    @SerializedName("message")
    val message: String?,
    @SerializedName("lock")
    val lock: OrderLock?
)

data class OrderLock(
    @SerializedName("order_id")
    val orderId: Int?,
    @SerializedName("t_id")
    val tableId: Int?,
    @SerializedName("lock_id")
    val lockId: String?,
    @SerializedName("lock_state")
    val lockState: String?
)
