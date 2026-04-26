package com.bca.apos

import android.os.Parcelable
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
data class PartnerSettlementData(
    @PrimaryKey val settlementId: String,
    var acquirerSettlementList: List<AcquirerSettlement>?= null,
    var totalAmount: Double ?= null,
    var settlementStatus: SettlementStatus
): Parcelable





