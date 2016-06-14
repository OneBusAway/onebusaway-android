/*
 * Copyright 2011 Marcy Gordon
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.onebusaway.android.directions.tasks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;


import org.opentripplanner.api.model.error.PlannerError;
import org.opentripplanner.api.ws.Message;
import org.opentripplanner.api.ws.Request;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.ws.Response;
import org.opentripplanner.routing.core.TraverseMode;

import org.onebusaway.android.R;
import org.onebusaway.android.directions.util.JacksonConfig;

/**
 * AsyncTask that invokes a trip planning request to the OTP Server
 *
 * @author Khoa Tran
 * @author Sean Barbeau (conversion to Jackson)
 * @author Simon Jacobs (integration for onebusaway-android)
 */

public class TripRequest extends AsyncTask<Request, Integer, Long> {


    public interface Callback {
        void onTripRequestComplete(List<Itinerary> itineraries);
    }

    // Constants that are defined in OTPApp in CUTR OTP Android app
    private static final String TAG = "TripRequest";
    private static final String FOLDER_STRUCTURE_PREFIX_NEW = "/routers/default";
    public static final String OTP_RENTAL_QUALIFIER = "_RENT";
    public static final String PLAN_LOCATION = "/plan";
    public static final int HTTP_CONNECTION_TIMEOUT = 15000;
    public static final int HTTP_SOCKET_TIMEOUT = 15000;

    private Response response;

    private ProgressDialog progressDialog;

    private WeakReference<Activity> activity;

    private Context context;

    private Resources resources;

    private String currentRequestString = "";

    private String baseURL;

    private Callback callback;

    // change Server object to baseUrl string.
    public TripRequest(WeakReference<Activity> activity, Context context, Resources resources,
                       String baseURL, Callback callback) {
        this.activity = activity;
        this.context = context;
        this.baseURL = baseURL;
        this.callback = callback;
        this.resources = resources;
        if (activity != null) {
            Activity activityRetrieved = activity.get();
            if (activityRetrieved != null)
                progressDialog = new ProgressDialog(activityRetrieved);
        }
    }

    protected void onPreExecute() {
        if (activity.get() != null) {
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(true);
            Activity activityRetrieved = activity.get();
            if (activityRetrieved != null) {
                progressDialog = ProgressDialog.show(activityRetrieved, "",
                        resources.getText(R.string.task_progress_tripplanner_progress), true);
            }
        }
    }

    protected Long doInBackground(Request... reqs) {
        long totalSize = 0;
        if (baseURL == null) {
            if (activity.get() != null) {
                activity.get().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Toast.makeText(activity.get(),
                                resources.getString(R.string.toast_no_server_selected_error),
                                Toast.LENGTH_SHORT).show();
                    }
                });

            }
            return null;
        } else {
            String prefix = FOLDER_STRUCTURE_PREFIX_NEW;
            for (Request req : reqs) {
                response = requestPlan(req, prefix, baseURL);
            }
        }
        return totalSize;
    }

    protected void onCancelled(Long result) {

        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in TripRequest Cancelled dismissing dialog: " + e);
        }

        Activity activityRetrieved = activity.get();
        if (activityRetrieved != null) {
            AlertDialog.Builder geocoderAlert = new AlertDialog.Builder(activityRetrieved);
            geocoderAlert.setTitle(R.string.tripplanner_results_title)
                    .setMessage(R.string.tripplanner_error_request_timeout)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });

            AlertDialog alert = geocoderAlert.create();
            alert.show();
        }

        Log.e(TAG, "No route to display!");
    }

    protected void onPostExecute(Long result) {

        if (result == null)
            return;

        if (activity.get() != null) {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in TripRequest PostExecute dismissing dialog: " + e);
            }
        }

        if (response != null && response.getPlan() != null
                && response.getPlan().getItinerary().get(0) != null) {

            callback.onTripRequestComplete(response.getPlan().getItinerary());
        } else {
            Log.e(TAG, "Response: " + response);
            Activity activityRetrieved = activity.get();
            if (activityRetrieved != null) {
                AlertDialog.Builder feedback = new AlertDialog.Builder(activityRetrieved);
                feedback.setTitle(resources
                        .getString(R.string.tripplanner_error_dialog_title));
                feedback.setNeutralButton(resources.getString(android.R.string.ok),
                        null);
                String msg = resources
                        .getString(R.string.tripplanner_error_not_defined);


                if (response != null && response.getError() != null) {
                    PlannerError error = response.getError();
                    int errorCode = error.getId();

                    if (response != null && response.getError() != null
                            && errorCode != Message.PLAN_OK
                            .getId()) {

                        msg = getErrorMessage(response.getError().getId());
                        if (msg == null) {
                            msg = response.getError().getMsg();
                        }
                    }
                }
                feedback.setMessage(msg);
                feedback.create().show();
            }

            Log.e(TAG, "No route to display!");
        }
    }

    protected String getErrorMessage(int errorCode) {
        if (errorCode == Message.SYSTEM_ERROR.getId()) {
            return (resources.getString(R.string.tripplanner_error_system));
        } else if (errorCode == Message.OUTSIDE_BOUNDS.getId()) {
            return (resources.getString(R.string.tripplanner_error_outside_bounds));
        } else if (errorCode == Message.PATH_NOT_FOUND.getId()) {
            return (resources.getString(R.string.tripplanner_error_path_not_found));
        } else if (errorCode == Message.NO_TRANSIT_TIMES.getId()) {
            return (resources.getString(R.string.tripplanner_error_no_transit_times));
        } else if (errorCode == Message.REQUEST_TIMEOUT.getId()) {
            return (resources.getString(R.string.tripplanner_error_request_timeout));
        } else if (errorCode == Message.BOGUS_PARAMETER.getId()) {
            return (resources.getString(R.string.tripplanner_error_bogus_parameter));
        } else if (errorCode == Message.GEOCODE_FROM_NOT_FOUND.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_from_not_found));
        } else if (errorCode == Message.GEOCODE_TO_NOT_FOUND.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_to_not_found));
        } else if (errorCode == Message.GEOCODE_FROM_TO_NOT_FOUND.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_from_to_not_found));
        } else if (errorCode == Message.TOO_CLOSE.getId()) {
            return (resources.getString(R.string.tripplanner_error_too_close));
        } else if (errorCode == Message.LOCATION_NOT_ACCESSIBLE.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_location_not_accessible));
        } else if (errorCode == Message.GEOCODE_FROM_AMBIGUOUS.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_from_ambiguous));
        } else if (errorCode == Message.GEOCODE_TO_AMBIGUOUS.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_to_ambiguous));
        } else if (errorCode == Message.GEOCODE_FROM_TO_AMBIGUOUS.getId()) {
            return (resources
                    .getString(R.string.tripplanner_error_geocode_from_to_ambiguous));
        } else if (errorCode == Message.UNDERSPECIFIED_TRIANGLE.getId()
                || errorCode == Message.TRIANGLE_NOT_AFFINE.getId()
                || errorCode == Message.TRIANGLE_OPTIMIZE_TYPE_NOT_SET.getId()
                || errorCode == Message.TRIANGLE_VALUES_NOT_SET.getId()) {
            return (resources.getString(R.string.tripplanner_error_triangle));
        } else {
            return null;
        }
    }

    protected Response requestPlan(Request requestParams, String prefix, String baseURL) {
        HashMap<String, String> tmp = requestParams.getParameters();

        Collection c = tmp.entrySet();
        Iterator itr = c.iterator();

        String params = "";
        boolean first = true;
        while (itr.hasNext()) {
            if (first) {
                params += "?" + itr.next();
                first = false;
            } else {
                params += "&" + itr.next();
            }
        }

        if (requestParams.getBikeRental()) {
            String updatedString;
            if (prefix.equals(FOLDER_STRUCTURE_PREFIX_NEW)) {
                updatedString = params.replace(TraverseMode.BICYCLE.toString(),
                        TraverseMode.BICYCLE.toString() + OTP_RENTAL_QUALIFIER);
            } else {
                updatedString = params.replace(TraverseMode.BICYCLE.toString(),
                        TraverseMode.BICYCLE.toString() + ", " + TraverseMode.WALK.toString());
            }

            params = updatedString;
        }

        String u = baseURL + prefix + PLAN_LOCATION + params;

        Log.d(TAG, "URL: " + u);

        currentRequestString = u;

        HttpURLConnection urlConnection = null;
        URL url;
        Response plan = null;

        try {
            url = new URL(u);

            disableConnectionReuseIfNecessary(); // For bugs in HttpURLConnection pre-Froyo

            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_SOCKET_TIMEOUT);
            plan = JacksonConfig.getObjectReaderInstance()
                    .readValue(urlConnection.getInputStream());
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Timeout fetching JSON or XML: " + e);
            e.printStackTrace();
            cancel(true);
        } catch (IOException e) {
            Log.e(TAG, "Error fetching JSON or XML: " + e);
            e.printStackTrace();
            cancel(true);
            // Reset timestamps to show there was an error
            // requestStartTime = 0;
            // requestEndTime = 0;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return plan;
    }

    /**
     * Disable HTTP connection reuse which was buggy pre-froyo
     */
    private void disableConnectionReuseIfNecessary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
    }

}
