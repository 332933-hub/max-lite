package ru.maxlite.app

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Блокировать телеметрию (по умолчанию — да). */
    var blockTelemetry: Boolean
        get() = sp.getBoolean("block_telemetry", true)
        set(v) = sp.edit().putBoolean("block_telemetry", v).apply()

    /** Подменять User-Agent на десктопный (если мобильная версия сайта урезана). */
    var desktopMode: Boolean
        get() = sp.getBoolean("desktop_mode", false)
        set(v) = sp.edit().putBoolean("desktop_mode", v).apply()

    /** Дополнительные блокируемые домены, по одному на строку. */
    var extraDomains: String
        get() = sp.getString("extra_domains", "") ?: ""
        set(v) = sp.edit().putString("extra_domains", v).apply()

    fun extraDomainsList(): List<String> =
        extraDomains.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
