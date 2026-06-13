package com.airysdark.gotchaterminal.protocol

object UUIDDatabase {
    private val database = mapOf(
        "00001800-0000-1000-8000-00805f9b34fb" to "Generic Access",
        "00001801-0000-1000-8000-00805f9b34fb" to "Generic Attribute",
        "0000180f-0000-1000-8000-00805f9b34fb" to "Battery Service",
        "00002a19-0000-1000-8000-00805f9b34fb" to "Battery Level",
        "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information",
        "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name",
        "00002a24-0000-1000-8000-00805f9b34fb" to "Model Number",
        "00002a25-0000-1000-8000-00805f9b34fb" to "Serial Number",
        "00002a27-0000-1000-8000-00805f9b34fb" to "Hardware Revision",
        "00002a26-0000-1000-8000-00805f9b34fb" to "Firmware Revision",
        "00002a28-0000-1000-8000-00805f9b34fb" to "Software Revision",
        
        "0000fef5-0000-1000-8000-00805f9b34fb" to "Dialog SUOTA Service",
        "8082caa8-41a6-4021-91c6-56f9b954cc34" to "SUOTA Mem Dev",
        "457871e8-d516-4ca1-9116-57d0b17b9cb2" to "SUOTA Patch Data",
        "9d84b9a3-000c-49d8-9183-855b673fda31" to "SUOTA Patch Len",
        "5f78df94-798c-46f5-990a-b3eb6a065c88" to "SUOTA Status",
        
        GoTchaUUIDs.FLASH_SERVICE to "Go-tcha Flash Service",
        GoTchaUUIDs.FLASH_COMMAND to "Flash Command",
        GoTchaUUIDs.FLASH_WRITE to "Flash Write",
        GoTchaUUIDs.FLASH_STATUS to "Flash Status",
        GoTchaUUIDs.FLASH_READ to "Flash Read",
        
        GoTchaUUIDs.IDENTITY_SERVICE to "Go-tcha Identity Service",
        GoTchaUUIDs.IDENTITY_ACTION to "Identity Action",
        GoTchaUUIDs.IDENTITY_WRITE_ADDR to "Identity Write MAC",
        GoTchaUUIDs.IDENTITY_READ_ADDR to "Identity Read MAC",
        GoTchaUUIDs.IDENTITY_READ_ADVERT to "Identity Read Adv",
        GoTchaUUIDs.IDENTITY_STATUS to "Identity Status",
        GoTchaUUIDs.IDENTITY_KEY to "Identity Key",
        GoTchaUUIDs.IDENTITY_BLOB to "Identity Blob"
    )

    fun getName(uuid: String): String? = database[uuid.lowercase()]
    
    fun getShortName(uuid: String): String {
        return getName(uuid) ?: uuid.take(8).uppercase()
    }
}
