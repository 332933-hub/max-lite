package ru.maxlite.app

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File

/**
 * Веб-мессенджеры часто отдают файлы как blob:-URL, которые DownloadManager
 * скачать не может. Этот мост принимает содержимое blob из JS (base64)
 * и сохраняет его в Downloads.
 */
class BlobBridge(private val activity: Activity) {

    @JavascriptInterface
    fun save(mime: String, base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            val name = "max_${System.currentTimeMillis()}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                }
                val uri = activity.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("MediaStore insert failed")
                activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeBytes(bytes)
            }
            toast("Сохранено в Downloads: $name")
        } catch (e: Exception) {
            toast("Ошибка сохранения файла: ${e.message}")
        }
    }

    private fun toast(msg: String) =
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }

    companion object {
        const val JS_NAME = "MaxLiteBlob"

        /** JS, который вытаскивает blob и передаёт его в save(). */
        fun fetchScript(blobUrl: String): String = """
            (function() {
                fetch('$blobUrl')
                    .then(function(r) { return r.blob(); })
                    .then(function(b) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            $JS_NAME.save(b.type || '', reader.result.split(',')[1]);
                        };
                        reader.readAsDataURL(b);
                    });
            })();
        """.trimIndent()
    }
}
