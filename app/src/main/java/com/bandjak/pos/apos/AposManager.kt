package com.bandjak.pos.apos

import android.content.*
import android.os.IBinder
import com.bca.apos.PartnerIntegrationAidl

class AposManager(private val context: Context) {

    var aposService: PartnerIntegrationAidl? = null
        private set
    private var isBound = false
    var onConnected: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aposService = PartnerIntegrationAidl.Stub.asInterface(service)
            isBound = true
            onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aposService = null
            isBound = false
        }
    }

    fun connect() {
        if (isBound) return

        val intent = Intent()
        intent.setClassName(
            "com.bca.apos",
            "com.bca.apos.service.PartnerIntegrationService"
        )

        isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        if (!isBound) return

        context.unbindService(serviceConnection)
        isBound = false
        aposService = null
    }
}
