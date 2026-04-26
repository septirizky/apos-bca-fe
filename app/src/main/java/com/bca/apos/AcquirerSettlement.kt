package com.bca.apos
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AcquirerSettlement(
    var acquirerSettlementStatus: SettlementStatus? = null,
    var txType: String? = null,
    var batchNo: String? = null,
    var settlementDateTime: String? = null,
    var currency: String? = null,
    var errorCode: String? = null,
    var errorMessage: String? = null,
    var partnerIssuer: List<PartnerIssuer>? = null
): Parcelable