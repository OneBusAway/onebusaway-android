/*
 * Copyright (C) 2010-2013 Paul Watts (paulcwatts@gmail.com)
 * and individual contributors.
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

package org.onebusaway.android.util;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.HomeActivity;
import org.onebusaway.android.view.RealtimeIndicatorView;
import org.onebusaway.util.comparators.AlphanumComparator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A class containing utility methods related to the user interface
 */
public final class UIUtils {

    private static final String TAG = "UIHelp";

    public static void setupActionBar(AppCompatActivity activity) {
        ActionBar bar = activity.getSupportActionBar();
        bar.setIcon(android.R.color.transparent);
        bar.setDisplayShowTitleEnabled(true);

        // HomeActivity is the root for all other activities
        if (!(activity instanceof HomeActivity)) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

    }

    /**
     * Sets up the search view in the action bar
     */
    public static void setupSearch(Activity activity, Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            SearchManager searchManager =
                    (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
            final MenuItem searchMenu = menu.findItem(R.id.action_search);
            SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenu);
            searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(activity.getComponentName()));
            // Close the keyboard and SearchView at same time when the back button is pressed
            searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean queryTextFocused) {
                    if (!queryTextFocused) {
                        MenuItemCompat.collapseActionView(searchMenu);
                    }
                }
            });
        }
    }

    public static void showProgress(Fragment fragment, boolean visible) {
        AppCompatActivity act = (AppCompatActivity) fragment.getActivity();
        if (act != null) {
            act.setSupportProgressBarIndeterminateVisibility(visible);
        }
    }

    public static void setClickableSpan(TextView v, ClickableSpan span) {
        Spannable text = (Spannable) v.getText();
        text.setSpan(span, 0, text.length(), 0);
        v.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static void removeAllClickableSpans(TextView v) {
        Spannable text = (Spannable) v.getText();
        ClickableSpan[] spans = text.getSpans(0, text.length(), ClickableSpan.class);
        for (ClickableSpan cs : spans) {
            text.removeSpan(cs);
        }
    }

    public static final int getStopDirectionText(String direction) {
        if (direction.equals("N")) {
            return R.string.direction_n;
        } else if (direction.equals("NW")) {
            return R.string.direction_nw;
        } else if (direction.equals("W")) {
            return R.string.direction_w;
        } else if (direction.equals("SW")) {
            return R.string.direction_sw;
        } else if (direction.equals("S")) {
            return R.string.direction_s;
        } else if (direction.equals("SE")) {
            return R.string.direction_se;
        } else if (direction.equals("E")) {
            return R.string.direction_e;
        } else if (direction.equals("NE")) {
            return R.string.direction_ne;
        } else {
            return R.string.direction_none;
        }
    }

    public static final String getRouteDisplayName(String routeShortName, String routeLongName) {
        if (!TextUtils.isEmpty(routeShortName)) {
            return routeShortName;
        }
        if (!TextUtils.isEmpty(routeLongName)) {
            return routeLongName;
        }
        // Just so we never return null.
        return "";
    }

    public static final String getRouteDisplayName(ObaRoute route) {
        return getRouteDisplayName(route.getShortName(), route.getLongName());
    }

    public static final String getRouteDisplayName(ObaArrivalInfo arrivalInfo) {
        return getRouteDisplayName(arrivalInfo.getShortName(), arrivalInfo.getRouteLongName());
    }

    public static final String getRouteDescription(ObaRoute route) {
        String shortName = route.getShortName();
        String longName = route.getLongName();

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = route.getDescription();
        }
        return MyTextUtils.toTitleCase(longName);
    }

    // Shows or hides the view, depending on whether or not the direction is
    // available.
    public static final void setStopDirection(View v, String direction, boolean show) {
        final TextView text = (TextView) v;
        final int directionText = UIUtils.getStopDirectionText(direction);
        if ((directionText != R.string.direction_none) || show) {
            text.setText(directionText);
            text.setVisibility(View.VISIBLE);
        } else {
            text.setVisibility(View.GONE);
        }
    }

    // Common code to set a route list item view
    public static final void setRouteView(View view, ObaRoute route) {
        TextView shortNameText = (TextView) view.findViewById(R.id.short_name);
        TextView longNameText = (TextView) view.findViewById(R.id.long_name);

        String shortName = route.getShortName();
        String longName = MyTextUtils.toTitleCase(route.getLongName());

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = MyTextUtils.toTitleCase(route.getDescription());
        }

        shortNameText.setText(shortName);
        longNameText.setText(longName);
    }

    private static final String[] STOP_USER_PROJECTION = {
            ObaContract.Stops._ID,
            ObaContract.Stops.FAVORITE,
            ObaContract.Stops.USER_NAME
    };

    public static class StopUserInfoMap {

        private final ContentQueryMap mMap;

        public StopUserInfoMap(Context context) {
            ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(ObaContract.Stops.CONTENT_URI, STOP_USER_PROJECTION, "("
                    + ObaContract.Stops.USER_NAME + " IS NOT NULL)" + "OR ("
                    + ObaContract.Stops.FAVORITE + "=1)", null, null);
            mMap = new ContentQueryMap(c, ObaContract.Stops._ID, true, null);
        }

        public void close() {
            mMap.close();
        }

        public void requery() {
            mMap.requery();
        }

        public void setView(View stopRoot, String stopId, String stopName) {
            TextView nameView = (TextView) stopRoot.findViewById(R.id.stop_name);
            setView2(nameView, stopId, stopName, true);
        }

        /**
         * This should be used with compound drawables
         */
        public void setView2(TextView nameView, String stopId, String stopName, boolean showIcon) {
            ContentValues values = mMap.getValues(stopId);
            int icon = 0;
            if (values != null) {
                Integer i = values.getAsInteger(ObaContract.Stops.FAVORITE);
                final boolean favorite = (i != null) && (i == 1);
                final String userName = values.getAsString(ObaContract.Stops.USER_NAME);

                nameView.setText(TextUtils.isEmpty(userName) ?
                        MyTextUtils.toTitleCase(stopName) : userName);
                icon = favorite && showIcon ? R.drawable.ic_toggle_star : 0;
            } else {
                nameView.setText(MyTextUtils.toTitleCase(stopName));
            }
            nameView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        }
    }

    /**
     * Returns a comma-delimited list of route display names that serve a stop
     * <p/>
     * For example, if a stop was served by "14" and "54", this method will return "14,54"
     *
     * @param stop   the stop for which the route display names should be serialized
     * @param routes a HashMap containing all routes that serve this stop, with the routeId as the
     *               key.
     *               Note that for efficiency this routes HashMap may contain routes that don't
     *               serve this stop as well -
     *               the routes for the stop are referenced via stop.getRouteDisplayNames()
     * @return comma-delimited list of route display names that serve a stop
     */
    public static String serializeRouteDisplayNames(ObaStop stop,
            HashMap<String, ObaRoute> routes) {
        StringBuffer sb = new StringBuffer();
        String[] routeIds = stop.getRouteIds();
        for (int i = 0; i < routeIds.length; i++) {
            if (routes != null) {
                ObaRoute route = routes.get(routeIds[i]);
                sb.append(getRouteDisplayName(route));
            } else {
                // We don't have route mappings - use routeIds
                sb.append(routeIds[i]);
            }

            if (i != routeIds.length - 1) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * Returns a list of route display names from a serialized list of route display names
     * <p/>
     * See {@link #serializeRouteDisplayNames(ObaStop, java.util.HashMap)}
     *
     * @param serializedRouteDisplayNames comma-separate list of routeIds from serializeRouteDisplayNames()
     * @return list of route display names
     */
    public static List<String> deserializeRouteDisplayNames(String serializedRouteDisplayNames) {
        String routes[] = serializedRouteDisplayNames.split(",");
        return Arrays.asList(routes);
    }

    /**
     * Returns a formatted and sorted list of route display names for presentation in a single line
     * <p/>
     * For example, the following list:
     * <p/>
     * 11,1,15, 8b
     * <p/>
     * ...would be formatted as:
     * <p/>
     * 4, 8b, 11, 15
     *
     * @param routeDisplayNames          list of route display names
     * @param nextArrivalRouteShortNames the short route names of the next X arrivals at the stop
     *                                   that are the same.  These will be highlighted in the
     *                                   results.
     * @return a formatted and sorted list of route display names for presentation in a single line
     */
    public static String formatRouteDisplayNames(List<String> routeDisplayNames,
            List<String> nextArrivalRouteShortNames) {
        Collections.sort(routeDisplayNames, new AlphanumComparator());
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < routeDisplayNames.size(); i++) {
            boolean match = false;

            for (String nextArrivalRouteShortName : nextArrivalRouteShortNames) {
                if (routeDisplayNames.get(i).equalsIgnoreCase(nextArrivalRouteShortName)) {
                    match = true;
                    break;
                }
            }

            if (match) {
                // If this route name matches a route name for the next X arrivals that are the same, highlight this route in the text
                sb.append(routeDisplayNames.get(i) + "*");
            } else {
                // Just append the normally-formatted route name
                sb.append(routeDisplayNames.get(i));
            }

            if (i != routeDisplayNames.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Generates the dialog text that is used to show detailed information about a particular stop
     *
     * @return a pair of Strings consisting of the <dialog title, dialog message>
     */
    public static Pair<String, String> createStopDetailsDialogText(Context context, String stopName,
            String stopUserName, String stopCode, String stopDirection,
            List<String> routeDisplayNames) {
        final String newLine = "\n";
        String title = "";
        StringBuilder message = new StringBuilder();

        if (!TextUtils.isEmpty(stopUserName)) {
            title = stopUserName;
            if (stopName != null) {
                // Show official stop name in addition to user name
                message.append(
                        context.getString(R.string.stop_info_official_stop_name_label, stopName))
                        .append(newLine);
            }
        } else if (stopName != null) {
            title = stopName;
        }

        if (stopCode != null) {
            message.append(context.getString(R.string.stop_details_code, stopCode) + newLine);
        }

        // Routes that serve this stop
        if (routeDisplayNames != null) {
            String routes = context.getString(R.string.stop_info_route_ids_label) + " " + UIUtils
                    .formatRouteDisplayNames(routeDisplayNames, new ArrayList<String>());
            message.append(routes);
        }

        if (!TextUtils.isEmpty(stopDirection)) {
            message.append(newLine)
                    .append(context.getString(UIUtils.getStopDirectionText(stopDirection)));
        }
        return new Pair(title, message.toString());
    }

    /**
     * Builds an AlertDialog with the given title and message
     *
     * @return an AlertDialog with the given title and message
     */
    public static AlertDialog buildAlertDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        return builder.create();
    }

    /**
     * Default implementation for creating a shortcut when in shortcut mode.
     *
     * @param name       The name of the shortcut.
     * @param destIntent The destination intent.
     */
    public static final Intent makeShortcut(Context context, String name, Intent destIntent) {
        // Set up the container intent
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, destIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource
                .fromContext(context, R.mipmap.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        return intent;
    }

    public static void goToUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.browser_error), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static void goToPhoneDialer(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse(url));
        context.startActivity(intent);
    }

    /**
     * Opens email apps based on the given email address
     * @param email address
     * @param location string that show the current location
     */
    public static void sendEmail(Context context, String email, String location) {
        PackageManager pm = context.getPackageManager();
        PackageInfo appInfo;
        try {
            appInfo = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        String body;
        if (location != null) {
            body = context.getString(R.string.bug_report_body,
                    appInfo.versionName,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                    location);
        } else {
            body = context.getString(R.string.bug_report_body_without_location,
                    appInfo.versionName,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT);
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_EMAIL,
                new String[]{email});
        send.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.bug_report_subject));
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.setType("message/rfc822");
        try {
            context.startActivity(Intent.createChooser(send,
                    context.getString(R.string.bug_report_subject)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.bug_report_error, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static final String getRouteErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                return context.getString(R.string.airplane_mode_error);
            } else {
                return context.getString(R.string.no_network_error);
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                return context.getString(R.string.internal_error);
            case ObaApi.OBA_NOT_FOUND:
                ObaRegion r = Application.get().getCurrentRegion();
                if (r != null) {
                    return context.getString(R.string.route_not_found_error_with_region_name,
                            r.getName());
                } else {
                    return context.getString(R.string.route_not_found_error_no_region);
                }
            case ObaApi.OBA_BAD_GATEWAY:
                return context.getString(R.string.bad_gateway_error);
            case ObaApi.OBA_OUT_OF_MEMORY:
                return context.getString(R.string.out_of_memory_error);
            default:
                return context.getString(R.string.generic_comm_error);
        }
    }

    public static final String getStopErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                return context.getString(R.string.airplane_mode_error);
            } else {
                return context.getString(R.string.no_network_error);
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                return context.getString(R.string.internal_error);
            case ObaApi.OBA_NOT_FOUND:
                ObaRegion r = Application.get().getCurrentRegion();
                if (r != null) {
                    return context
                            .getString(R.string.stop_not_found_error_with_region_name, r.getName());
                } else {
                    return context.getString(R.string.stop_not_found_error_no_region);
                }
            case ObaApi.OBA_BAD_GATEWAY:
                return context.getString(R.string.bad_gateway_error);
            case ObaApi.OBA_OUT_OF_MEMORY:
                return context.getString(R.string.out_of_memory_error);
            default:
                return context.getString(R.string.generic_comm_error);
        }
    }

    public static final int getMapErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                return R.string.airplane_mode_error;
            } else {
                return R.string.no_network_error;
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                return R.string.internal_error;
            case ObaApi.OBA_BAD_GATEWAY:
                return R.string.bad_gateway_error;
            case ObaApi.OBA_OUT_OF_MEMORY:
                return R.string.out_of_memory_error;
            default:
                return R.string.map_generic_error;
        }
    }

    public static boolean isAirplaneMode(Context context) {
        ContentResolver cr = context.getContentResolver();
        return Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null) && activeNetwork.isConnectedOrConnecting();
    }

    /**
     * Returns the first string for the query URI.
     */
    public static String stringForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[]{column}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return "";
    }

    public static Integer intForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[]{column}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    public static final int MINUTES_IN_HOUR = 60;

    /**
     * Takes the number of minutes, and returns a user-readable string
     * saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes if minutes > 60
     *
     * @param minutes            number of minutes for which there are no upcoming arrivals
     * @param additionalArrivals true if the response should include the word additional, false if
     *                           it should not
     * @param shortFormat        true if the format should be abbreviated, false if it should be
     *                           long
     * @return a user-readable string saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes > 60
     */
    public static String getNoArrivalsMessage(Context context, int minutes,
            boolean additionalArrivals, boolean shortFormat) {
        if (minutes <= MINUTES_IN_HOUR) {
            // Return just minutes
            if (additionalArrivals) {
                if (shortFormat) {
                    // Abbreviated version
                    return context
                            .getString(R.string.stop_info_no_additional_data_minutes_short_format,
                                    minutes);
                } else {
                    // Long version
                    return context
                            .getString(R.string.stop_info_no_additional_data_minutes, minutes);
                }
            } else {
                if (shortFormat) {
                    // Abbreviated version
                    return context
                            .getString(R.string.stop_info_nodata_minutes_short_format, minutes);
                } else {
                    // Long version
                    return context.getString(R.string.stop_info_nodata_minutes, minutes);
                }
            }
        } else {
            // Return hours and minutes
            if (additionalArrivals) {
                if (shortFormat) {
                    // Abbreviated version
                    return context.getResources()
                            .getQuantityString(
                                    R.plurals.stop_info_no_additional_data_hours_minutes_short_format,
                                    minutes / 60, minutes % 60, minutes / 60);
                } else {
                    // Long version
                    return context.getResources()
                            .getQuantityString(R.plurals.stop_info_no_additional_data_hours_minutes,
                                    minutes / 60, minutes % 60, minutes / 60);
                }
            } else {
                if (shortFormat) {
                    // Abbreviated version
                    return context.getResources()
                            .getQuantityString(
                                    R.plurals.stop_info_nodata_hours_minutes_short_format,
                                    minutes / 60,
                                    minutes % 60, minutes / 60);
                } else {
                    // Long version
                    return context.getResources()
                            .getQuantityString(R.plurals.stop_info_nodata_hours_minutes,
                                    minutes / 60,
                                    minutes % 60, minutes / 60);
                }
            }
        }
    }

    /**
     * Returns true if the activity is still active and dialogs can be managed (i.e., displayed
     * or dismissed), or false if it is
     * not
     *
     * @param activity Activity to check for displaying/dismissing a dialog
     * @return true if the activity is still active and dialogs can be managed, or false if it is
     * not
     */
    public static boolean canManageDialog(Activity activity) {
        if (activity == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return !activity.isFinishing() && !activity.isDestroyed();
        } else {
            return !activity.isFinishing();
        }
    }

    /**
     * Returns true if the context is an Activity and is still active and dialogs can be managed
     * (i.e., displayed or dismissed) OR the context is not an Activity, or false if the Activity
     * is
     * no longer active.
     *
     * NOTE: We really shouldn't display dialogs from a Service - a notification is a better way
     * to communicate with the user.
     *
     * @param context Context to check for displaying/dismissing a dialog
     * @return true if the context is an Activity and is still active and dialogs can be managed
     * (i.e., displayed or dismissed) OR the context is not an Activity, or false if the Activity
     * is
     * no longer active
     */
    public static boolean canManageDialog(Context context) {
        if (context == null) {
            return false;
        }

        if (context instanceof Activity) {
            return canManageDialog((Activity) context);
        } else {
            // We really shouldn't be displaying dialogs from a Service, but if for some reason we
            // need to do this, we don't have any way of checking whether its possible
            return true;
        }
    }

    /**
     * Returns true if the API level supports animating Views using ViewPropertyAnimator, false if
     * it doesn't
     *
     * @return true if the API level supports animating Views using ViewPropertyAnimator, false if
     * it doesn't
     */
    public static boolean canAnimateViewModern() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    /**
     * Returns true if the API level supports canceling existing animations via the
     * ViewPropertyAnimator, and false if it does not
     *
     * @return true if the API level supports canceling existing animations via the
     * ViewPropertyAnimator, and false if it does not
     */
    public static boolean canCancelAnimation() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    /**
     * Returns true if the API level supports our Arrival Info Style B (sort by route) views, false
     * if it does not.  See #350 and #275.
     *
     * @return true if the API level supports our Arrival Info Style B (sort by route) views, false
     * if it does not
     */
    public static boolean canSupportArrivalInfoStyleB() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    /**
     * Shows a view, using animation if the platform supports it
     *
     * @param v                 View to show
     * @param animationDuration duration of animation
     */
    @TargetApi(14)
    public static void showViewWithAnimation(final View v, int animationDuration) {
        // If we're on a legacy device, show the view without the animation
        if (!canAnimateViewModern()) {
            showViewWithoutAnimation(v);
            return;
        }

        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1) {
            // View is already visible and not transparent, return without doing anything
            return;
        }

        v.clearAnimation();
        if (canCancelAnimation()) {
            v.animate().cancel();
        }

        if (v.getVisibility() != View.VISIBLE) {
            // Set the content view to 0% opacity but visible, so that it is visible
            // (but fully transparent) during the animation.
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
        }

        // Animate the content view to 100% opacity, and clear any animation listener set on the view.
        v.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .setListener(null);
    }

    /**
     * Shows a view without using animation
     *
     * @param v View to show
     */
    public static void showViewWithoutAnimation(final View v) {
        if (v.getVisibility() == View.VISIBLE) {
            // View is already visible, return without doing anything
            return;
        }
        v.setVisibility(View.VISIBLE);
    }

    /**
     * Hides a view, using animation if the platform supports it
     *
     * @param v                 View to hide
     * @param animationDuration duration of animation
     */
    @TargetApi(14)
    public static void hideViewWithAnimation(final View v, int animationDuration) {
        // If we're on a legacy device, hide the view without the animation
        if (!canAnimateViewModern()) {
            hideViewWithoutAnimation(v);
            return;
        }

        if (v.getVisibility() == View.GONE) {
            // View is already gone, return without doing anything
            return;
        }

        v.clearAnimation();
        if (canCancelAnimation()) {
            v.animate().cancel();
        }

        // Animate the view to 0% opacity. After the animation ends, set its visibility to GONE as
        // an optimization step (it won't participate in layout passes, etc.)
        v.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        v.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Hides a view without using animation
     *
     * @param v View to hide
     */
    public static void hideViewWithoutAnimation(final View v) {
        if (v.getVisibility() == View.GONE) {
            // View is already gone, return without doing anything
            return;
        }
        // Hide the view without animation
        v.setVisibility(View.GONE);
    }

    /**
     * Prints View visibility information to the log for debugging purposes
     *
     * @param v View to log visibility information for
     */
    @TargetApi(12)
    public static void logViewVisibility(View v) {
        if (v != null) {
            if (v.getVisibility() == View.VISIBLE) {
                Log.d(TAG, v.getContext().getResources().getResourceEntryName(v.getId())
                        + " is visible");
                if (UIUtils.canAnimateViewModern()) {
                    Log.d(TAG, v.getContext().getResources().getResourceEntryName(v.getId())
                            + " alpha - " + v.getAlpha());
                }
            } else if (v.getVisibility() == View.INVISIBLE) {
                Log.d(TAG, v.getContext().getResources().getResourceEntryName(v.getId())
                        + " is INVISIBLE");
            } else if (v.getVisibility() == View.GONE) {
                Log.d(TAG,
                        v.getContext().getResources().getResourceEntryName(v.getId()) + " is GONE");
            } else {
                Log.d(TAG, v.getContext().getResources().getResourceEntryName(v.getId())
                        + ".getVisibility() - " + v.getVisibility());
            }
        }
    }

    /**
     * Converts screen dimension units from dp to pixels, based on algorithm defined in
     * http://developer.android.com/guide/practices/screens_support.html#dips-pels
     *
     * @param dp value in dp
     * @return value in pixels
     */
    public static int dpToPixels(Context context, float dp) {
        // Get the screen's density scale
        final float scale = context.getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (dp * scale + 0.5f);
    }

    /**
     * Sets the margins for a given view
     *
     * @param v View to set the margin for
     * @param l left margin, in pixels
     * @param t top margin, in pixels
     * @param r right margin, in pixels
     * @param b bottom margin, in pixels
     */
    public static void setMargins(View v, int l, int t, int r, int b) {
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        p.setMargins(l, t, r, b);
        v.setLayoutParams(p);
    }

    /**
     * Formats a view so it is ignored for accessible access
     */
    public static void setAccessibilityIgnore(View view) {
        view.setClickable(false);
        view.setFocusable(false);
        view.setContentDescription("");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /**
     * Builds the list of Strings that should be shown for a given trip "Bus Options" menu,
     * provided the arguments for that trip
     *
     * @param c                 Context
     * @param isRouteFavorite   true if this route is a user favorite, false if it is not
     * @param hasUrl            true if the route provides a URL for schedule data, false if it does
     *                          not
     * @param isReminderVisible true if the reminder is currently visible for a trip, false if it
     *                          is
     *                          not
     * @return the list of Strings that should be shown for a given trip, provided the arguments for
     * that trip
     */
    public static List<String> buildTripOptions(Context c, boolean isRouteFavorite, boolean hasUrl,
            boolean isReminderVisible) {
        ArrayList<String> list = new ArrayList<>();
        if (!isRouteFavorite) {
            list.add(c.getString(R.string.bus_options_menu_add_star));
        } else {
            list.add(c.getString(R.string.bus_options_menu_remove_star));
        }

        list.add(c.getString(R.string.bus_options_menu_show_route_on_map));
        list.add(c.getString(R.string.bus_options_menu_show_trip_details));

        if (!isReminderVisible) {
            list.add(c.getString(R.string.bus_options_menu_set_reminder));
        } else {
            list.add(c.getString(R.string.bus_options_menu_edit_reminder));
        }

        list.add(c.getString(R.string.bus_options_menu_show_only_this_route));

        if (hasUrl) {
            list.add(c.getString(R.string.bus_options_menu_show_route_schedule));
        }

        list.add(c.getString(R.string.bus_options_menu_report_trip_problem));
        return list;
    }

    /**
     * Builds the array of icons that should be shown for the trip "Bus Options" menu, given the
     * provided arguments for that trip
     *
     * @param isRouteFavorite   true if this route is a user favorite, false if it is not
     * @param hasUrl true if the route provides a URL for schedule data, false if it does
     *               not
     * @return the array of icons that should be shown for a given trip
     */
    public static List<Integer> buildTripOptionsIcons(boolean isRouteFavorite, boolean hasUrl) {
        ArrayList<Integer> list = new ArrayList<>();
        if (!isRouteFavorite) {
            list.add(R.drawable.focus_star_on);
        } else {
            list.add(R.drawable.focus_star_off);
        }
        list.add(R.drawable.ic_arrivals_styleb_action_map);
        list.add(R.drawable.ic_trip_details);
        list.add(R.drawable.ic_drawer_alarm);
        list.add(R.drawable.ic_content_filter_list);
        if (hasUrl) {
            list.add(R.drawable.ic_notification_event_note);
        }
        list.add(R.drawable.ic_alert_warning);
        return list;
    }

    /**
     * Sets the line and fill colors for real-time indicator circles contained in the provided
     * realtime_indicator.xml layout.  There are several circles, so each needs to be set
     * individually.  The resource code for the color to be used should be provided.
     *
     * @param vg        realtime_indicator.xml layout
     * @param lineColor resource code color to be used as line color, or null to use the default
     *                  colors
     * @param fillColor resource code color to be used as fill color, or null to use the default
     *                  colors
     */
    public static void setRealtimeIndicatorColorByResourceCode(ViewGroup vg, Integer lineColor,
            Integer fillColor) {
        Resources r = vg.getResources();
        setRealtimeIndicatorColor(vg, r.getColor(lineColor), r.getColor(fillColor));
    }

    /**
     * Sets the line and fill colors for real-time indicator circles contained in the provided
     * realtime_indicator.xml layout.  There are several circles, so each needs to be set
     * individually.  The integer representation of the color to be used should be provided.
     *
     * @param vg        realtime_indicator.xml layout
     * @param lineColor color to be used as line color, or null to use the default colors
     * @param fillColor color to be used as fill color, or null to use the default colors
     */
    public static void setRealtimeIndicatorColor(ViewGroup vg, Integer lineColor,
            Integer fillColor) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View v = vg.getChildAt(i);
            if (v instanceof RealtimeIndicatorView) {
                if (lineColor != null) {
                    ((RealtimeIndicatorView) v).setLineColor(lineColor);
                } else {
                    // Use default color
                    ((RealtimeIndicatorView) v).setLineColor(
                            R.color.realtime_indicator_line);
                }
                if (fillColor != null) {
                    ((RealtimeIndicatorView) v).setFillColor(fillColor);
                } else {
                    // Use default color
                    ((RealtimeIndicatorView) v).setLineColor(
                            R.color.realtime_indicator_fill);
                }
            }
        }
    }

    /**
     * Creates a new Bitmap, with the black color of the source image changed to the given color.
     * The source Bitmap isn't modified.
     *
     * @param source the source Bitmap with a black background
     * @param color  the color to change the black color to
     * @return the resulting Bitmap that has the black changed to the color
     */
    public static Bitmap colorBitmap(Bitmap source, int color) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int x = 0; x < pixels.length; ++x) {
            pixels[x] = (pixels[x] == Color.BLACK) ? color : pixels[x];
        }

        Bitmap out = Bitmap.createBitmap(width, height, source.getConfig());
        out.setPixels(pixels, 0, width, 0, 0, width, height);
        return out;
    }

    /**
     * Returns true if the provided touch event was within the provided view
     *
     * @return true if the provided touch event was within the provided view
     */
    public static boolean isTouchInView(View view, MotionEvent event) {
        Rect rect = new Rect();
        view.getGlobalVisibleRect(rect);
        return rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    /**
     * Returns the current time for comparison against another current time.  For API levels >=
     * Jelly Bean MR1 the SystemClock.getElapsedRealtimeNanos() method is used, and for API levels
     * <
     * Jelly Bean MR1 System.currentTimeMillis() is used.
     *
     * @return the current time for comparison against another current time, in nanoseconds
     */
    public static long getCurrentTimeForComparison() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Use elapsed real-time nanos, since its guaranteed monotonic
            return SystemClock.elapsedRealtimeNanos();
        } else {
            return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        }
    }

    /**
     * Open the soft keyboard
     */
    public static void openKeyboard(Context context) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED,
                InputMethodManager.HIDE_NOT_ALWAYS);
    }

    /**
     * Closes the soft keyboard
     */
    public static void closeKeyboard(Context context, View v) {
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /**
     * Returns true if the provided currentTime falls within the situation's (i.e., alert's) active
     * windows or if the situation does not provide an active window, and false if the currentTime
     * falls outside of the situation's active windows
     *
     * @param currentTime the time to compare to the situation's windows
     * @return true if the provided currentTime falls within the situation's (i.e., alert's) active
     * windows or if the situation does not provide an active window, and false if the currentTime
     * falls outside of the situation's active windows
     */
    public static boolean isActiveWindowForSituation(ObaSituation situation, long currentTime) {
        if (situation.getActiveWindows().length == 0) {
            // We assume a situation is active if it doesn't contain any active window information
            return true;
        }
        boolean isActiveWindowForSituation = false;
        for (ObaSituation.ActiveWindow activeWindow : situation.getActiveWindows()) {
            long from = activeWindow.getFrom();
            long to = activeWindow.getTo();
            if (from <= currentTime && currentTime <= to) {
                isActiveWindowForSituation = true;
                break;
            }
        }
        return isActiveWindowForSituation;
    }

    /**
     * Returns the time formatting as "1:10pm" to be displayed as an absolute time for an
     * arrival/departure
     *
     * @param time an arrival or departure time (e.g., from ArrivalInfo)
     * @return the time formatting as "1:10pm" to be displayed as an absolute time for an
     * arrival/departure
     */
    public static String formatTime(Context context, long time) {
        return DateUtils.formatDateTime(context,
                time,
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT
        );
    }

    /**
     * Set smaller text size if the route short name has more than 3 characters
     *
     * @param view Text view
     * @param routeShortName Route short name
     */
    public static void maybeShrinkRouteName(Context context, TextView view, String routeShortName) {
        if (routeShortName.length() < 4) {
            // No-op if text is short enough to fit
            return;
        } else if (routeShortName.length() == 4) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().
                    getDimension(R.dimen.route_name_text_size_medium));
        } else if (routeShortName.length() > 4) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().
                    getDimension(R.dimen.route_name_text_size_small));
        }
    }
}
