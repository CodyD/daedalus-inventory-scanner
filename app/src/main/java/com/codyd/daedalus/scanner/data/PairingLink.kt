package com.codyd.daedalus.scanner.data

import java.net.URI
import java.net.URLDecoder

/** What the desktop's pairing QR holds: daedalus://pair?api=<url>&code=XXXX-XXXX. */
data class PairingLink(val apiUrl: String, val code: String) {
    companion object {
        /**
         * Parses a pairing link. Plain HTTP APIs are only accepted when
         * [allowHttp] (debug builds, for a local `wrangler dev`).
         */
        fun parse(raw: String, defaultApi: String, allowHttp: Boolean): PairingLink? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "daedalus" || uri.host != "pair") return null
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()
            val code = query["code"]?.takeIf { it.isNotBlank() } ?: return null
            val api = query["api"]?.trimEnd('/') ?: defaultApi
            if (!isAllowedApi(api, allowHttp)) return null
            return PairingLink(api, code)
        }

        fun isAllowedApi(url: String, allowHttp: Boolean): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return !uri.host.isNullOrBlank() && (uri.scheme == "https" || (allowHttp && uri.scheme == "http"))
        }
    }
}
