package com.example.jefor

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        }

        webView = findViewById(R.id.webView)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val btnAsistencia = findViewById<ImageButton>(R.id.btnAsistencia)

        webView.webViewClient = object : WebViewClient() {
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
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popupWebView = WebView(this@MainActivity)
                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        webView.loadUrl(url)
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let { webView.loadUrl(it) }
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

        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

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

        btnAsistencia.setOnClickListener {
            webView.loadUrl(ASISTENCIA_URL)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        comprobarActualizaciones()
    }

    private fun comprobarActualizaciones() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val conn = URL(GITHUB_API_URL).openConnection() as HttpsURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "JeforApp/3.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val remoteVersion = tag.removePrefix("v")
                val apkAsset = json.getJSONArray("assets").let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                }.firstOrNull { it.getString("name").endsWith(".apk") }

                if (remoteVersion.isBlank() || apkAsset == null) return@execute

                val localVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                } catch (_: Exception) { "0" }

                if (compareVersions(remoteVersion, localVersion) > 0) {
                    val downloadUrl = apkAsset.getString("browser_download_url")
                    runOnUiThread {
                        mostrarDialogoActualizacion(remoteVersion, downloadUrl)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    private fun mostrarDialogoActualizacion(version: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Actualizacion disponible")
            .setMessage("Hay una nueva version ($version). Se descargara automaticamente.")
            .setPositiveButton("Actualizar") { _, _ -> descargarActualizacion(downloadUrl) }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun descargarActualizacion(url: String) {
        Toast.makeText(this, "Descargando actualizacion...", Toast.LENGTH_SHORT).show()

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Actualizando Jefor")
            setDescription("Descargando version nueva...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(this@MainActivity, null, "Jefor-update.apk")
        }
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id != downloadId) return
                try { ctx?.unregisterReceiver(this) } catch (_: Exception) {}

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    cursor.close()

                    if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                        try {
                            val apkFile = File(Uri.parse(localUri).path!!)
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        applicationContext,
                                        "$packageName.fileprovider",
                                        apkFile
                                    )
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                } else {
                                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                                }
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(installIntent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Error al instalar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
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
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    setDestinationUri(
                        Uri.fromFile(File(downloadsDir, fileName))
                    )
                } else {
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }
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
        private const val ASISTENCIA_URL = "https://jefor.online/asistencia"
        private const val GITHUB_API_URL = "https://api.github.com/repos/edchp/Jefor/releases/latest"
    }
}
