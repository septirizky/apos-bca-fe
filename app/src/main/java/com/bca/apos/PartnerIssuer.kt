package com.bca.apos

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PartnerIssuer(
    var nama: String? = null,
    var txCount: Int? = null,
    var tipCount: Int? = null,
    var cashCount: Int? = null,
    var totalCount: Int? = null,
    var totalTxAmount: Double? = null,
    var totalTipAmount: Double? = null,
    var totalCashAmount: Double? = null,
    var totalAmount: Double? = null
): Parcelable
