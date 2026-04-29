package com.bandjak.pos.apos

import android.content.*
import android.os.IBinder
import com.bca.apos.PartnerIntegrationAidl

class AposManager(private val context: Context) {

    var aposService: PartnerIntegrationAidl? = null
        private set
    var aposPackageName: String? = null
        private set
    private var isBound = false
    var onConnected: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aposService = PartnerIntegrationAidl.Stub.asInterface(service)
            aposPackageName = name?.packageName
            isBound = true
            onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aposService = null
            aposPackageName = null
            isBound = false
        }
    }

    fun connect() {
        if (isBound) return

        for (packageName in APOS_PACKAGE_CANDIDATES) {
            val intent = Intent().apply {
                setClassName(packageName, APOS_SERVICE_CLASS)
            }

            val bound = runCatching {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)

            if (bound) {
                aposPackageName = packageName
                isBound = true
                return
            }
        }
    }

    fun disconnect() {
        if (!isBound) return

        context.unbindService(serviceConnection)
        isBound = false
        aposService = null
        aposPackageName = null
    }

    companion object {
        private const val APOS_SERVICE_CLASS = "com.bca.apos.service.PartnerIntegrationService"
        private val APOS_PACKAGE_CANDIDATES = listOf(
            "com.bca.apos",
            "com.bca.apos.ingenico",
            "com.bca.apos.pax",
            "com.bca.apos.verifone",
            "com.bca.apos.castles",
            "com.bca.apos.dev.ingenico",
            "com.bca.apos.dev.pax",
            "com.bca.apos.dev.verifone",
            "com.bca.apos.dev.castles",
            "com.bca.apos.staging.ingenico",
            "com.bca.apos.staging.pax",
            "com.bca.apos.staging.verifone",
            "com.bca.apos.staging.castles"
        )
    }
}
