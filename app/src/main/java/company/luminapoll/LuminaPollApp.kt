package company.luminapoll

import android.app.Application
import company.luminapoll.core.network.KtorLocalClient
import company.luminapoll.core.network.KtorLocalServer
import company.luminapoll.core.network.OnlinePollManager
import company.luminapoll.core.utils.NsdHelper

class LuminaPollApp : Application() {
    
    val localServer by lazy { KtorLocalServer(this) }
    val localClient by lazy { KtorLocalClient() }
    val nsdHelper by lazy { NsdHelper(this) }
    val onlinePollManager by lazy { OnlinePollManager() }

    override fun onCreate() {
        super.onCreate()
    }
}
