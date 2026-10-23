package com.nexus.gamebooster.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.nexus.gamebooster.MainActivity
import com.nexus.gamebooster.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** DNS-only IPv4 UDP tunnel. Ordinary application traffic is not routed into this VPN. */
class DnsForwardingVpnService : VpnService() {
    @Volatile private var tun: ParcelFileDescriptor? = null
    @Volatile private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Explicit stop action closes the TUN descriptor before stopping the service.
        // This wakes a worker blocked in FileInputStream.read() and avoids a stale VPN.
        if (intent?.action == ACTION_STOP) {
            stopTunnelNow()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val resolver = intent?.getStringExtra(EXTRA_RESOLVER)?.takeIf(::isIpv4Address)
        if (resolver == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (running.get()) return START_STICKY
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            val descriptor = Builder()
                .setSession(getString(R.string.dns_vpn_session))
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .setBlocking(true)
                .establish()
            if (descriptor == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            tun = descriptor
            running.set(true)
            isRunning = true
            worker = thread(name = "NexusDnsForwarder", isDaemon = true) {
                runForwarder(descriptor, resolver)
            }
            return START_STICKY
        } catch (_: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private fun runForwarder(descriptor: ParcelFileDescriptor, resolver: String) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val count = input.read(buffer)
                if (count <= 0) continue
                val packet = buffer.copyOf(count)
                val response = forwardIpv4UdpDns(packet, resolver)
                if (response != null && running.get()) output.write(response)
            } catch (_: Exception) {
                if (running.get()) continue
                break
            }
        }
    }

    private fun forwardIpv4UdpDns(packet: ByteArray, resolver: String): ByteArray? {
        if (packet.size < 28 || (packet[0].toInt() ushr 4 and 0xF) != 4) return null
        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null // UDP only
        val totalLength = u16(packet, 2).coerceAtMost(packet.size)
        if (totalLength < ipHeaderLength + 8) return null
        val destinationIp = packet.copyOfRange(16, 20)
        if (!destinationIp.contentEquals(VIRTUAL_DNS_BYTES)) return null
        val sourcePort = u16(packet, ipHeaderLength)
        val destinationPort = u16(packet, ipHeaderLength + 2)
        if (destinationPort != 53) return null
        val udpLength = u16(packet, ipHeaderLength + 4)
        if (udpLength < 8 || ipHeaderLength + udpLength > totalLength) return null
        val dnsQuery = packet.copyOfRange(ipHeaderLength + 8, ipHeaderLength + udpLength)
        if (dnsQuery.size < 12) return null

        val dnsResponse = try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = 1800
                val address = InetAddress.getByName(resolver)
                socket.connect(InetSocketAddress(address, 53))
                socket.send(DatagramPacket(dnsQuery, dnsQuery.size))
                val answer = ByteArray(4096)
                val responsePacket = DatagramPacket(answer, answer.size)
                socket.receive(responsePacket)
                answer.copyOf(responsePacket.length)
            }
        } catch (_: Exception) {
            null
        } ?: return null
        if (dnsResponse.size < 12) return null
        if (dnsResponse[0] != dnsQuery[0] || dnsResponse[1] != dnsQuery[1]) return null

        val sourceIp = packet.copyOfRange(12, 16)
        val out = ByteArray(20 + 8 + dnsResponse.size)
        out[0] = 0x45
        out[1] = 0
        put16(out, 2, out.size)
        put16(out, 4, u16(packet, 4))
        put16(out, 6, 0x4000)
        out[8] = 64
        out[9] = 17
        System.arraycopy(VIRTUAL_DNS_BYTES, 0, out, 12, 4)
        System.arraycopy(sourceIp, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, 53)
        put16(out, 22, sourcePort)
        put16(out, 24, 8 + dnsResponse.size)
        put16(out, 26, 0) // UDP checksum is optional for IPv4.
        System.arraycopy(dnsResponse, 0, out, 28, dnsResponse.size)
        return out
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.dns_vpn_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.dns_vpn_notification_title))
            .setContentText(getString(R.string.dns_vpn_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun stopTunnelNow() {
        running.set(false)
        isRunning = false
        worker?.interrupt()
        // Closing the descriptor is essential: interrupt alone does not reliably
        // unblock a thread reading a ParcelFileDescriptor on every Android build.
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        worker = null
    }

    override fun onRevoke() {
        stopTunnelNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnelNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun isIpv4Address(value: String): Boolean = try {
        val address = InetAddress.getByName(value)
        address.address.size == 4 && value.matches(Regex("[0-9.]+"))
    } catch (_: Exception) { false }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) sum += (bytes[i].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        const val EXTRA_RESOLVER = "resolver_ipv4"
        const val ACTION_STOP = "com.nexus.gamebooster.action.STOP_DNS_VPN"
        private const val CHANNEL_ID = "nexus_dns_vpn"
        private const val NOTIFICATION_ID = 4107
        private const val VPN_ADDRESS = "10.111.0.1"
        private const val VIRTUAL_DNS = "10.111.0.2"
        private val VIRTUAL_DNS_BYTES = byteArrayOf(10, 111, 0, 2)
    }
}
