package com.bandjak.pos.apos

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bca.apos.FeatureType

/**
 * Pembangun deep link ke aplikasi APOS BCA.
 *
 * Soal authority: APOS dummy castles mendeklarasikan intent-filter dengan host tetap
 * "com.bca.apos" — BUKAN applicationId penuh (com.bca.apos.staging.castles). Memakai host yang
 * salah membuat intent tidak ter-resolve dan berujung ActivityNotFoundException.
 *
 * Karena kita tidak bisa memastikan APOS staging/produksi memakai host yang sama, host tidak
 * ditebak: kandidat diuji ke PackageManager dan yang benar-benar ter-resolve di perangkat itulah
 * yang dipakai. Ini membuat build yang sama jalan di dummy lokal maupun di perangkat UAT BCA.
 *
 * Aplikasi tujuan tetap dikunci lewat setPackage(), bukan lewat authority.
 */
object AposDeepLink {

    /** Host yang dideklarasikan APOS pada intent-filter-nya. */
    const val AUTHORITY = "com.bca.apos"

    fun url(authority: String, featureType: FeatureType): String =
        "android-app://$authority/${featureType.uriSuffix}"

    /**
     * Intent deep link yang dijamin ter-resolve bila APOS memang terpasang.
     *
     * Mengembalikan null kalau tidak ada kandidat host yang ter-resolve — pemanggil harus
     * memperlakukannya sebagai "APOS tidak ditemukan", bukan melempar intent yang pasti gagal.
     */
    fun intent(
        context: Context,
        packageName: String,
        featureType: FeatureType,
        transactionData: String?
    ): Intent? {
        // Host tetap lebih dulu (terbukti dipakai APOS), lalu applicationId penuh sebagai cadangan
        // kalau build APOS lain ternyata memakai package sebagai host.
        val authorities = listOf(AUTHORITY, packageName).distinct()

        return authorities
            .map { authority -> build(authority, packageName, featureType, transactionData) }
            .firstOrNull { it.resolveActivity(context.packageManager) != null }
    }

    private fun build(
        authority: String,
        packageName: String,
        featureType: FeatureType,
        transactionData: String?
    ): Intent {
        val uri = Uri.parse(url(authority, featureType))
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
