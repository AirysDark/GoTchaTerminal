package com.airysdark.gotchaterminal.core

fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
