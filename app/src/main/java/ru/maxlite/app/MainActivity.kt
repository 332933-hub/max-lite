package ru.maxlite.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerController
import android.webkit.ServiceWorkerClient
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var prefs: Prefs
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Кэш настроек, чтобы не читать SharedPreferences на каждый сетевой запрос.
    @Volatile private var blockingEnabled = true
    @Volatile private var extraDomains: List<String> = emptyList()
    private var appliedDesktopMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        refreshPrefsCache()

        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(false)
        configureWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    private fun refreshPrefsCache() {
        blockingEnabled = prefs.blockTelemetry
        extraDomains = prefs.extraDomainsList()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Веб-контенту незачем читать локальные файлы (file://).
            allowFileAccess = false
            // А вот content:// нужен: через него WebView читает файлы,
            // выбранные в системном пикере. Без него отправка файлов
            // молча виснет (проверено).
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            // Safe Browsing шлёт URL-ы в Google — отключаем.
            safeBrowsingEnabled = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        applyUserAgent()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.addJavascriptInterface(BlobBridge(this), BlobBridge.JS_NAME)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val scheme = url.scheme ?: return true
                if (scheme != "https" && scheme != "http") {
                    openExternal(url); return true
                }
                val host = url.host ?: return true
                val inside = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                if (!inside) openExternal(url)
                return !inside
            }

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = maybeBlock(request)

            override fun onRenderProcessGone(
                view: WebView, detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                recreate()
                return true
            }
        }

        // Часть запросов может уходить через service worker мимо WebViewClient —
        // перехватываем и их.
        ServiceWorkerController.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(
                    request: WebResourceRequest
                ): WebResourceResponse? = maybeBlock(request)
            }
        )

        webView.webChromeClient = object : WebChromeClient() {
            // Камера/микрофон сайту не выдаются никогда: чат + файлы, звонков нет.
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onGeolocationPermissionsShowPrompt(
                origin: String, callback: GeolocationPermissions.Callback
            ) = callback.invoke(origin, false, false)

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    val accept = params.acceptTypes.filter { it.isNotBlank() }
                    if (accept.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, accept.toTypedArray())
                    if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    // Защита от убийства процесса MIUI, пока открыт пикер.
                    startForegroundService(Intent(this@MainActivity, KeepAliveService::class.java))
                    startActivityForResult(
                        Intent.createChooser(intent, "Выбор файла"), FILE_CHOOSER_REQUEST
                    )
                    true
                } catch (e: ActivityNotFoundException) {
                    stopService(Intent(this@MainActivity, KeepAliveService::class.java))
                    filePathCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            when {
                url.startsWith("blob:") ->
                    webView.evaluateJavascript(BlobBridge.fetchScript(url), null)
                url.startsWith("data:") ->
                    Toast.makeText(this, "Формат data: не поддерживается", Toast.LENGTH_SHORT).show()
                else -> {
                    android.util.Log.d(
                        "MaxLite", "download url=$url cd=$contentDisposition mime=$mimeType"
                    )
                    val fileName = pickFileName(url, contentDisposition, mimeType)
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                        addRequestHeader("User-Agent", userAgent)
                        setMimeType(mimeType)
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "Скачивание: $fileName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * MAX отдаёт файлы с .../getfile?rq=... и именем только в Content-Disposition,
     * причём в RFC 5987-форме (filename*=utf-8''имя), которую URLUtil.guessFileName
     * не понимает и возвращает "getfile.bin". Парсим заголовок сами.
     */
    private fun pickFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            // filename*=utf-8''%D0%B8%D0%BC%D1%8F.txt — приоритетная форма, тут оригинал.
            Regex("""filename\*\s*=\s*[^']*''([^;]+)""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
                ?.let { return sanitize(Uri.decode(it.groupValues[1].trim().trim('"'))) }
            // filename="имя"
            Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
                ?.let { return sanitize(it.groupValues[1].trim()) }
        }
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        if (!guessed.startsWith("getfile") && !guessed.startsWith("downloadfile")) return guessed
        val uri = Uri.parse(url)
        for (key in uri.queryParameterNames) {
            val v = uri.getQueryParameter(key)
            if (!v.isNullOrBlank() && Regex(""".+\.[A-Za-z0-9]{1,6}$""").matches(v)) return sanitize(v)
        }
        return guessed
    }

    /** Имя из заголовка — внешние данные: срезаем разделители путей. */
    private fun sanitize(name: String): String =
        name.replace('/', '_').replace('\\', '_').trim().ifEmpty { "file.bin" }

    private fun maybeBlock(request: WebResourceRequest): WebResourceResponse? {
        if (!blockingEnabled) return null
        return if (TelemetryBlocker.shouldBlock(request.url.host, extraDomains)) {
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        } else null
    }

    private fun applyUserAgent() {
        appliedDesktopMode = prefs.desktopMode
        if (appliedDesktopMode) {
            webView.settings.userAgentString = DESKTOP_UA
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
        } else {
            webView.settings.userAgentString = null // системный по умолчанию
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Нет приложения для: $uri", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            stopService(Intent(this, KeepAliveService::class.java))
            val callback = filePathCallback ?: return
            filePathCallback = null
            if (resultCode != RESULT_OK || data == null) {
                callback.onReceiveValue(null)
                return
            }
            val clip = data.clipData
            val uris: Array<Uri>? = when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
            callback.onReceiveValue(uris)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPrefsCache()
        if (prefs.desktopMode != appliedDesktopMode) {
            applyUserAgent()
            webView.reload()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    // Назад — по истории WebView; с корня сворачиваемся, не убивая сессию.
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
    }

    // Долгое нажатие «Назад» — настройки (основной путь — ярлык на иконке).
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val START_URL = "https://web.max.ru"
        private const val FILE_CHOOSER_REQUEST = 1
        // Навигация разрешена только по доменам MAX; всё остальное — во внешний браузер.
        private val ALLOWED_HOSTS = listOf("max.ru", "oneme.ru")
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
