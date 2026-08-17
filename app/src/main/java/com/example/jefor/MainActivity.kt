package com.example.jefor

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null

        if (callback == null) {
            return@registerForActivityResult
        }

        val uris = when {
            result.resultCode != RESULT_OK -> null
            result.data?.clipData != null -> {
                val clipData = result.data?.clipData
                Array(clipData?.itemCount ?: 0) { index ->
                    clipData!!.getItemAt(index).uri
                }
            }
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            else -> null
        }

        callback.onReceiveValue(uris)
    }

    private fun getOrCreateDeviceToken(): String {
        val prefs = getSharedPreferences("jefor_prefs", Context.MODE_PRIVATE)
        var token = prefs.getString("device_token", null)
        if (token.isNullOrBlank() || !Regex("^[a-f0-9]{32,128}$").matches(token)) {
            token = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("device_token", token).apply()
        }
        return token
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }

        webView = findViewById(R.id.webView)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnHome = findViewById<ImageButton>(R.id.btnHome)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectDeviceToken(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrl(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url?.let { handleUrl(it) } ?: false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = createSafeFileChooserIntent(fileChooserParams)

                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "No hay una aplicacion para seleccionar archivos",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popupWebView = WebView(this@MainActivity)
                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.toString()?.let { handlePopupUrl(it) }
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let { handlePopupUrl(it) }
                        return true
                    }
                }
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }

        webView.loadUrl(HOME_URL)

        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnHome.setOnClickListener {
            webView.loadUrl(HOME_URL)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    private fun injectDeviceToken(view: WebView?) {
        val token = getOrCreateDeviceToken()
        val js = "(function(){" +
            "try{" +
            "localStorage.setItem('asistencia_device_token','${token}');" +
            "document.cookie='asistencia_device_token=${token}; max-age=31536000; path=/asistencia; SameSite=Lax';" +
            "}catch(e){}" +
            "})();"
        view?.evaluateJavascript(js, null)
    }

    private fun createSafeFileChooserIntent(
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Intent {
        val supportedMimes = listOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/*"
        )

        return try {
            val originalIntent = fileChooserParams?.createIntent()
            if (originalIntent != null) {
                val originalTypes = fileChooserParams.acceptTypes
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                val hasUnsupported = originalTypes.any { type ->
                    supportedMimes.none { s -> type.equals(s, ignoreCase = true) || s.endsWith("/*") }
                }

                if (hasUnsupported || originalTypes.isEmpty()) {
                    createOpenDocumentIntent(fileChooserParams)
                } else {
                    originalIntent
                }
            } else {
                createOpenDocumentIntent(fileChooserParams)
            }
        } catch (_: Exception) {
            createOpenDocumentIntent(fileChooserParams)
        }
    }

    private fun createOpenDocumentIntent(fileChooserParams: WebChromeClient.FileChooserParams?): Intent {
        val acceptTypes = fileChooserParams?.acceptTypes
            ?.filter { it.isNotBlank() }
            ?.toTypedArray()
            .orEmpty()

        val filteredTypes = acceptTypes.filter { type ->
            type.equals("application/pdf", ignoreCase = true) ||
                type.equals("image/jpeg", ignoreCase = true) ||
                type.equals("image/png", ignoreCase = true) ||
                type.startsWith("image/", ignoreCase = true)
        }.toTypedArray()

        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (filteredTypes.size == 1) filteredTypes[0] else "*/*"
            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            )
            if (filteredTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, filteredTypes)
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()

        if (scheme == "http" || scheme == "https") {
            if (isMoodleFileOrPdf(url)) {
                enqueueDownload(url, webView.settings.userAgentString, null, guessMimeType(url))
                return true
            }
            return false
        }

        openExternalUrl(url)
        return true
    }

    private fun handlePopupUrl(url: String) {
        if (isMoodleFileOrPdf(url)) {
            enqueueDownload(url, webView.settings.userAgentString, null, guessMimeType(url))
        } else {
            openExternalUrl(url)
        }
    }

    private fun isMoodleFileOrPdf(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains(".pdf") ||
            lowerUrl.contains("pluginfile.php") ||
            lowerUrl.contains("forcedownload=1")
    }

    private fun guessMimeType(url: String): String {
        return if (url.lowercase().contains(".pdf")) "application/pdf" else "application/octet-stream"
    }

    private fun openExternalUrl(url: String) {
        try {
            if (url.startsWith("intent://", ignoreCase = true)) {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                    } else {
                        throw ActivityNotFoundException()
                    }
                }
                return
            }

            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No se puede abrir este enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType ?: "application/octet-stream")
                addRequestHeader("User-Agent", userAgent ?: "")
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
            }
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            openExternalUrl(url)
        }
    }

    companion object {
        private const val HOME_URL = "https://jefor.online/enlaces.php"
    }
}
