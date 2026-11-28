package dev.sito8943.sitonotas.core.sync

/**
 * Un frame STOMP 1.2 (lo justo para el cliente de [RealtimeClient]): comando,
 * cabeceras y cuerpo de texto, terminado en NUL. Spring manda un frame por
 * mensaje de WebSocket; un mensaje que solo trae saltos de linea es un latido.
 */
data class StompFrame(
    val command: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {

    fun encode(): String = buildString {
        append(command).append('\n')
        // CONNECT no escapa cabeceras (STOMP 1.2, por compatibilidad con 1.0).
        val escape = command != CONNECT
        for ((name, value) in headers) {
            append(if (escape) escapeHeader(name) else name)
            append(':')
            append(if (escape) escapeHeader(value) else value)
            append('\n')
        }
        append('\n').append(body).append(NUL)
    }

    companion object {
        const val CONNECT = "CONNECT"
        const val CONNECTED = "CONNECTED"
        const val SUBSCRIBE = "SUBSCRIBE"
        const val MESSAGE = "MESSAGE"
        const val ERROR = "ERROR"
        const val DISCONNECT = "DISCONNECT"
        private const val NUL = '\u0000'

        /** @return el frame, o nulo si es un latido o no se entiende. */
        fun parse(raw: String): StompFrame? {
            val text = raw.trimStart('\n', '\r').substringBefore(NUL)
            if (text.isBlank()) return null
            val lf = text.indexOf("\n\n")
            val crlf = text.indexOf("\r\n\r\n")
            val (headerEnd, separator) = when {
                crlf >= 0 && (lf < 0 || crlf < lf) -> crlf to 4
                else -> lf to 2
            }
            val head = if (headerEnd < 0) text else text.substring(0, headerEnd)
            val body = if (headerEnd < 0) "" else text.substring(headerEnd + separator)
            val lines = head.split('\n').map { it.removeSuffix("\r") }
            val command = lines.first().trim()
            if (command.isEmpty()) return null
            val unescape = command != CONNECTED
            val headers = linkedMapOf<String, String>()
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).let { if (unescape) unescapeHeader(it) else it }
                val value = line.substring(colon + 1).let { if (unescape) unescapeHeader(it) else it }
                // Si se repite una cabecera, vale la primera (STOMP 1.2).
                headers.putIfAbsent(name, value)
            }
            return StompFrame(command, headers, body)
        }

        private fun escapeHeader(value: String): String = buildString {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    ':' -> append("\\c")
                    else -> append(char)
                }
            }
        }

        private fun unescapeHeader(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder()
            var i = 0
            while (i < value.length) {
                val char = value[i]
                if (char == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        'c' -> out.append(':')
                        '\\' -> out.append('\\')
                        else -> out.append(char).append(value[i + 1])
                    }
                    i += 2
                } else {
                    out.append(char)
                    i++
                }
            }
            return out.toString()
        }
    }
}
