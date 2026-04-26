package com.bca.apos

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class PartnerInquiryData(
    val partnerRefId: String,
    val txStatus: TransactionStatus,
    val featureType: FeatureType,
    val merchantId: String? = null,
    val terminalId: String? = null,
    val paymentDateTime: String? = null,
    val approvalCode: String? = null,
    var batchNo: String? = null,
    val traceNo: String? = null,
    val refNo: String? = null,
    val amount: BigDecimal? = null,
    val tip: BigDecimal? = null,
    val fee: BigDecimal? = null,
    val cash: BigDecimal? = null,
    val dccAmount: BigDecimal? = null,
    val dccTip: BigDecimal? = null,
    val acquirerType: String? = null
//    val isOnUs: Boolean? = null,
//    val issuerName: String? = null
): Parcelable