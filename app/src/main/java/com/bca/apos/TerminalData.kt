package com.bca.apos

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class TerminalData(
    val merchantId: String,
    val terminalId: String,
    val serialNo: String,
    val merchantName: String
): Parcelable