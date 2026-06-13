package com.airysdark.gotchaterminal.ble

import org.json.JSONObject

/**
 * Compares two device dumps and generates a human-readable difference report.
 */
object DeviceComparison {

    fun compare(dumpA: JSONObject, dumpB: JSONObject): String {
        val report = StringBuilder()
        report.appendLine("Comparison Report")
        report.appendLine("Device A: ${dumpA.optString("device_name")} (${dumpA.optString("address")})")
        report.appendLine("Device B: ${dumpB.optString("device_name")} (${dumpB.optString("address")})")
        report.appendLine("--------------------------------------------------")

        val svcsA = dumpA.getJSONArray("services")
        val svcsB = dumpB.getJSONArray("services")

        val mapA = (0 until svcsA.length()).associateBy { svcsA.getJSONObject(it).getString("uuid") }
        val mapB = (0 until svcsB.length()).associateBy { svcsB.getJSONObject(it).getString("uuid") }

        // 1. Missing Services
        (mapA.keys - mapB.keys).forEach { uuid ->
            report.appendLine("[MISSING IN B] Service: $uuid")
        }
        (mapB.keys - mapA.keys).forEach { uuid ->
            report.appendLine("[EXTRA IN B] Service: $uuid")
        }

        // 2. Compare Common Services
        mapA.keys.intersect(mapB.keys).forEach { sUuid ->
            val sA = svcsA.getJSONObject(mapA[sUuid]!!)
            val sB = svcsB.getJSONObject(mapB[sUuid]!!)

            val charsA = sA.getJSONArray("characteristics")
            val charsB = sB.getJSONArray("characteristics")

            val cMapA = (0 until charsA.length()).associateBy { charsA.getJSONObject(it).getString("uuid") }
            val cMapB = (0 until charsB.length()).associateBy { charsB.getJSONObject(it).getString("uuid") }

            // Missing Characteristics
            (cMapA.keys - cMapB.keys).forEach { cUuid ->
                report.appendLine("[MISSING IN B] Svc $sUuid -> Char: $cUuid")
            }
            (cMapB.keys - cMapA.keys).forEach { cUuid ->
                report.appendLine("[EXTRA IN B] Svc $sUuid -> Char: $cUuid")
            }

            // Compare Values and Properties
            cMapA.keys.intersect(cMapB.keys).forEach { cUuid ->
                val charA = charsA.getJSONObject(cMapA[cUuid]!!)
                val charB = charsB.getJSONObject(cMapB[cUuid]!!)

                val propA = charA.getJSONArray("properties").toString()
                val propB = charB.getJSONArray("properties").toString()
                if (propA != propB) {
                    report.appendLine("[CHANGED PROPERTIES] Char $cUuid:")
                    report.appendLine("  A: $propA")
                    report.appendLine("  B: $propB")
                }

                val valA = charA.optString("value_hex", "N/A")
                val valB = charB.optString("value_hex", "N/A")
                if (valA != valB) {
                    report.appendLine("[CHANGED VALUE] Char $cUuid:")
                    report.appendLine("  A: $valA")
                    report.appendLine("  B: $valB")
                }
            }
        }

        return if (report.length < 200) "No significant differences found." else report.toString()
    }
}
