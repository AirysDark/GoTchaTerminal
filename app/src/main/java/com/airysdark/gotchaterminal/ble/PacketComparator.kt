package com.airysdark.gotchaterminal.ble

import com.airysdark.gotchaterminal.models.ble.BleSession
import com.airysdark.gotchaterminal.models.ble.ComparisonResult
import com.airysdark.gotchaterminal.models.ble.PacketInfo
import com.airysdark.gotchaterminal.models.ble.ServiceInfo

class PacketComparator {

    fun compare(realSession: BleSession, espSession: BleSession): ComparisonResult {
        val diffs = mutableListOf<String>()

        // 1. Compare Advertisements
        val realAdverts = realSession.advertisements.map { it.serviceUuids }.flatten().distinct()
        val espAdverts = espSession.advertisements.map { it.serviceUuids }.flatten().distinct()

        if (realAdverts.toSet() != espAdverts.toSet()) {
            diffs.add("ADVERT: Services mismatch. Real: $realAdverts, ESP: $espAdverts")
        }

        // 2. Compare Services & Characteristics
        val realServices = realSession.services.map { s: ServiceInfo -> s.uuid }.toSet()
        val espServices = espSession.services.map { s: ServiceInfo -> s.uuid }.toSet()

        if (realServices != espServices) {
            diffs.add("SERVICES: Missing/Extra services detected.")
            val missingInEsp = realServices - espServices
            val extraInEsp = espServices - realServices
            if (missingInEsp.isNotEmpty()) diffs.add("  Missing in ESP: $missingInEsp")
            if (extraInEsp.isNotEmpty()) diffs.add("  Extra in ESP: $extraInEsp")
        }

        realSession.services.forEach { realService: ServiceInfo ->
            val espService = espSession.services.find { s: ServiceInfo -> s.uuid == realService.uuid }
            if (espService != null) {
                val realChars = realService.characteristics.map { c -> c.uuid }.toSet()
                val espChars = espService.characteristics.map { c -> c.uuid }.toSet()
                if (realChars != espChars) {
                    diffs.add("CHARS: Mismatch in service ${realService.uuid}")
                }
            }
        }

        // 3. Compare Packet Sequence (Reads, Writes, Notifs)
        // This is complex as timing varies, but we can compare the order of characteristic interaction.
        val realSequence = realSession.packets.map { p: PacketInfo -> "${p.type}:${p.charUuid}" }
        val espSequence = espSession.packets.map { p: PacketInfo -> "${p.type}:${p.charUuid}" }

        if (realSequence != espSequence) {
            diffs.add("SEQUENCE: Interaction order differs.")
            // More detailed diffing could be added here
        }

        // 4. Data Comparison
        val realDataMap = realSession.packets.groupBy { p: PacketInfo -> "${p.type}:${p.charUuid}" }
        val espDataMap = espSession.packets.groupBy { p: PacketInfo -> "${p.type}:${p.charUuid}" }

        realDataMap.forEach { (key: String, packets: List<PacketInfo>) ->
            val espPackets = espDataMap[key]
            if (espPackets != null) {
                packets.forEachIndexed { index: Int, packet: PacketInfo ->
                    if (index < espPackets.size && packet.data != espPackets[index].data) {
                        diffs.add("DATA: Mismatch at $key (Index $index). Real: ${packet.data}, ESP: ${espPackets[index].data}")
                    }
                }
            }
        }

        return ComparisonResult(realSession, espSession, diffs)
    }
}
