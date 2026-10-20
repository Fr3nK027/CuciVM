package com.cuvm.data

import kotlin.random.Random

/** Random per-VM identity for isolation. Never copies a real device: IMEI uses the 001 test TAC range. */
object Identity {
    private fun hex(n: Int, r: Random) = (1..n).joinToString("") { "0123456789ABCDEF"[r.nextInt(16)].toString() }

    fun luhnDigit(body: String): Int {
        var sum = 0
        body.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 0) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return (10 - sum % 10) % 10
    }

    fun imei(r: Random = Random.Default): String {
        val body = "001" + (1..11).joinToString("") { r.nextInt(10).toString() }
        return body + luhnDigit(body)
    }

    /** Locally administered, unicast MAC (second hex digit 2, 6, A or E). */
    fun mac(r: Random = Random.Default): String {
        val first = (r.nextInt(256) and 0xFC) or 0x02
        return (listOf(first) + List(5) { r.nextInt(256) }).joinToString(":") { "%02x".format(it) }
    }

    fun newIdentity(vmId: String, now: Long, r: Random = Random.Default) = VmIdentity(
        vmId = vmId, model = "Virtual Phone", brand = "vmbox", product = "vphone", device = "vphone",
        manufacturer = "CuciVM", serial = hex(12, r), androidId = hex(16, r).lowercase(),
        processorInfo = "ARMv8 (host)", imei = imei(r), baseband = "virtual-1.0",
        carrierMccMnc = "00101", carrierName = "Test Network", updatedAt = now,
    )

    fun newNetwork(vmId: String, now: Long, r: Random = Random.Default) = VmNetwork(
        vmId = vmId, wifiSsid = "CuciVM-WiFi", wifiBssid = mac(r), nicMac = mac(r),
        ipv4 = "10.0.2.15", gatewayIpv4 = "10.0.2.2", updatedAt = now,
    )

    fun newSensors(vmId: String) = SENSORS.map { VmSensor(vmId, it, "host") }
}
