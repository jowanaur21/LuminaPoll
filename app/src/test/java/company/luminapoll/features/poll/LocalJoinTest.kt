package company.luminapoll.features.poll

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalJoinTest {

    @Test
    fun testIpFromHexCode() {
        // Example: Host IP is 192.168.1.174
        // 174 in hex is AE
        val code = "AE1B"
        val lastByteHex = code.take(2)
        val lastByte = Integer.parseInt(lastByteHex, 16)
        
        assertEquals(174, lastByte)
        
        val prefix = "192.168.1"
        val derivedIp = "$prefix.$lastByte"
        assertEquals("192.168.1.174", derivedIp)
    }

    @Test
    fun testIpFromHexCode_DifferentValues() {
        // Example: Host IP is 10.0.0.45
        // 45 in hex is 2D
        val code = "2DXY"
        val lastByteHex = code.take(2)
        val lastByte = Integer.parseInt(lastByteHex, 16)
        
        assertEquals(45, lastByte)
        
        val prefix = "10.0.0"
        val derivedIp = "$prefix.$lastByte"
        assertEquals("10.0.0.45", derivedIp)
    }

    @Test
    fun testIpFromHexCode_MaxByte() {
        // Example: Host IP is 192.168.1.255
        // 255 in hex is FF
        val code = "FF99"
        val lastByteHex = code.take(2)
        val lastByte = Integer.parseInt(lastByteHex, 16)
        
        assertEquals(255, lastByte)
    }
}
