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
        try { Tts.init(this) } catch (_: Exception) {}
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
        checkApk()
        try { FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> Kt.prefs(this).edit().putString("fcm", t).apply(); sendToken() } } catch (_: Exception) {}
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true } }
        })
    }

    private fun startUrl(i: Intent?): String {
        val acc = i?.getStringExtra("acc")
        val go = i?.getStringExtra("go")
        val sep = if (BuildConfig.START_URL.contains("?")) "&" else "?"
        return if (acc != null) BuildConfig.START_URL + sep + "acc=" + acc else if (!go.isNullOrEmpty()) BuildConfig.START_URL + sep + "go=" + Uri.encode(go) else BuildConfig.START_URL
    }
    @Volatile private var inCall = false
    @Volatile private var apkNew = false
    private fun js(code: String) { runOnUiThread { try { web.evaluateJavascript(code, null) } catch (_: Exception) {} } }
    private fun q(x: String) = org.json.JSONObject.quote(x)
    /* v246: is a newer APK published? (GitHub release asset newer than this build) */
    private fun checkApk() {
        Thread {
            try {
                val c = java.net.URL("https://api.github.com/repos/karnaphulitower/karnaphuli-app/releases/tags/android-latest").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("Accept", "application/vnd.github+json")
                val j = org.json.JSONObject(c.inputStream.bufferedReader().readText())
                val a = j.getJSONArray("assets")
                for (k in 0 until a.length()) {
                    val o = a.getJSONObject(k)
                    if (o.optString("name").endsWith(".apk")) {
                        val fm = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        val t = fm.parse(o.optString("updated_at"))?.time ?: 0L
                        apkNew = t > BuildConfig.BUILD_TIME + 15 * 60 * 1000
                    }
                }
                if (apkNew) js("try{window.ktApkNew&&ktApkNew()}catch(e){}")
            } catch (_: Exception) {}
        }.start()
    }
    private fun mediaSave(name: String, mime: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Karnaphuli")
            }
            val u = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            contentResolver.openOutputStream(u)?.use { it.write(bytes) }; u
        } else {
            val d = java.io.File(getExternalFilesDir(null), "Download"); d.mkdirs()
            val f = java.io.File(d, name); f.writeBytes(bytes)
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        i.getStringExtra("go")?.takeIf { it.isNotEmpty() }?.let { g -> web.evaluateJavascript("try{window.ktGo&&ktGo(" + q(g) + ")}catch(e){}", null) }
        val acc = i.getStringExtra("acc") ?: return
        AlertService.stop(this)
        web.evaluateJavascript("try{if(window.ktAcc)ktAcc('" + acc.replace("'", "") + "')}catch(e){}", null)
    }

    override fun onResume() { super.onResume(); Kt.foreground = true; web.onResume(); sendToken() }
    override fun onPause() { Kt.foreground = false; if (!inCall) web.onPause(); super.onPause() }
    override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); web.saveState(o) }

    private fun sendToken() { Kt.prefs(this).getString("fcm", null)?.let { giveToken(it) } }
    fun giveToken(t: String) { web.evaluateJavascript("try{window.ktFcm&&ktFcm('" + t.replace("'", "") + "')}catch(e){}", null) }

    inner class Bridge {
        @JavascriptInterface fun fcmToken(): String = Kt.prefs(this@MainActivity).getString("fcm", "") ?: ""
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface fun setBars(top: String, bottom: String) {
            runOnUiThread {
                try {
                    val t = android.graphics.Color.parseColor(top); val n = android.graphics.Color.parseColor(bottom)
                    fun light(c: Int) = (0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)) > 150
                    window.statusBarColor = t; window.navigationBarColor = n
                    val ic = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                    ic.isAppearanceLightStatusBars = light(t); ic.isAppearanceLightNavigationBars = light(n)
                } catch (_: Exception) {}
            }
        }
        @JavascriptInterface fun iconKind(): String {
            val c = android.content.ComponentName(this@MainActivity, "$packageName.IconBld")
            return if (packageManager.getComponentEnabledSetting(c) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) "bld" else "kt"
        }
        @JavascriptInterface fun setIcon(kind: String) {
            if (kind == iconKind()) return
            val pm = packageManager
            val on = if (kind == "bld") "IconBld" else "IconKT"
            val off = if (kind == "bld") "IconKT" else "IconBld"
            pm.setComponentEnabledSetting(android.content.ComponentName(this@MainActivity, "$packageName.$on"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(android.content.ComponentName(this@MainActivity, "$packageName.$off"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        @JavascriptInterface fun stopAlarm() { AlertService.stop(this@MainActivity) }
        /* v262: SOS voice through the phone's text-to-speech */
        @JavascriptInterface fun ttsHas(lang: String): Boolean { try { Tts.init(this@MainActivity) } catch (_: Exception) {}; return Tts.has(lang) }
        @JavascriptInterface fun speak(text: String, lang: String) {
            Tts.speak(this@MainActivity, text, lang) { runOnUiThread { try { web.evaluateJavascript("window.ktSpeakDone&&ktSpeakDone()", null) } catch (_: Exception) {} } }
        }
        @JavascriptInterface fun stopSpeak() { Tts.stop() }
        /* v263: download the new APK inside the app and open Android's installer */
        @JavascriptInterface fun installApk(url: String) {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                runOnUiThread {
                    try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
                    js("window.ktApkProg&&ktApkProg(-2)")
                }
                return
            }
            Thread {
                try {
                    val d = java.io.File(cacheDir, "apk"); d.mkdirs()
                    val f = java.io.File(d, "karnaphuli.apk"); if (f.exists()) f.delete()
                    var u = java.net.URL(url); var con: java.net.HttpURLConnection
                    var hops = 0
                    while (true) {
                        con = u.openConnection() as java.net.HttpURLConnection
                        con.instanceFollowRedirects = false; con.connectTimeout = 20000; con.readTimeout = 30000
                        val code = con.responseCode
                        if (code in 300..399 && hops < 6) { u = java.net.URL(u, con.getHeaderField("Location")); con.disconnect(); hops++; continue }
                        break
                    }
                    val total = con.contentLength.toLong(); var got = 0L; var last = -1
                    con.inputStream.use { inp -> java.io.FileOutputStream(f).use { out ->
                        val buf = ByteArray(32768)
                        while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); got += n
                            if (total > 0) { val pc = (got * 100 / total).toInt(); if (pc != last) { last = pc; runOnUiThread { js("window.ktApkProg&&ktApkProg($pc)") } } } }
                    } }
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    runOnUiThread {
                        js("window.ktApkProg&&ktApkProg(100)")
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { js("window.ktApkProg&&ktApkProg(-1)") }
                    }
                } catch (e: Exception) { runOnUiThread { js("window.ktApkProg&&ktApkProg(-1)") } }
            }.start()
        }
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
        @JavascriptInterface fun batteryOk(): Boolean = try { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName) } catch (_: Exception) { true }
        /* v246: native helpers the WebView cannot do itself */
        @JavascriptInterface fun saveFile(name: String, mime: String, b64: String): Boolean {
            return try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val u = mediaSave(name, mime, bytes) ?: return false
                runOnUiThread {
                    try {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), name))
                    } catch (_: Exception) {}
                }
                true
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun shareFile(name: String, mime: String, b64: String, text: String): Boolean {
            return try {
                val d = java.io.File(cacheDir, "share"); d.mkdirs()
                val f = java.io.File(d, name); f.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                val u = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                val it = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (text.isNotEmpty()) it.putExtra(Intent.EXTRA_TEXT, text)
                runOnUiThread { startActivity(Intent.createChooser(it, name)) }; true
            } catch (_: Exception) { false }
        }
        @JavascriptInterface fun shareText(text: String) {
            runOnUiThread { try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share")) } catch (_: Exception) {} }
        }
        @JavascriptInterface fun print(job: String) {
            runOnUiThread {
                try {
                    val pm = getSystemService(PRINT_SERVICE) as android.print.PrintManager
                    pm.print(job.ifEmpty { "Karnaphuli" }, web.createPrintDocumentAdapter(job.ifEmpty { "Karnaphuli" }), android.print.PrintAttributes.Builder().build())
                } catch (_: Exception) {}
            }
        }
        @JavascriptInterface fun bioAvail(): Boolean = try {
            androidx.biometric.BiometricManager.from(this@MainActivity).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
        @JavascriptInterface fun bioAuth(id: String, title: String) {
            runOnUiThread {
                try {
                    val p = androidx.biometric.BiometricPrompt(this@MainActivity, androidx.core.content.ContextCompat.getMainExecutor(this@MainActivity), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { js("try{ktBio(" + q(id) + ",true)}catch(e){}") }
                        override fun onAuthenticationError(c: Int, m: CharSequence) { js("try{ktBio(" + q(id) + ",false)}catch(e){}") }
                    })
                    val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle(title.ifEmpty { "কর্ণফুলী টাওয়ার" })
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK).setNegativeButtonText("বাতিল").build()
                    p.authenticate(info)
                } catch (_: Exception) { js("try{ktBio(" + q(id) + ",false)}catch(e){}") }
            }
        }
        @JavascriptInterface fun callStart() { inCall = true; CallService.start(this@MainActivity) }
        @JavascriptInterface fun callEnd() { inCall = false; CallService.stop(this@MainActivity) }
        @JavascriptInterface fun apkNew(): Boolean = apkNew
        @JavascriptInterface fun hasMicCam(): Boolean = has(Manifest.permission.RECORD_AUDIO)
        @JavascriptInterface fun askBattery() {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        }
    }
}
