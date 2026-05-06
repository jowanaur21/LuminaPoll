package company.luminapoll.core.utils

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val interfaceList = interfaces.toList()
            
            // Prioritize hotspot/AP interfaces (usually ap0, softap, wlan1)
            val prioritizedInterfaces = interfaceList.sortedByDescending { 
                val name = it.name.lowercase()
                name.contains("ap") || name.contains("softap") || name.contains("wlan1")
            }

            for (networkInterface in prioritizedInterfaces) {
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun getGatewayIpAddress(): String? {
        val ip = getLocalIpAddress()
        if (ip == "127.0.0.1") return null
        // On most Android hotspots, the host is .1
        return ip.substringBeforeLast(".") + ".1"
    }
}
