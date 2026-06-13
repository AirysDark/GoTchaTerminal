package com.airysdark.gotchaterminal.ble

import com.airysdark.gotchaterminal.models.ble.BleSession
import com.airysdark.gotchaterminal.models.ble.ComparisonResult

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
        val realServices = realSession.services.map { it.uuid }.toSet()
        val espServices = espSession.services.map { it.uuid }.toSet()

        if (realServices != espServices) {
            diffs.add("SERVICES: Missing/Extra services detected.")
            val missingInEsp = realServices - espServices
            val extraInEsp = espServices - realServices
            if (missingInEsp.isNotEmpty()) diffs.add("  Missing in ESP: $missingInEsp")
            if (extraInEsp.isNotEmpty()) diffs.add("  Extra in ESP: $extraInEsp")
        }

        realSession.services.forEach { realService ->
            val espService = espSession.services.find { it.uuid == realService.uuid }
            if (espService != null) {
                val realChars = realService.characteristics.map { it.uuid }.toSet()
                val espChars = espService.characteristics.map { it.uuid }.toSet()
                if (realChars != espChars) {
                    diffs.add("CHARS: Mismatch in service ${realService.uuid}")
                }
            }
        }

        // 3. Compare Packet Sequence (Reads, Writes, Notifs)
        val realSequence = realSession.packets.map { "${it.type}:${it.charUuid}" }
        val espSequence = espSession.packets.map { "${it.type}:${it.charUuid}" }

        if (realSequence != espSequence) {
            diffs.add("SEQUENCE: Interaction order differs.")
        }

        // 4. Data Comparison
        val realDataMap = realSession.packets.groupBy { "${it.type}:${it.charUuid}" }
        val espDataMap = espSession.packets.groupBy { "${it.type}:${it.charUuid}" }

        realDataMap.forEach { (key, packets) ->
            val espPackets = espDataMap[key]
            if (espPackets != null) {
                packets.forEachIndexed { index, packet ->
                    if (index < espPackets.size && packet.data != espPackets[index].data) {
                        diffs.add("DATA: Mismatch at $key (Index $index). Real: ${packet.data}, ESP: ${espPackets[index].data}")
                    }
                }
            }
        }

        return ComparisonResult(realSession, espSession, diffs)
    }
}
