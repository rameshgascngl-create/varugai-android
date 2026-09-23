package com.gasczoology.varugai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBytes: ByteArray? = null

    private val chooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        val uris: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            result.data?.clipData != null -> {
                val clip = result.data!!.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            else -> null
        }
        cb.onReceiveValue(uris)
    }

    private val saveAs = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val bytes = pendingBytes
        pendingBytes = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null || bytes == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("no output stream")
            toast(getString(R.string.saved_ok))
        } catch (e: Exception) {
            toast(getString(R.string.save_failed) + ": " + e.message)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val loader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = "Attendance ledger"
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val content = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    val mimes = params.acceptTypes.filter { it.isNotBlank() }
                    if (mimes.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                }
                return try {
                    chooser.launch(Intent.createChooser(content, getString(R.string.pick_file))); true
                } catch (e: Exception) {
                    fileCallback = null; callback.onReceiveValue(null); false
                }
            }
        }
        web.addJavascriptInterface(VarugaiBridge(), "VarugaiAndroid")
        web.addJavascriptInterface(VarugaiBridge(), "VarugaiNative")
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var armed = 0L
            override fun handleOnBackPressed() {
                web.evaluateJavascript(
                    "(function(){try{return window.varugaiBack?String(window.varugaiBack()):'false'}catch(e){return 'false'}})()"
                ) { handled ->
                    if (handled != null && handled.contains("true")) return@evaluateJavascript
                    if (web.canGoBack()) { web.goBack(); return@evaluateJavascript }
                    val now = System.currentTimeMillis()
                    if (now - armed < 2000) finish() else {
                        armed = now
                        toast(getString(R.string.exit_prompt))
                    }
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.menu_print)
        menu.add(0, 2, 1, R.string.menu_privacy)
        menu.add(0, 3, 2, R.string.menu_about)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> { web.evaluateJavascript("window.VarugaiAndroid && window.VarugaiAndroid.printPage()", null); return true }
            2 -> { web.loadUrl("https://appassets.androidplatform.net/assets/privacy.html"); return true }
            3 -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }
    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class VarugaiBridge {
        private val limit = 24 * 1024 * 1024
        private val allowedExt = setOf("xlsx", "csv", "json", "html", "txt", "pdf")
        @JavascriptInterface fun saveBase64(name: String, data: String, mime: String) = save(name, data, mime)
        @JavascriptInterface fun saveExcel(name: String, data: String) =
            save(name, data, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun versionCode(): Int = BuildConfig.VERSION_CODE
        @JavascriptInterface fun printPage() = runOnUiThread {
            try {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                val job = "VARUGAI attendance statement"
                pm.print(job, web.createPrintDocumentAdapter(job),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build())
            } catch (e: Exception) {
                toast("Printing is unavailable on this device")
            }
        }
        private fun save(name: String, data: String, mime: String) {
            val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
            val ext = clean.substringAfterLast('.', "").lowercase()
            if (clean.isEmpty() || ext !in allowedExt) {
                runOnUiThread { toast(getString(R.string.save_failed)) }
                return
            }
            val bytes = try { Base64.decode(data, Base64.DEFAULT) } catch (e: Exception) { null }
            if (bytes == null || bytes.size > limit) {
                runOnUiThread { toast(getString(R.string.save_failed)) }
                return
            }
            pendingBytes = bytes
            runOnUiThread {
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, clean)
                }
                try { saveAs.launch(i) } catch (e: Exception) {
                    pendingBytes = null
                    toast(getString(R.string.save_failed))
                }
            }
        }
    }
}
