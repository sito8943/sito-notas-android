package dev.sito8943.sitonotas.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StompFrameTest {

    @Test
    fun `connect carries the bearer token unescaped`() {
        val frame = StompFrame(
            StompFrame.CONNECT,
            linkedMapOf("accept-version" to "1.2", "host" to "api.test", "Authorization" to "Bearer a:b"),
        )

        assertEquals(
            "CONNECT\naccept-version:1.2\nhost:api.test\nAuthorization:Bearer a:b\n\n\u0000",
            frame.encode(),
        )
    }

    @Test
    fun `subscribe escapes header values`() {
        val frame = StompFrame(StompFrame.SUBSCRIBE, linkedMapOf("id" to "sub-0", "destination" to "/a:b\nc\\d"))

        assertEquals("SUBSCRIBE\nid:sub-0\ndestination:/a\\cb\\nc\\\\d\n\n\u0000", frame.encode())
    }

    @Test
    fun `parses a message from Spring`() {
        val raw = "MESSAGE\ndestination:/user/queue/notes\ncontent-type:application/json\n" +
            "subscription:sub-0\nmessage-id:abc-1\ncontent-length:41\n\n" +
            "{\"type\":\"NOTE_UPDATED\",\"noteId\":12}\n\u0000"

        val frame = StompFrame.parse(raw)!!

        assertEquals(StompFrame.MESSAGE, frame.command)
        assertEquals("/user/queue/notes", frame.headers["destination"])
        assertEquals("sub-0", frame.headers["subscription"])
        assertEquals("{\"type\":\"NOTE_UPDATED\",\"noteId\":12}\n", frame.body)
    }

    @Test
    fun `parses connected and error frames, crlf included`() {
        val connected = StompFrame.parse("CONNECTED\r\nversion:1.2\r\nheart-beat:0,0\r\n\r\n\u0000")!!
        assertEquals(StompFrame.CONNECTED, connected.command)
        assertEquals("1.2", connected.headers["version"])

        val error = StompFrame.parse("ERROR\nmessage:Failed to send message\\c bad token\n\n\u0000")!!
        assertEquals(StompFrame.ERROR, error.command)
        assertEquals("Failed to send message: bad token", error.headers["message"])
    }

    @Test
    fun `the first repeated header wins and heartbeats are ignored`() {
        assertEquals("1", StompFrame.parse("MESSAGE\nx:1\nx:2\n\n\u0000")!!.headers["x"])
        assertNull(StompFrame.parse("\n"))
        assertNull(StompFrame.parse(""))
    }

    @Test
    fun `round trip`() {
        val frame = StompFrame(StompFrame.MESSAGE, linkedMapOf("destination" to "/q:1"), "{}")
        assertEquals(frame, StompFrame.parse(frame.encode()))
    }

    @Test
    fun `socket url follows the api base url like the web`() {
        assertEquals("wss://api.notes.test/ws", RealtimeClient.socketUrlOf("https://api.notes.test/"))
        assertEquals("ws://10.0.2.2:8083/ws", RealtimeClient.socketUrlOf("http://10.0.2.2:8083"))
        assertEquals("wss://host/api/ws", RealtimeClient.socketUrlOf("https://host/api"))
    }
}
