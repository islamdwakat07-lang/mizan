package com.islam.mizan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null

    private val healthPermLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            pushSteps()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cb = fileCallback ?: return@registerForActivityResult
            fileCallback = null
            if (res.resultCode != RESULT_OK) {
                cb.onReceiveValue(null); return@registerForActivityResult
            }
            val picked = res.data?.data ?: res.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            val uri = picked ?: cameraUri?.takeIf { (cameraFile?.length() ?: 0L) > 0L }
            cb.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "appassets.androidplatform.net") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, u)); true } catch (e: Exception) { true }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    val dir = File(cacheDir, "camera").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val f = File(dir, "meal_${System.currentTimeMillis()}.jpg")
                    cameraFile = f
                    cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        .putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val gallery = Intent(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*")
                    val chooser = Intent.createChooser(gallery, "صورة الأكلة")
                        .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                    fileLauncher.launch(chooser)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onBack?window.onBack():false)") { r ->
                    if (r != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }

        askNotificationPermission()
        scheduleReminders(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        pushSteps()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    /** يقرأ خطوات اليوم ويرسلها للصفحة */
    private fun pushSteps() {
        lifecycleScope.launch {
            try {
                val st = Health.status(this@MainActivity)
                if (st != "ok") {
                    js("window.onHealthStatus&&onHealthStatus('$st')"); return@launch
                }
                if (!Health.granted(this@MainActivity)) {
                    js("window.onHealthStatus&&onHealthStatus('no_permission')"); return@launch
                }
                val n = Health.todaySteps(this@MainActivity)
                js("window.onHealthStatus&&onHealthStatus('ok');window.onNativeSteps&&onNativeSteps($n)")
            } catch (e: Exception) {
                js("window.onHealthStatus&&onHealthStatus('no_permission')")
            }
        }
    }

    private fun connectHealth() {
        when (Health.status(this)) {
            "ok" -> try { healthPermLauncher.launch(Health.REQUEST) } catch (e: Exception) {
                healthPermLauncher.launch(setOf(Health.STEPS))
            }
            "update_required" -> {
                val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                }
            }
            else -> js("window.onHealthStatus&&onHealthStatus('unavailable')")
        }
    }

    /** الجسر بين الصفحة والجوال */
    inner class Bridge {
        @JavascriptInterface
        fun saveSummary(json: String) {
            getSharedPreferences("mizan", Context.MODE_PRIVATE).edit().putString("summary", json).apply()
        }

        @JavascriptInterface
        fun requestSteps() = runOnUiThread { pushSteps() }

        @JavascriptInterface
        fun connectHealth() = runOnUiThread { this@MainActivity.connectHealth() }

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        fun scheduleReminders(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("mizan-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
