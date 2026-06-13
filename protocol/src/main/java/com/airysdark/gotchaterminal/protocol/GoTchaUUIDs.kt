package com.airysdark.gotchaterminal.protocol

/**
 * Protocol-specific aliases for GoTcha UUIDs.
 * Direct references to core.GoTchaUUIDs are preferred for hardware-level constants.
 */
object GoTchaUUIDs {
    const val BATTERY_SERVICE = com.airysdark.gotchaterminal.core.GoTchaUUIDs.BATTERY_SERVICE
    const val BATTERY_LEVEL   = com.airysdark.gotchaterminal.core.GoTchaUUIDs.BATTERY_LEVEL

    const val DEVICE_INFO       = com.airysdark.gotchaterminal.core.GoTchaUUIDs.DEVICE_INFO
    const val SOFTWARE_REVISION = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SOFTWARE_REVISION
    const val MANUFACTURER_NAME = com.airysdark.gotchaterminal.core.GoTchaUUIDs.MANUFACTURER_NAME
    const val MODEL_NUMBER      = com.airysdark.gotchaterminal.core.GoTchaUUIDs.MODEL_NUMBER

    const val SUOTA_SERVICE    = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SUOTA_SERVICE
    const val SUOTA_MEM_DEV    = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SUOTA_MEM_DEV
    const val SUOTA_PATCH_DATA = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SUOTA_PATCH_DATA
    const val SUOTA_PATCH_LEN  = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SUOTA_PATCH_LEN
    const val SUOTA_STATUS     = com.airysdark.gotchaterminal.core.GoTchaUUIDs.SUOTA_STATUS

    const val FLASH_SERVICE = com.airysdark.gotchaterminal.core.GoTchaUUIDs.FLASH_SERVICE
    const val FLASH_COMMAND = com.airysdark.gotchaterminal.core.GoTchaUUIDs.FLASH_COMMAND
    const val FLASH_WRITE   = com.airysdark.gotchaterminal.core.GoTchaUUIDs.FLASH_WRITE
    const val FLASH_STATUS  = com.airysdark.gotchaterminal.core.GoTchaUUIDs.FLASH_STATUS
    const val FLASH_READ    = com.airysdark.gotchaterminal.core.GoTchaUUIDs.FLASH_READ

    const val IDENTITY_SERVICE     = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_SERVICE
    const val IDENTITY_ACTION      = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_ACTION
    const val IDENTITY_WRITE_ADDR  = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_WRITE_ADDR
    const val IDENTITY_READ_ADDR   = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_READ_ADDR
    const val IDENTITY_READ_ADVERT = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_READ_ADVERT
    const val IDENTITY_STATUS      = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_STATUS
    const val IDENTITY_KEY         = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_KEY
    const val IDENTITY_BLOB        = com.airysdark.gotchaterminal.core.GoTchaUUIDs.IDENTITY_BLOB
}
