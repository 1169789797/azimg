package com.qyan.azimg

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.qyan.azimg.databinding.ActivityMainBinding
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    /** 分块保存会话（避免大图 data URL 走 Binder 导致闪退） */
    private val saveLock = Any()
    private var saveUri: Uri? = null
    private var saveStream: OutputStream? = null
    private var savePendingB64 = StringBuilder()
    private var saveActive = false

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return@registerForActivityResult

            if (result.resultCode != RESULT_OK) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val data = result.data
            val uris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let(uris::add)
                }
            }
            if (uris.isEmpty()) {
                data?.data?.let(uris::add)
            }
            callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        setupWebView()
        requestMediaPermissionIfNeeded()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // 标记运行环境，供页面走分块保存
                view.evaluateJavascript(
                    "window.__AZ_ANDROID_APP__=true;",
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    fileChooserLauncher.launch(
                        Intent.createChooser(intent, getString(R.string.app_name))
                    )
                    true
                } catch (_: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        // 不再用 DownloadListener 接 data: URL（大图会 TransactionTooLarge 闪退）
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun beginSave(fileName: String, mimeType: String): Boolean {
            return try {
                synchronized(saveLock) {
                    abortSaveLocked()
                    val mime = mimeType.ifBlank { "image/jpeg" }
                    val name = sanitizeFileName(fileName, mime)
                    val uri = createGalleryUri(name, mime) ?: return false
                    val stream = contentResolver.openOutputStream(uri) ?: run {
                        contentResolver.delete(uri, null, null)
                        return false
                    }
                    saveUri = uri
                    saveStream = stream
                    savePendingB64.setLength(0)
                    saveActive = true
                    true
                }
            } catch (_: Exception) {
                synchronized(saveLock) { abortSaveLocked() }
                false
            }
        }

        @JavascriptInterface
        fun appendChunk(chunk: String): Boolean {
            if (chunk.isEmpty()) return true
            return try {
                synchronized(saveLock) {
                    if (!saveActive || saveStream == null) return false
                    savePendingB64.append(chunk)
                    flushPendingB64Locked(false)
                    true
                }
            } catch (_: Exception) {
                synchronized(saveLock) { abortSaveLocked() }
                false
            }
        }

        @JavascriptInterface
        fun endSave(): Boolean {
            val ok = try {
                synchronized(saveLock) {
                    if (!saveActive) return false
                    flushPendingB64Locked(true)
                    saveStream?.flush()
                    saveStream?.close()
                    saveStream = null
                    val uri = saveUri
                    saveUri = null
                    saveActive = false
                    if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        contentResolver.update(uri, values, null, null)
                    }
                    uri != null
                }
            } catch (_: Exception) {
                synchronized(saveLock) { abortSaveLocked() }
                false
            }
            runOnUiThread {
                toast(if (ok) R.string.saved_to_gallery else R.string.save_failed)
            }
            return ok
        }

        @JavascriptInterface
        fun cancelSave() {
            synchronized(saveLock) { abortSaveLocked() }
        }
    }

    private fun flushPendingB64Locked(finish: Boolean) {
        val stream = saveStream ?: return
        val buf = savePendingB64
        val len = buf.length
        val usable = if (finish) len else len - (len % 4)
        if (usable <= 0) {
            if (finish) buf.setLength(0)
            return
        }
        val piece = buf.substring(0, usable)
        if (usable < len) {
            val rest = buf.substring(usable)
            buf.setLength(0)
            buf.append(rest)
        } else {
            buf.setLength(0)
        }
        val bytes = Base64.decode(piece, Base64.DEFAULT)
        stream.write(bytes)
    }

    private fun abortSaveLocked() {
        try {
            saveStream?.close()
        } catch (_: Exception) {
        }
        saveStream = null
        savePendingB64.setLength(0)
        saveActive = false
        saveUri?.let { uri ->
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
        }
        saveUri = null
    }

    private fun createGalleryUri(displayName: String, mimeType: String): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AzImg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return contentResolver.insert(collection, values)
    }

    private fun sanitizeFileName(name: String, mimeType: String): String {
        var cleaned = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (cleaned.isBlank()) {
            cleaned = "azimg_${System.currentTimeMillis()}"
        }
        if (!cleaned.contains('.')) {
            cleaned += when (mimeType) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                else -> ".jpg"
            }
        }
        return cleaned
    }

    private fun requestMediaPermissionIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_MEDIA_IMAGES
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        synchronized(saveLock) { abortSaveLocked() }
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }
}
