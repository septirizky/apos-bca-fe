package com.bca.apos

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NonBcaTransactionData(
    val partnerRefId: String,
    val txCategory: String,
    val transactionDate: String,
    val amount: Long
): Parcelable