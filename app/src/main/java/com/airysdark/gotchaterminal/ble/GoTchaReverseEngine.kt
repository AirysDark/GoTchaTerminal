package com.airysdark.gotchaterminal.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * GoTchaReverseEngine provides high-level tools for dumping and comparing 
 * Go-tcha and Pokemon GO Plus device states.
 */
class GoTchaReverseEngine(private val context: Context, private val bleManager: BLEManager) {

    private val TAG = "GoTchaReverseEngine"

    private val targetNames = listOf("Pokemon GO Plus", "Go-tcha", "Go-tcha Evolve")

    private val knownServices = mapOf(
        "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information",
        "0000180f-0000-1000-8000-00805f9b34fb" to "Battery",
        "0000fef5-0000-1000-8000-00805f9b34fb" to "SUOTA",
        "b6954f1f-0c2b-4537-80a3-91d492d03f15" to "Flash",
        "6662e10f-358b-4f06-9d72-275e5c8f4ed5" to "Assistant"
    )

    /**
     * Identifies if a discovered device is likely a Go-tcha or PGP.
     */
    fun isGoTchaDevice(name: String?): Boolean {
        if (name == null) return false
        return targetNames.any { name.contains(it, ignoreCase = true) }
    }

    /**
     * Orchestrates a full read of all readable characteristics and generates a JSON dump.
     */
    suspend fun performFullDump(onProgress: (String) -> Unit): JSONObject {
        onProgress("Starting deep dump...")
        val services = bleManager.discoveredServices.value
        
        // 1. Trigger reads for all readable characteristics
        services.forEach { service ->
            service.characteristics.forEach { char ->
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    onProgress("Reading ${char.uuid.toString().take(8)}...")
                    bleManager.readCharacteristic(service.uuid, char.uuid)
                    delay(200) // Small delay to avoid GATT congestion
                }
            }
        }

        onProgress("Compiling results...")
        return generateJsonDump()
    }

    private fun generateJsonDump(): JSONObject {
        val json = JSONObject()
        val services = bleManager.discoveredServices.value
        val charValues = bleManager.characteristicValues.value

        json.put("dump_timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        
        val servicesArray = JSONArray()
        services.forEach { service ->
            val sObj = JSONObject()
            val sUuid = service.uuid.toString()
            sObj.put("uuid", sUuid)
            sObj.put("name", knownServices[sUuid] ?: "Unknown Service")

            val charArray = JSONArray()
            service.characteristics.forEach { char ->
                val cObj = JSONObject()
                cObj.put("uuid", char.uuid.toString())
                cObj.put("properties", decodeProperties(char.properties))
                
                charValues[char.uuid]?.let { value ->
                    cObj.put("value_hex", value.joinToString("") { "%02x".format(it) })
                    cObj.put("value_ascii", value.map { if (it in 32..126) it.toChar() else '.' }.joinToString(""))
                }
                charArray.put(cObj)
            }
            sObj.put("characteristics", charArray)
            servicesArray.put(sObj)
        }
        json.put("services", servicesArray)
        return json
    }

    /**
     * Saves the dump to a text report for manual inspection.
     */
    fun exportToTextReport(json: JSONObject): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.getExternalFilesDir(null), "gotcha_dump_$timestamp.txt")
        
        val report = buildString {
            appendLine("GoTcha Reverse Engineering Report")
            appendLine("Timestamp: ${json.optString("dump_timestamp")}")
            appendLine("------------------------------------------")

            val svcs = json.getJSONArray("services")
            for (i in 0 until svcs.length()) {
                val s = svcs.getJSONObject(i)
                appendLine("\n[SERVICE] ${s.getString("name")} (${s.getString("uuid")})")
                val chars = s.getJSONArray("characteristics")
                for (j in 0 until chars.length()) {
                    val c = chars.getJSONObject(j)
                    appendLine("  ├ CHAR: ${c.getString("uuid")}")
                    appendLine("  │ PROPS: ${c.getJSONArray("properties")}")
                    if (c.has("value_hex")) {
                        appendLine("  │ HEX:   ${c.getString("value_hex")}")
                        appendLine("  │ ASCII: ${c.getString("value_ascii")}")
                    }
                }
            }
        }
        file.writeText(report)
        return file
    }

    /**
     * Compares two device dumps and lists discrepancies.
     */
    fun compare(dumpA: JSONObject, dumpB: JSONObject): List<String> {
        val diffs = mutableListOf<String>()
        val svcsA = dumpA.getJSONArray("services")
        val svcsB = dumpB.getJSONArray("services")

        val mapA = (0 until svcsA.length()).associateBy { svcsA.getJSONObject(it).getString("uuid") }
        val mapB = (0 until svcsB.length()).associateBy { svcsB.getJSONObject(it).getString("uuid") }

        (mapA.keys - mapB.keys).forEach { diffs.add("Service $it missing in second device") }
        (mapB.keys - mapA.keys).forEach { diffs.add("Service $it extra in second device") }

        mapA.keys.intersect(mapB.keys).forEach { sUuid ->
            val sA = svcsA.getJSONObject(mapA[sUuid]!!)
            val sB = svcsB.getJSONObject(mapB[sUuid]!!)
            
            val charsA = sA.getJSONArray("characteristics")
            val charsB = sB.getJSONArray("characteristics")
            
            val cMapA = (0 until charsA.length()).associateBy { charsA.getJSONObject(it).getString("uuid") }
            val cMapB = (0 until charsB.length()).associateBy { charsB.getJSONObject(it).getString("uuid") }

            (cMapA.keys - cMapB.keys).forEach { diffs.add("Svc $sUuid: Char $it missing") }
            (cMapB.keys - cMapA.keys).forEach { diffs.add("Svc $sUuid: Char $it extra") }

            cMapA.keys.intersect(cMapB.keys).forEach { cUuid ->
                val valA = charsA.getJSONObject(cMapA[cUuid]!!).optString("value_hex")
                val valB = charsB.getJSONObject(cMapB[cUuid]!!).optString("value_hex")
                if (valA != valB) {
                    diffs.add("Value mismatch [$cUuid]:\n  A: $valA\n  B: $valB")
                }
            }
        }
        return diffs
    }

    private fun decodeProperties(props: Int): JSONArray {
        val arr = JSONArray()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) arr.put("READ")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) arr.put("WRITE")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) arr.put("NOTIFY")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) arr.put("INDICATE")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) arr.put("WRITE_NO_RESP")
        return arr
    }
}
