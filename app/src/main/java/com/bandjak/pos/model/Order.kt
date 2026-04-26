package com.bandjak.pos.model

import com.google.gson.annotations.SerializedName

data class Order(

    @SerializedName("o_id")
    val oId: Int?,

    @SerializedName("o_start_time")
    val oStartTime: String?,

    @SerializedName("o_pax")
    val oPax: String?,

    @SerializedName("o_locked")
    val oLocked: String?,

    @SerializedName("TablesArea")
    val tablesArea: TablesArea?,

    @SerializedName("Table")
    val table: Table?
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