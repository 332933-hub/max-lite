package ru.maxlite.app

import java.util.concurrent.atomic.AtomicInteger

/**
 * Блокировка телеметрии на уровне сетевых запросов WebView.
 *
 * MAX — продукт VK, поэтому в списке в первую очередь трекеры экосистемы
 * VK/mail.ru (myTracker, top.mail.ru), плюс стандартный набор
 * (Яндекс, Google, Sentry и т.д.). Домены сравниваются по суффиксу:
 * "sentry.io" блокирует и "o12345.ingest.sentry.io".
 */
object TelemetryBlocker {

    val BUILTIN = listOf(
        // VK / mail.ru
        "tracker-api.my.com",   // myTracker (основная аналитика VK)
        "tracker.my.com",
        "trk.mail.ru",
        "top-fwz1.mail.ru",     // счётчик top.mail.ru
        "top-mail.ru",
        "rs.mail.ru",
        "ad.mail.ru",
        "r.mail.ru",
        "ads.vk.com",
        // Яндекс
        "mc.yandex.ru",
        "mc.yandex.com",
        "mc.webvisor.org",
        "an.yandex.ru",
        "appmetrica.yandex.ru",
        "report.appmetrica.yandex.net",
        "startup.mobile.yandex.net",
        // Google
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "app-measurement.com",
        "firebaselogging-pa.googleapis.com",
        "crashlytics.com",
        // Прочая аналитика и краш-репортинг
        "sentry.io",
        "browser.sentry-cdn.com",
        "amplitude.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "hotjar.com",
    )

    private val blocked = AtomicInteger(0)
    private val recentLog = ArrayDeque<String>()

    val blockedCount: Int get() = blocked.get()

    fun recent(): List<String> = synchronized(recentLog) { recentLog.toList() }

    /** host — хост запроса; extra — пользовательские домены из настроек. */
    fun shouldBlock(host: String?, extra: List<String>): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        val hit = (BUILTIN.asSequence() + extra.asSequence())
            .any { d -> h == d || h.endsWith(".$d") }
        if (hit) {
            blocked.incrementAndGet()
            synchronized(recentLog) {
                if (recentLog.size >= 20) recentLog.removeFirst()
                if (recentLog.lastOrNull() != h) recentLog.addLast(h)
            }
        }
        return hit
    }
}
