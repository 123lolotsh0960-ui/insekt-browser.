
package com.insekt.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var browser: WebView

    private val home = "file:///android_asset/index.html"

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        browser = WebView(this)

        browser.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            mixedContentMode =
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(browser, true)

        browser.addJavascriptInterface(
            BrowserBridge(),
            "Insekt"
        )

        browser.webChromeClient = WebChromeClient()

        browser.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return !(url.startsWith("https://") ||
                         url.startsWith("http://"))
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                injectVideoDetector()
            }
        }

        browser.setDownloadListener {
            url, userAgent, disposition, mimeType, _ ->
            downloadFile(
                url,
                userAgent,
                disposition,
                mimeType
            )
        }

        setContentView(browser)

        if (savedInstanceState == null) {
            browser.loadUrl(home)
        } else {
            browser.restoreState(savedInstanceState)
        }
    }

    private fun injectVideoDetector() {

        val script = """
            (function() {
              if (window.insektInstalled) return;
              window.insektInstalled = true;

              function addDownloadButton(video) {
                const url = video.currentSrc || video.src;

                if (!url || !/^https?:\/\//i.test(url)) return;
                if (url.startsWith('blob:')) return;
                if (/\.m3u8|\.mpd/i.test(url)) return;

                let button = document.getElementById(
                    'insekt-download-button'
                );

                if (!button) {
                  button = document.createElement('button');
                  button.id = 'insekt-download-button';
                  button.textContent = '⬇ تنزيل الفيديو';

                  button.style.cssText =
                    'position:fixed;bottom:90px;right:15px;' +
                    'z-index:2147483647;background:#16794a;' +
                    'color:white;border:0;border-radius:25px;' +
                    'padding:14px 20px;font-size:15px;' +
                    'font-weight:bold;box-shadow:0 3px 12px #0005;';

                  document.body.appendChild(button);
                }

                button.onclick = function() {
                  if (window.Insekt) {
                    window.Insekt.downloadVideo(
                      url,
                      document.title || 'video'
                    );
                  }
                };
              }

              document.addEventListener('play', function(e) {
                if (e.target && e.target.tagName === 'VIDEO') {
                  addDownloadButton(e.target);
                }
              }, true);

              document.addEventListener('pause', function(e) {
                if (e.target && e.target.tagName === 'VIDEO') {
                  const b = document.getElementById(
                    'insekt-download-button'
                  );
                  if (b) b.remove();
                }
              }, true);

              document.addEventListener('ended', function() {
                const b = document.getElementById(
                  'insekt-download-button'
                );
                if (b) b.remove();
              }, true);
            })();
        """.trimIndent()

        browser.evaluateJavascript(script, null)
    }

    private fun downloadFile(
        url: String,
        userAgent: String?,
        disposition: String?,
        mimeType: String?
    ) {
        if (!url.startsWith("http://") &&
            !url.startsWith("https://")) {
            return
        }

        try {
            val filename = URLUtil.guessFileName(
                url,
                disposition,
                mimeType
            )

            val request = DownloadManager.Request(
                Uri.parse(url)
            ).apply {
                setTitle(filename)
                setDescription("إنسِكت Browser")
                setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    filename
                )

                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }

                val cookie =
                    CookieManager.getInstance().getCookie(url)

                if (!cookie.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookie)
                }
            }

            val manager = getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

            manager.enqueue(request)

            Toast.makeText(
                this,
                "بدأ التنزيل",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "تعذر تنزيل الملف",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    inner class BrowserBridge {

        @JavascriptInterface
        fun openUrl(value: String) {
            runOnUiThread {
                val input = value.trim()
                if (input.isEmpty()) return@runOnUiThread

                val target = when {
                    input.startsWith("https://") ||
                    input.startsWith("http://") -> input

                    input.contains(" ") ||
                    !input.contains(".") ->
                        "https://www.google.com/search?q=" +
                        Uri.encode(input)

                    else -> "https://$input"
                }

                browser.loadUrl(target)
            }
        }

        @JavascriptInterface
        fun goHome() {
            runOnUiThread {
                browser.loadUrl(home)
            }
        }

        @JavascriptInterface
        fun downloadVideo(url: String, title: String) {
            runOnUiThread {
                downloadFile(
                    url,
                    browser.settings.userAgentString,
                    null,
                    "video/*"
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (browser.canGoBack()) {
            browser.goBack()
        } else {
            browser.loadUrl(home)
        }
    }

    override fun onDestroy() {
        browser.removeJavascriptInterface("Insekt")
        browser.destroy()
        super.onDestroy()
    }
}
