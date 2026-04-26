package com.bandjak.pos

import android.content.*
import android.os.IBinder
import com.bca.apos.PartnerIntegrationAidl

class AposManager(private val context: Context) {

    var aposService: PartnerIntegrationAidl? = null

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aposService = PartnerIntegrationAidl.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aposService = null
        }
    }

    fun connect() {
        val intent = Intent()
        intent.setClassName(
            "com.bca.apos",
            "com.bca.apos.service.PartnerIntegrationService"
        )

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

}