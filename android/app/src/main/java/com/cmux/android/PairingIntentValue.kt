package com.cmux.android

object PairingIntentValue {
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"

    fun fromParts(
        action: String?,
        type: String?,
        dataString: String?,
        extraText: CharSequence?,
        processText: CharSequence?
    ): String? {
        if (action == ACTION_SEND && type?.startsWith("text/") == true) {
            return firstNonBlank(extraText?.toString(), dataString, processText?.toString())
        }
        if (action == ACTION_PROCESS_TEXT) {
            return firstNonBlank(processText?.toString(), dataString, extraText?.toString())
        }
        return firstNonBlank(dataString, extraText?.toString(), processText?.toString())
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }
}
