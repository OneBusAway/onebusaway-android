/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.survey

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.onebusaway.android.ui.compose.components.ObaTopAppBar

/**
 * The external-survey WebView destination. Hosts a [WebView] (the same settings as the former
 * Activity) via [AndroidView], with a determinate progress bar that fades out at 100% and a top app
 * bar whose title tracks the page title. JavaScript is enabled (surveys require it).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SurveyWebViewScreen(url: String, onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { ObaTopAppBar(title, onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, t: String?) {
                                    super.onReceivedTitle(view, t)
                                    title = t.orEmpty()
                                }
                            }
                            loadUrl(url)
                        }
                    }
                )
                // The former Activity showed a centered indeterminate spinner (LoadingProgress style is
                // an indeterminate AppCompat ProgressBar) until the page hit 100%.
                if (progress < 100) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
