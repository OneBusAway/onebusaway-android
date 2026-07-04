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
package org.onebusaway.android.app.di

import org.onebusaway.android.api.net.ApiParamsInterceptor
import org.onebusaway.android.api.net.ObaEndpointResolver

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.api.contract.BikeWebService
import org.onebusaway.android.api.contract.OtpWebService
import org.onebusaway.android.api.contract.RegionsWebService
import org.onebusaway.android.api.contract.ReminderWebService
import org.onebusaway.android.api.contract.SurveyWebService
import org.onebusaway.android.api.contract.WeatherWebService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

/**
 * Wires the modernized Retrofit-based OBA REST client. The OBA host is region-dependent, so
 * `ObaApiProvider` builds the web service against the live region base URL (rebuilding on region
 * change) and `ApiParamsInterceptor` appends the shared key/version/app params. The sidecar services
 * below target fixed / sidecar hosts via absolute `@Url`, so they use a plain client.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * The shared client for the OBA "where" API. [ApiParamsInterceptor] appends the key/version/app
     * params to every request; the request's host is set per-region by the Retrofit that
     * [ObaApiProvider] builds against the live base URL (so there's no throwaway base / host rewrite).
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(resolver: ObaEndpointResolver): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiParamsInterceptor(resolver))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                    )
                }
            }
            .build()

    /**
     * The regions-directory client. Built with a plain client (NO [ApiParamsInterceptor]) since regions
     * is fetched from a fixed directory host via `@Url`, not the selected region's OBA host.
     */
    @Provides
    @Singleton
    fun provideRegionsWebService(json: Json): RegionsWebService =
        plainRetrofit(json).create(RegionsWebService::class.java)

    /**
     * The surveys client. Like regions, it targets a non-OBA host (the region's sidecar) via `@Url`,
     * so it uses a plain client without [ApiParamsInterceptor].
     */
    @Provides
    @Singleton
    fun provideSurveyWebService(json: Json): SurveyWebService =
        plainRetrofit(json).create(SurveyWebService::class.java)

    /**
     * The bike-rental client. Targets an OpenTripPlanner host (the region's `otpBaseUrl`) via `@Url`,
     * so like regions/surveys it uses a plain client without [ApiParamsInterceptor].
     */
    @Provides
    @Singleton
    fun provideBikeWebService(json: Json): BikeWebService =
        plainRetrofit(json).create(BikeWebService::class.java)

    /**
     * The weather client. Targets the region's sidecar host via `@Url` (like surveys), so it uses a
     * plain client without [ApiParamsInterceptor].
     */
    @Provides
    @Singleton
    fun provideWeatherWebService(json: Json): WeatherWebService =
        plainRetrofit(json).create(WeatherWebService::class.java)

    /**
     * The arrivals-reminders client. Targets the region's sidecar host via `@Url`, so like surveys
     * it uses a plain client without [ApiParamsInterceptor].
     */
    @Provides
    @Singleton
    fun provideReminderWebService(json: Json): ReminderWebService =
        plainRetrofit(json).create(ReminderWebService::class.java)

    /**
     * The OpenTripPlanner trip-planner client. Like [provideBikeWebService] it targets the region's OTP
     * host via absolute `@Url`, so it uses a plain client without [ApiParamsInterceptor] — but with the
     * legacy 15s connect/read timeouts the OTP `/plan` call has always used (OTP servers can be slow),
     * rather than OkHttp's shorter defaults.
     */
    @Provides
    @Singleton
    fun provideOtpWebService(json: Json): OtpWebService {
        val client = plainClient {
            connectTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return plainRetrofit(json, client).create(OtpWebService::class.java)
    }

    /** A plain OkHttp client (debug logging only, no [ApiParamsInterceptor]), optionally [configure]d. */
    private fun plainClient(configure: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                    )
                }
            }
            .apply(configure)
            .build()

    /**
     * A Retrofit built on a plain [client] (defaults to [plainClient]) for services that pass an
     * absolute `@Url` per call rather than relying on the region host rewrite.
     */
    private fun plainRetrofit(json: Json, client: OkHttpClient = plainClient()): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private const val OTP_TIMEOUT_SECONDS = 15L
}
