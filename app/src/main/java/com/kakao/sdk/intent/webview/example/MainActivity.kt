package com.kakao.sdk.intent.webview.example

import android.annotation.SuppressLint
import android.app.ActionBar
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    private lateinit var parentLayout: ViewGroup
    private lateinit var webView: WebView

    private var childWebViews = mutableListOf<WebView>() // 만들어진 popup 용 웹뷰들을 관리하기 위한 리스트.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰 바인딩
        parentLayout = findViewById(R.id.parent_layout)
        webView = findViewById(R.id.webview)

        configureWebView(webView)

        // 웹뷰 상세 구현
        webView.webViewClient = webViewClient
        webView.webChromeClient = ParentWebChromeClient(parentLayout, this)

        // load test url
        webView.loadUrl("https://developers.kakao.com/docs/js/demos/custom-login")
    }

    /**
     * 웹뷰 기본 설정 메소드
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true // 자바스크립트 실행 가능하여야 한다.
        settings.setSupportMultipleWindows(true) // 팝업을 허용하기 위해 꼭 해줘야 하는 설정. 설정하지 않으면 WebChromeClient#onCreateWindow() 호출이 되지 않음.
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.101 Mobile Safari/537.36" // user-agent 내에 wv 라는 스트링이 없어야 함.
    }

    /**
     * 팝업 처리를 해주기 위하여 필요한 WebChromeClient 상속체
     */
    private inner class ParentWebChromeClient(val parent: ViewGroup, val context: Context) : WebChromeClient() {
        /**
         * Javascript 상에서 window.open() 호출 시 호출되는 메소드. 팝업 용 웹뷰를 만들고 parent view group 에 추가해야 한다.
         * WebViewTransport 를 통하여 parent 웹뷰에 child 웹뷰를 전달해야 함.
         */
        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {

            // 팝업을 위한 웹뷰를 만든다.
            val targetWebView = WebView(context)
            targetWebView.settings.javaScriptEnabled = true
            targetWebView.layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ActionBar.LayoutParams.MATCH_PARENT
                ) // 팝업 웹뷰 layout 설정. parent 와 동일해야 함.

            // 팝업을 위한 웹뷰를
            parent.addView(targetWebView)
            childWebViews.add(targetWebView)

            // WebViewTransport 를 통하여 팝업용 웹뷰 전달.
            val transport = resultMsg!!.obj as WebView.WebViewTransport
            transport.webView = targetWebView
            resultMsg.sendToTarget()

            // 팝업용 웹뷰 설정
            targetWebView.webViewClient = webViewClient // 부모 웹뷰와 같은 WebViewClient 를 사용. (URL 처리는 동일하기 때문)
            // window.close() 가 호출될 때 parent view group 에서 삭제하고 childWebView 리스트에서 삭제하여야 함.
            targetWebView.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    super.onCloseWindow(window)
                    parent.removeView(targetWebView)
                    childWebViews.remove(targetWebView)
                }
            }
            return true
        }
    }

    /**
     * 웹뷰에 호출되는 url 들을 처리하기 위한 WebViewClientCompat anonymous object.
     */
    private val webViewClient = object : WebViewClientCompat() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (arrayOf("http", "https").contains(request.url.scheme)) return false // http, https 주소는 웹뷰가 직접 load 한다.
            val intent = Intent.parseUri(
                request.url.toString(),
                Intent.URI_INTENT_SCHEME
            ) // javascript uri intent 를 parsing 한다. 반드시 해당 flag 를 줘야 함.
            // 실행 가능한 앱이 있으면 실행한다.
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return true
            }
            // 실행 가능한 앱이 없으면 인텐트에 fallback  url 값이 있는지 체크한다.
            if (!resortToFallbackUrl(view, intent)) {
                Toast.makeText(applicationContext, "URI Intent 에 해당하는 애플리케이션을 찾을 수 없습니다.", Toast.LENGTH_LONG)
                    .show() // fallback url 이 없는 경우, market 으로 보내지 않고 유저에게 실행불가능 설명.
            }
            return true
        }
    }

    /**
     * 브라우저에서 들어온 intent 에서 browser_fallback_url 을 찾아서 실행시키는 메소드
     * fallback url validation 은 따로하지 않는데, 필요하면 해야함.
     *
     * @return true if webView loaded fallback url, false otherwise
     */
    private fun resortToFallbackUrl(webView: WebView, intent: Intent): Boolean {
        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (fallbackUrl != null) {
            webView.loadUrl(fallbackUrl)
            return true
        }
        return false
    }

    /**
     * 안드로이드 백버튼 핸들러 구현
     */
    override fun onBackPressed() {
        // 팝업용 웹뷰가 있을 시 닫아주는 코드 구현
        childWebViews.firstOrNull {
            if (it.canGoBack()) {
                it.goBack()
                return
            }
            parentLayout.removeView(it)
            childWebViews.remove(it)
            return
        }
        // 부모 웹뷰의 백버튼 구현
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        // 액티비티 종료
        super.onBackPressed()
    }
}
