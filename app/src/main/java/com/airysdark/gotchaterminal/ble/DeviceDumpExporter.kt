package com.airysdark.gotchaterminal.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports Bluetooth device data to a structured JSON format.
 */
object DeviceDumpExporter {

    fun exportToJson(
        deviceName: String,
        address: String,
        services: List<BluetoothGattService>,
        characteristicValues: Map<UUID, ByteArray>
    ): JSONObject {
        val root = JSONObject()
        root.put("device_name", deviceName)
        root.put("address", address)
        root.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val servicesArray = JSONArray()
        services.forEach { service ->
            val sObj = JSONObject()
            sObj.put("uuid", service.uuid.toString())
            
            val charArray = JSONArray()
            service.characteristics.forEach { char ->
                val cObj = JSONObject()
                cObj.put("uuid", char.uuid.toString())
                cObj.put("properties", decodeProperties(char.properties))
                
                // Only include value if it's readable and we have a cached value
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    characteristicValues[char.uuid]?.let { value ->
                        cObj.put("value_hex", value.joinToString("") { "%02x".format(it) })
                    }
                }
                charArray.put(cObj)
            }
            sObj.put("characteristics", charArray)
            servicesArray.put(sObj)
        }
        root.put("services", servicesArray)
        return root
    }

    private fun decodeProperties(props: Int): JSONArray {
        val arr = JSONArray()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) arr.put("READ")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) arr.put("WRITE")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) arr.put("WRITE_NO_RESPONSE")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) arr.put("NOTIFY")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) arr.put("INDICATE")
        return arr
    }
}
