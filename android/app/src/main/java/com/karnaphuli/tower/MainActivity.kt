package com.karnaphuli.tower

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {
    companion object {
        private var inst: java.lang.ref.WeakReference<MainActivity>? = null
        fun pushToken(t: String) { inst?.get()?.let { a -> a.runOnUiThread { a.giveToken(t) } } }
    }
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendPerm: PermissionRequest? = null
    private var geoCb: Pair<String, GeolocationPermissions.Callback>? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)); fileCb = null
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        pendPerm?.let { p ->
            val ok = mutableListOf<String>()
            p.resources.forEach { r ->
                if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && has(Manifest.permission.CAMERA)) ok.add(r)
                if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && has(Manifest.permission.RECORD_AUDIO)) ok.add(r)
            }
            if (ok.isEmpty()) p.deny() else p.grant(ok.toTypedArray())
        }
        pendPerm = null
        geoCb?.let { (o, cb) -> cb.invoke(o, has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION), false) }
        geoCb = null
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = java.lang.ref.WeakReference(this)
        Kt.channels(this)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false; allowFileAccess = false
            setGeolocationEnabled(true)
            userAgentString = "$userAgentString KTAndroid/1"
        }
        web.addJavascriptInterface(Bridge(), "KTAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                val home = Uri.parse(BuildConfig.START_URL)
                if ((u.scheme == "https" || u.scheme == "http") && u.host == home.host) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                return true
            }
            override fun onPageFinished(v: WebView, url: String) { sendToken() }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) {
                runOnUiThread {
                    val need = mutableListOf<String>()
                    if (r.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA)
                    if (r.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
                    if (need.isEmpty()) r.grant(r.resources) else { pendPerm = r; permLauncher.launch(need.toTypedArray()) }
                }
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
                if (has(Manifest.permission.ACCESS_FINE_LOCATION)) cb.invoke(origin, true, false)
                else { geoCb = origin to cb; permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
            }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                return try { fileLauncher.launch(p.createIntent()); true } catch (_: Exception) { fileCb = null; false }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS))
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        if (b != null) web.restoreState(b) else web.loadUrl(startUrl(intent))
        try { FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> Kt.prefs(this).edit().putString("fcm", t).apply(); sendToken() } } catch (_: Exception) {}
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true } }
        })
    }

    private fun startUrl(i: Intent?): String {
        val acc = i?.getStringExtra("acc")
        return if (acc != null) BuildConfig.START_URL + (if (BuildConfig.START_URL.contains("?")) "&" else "?") + "acc=" + acc else BuildConfig.START_URL
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        val acc = i.getStringExtra("acc") ?: return
        AlertService.stop(this)
        web.evaluateJavascript("try{if(window.ktAcc)ktAcc('" + acc.replace("'", "") + "')}catch(e){}", null)
    }

    override fun onResume() { super.onResume(); Kt.foreground = true; web.onResume(); sendToken() }
    override fun onPause() { Kt.foreground = false; web.onPause(); super.onPause() }
    override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); web.saveState(o) }

    private fun sendToken() { Kt.prefs(this).getString("fcm", null)?.let { giveToken(it) } }
    fun giveToken(t: String) { web.evaluateJavascript("try{window.ktFcm&&ktFcm('" + t.replace("'", "") + "')}catch(e){}", null) }

    inner class Bridge {
        @JavascriptInterface fun fcmToken(): String = Kt.prefs(this@MainActivity).getString("fcm", "") ?: ""
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun stopAlarm() { AlertService.stop(this@MainActivity) }
        @JavascriptInterface fun canFullScreen(): Boolean =
            if (Build.VERSION.SDK_INT >= 34) getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true
        @JavascriptInterface fun askFullScreen() {
            if (Build.VERSION.SDK_INT >= 34) try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
        @JavascriptInterface fun openNotifSettings() {
            try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) } catch (_: Exception) {}
        }
        @JavascriptInterface fun askBattery() {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        }
    }
}
