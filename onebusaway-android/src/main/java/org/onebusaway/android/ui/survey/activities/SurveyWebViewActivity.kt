package org.onebusaway.android.ui.survey.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.ui.survey.SurveyPreferences
import org.onebusaway.android.ui.survey.utils.SurveyUtils
import org.onebusaway.android.util.LocationUtils

/**
 Activity that hosts a WebView for launching and displaying external surveys
 */
class SurveyWebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var mGoogleApiClient: GoogleApiClient
    private var mStopID: String? = null
    private var mRouteIDList: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survey_web_view)
        initLocationService()
        initWebView()
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        val url = intent.getStringExtra("url") as String
        val embeddedValuesList = intent.getStringArrayListExtra("embedded_data") as? ArrayList<String> ?: arrayListOf()
        mStopID = intent.getStringExtra("stop_id")
        mRouteIDList  = intent.getStringArrayListExtra("route_ids") as? ArrayList<String> ?: arrayListOf()
        Log.d("Routes",mRouteIDList.toString())
        val newURl = getEmbeddedLink(url, embeddedValuesList)

        Log.d("ExternalSurveyData", embeddedValuesList.toString())
        Log.d("ExternalSurveyURL", newURl)

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                supportActionBar?.title = title
            }
        }

        webView.loadUrl(url)
    }

    override fun onStop() {
        super.onStop()
        mGoogleApiClient.disconnect();
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Constructs an embedded URL by appending key-value pairs from `embeddedValueList` to the base `url`.
     *
     * For example, given:
     * - `url` = "https://example.com?"
     * - `embeddedValueList` = ["user_id=1"]
     *
     * The function will produce: "https://example.com?user_id=1getEmbeddedDataValue(user_id=1)"
     *
     * @param url The base URL to which data will be appended.
     * @param embeddedValueList A list of strings representing key-value pairs to be added to the URL.
     * @return A new URL string with the appended key-value pairs and their corresponding embedded data values.
     */
    private fun getEmbeddedLink(url: String, embeddedValueList: ArrayList<String>): String {
        var newUrl = "$url?"
        val size = embeddedValueList.size
        for (index in 0..<size) {
            newUrl += embeddedValueList[index]
            newUrl += "="
            newUrl += getEmbeddedDataValue(embeddedValueList[index])
            if (index + 1 != size) newUrl += "&"
        }
        return newUrl
    }

    /**
     * Retrieves the value for the specified embedded data key.
     *
     * Valid keys:
     * - USER_ID: User UUID.
     * - REGION_ID: Current region ID.
     * - ROUTE_ID: Current route ID, or "NA" if unavailable.
     * - STOP_ID: Current stop ID, or "NA" if unavailable.
     * - CURRENT_LOCATION: Latitude,Longitude of current location.
     *
     * @param key The embedded data key.
     * @return The corresponding value, or an empty string if the key is invalid.
     */
    private fun getEmbeddedDataValue(key : String): String {
        return when (key) {
            SurveyUtils.USER_ID -> SurveyPreferences.getUserUUID(this@SurveyWebViewActivity)
            SurveyUtils.REGION_ID -> Application.get().currentRegion.id.toString()
            SurveyUtils.ROUTE_ID -> getRouteID()
            SurveyUtils.STOP_ID -> getStopID()
            SurveyUtils.CURRENT_LOCATION -> getCurrentLocation()
            else -> ""
        }
    }

    private fun initLocationService() {
        val api = GoogleApiAvailability.getInstance()
        if (api.isGooglePlayServicesAvailable(this@SurveyWebViewActivity) == ConnectionResult.SUCCESS) {
            mGoogleApiClient =
                LocationUtils.getGoogleApiClientWithCallbacks(this@SurveyWebViewActivity)
            mGoogleApiClient.connect()
        }
    }

    private fun getCurrentLocation(): String {
        val location =
            Application.getLastKnownLocation(this@SurveyWebViewActivity, mGoogleApiClient)
        return if (location != null) {
            "${location.latitude},${location.longitude}"
        } else {
            "NA"
        }
    }

    private fun getStopID(): String {
        return mStopID ?: "NA"
    }

    /**
     * Retrieves a comma-separated string of route IDs from the list.
     *
     * If `mRouteIDList` is not null and contains elements, it converts the list
     * to a comma-separated.
     *
     * @return A comma-separated string of route IDs or "NA" if the list is null or empty.
     */
    private fun getRouteID(): String {
        return mRouteIDList?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ",")
            ?: "NA"
    }


}