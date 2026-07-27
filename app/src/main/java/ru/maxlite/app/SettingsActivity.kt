package ru.maxlite.app

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Настройки собраны кодом, без layout-XML и AndroidX —
 * у приложения ноль внешних зависимостей.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var extraEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(header("Приватность"))

        root.addView(Switch(this).apply {
            text = "Блокировать телеметрию"
            isChecked = prefs.blockTelemetry
            setOnCheckedChangeListener { _, v -> prefs.blockTelemetry = v }
        })

        root.addView(caption(
            "Заблокировано запросов за сессию: ${TelemetryBlocker.blockedCount}" +
                TelemetryBlocker.recent().takeIf { it.isNotEmpty() }
                    ?.joinToString("\n", prefix = "\nПоследние:\n") .orEmpty()
        ))

        root.addView(header("Дополнительные домены для блокировки"))
        extraEdit = EditText(this).apply {
            hint = "по одному домену на строку"
            setText(prefs.extraDomains)
            minLines = 3
        }
        root.addView(extraEdit)

        root.addView(header("Вид"))
        root.addView(Switch(this).apply {
            text = "Вписывать страницу в ширину экрана"
            isChecked = prefs.fitWidth
            setOnCheckedChangeListener { _, v -> prefs.fitWidth = v }
        })
        root.addView(caption(
            "Чтобы длинные ссылки в сообщениях не растягивали чат " +
                "и кнопка отправки не уезжала за край."
        ))
        root.addView(Switch(this).apply {
            text = "Версия для ПК (User-Agent)"
            isChecked = prefs.desktopMode
            setOnCheckedChangeListener { _, v -> prefs.desktopMode = v }
        })

        root.addView(header("Данные"))
        root.addView(Button(this).apply {
            text = "Очистить cookies и данные сайта"
            setOnClickListener {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                Toast.makeText(
                    this@SettingsActivity,
                    "Очищено. Перезапустите приложение — потребуется войти заново.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        root.addView(header("Встроенный список блокировки"))
        root.addView(caption(TelemetryBlocker.BUILTIN.joinToString("\n")))

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onPause() {
        prefs.extraDomains = extraEdit.text.toString()
        super.onPause()
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        alpha = 0.7f
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
