package com.bandjak.pos.apos

import android.content.Intent
import android.net.Uri
import com.bca.apos.FeatureType

/**
 * Pembangun deep link ke aplikasi APOS BCA.
 *
 * Authority-nya FIXED "com.bca.apos" sesuai intent-filter APOS. Memakai applicationId penuh
 * (mis. com.bca.apos.staging.castles) membuat intent tidak ter-resolve dan berujung
 * ActivityNotFoundException. Aplikasi tujuan dikunci lewat setPackage(), bukan lewat authority.
 */
object AposDeepLink {

    const val AUTHORITY = "com.bca.apos"

    fun url(featureType: FeatureType): String = "android-app://$AUTHORITY/${featureType.uriSuffix}"

    fun intent(packageName: String, featureType: FeatureType, transactionData: String?): Intent {
        val uri = Uri.parse(url(featureType))
            .buildUpon()
            .appendQueryParameter(PARAM_TRANSACTION_DATA, transactionData)
            .build()

        return Intent(Intent.ACTION_VIEW).apply {
            data = uri
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    /** Payload terenkripsi yang diminta APOS pada query TRANSACTION_DATA. */
    fun transactionData(serialNumber: String, partnerRefId: String, amount: Long): String? {
        val encryption = DeepLinkEncryptionUtil()
        val json = """
            {
                "PARTNER_REF_ID": "$partnerRefId",
                "AMOUNT": "$amount",
                "SIGNATURE": "${encryption.generateSignature(serialNumber)}"
            }
        """.trimIndent()

        return encryption.encrypt(json)
    }

    private const val PARAM_TRANSACTION_DATA = "TRANSACTION_DATA"
}
