package id.pemilah.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private object LinkFixture {
    class FakeChannel : SerialChannel {
        var closed = false
        var written = mutableListOf<ByteArray>()

        override fun write(bytes: ByteArray, timeoutMs: Int): Int {
            written.add(bytes)
            return bytes.size
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int = 0

        override fun close() {
            closed = true
        }
    }

    class FakeOpener(private var results: MutableList<Result<SerialChannel>>) : SerialChannelOpener {
        var openCount = 0
        val opened = mutableListOf<FakeChannel>()

        constructor(channel: FakeChannel) : this(mutableListOf(Result.success(channel)))

        override fun open(): SerialChannel {
            openCount += 1
            val result = if (results.size > 1) results.removeAt(0) else results.first()
            return result.getOrThrow().also { if (it is FakeChannel) opened.add(it) }
        }
    }
}

class ControllerConnectionTest {

    @Test
    fun connect_successfulOpen_becomesConnected() {
        val channel = LinkFixture.FakeChannel()
        val connection = ControllerConnection(LinkFixture.FakeOpener(channel))

        val status = connection.connect()

        assertEquals(LinkState.CONNECTED, status.state)
        assertNull(status.failure)
        assertTrue(connection.status.isConnected)
    }

    @Test
    fun connect_permissionMissing_reportsSpecificFailure() {
        val opener = LinkFixture.FakeOpener(
            mutableListOf(Result.failure(SerialOpenException(SerialOpenReason.PERMISSION_MISSING))),
        )
        val connection = ControllerConnection(opener)

        val status = connection.connect()

        assertEquals(LinkState.FAILED, status.state)
        assertEquals(LinkFailure.PERMISSION_MISSING, status.failure)
        assertNull(connection.currentChannel)
    }

    @Test
    fun connect_nonCdcDevice_reportsUnsupportedDriver() {
        val opener = LinkFixture.FakeOpener(
            mutableListOf(Result.failure(SerialOpenException(SerialOpenReason.UNSUPPORTED_DRIVER))),
        )

        val status = ControllerConnection(opener).connect()

        assertEquals(LinkFailure.UNSUPPORTED_DRIVER, status.failure)
    }

    @Test
    fun connect_unexpectedError_doesNotPropagate() {
        val opener = LinkFixture.FakeOpener(mutableListOf(Result.failure(IllegalStateException("boom"))))

        val status = ControllerConnection(opener).connect()

        assertEquals(LinkState.FAILED, status.state)
        assertEquals(LinkFailure.UNEXPECTED, status.failure)
    }

    @Test
    fun connect_deviceGone_reportsDeviceGone() {
        val opener = LinkFixture.FakeOpener(
            mutableListOf(Result.failure(SerialOpenException(SerialOpenReason.DEVICE_GONE))),
        )

        val status = ControllerConnection(opener).connect()

        assertEquals(LinkFailure.DEVICE_GONE, status.failure)
    }

    @Test
    fun connect_twice_closesPreviousChannelFirst() {
        val first = LinkFixture.FakeChannel()
        val second = LinkFixture.FakeChannel()
        val opener = LinkFixture.FakeOpener(
            mutableListOf(Result.success(first), Result.success(second)),
        )
        val connection = ControllerConnection(opener)

        connection.connect()
        connection.connect()

        assertTrue(first.closed)
        assertSame(second, connection.currentChannel)
        assertEquals(2, opener.openCount)
    }

    @Test
    fun disconnect_closesChannelAndClearsStatus() {
        val channel = LinkFixture.FakeChannel()
        val connection = ControllerConnection(LinkFixture.FakeOpener(channel))
        connection.connect()

        val status = connection.disconnect()

        assertTrue(channel.closed)
        assertEquals(LinkState.DISCONNECTED, status.state)
        assertNull(connection.currentChannel)
    }

    @Test
    fun markLost_reportsDisconnectedWithoutAutoRestart() {
        val channel = LinkFixture.FakeChannel()
        val opener = LinkFixture.FakeOpener(channel)
        val connection = ControllerConnection(opener)
        connection.connect()

        val status = connection.markLost()

        assertTrue(channel.closed)
        assertEquals(LinkState.DISCONNECTED, status.state)
        assertEquals(LinkFailure.DEVICE_GONE, status.failure)
        // Tidak ada upaya membuka ulang otomatis (Rules.md §2.9): kanal dilepas
        // dan pemanggil yang memutuskan kapan mencoba lagi.
        assertNull(connection.currentChannel)
        assertEquals(1, opener.openCount)
    }

    @Test
    fun disconnect_withoutChannel_isSafe() {
        val connection = ControllerConnection(LinkFixture.FakeOpener(LinkFixture.FakeChannel()))

        val status = connection.disconnect()

        assertEquals(LinkState.DISCONNECTED, status.state)
    }

    @Test
    fun reconnect_afterFailure_canSucceed() {
        val channel = LinkFixture.FakeChannel()
        val opener = LinkFixture.FakeOpener(
            mutableListOf(
                Result.failure(SerialOpenException(SerialOpenReason.DEVICE_GONE)),
                Result.success(channel),
            ),
        )
        val connection = ControllerConnection(opener)

        assertEquals(LinkState.FAILED, connection.connect().state)
        assertEquals(LinkState.CONNECTED, connection.connect().state)
        assertSame(channel, connection.currentChannel)
    }
}
