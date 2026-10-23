package id.pemilah.telemetry

/**
 * Penyimpanan antrean event yang tahan mati. Implementasi nyata memakai
 * penyimpanan lokal (WP-11/`ConfigStore`); antarmuka ini menjaga logika antrean
 * tetap dapat diuji tanpa Android framework.
 */
interface TelemetryStore {
    fun pending(): List<TelemetryEvent>
    fun append(event: TelemetryEvent)
    fun remove(eventId: String)
}

/** Penyimpanan sementara di memori; bukan penyimpanan produksi. */
class InMemoryTelemetryStore(initial: List<TelemetryEvent> = emptyList()) : TelemetryStore {
    private val events = initial.toMutableList()

    override fun pending(): List<TelemetryEvent> = events.toList()

    override fun append(event: TelemetryEvent) {
        if (events.none { it.eventId == event.eventId }) events.add(event)
    }

    override fun remove(eventId: String) {
        events.removeAll { it.eventId == eventId }
    }
}

/** Pengiriman ke backend. Mengembalikan `true` bila backend mengonfirmasi. */
interface TelemetrySender {
    fun send(event: TelemetryEvent): Boolean
}

data class FlushReport(
    val sent: Int,
    val remaining: Int,
    val failedEventId: String?,
)

/**
 * Antrean event siklus ke backend (COM-07, COM-08, COM-09).
 *
 * Aturan yang dijaga:
 * - Event disimpan lebih dulu, baru dikirim; antrean bertahan setelah aplikasi
 *   ditutup bila [TelemetryStore] persisten.
 * - Retry memakai `eventId` yang sama sehingga backend dapat menolak duplikat.
 * - Kegagalan backend tidak melempar eksepsi ke pemanggil dan tidak pernah
 *   menjadi syarat operasi (`Rules.md` §2.8).
 *
 * Antrean dijalankan pada satu worker pengiriman (Architecture.md §5), sehingga
 * akses di bawah ini diserialkan lewat [synchronized].
 */
class TelemetryQueue(
    private val store: TelemetryStore,
    private val sender: TelemetrySender,
) {

    /** Menyimpan event sebelum ada upaya pengiriman; idempoten per `eventId`. */
    @Synchronized
    fun enqueue(event: TelemetryEvent) {
        store.append(event)
    }

    /** Jumlah event yang belum dikonfirmasi backend. */
    @Synchronized
    fun pendingCount(): Int = store.pending().size

    /**
     * Kirim seluruh event tertunda. Berhenti pada kegagalan pertama agar urutan
     * tetap terjaga; event yang gagal tetap di antrean dengan `eventId` sama.
     */
    @Synchronized
    fun flush(): FlushReport {
        var sent = 0
        for (event in store.pending()) {
            val delivered = runCatching { sender.send(event) }
                .getOrElse { false }
            if (!delivered) {
                return FlushReport(sent = sent, remaining = store.pending().size, failedEventId = event.eventId)
            }
            store.remove(event.eventId)
            sent += 1
        }
        return FlushReport(sent = sent, remaining = store.pending().size, failedEventId = null)
    }
}
