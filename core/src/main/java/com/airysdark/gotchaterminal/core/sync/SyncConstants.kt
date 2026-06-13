package com.airysdark.gotchaterminal.core.sync

object SyncConstants {
    // Paths for DataClient
    const val PATH_PACKET_CAPTURE = "/packet_capture"
    const val PATH_SESSION_CAPTURE = "/session_capture"
    const val PATH_ADVERT_CAPTURE = "/advert_capture"
    const val PATH_CHALLENGE_CAPTURE = "/challenge_capture"
    const val PATH_RESEARCH_DATA = "/research_data"
    const val PATH_SYNC_DATA = "/sync/sessions"

    // Paths for MessageClient (commands)
    const val PATH_START_SCAN = "/command/start_scan"
    const val PATH_STOP_SCAN = "/command/stop_scan"
    const val PATH_CONNECT_DEVICE = "/command/connect"
    const val PATH_DISCONNECT_DEVICE = "/command/disconnect"
    const val PATH_SYNC_STATUS = "/status/sync"

    // Capability names
    const val CAPABILITY_PHONE_APP = "gotcha_terminal_phone_app"
    const val CAPABILITY_WEAR_APP = "gotcha_terminal_wear_app"

    // Keys for DataMap
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_DATA_JSON = "data_json"
    const val KEY_SESSION_NAME = "session_name"
}
