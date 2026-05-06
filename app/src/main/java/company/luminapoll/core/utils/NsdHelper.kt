package company.luminapoll.core.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Helper class for Network Service Discovery (NSD) / mDNS.
 * 
 * This enables "Magic Local Polling". When a host starts a local poll, this class 
 * broadcasts the service over the local Wi-Fi. Other devices running the app use 
 * this class to listen for those broadcasts, allowing them to find the host's IP 
 * address automatically without manual entry.
 */
class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_luminapoll._tcp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(port: Int, pollCode: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "LuminaPoll_$pollCode"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d("NSD", "Service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private val resolveQueue = java.util.ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    fun discoverServices(onServiceFound: (NsdServiceInfo) -> Unit) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Some Android versions return the service type with a trailing dot, others don't.
                // We use contains to be safe.
                if (service.serviceType.contains("luminapoll", ignoreCase = true)) {
                    synchronized(resolveQueue) {
                        resolveQueue.add(service)
                        processNextResolve(onServiceFound)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("NSD", "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun processNextResolve(onServiceFound: (NsdServiceInfo) -> Unit) {
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            
            isResolving = true
            val service = resolveQueue.poll()
            
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e("NSD", "Resolve failed: $errorCode for ${serviceInfo.serviceName}")
                    synchronized(resolveQueue) {
                        isResolving = false
                        processNextResolve(onServiceFound)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d("NSD", "Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")
                    onServiceFound(serviceInfo)
                    synchronized(resolveQueue) {
                        isResolving = false
                        processNextResolve(onServiceFound)
                    }
                }
            })
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { 
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NSD", "Error stopping discovery", e)
            }
        }
        discoveryListener = null
        synchronized(resolveQueue) {
            resolveQueue.clear()
            isResolving = false
        }
    }

    fun unregisterService() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        registrationListener = null
    }
}
