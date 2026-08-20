package com.example.jefor

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
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
            mostrarDialogoAsistencia()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    private fun mostrarDialogoAsistencia() {
        val prefs = getSharedPreferences("jefor_prefs", Context.MODE_PRIVATE)
        val dniGuardado = prefs.getString("dni", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 24)
        }

        val titulo = TextView(this).apply {
            text = "Registro de Asistencia"
            textSize = 20f
            typeface = null
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titulo)

        val etDni = EditText(this).apply {
            hint = "DNI"
            setText(dniGuardado)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(32, 24, 32, 24)
        }
        layout.addView(etDni)

        val btnEntrada = android.widget.Button(this).apply {
            text = "ENTRADA"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 24, 0, 0) }
            layoutParams = params
        }
        layout.addView(btnEntrada)

        val btnSalida = android.widget.Button(this).apply {
            text = "SALIDA"
            setBackgroundColor(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
            layoutParams = params
        }
        layout.addView(btnSalida)

        val progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(0, 24, 0, 0)
            }
            layoutParams = params
        }
        layout.addView(progressBar)

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .create()

        btnEntrada.setOnClickListener {
            val dni = etDni.text.toString().trim()
            if (dni.isEmpty()) {
                Toast.makeText(this, "Introduce tu DNI", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("dni", dni).apply()
            progressBar.visibility = View.VISIBLE
            btnEntrada.isEnabled = false
            btnSalida.isEnabled = false
            registrarAsistencia(dni, "entrada", dialog, progressBar, btnEntrada, btnSalida)
        }

        btnSalida.setOnClickListener {
            val dni = etDni.text.toString().trim()
            if (dni.isEmpty()) {
                Toast.makeText(this, "Introduce tu DNI", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("dni", dni).apply()
            progressBar.visibility = View.VISIBLE
            btnEntrada.isEnabled = false
            btnSalida.isEnabled = false
            registrarAsistencia(dni, "salida", dialog, progressBar, btnEntrada, btnSalida)
        }

        dialog.show()
    }

    private fun registrarAsistencia(
        dni: String,
        accion: String,
        dialog: AlertDialog,
        progressBar: ProgressBar,
        btnEntrada: android.widget.Button,
        btnSalida: android.widget.Button
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permiso de ubicacion no concedido", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            btnEntrada.isEnabled = true
            btnSalida.isEnabled = true
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val lat = location?.latitude ?: 0.0
            val lon = location?.longitude ?: 0.0
            val ip = getDeviceIpAddress()

            Executors.newSingleThreadExecutor().execute {
                try {
                    val params = mapOf(
                        "dni" to dni,
                        "accion" to accion,
                        "ip" to ip,
                        "latitud" to lat.toString(),
                        "longitud" to lon.toString()
                    )
                    val respuesta = httpPost(ASISTENCIA_URL, params)

                    runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(this, respuesta, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        btnEntrada.isEnabled = true
                        btnSalida.isEnabled = true
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "No se pudo obtener la ubicacion", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            btnEntrada.isEnabled = true
            btnSalida.isEnabled = true
        }
    }

    private fun httpPost(url: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = URL(url).openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "JeforApp/3.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.bufferedWriter().use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: "Sin respuesta"
        } finally {
            conn.disconnect()
        }
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (_: Exception) {}
        return "0.0.0.0"
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
        private const val ASISTENCIA_URL = "https://jefor.online/asistencia/guardar_asistencia.php"
    }
}
