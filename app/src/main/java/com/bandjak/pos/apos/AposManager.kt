package com.bandjak.pos.apos

import android.content.*
import android.os.IBinder
import com.bandjak.pos.BuildConfig
import com.bca.apos.PartnerIntegrationAidl

class AposManager(private val context: Context) {

    var aposService: PartnerIntegrationAidl? = null
        private set
    var aposPackageName: String? = null
        private set
    private var isBound = false
    private var isBinding = false
    var onConnected: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aposService = PartnerIntegrationAidl.Stub.asInterface(service)
            aposPackageName = name?.packageName
            isBound = true
            isBinding = false
            onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aposService = null
            aposPackageName = null
            isBound = false
            isBinding = false
        }
    }

    fun connect() {
        if (isBound || isBinding) return

        for (packageName in APOS_PACKAGE_CANDIDATES) {
            val intent = Intent().apply {
                setClassName(packageName, APOS_SERVICE_CLASS)
            }

            val bound = runCatching {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)

            if (bound) {
                aposPackageName = packageName
                isBinding = true
                return
            }
        }
    }

    fun disconnect() {
        if (!isBound && !isBinding) return

        context.unbindService(serviceConnection)
        isBound = false
        isBinding = false
        aposService = null
        aposPackageName = null
    }

    companion object {
        private const val APOS_SERVICE_CLASS = "com.bca.apos.service.PartnerIntegrationService"

        /**
         * Daftar kandidat package APOS yang dicoba saat binding, diurutkan berdasarkan prioritas.
         *
         * Package dari BuildConfig (mis. "com.bca.apos.staging.castles") dicoba lebih dulu,
         * lalu varian lain dari device yang sama (non-staging "com.bca.apos.castles" & dev
         * "com.bca.apos.dev.castles"). Ini membuat koneksi tahan terhadap perbedaan dummy
         * yang terinstall (staging vs non-staging). Semua kandidat sudah terdaftar di
         * <queries> AndroidManifest agar tetap visible di Android 11+.
         */
        private val APOS_PACKAGE_CANDIDATES: List<String> = buildCandidates()

        private fun buildCandidates(): List<String> {
            val configured = BuildConfig.BCA_APOS_PACKAGE_NAME
            // Ambil nama device dari segmen terakhir, mis. "castles" dari "com.bca.apos.staging.castles".
            val device = configured.substringAfterLast('.')
            val variants = listOf(
                "com.bca.apos.$device",
                "com.bca.apos.staging.$device",
                "com.bca.apos.dev.$device",
            )
            // BuildConfig diutamakan, lalu varian lain, tanpa duplikat & urutan terjaga.
            return (listOf(configured) + variants).distinct()
        }
    }
}
