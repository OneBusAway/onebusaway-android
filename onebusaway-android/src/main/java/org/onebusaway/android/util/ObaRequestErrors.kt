/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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

package org.onebusaway.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings

import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaApi

/**
 * Maps OBA REST API response codes and device connectivity state to user-friendly error messages.
 */
object ObaRequestErrors {

    @JvmStatic
    fun getRouteErrorString(context: Context, code: Int): String {
        if (!isConnected(context)) {
            return if (isAirplaneMode(context)) {
                context.getString(R.string.airplane_mode_error)
            } else {
                context.getString(R.string.no_network_error)
            }
        }
        return when (code) {
            ObaApi.OBA_INTERNAL_ERROR ->
                context.getString(R.string.internal_error)
            ObaApi.OBA_NOT_FOUND -> {
                val r = Application.get().currentRegion
                if (r != null) {
                    context.getString(R.string.route_not_found_error_with_region_name, r.name)
                } else {
                    context.getString(R.string.route_not_found_error_no_region)
                }
            }
            ObaApi.OBA_BAD_GATEWAY ->
                context.getString(R.string.bad_gateway_error, context.getString(R.string.app_name))
            ObaApi.OBA_OUT_OF_MEMORY ->
                context.getString(R.string.out_of_memory_error)
            else ->
                context.getString(R.string.generic_comm_error)
        }
    }

    @JvmStatic
    fun getStopErrorString(context: Context, code: Int): String {
        if (!isConnected(context)) {
            return if (isAirplaneMode(context)) {
                context.getString(R.string.airplane_mode_error)
            } else {
                context.getString(R.string.no_network_error)
            }
        }
        return when (code) {
            ObaApi.OBA_INTERNAL_ERROR ->
                context.getString(R.string.internal_error)
            ObaApi.OBA_NOT_FOUND -> {
                val r = Application.get().currentRegion
                if (r != null) {
                    context.getString(R.string.stop_not_found_error_with_region_name, r.name)
                } else {
                    context.getString(R.string.stop_not_found_error_no_region)
                }
            }
            ObaApi.OBA_BAD_GATEWAY ->
                context.getString(R.string.bad_gateway_error, context.getString(R.string.app_name))
            ObaApi.OBA_OUT_OF_MEMORY ->
                context.getString(R.string.out_of_memory_error)
            else ->
                context.getString(R.string.generic_comm_error)
        }
    }

    /**
     * Returns a user-friendly error message based on device state (whether a network connection is
     * available or airplane mode is on) or an OBA REST API response code.
     *
     * @param code The status code (one of the ObaApi.OBA_* constants)
     */
    @JvmStatic
    fun getMapErrorString(context: Context, code: Int): String {
        if (!isConnected(context)) {
            return if (isAirplaneMode(context)) {
                context.getString(R.string.airplane_mode_error)
            } else {
                context.getString(R.string.no_network_error)
            }
        }
        return when (code) {
            ObaApi.OBA_INTERNAL_ERROR ->
                context.getString(R.string.internal_error)
            ObaApi.OBA_BAD_GATEWAY ->
                context.getString(R.string.bad_gateway_error)
            ObaApi.OBA_OUT_OF_MEMORY ->
                context.getString(R.string.out_of_memory_error)
            else ->
                context.getString(R.string.map_generic_error)
        }
    }

    /**
     * Returns true if the device is in Airplane Mode, and false if the device isn't in Airplane
     * mode or if it can't be determined
     * @param context
     * @return true if the device is in Airplane Mode, and false if the device isn't in Airplane
     * mode or if it can't be determined
     */
    private fun isAirplaneMode(context: Context?): Boolean {
        if (context == null) {
            // If the context is null, we can't get airplane mode state - assume no
            return false
        }
        val cr = context.contentResolver
        return Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON, 0) != 0
    }

    /**
     * Returns true if the device is connected to a network, and false if the device isn't or if it
     * can't be determined
     * @param context
     * @return true if the device is connected to a network, and false if the device isn't or if it
     * can't be determined
     */
    private fun isConnected(context: Context?): Boolean {
        if (context == null) {
            // If the context is null, we can't get connected state - assume yes
            return true
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }
}
