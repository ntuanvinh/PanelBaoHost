package com.panel.baohost

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.panel.baohost.ui.theme.PanelBaoHostTheme

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Quyền thông báo bị từ chối.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            var localWebView by remember { mutableStateOf<WebView?>(null) }

            PanelBaoHostTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { /* Title */ },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Black,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            ),
                            navigationIcon = {
                                IconButton(onClick = { 
                                    if (localWebView?.canGoBack() == true) {
                                        localWebView?.goBack()
                                    } else {
                                        finish()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                                }
                            },
                            actions = {
                                IconButton(onClick = { localWebView?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    WebViewScreen(
                        url = "https://panel.baohost.com",
                        modifier = Modifier.padding(innerPadding),
                        onWebViewReady = { readyWebView ->
                            webView = readyWebView
                            localWebView = readyWebView
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, modifier: Modifier = Modifier, onWebViewReady: (WebView) -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    // Cấu hình Custom Tabs
    val customTabsIntent = remember {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.Black.toArgb())
            .build()
        val builder = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setShowTitle(true)
            .build()
        
        // Chỉ định ưu tiên mở bằng Chrome
        builder.intent.setPackage("com.android.chrome")
        builder
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val urlString = request?.url?.toString() ?: return false
                        
                        // Link bên ngoài (không chứa tên miền chính) -> Mở trong Custom Tab
                        if (!urlString.contains("panel.baohost.com")) {
                            try {
                                customTabsIntent.launchUrl(context, Uri.parse(urlString))
                            } catch (e: Exception) {
                                // Nếu Chrome không có sẵn, xóa package để hệ thống dùng trình duyệt mặc định khác
                                customTabsIntent.intent.setPackage(null)
                                customTabsIntent.launchUrl(context, Uri.parse(urlString))
                            }
                            return true
                        }
                        return false 
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                     override fun onShowFileChooser(
                        mWebView: WebView,
                        mFilePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams
                    ): Boolean {
                        filePathCallback = mFilePathCallback
                        try {
                            fileChooserLauncher.launch(fileChooserParams.createIntent())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi mở trình chọn tệp", Toast.LENGTH_SHORT).show()
                            return false
                        }
                        return true
                    }
                }

                // Xử lý nút tải xuống bằng Custom Tab
                setDownloadListener { downloadUrl, _, _, _, _ ->
                    CookieManager.getInstance().flush() // Đảm bảo cookie được gửi đi
                    try {
                        customTabsIntent.launchUrl(context, Uri.parse(downloadUrl))
                    } catch (e: Exception) {
                        customTabsIntent.intent.setPackage(null)
                        customTabsIntent.launchUrl(context, Uri.parse(downloadUrl))
                    }
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                
                onWebViewReady(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDarkTheme)
            }
        }
    )
}
