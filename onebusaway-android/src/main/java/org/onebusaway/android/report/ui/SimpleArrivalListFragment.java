/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com), and individual contributors.
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

package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalInfo;
import org.onebusaway.android.ui.ArrivalsListLoader;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class SimpleArrivalListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse> {

    public interface Callback {

        void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo, String agencyName, String blockId);
    }

    private ObaStop mObaStop;

    private String mBundleObaStopId;

    private Callback mCallback;

    public static final String TAG = "SimpArrivalListFragment";

    private static int ARRIVALS_LIST_LOADER = 3;

    public static void show(AppCompatActivity activity, Integer containerViewId,
                            ObaStop stop, Callback callback) {
        FragmentManager fm = activity.getSupportFragmentManager();

        SimpleArrivalListFragment fragment = new SimpleArrivalListFragment();
        fragment.setObaStop(stop);

        Intent intent = new Intent(activity, SimpleArrivalListFragment.class);
        intent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
        fragment.setArguments(FragmentUtils.getIntentArgs(intent));
        fragment.setCallback(callback);

        try {
            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(containerViewId, fragment, TAG);
            ft.addToBackStack(null);
            ft.commit();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot show SimpleArrivalListFragment after onSaveInstanceState has been called");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mObaStop != null) {
            outState.putString(MapParams.STOP_ID, mObaStop.getId());
        } else if (mBundleObaStopId != null) {
            outState.putString(MapParams.STOP_ID, mBundleObaStopId);
        }
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mBundleObaStopId = savedInstanceState.getString(MapParams.STOP_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_arrival_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((ImageView) getActivity().findViewById(R.id.arrival_list_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LoaderManager mgr = getActivity().getSupportLoaderManager();

        mgr.initLoader(ARRIVALS_LIST_LOADER, getArguments(), this).forceLoad();
    }

    public void setObaStop(ObaStop obaStop) {
        mObaStop = obaStop;
    }

    @Override
    public void onResume() {
        super.onResume();

        getActivity().getSupportLoaderManager().restartLoader(ARRIVALS_LIST_LOADER, getArguments(), this).
                forceLoad();
    }

    @Override
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        String stopId;
        if (mObaStop == null) {
            stopId = mBundleObaStopId;
        } else {
            stopId = mObaStop.getId();
        }
        return new ArrivalsListLoader(getActivity(), stopId);
    }

    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader, ObaArrivalInfoResponse data) {
        ObaArrivalInfo[] info;
        if (data.getCode() == ObaApi.OBA_OK) {
            info = data.getArrivalInfo();
            if (info.length > 0) {
                loadArrivalList(info, data.getRefs(), data.getCurrentTime());
            } else {
                showErrorText();
            }
        }
    }

    private void showErrorText() {
        String text = getResources().getString(R.string.ri_no_trip);
        ((TextView) getActivity().findViewById(R.id.simple_arrival_info_text)).setText(text);
    }

    private void loadArrivalList(ObaArrivalInfo[] info, final ObaReferences refs, long currentTime) {
        LinearLayout contentLayout = (LinearLayout) getActivity().
                findViewById(R.id.simple_arrival_content);
        contentLayout.removeAllViews();

        ArrayList<ArrivalInfo> arrivalInfos = ArrivalInfoUtils.convertObaArrivalInfo(getActivity(),
                info, new ArrayList<String>(), currentTime, false);

        for (ArrivalInfo stopInfo : arrivalInfos) {

            final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();

            LayoutInflater inflater = LayoutInflater.from(getActivity());
            LinearLayout view = (LinearLayout) inflater.inflate(
                    R.layout.arrivals_list_item, null, false);
            view.setBackgroundColor(getResources().getColor(R.color.material_background));
            TextView route = (TextView) view.findViewById(R.id.route);
            TextView destination = (TextView) view.findViewById(R.id.destination);
            TextView time = (TextView) view.findViewById(R.id.time);
            TextView status = (TextView) view.findViewById(R.id.status);
            TextView etaView = (TextView) view.findViewById(R.id.eta);
            TextView minView = (TextView) view.findViewById(R.id.eta_min);
            ViewGroup realtimeView = (ViewGroup) view.findViewById(R.id.eta_realtime_indicator);

            view.findViewById(R.id.more_horizontal).setVisibility(View.INVISIBLE);
            view.findViewById(R.id.route_favorite).setVisibility(View.INVISIBLE);

            String routeShortName = arrivalInfo.getShortName();
            route.setText(routeShortName);
            UIUtils.maybeShrinkRouteName(getActivity(), route, routeShortName);

            destination.setText(UIUtils.formatDisplayText(arrivalInfo.getHeadsign()));
            status.setText(stopInfo.getStatusText());

            long eta = stopInfo.getEta();
            if (eta == 0) {
                etaView.setText(R.string.stop_info_eta_now);
                minView.setVisibility(View.GONE);
            } else {
                etaView.setText(String.valueOf(eta));
                minView.setVisibility(View.VISIBLE);
            }

            status.setBackgroundResource(R.drawable.round_corners_style_b_status);
            GradientDrawable d = (GradientDrawable) status.getBackground();

            Integer colorCode = stopInfo.getColor();
            int color = getActivity().getResources().getColor(colorCode);
            if (stopInfo.getPredicted()) {
                // Show real-time indicator
                UIUtils.setRealtimeIndicatorColorByResourceCode(realtimeView, colorCode,
                        android.R.color.transparent);
                realtimeView.setVisibility(View.VISIBLE);
            } else {
                realtimeView.setVisibility(View.INVISIBLE);
            }

            etaView.setTextColor(color);
            minView.setTextColor(color);
            d.setColor(color);

            // Set padding on status view
            int pSides = UIUtils.dpToPixels(getActivity(), 5);
            int pTopBottom = UIUtils.dpToPixels(getActivity(), 2);
            status.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            time.setText(DateUtils.formatDateTime(getActivity(),
                    stopInfo.getDisplayTime(),
                    DateUtils.FORMAT_SHOW_TIME |
                            DateUtils.FORMAT_NO_NOON |
                            DateUtils.FORMAT_NO_MIDNIGHT
            ));
            View reminder = view.findViewById(R.id.reminder);
            reminder.setVisibility(View.GONE);

            contentLayout.addView(view);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String agencyName = findAgencyNameByRouteId(refs, arrivalInfo.getRouteId());
                    String blockId = findBlockIdByTripId(refs, arrivalInfo.getTripId());
                    mCallback.onArrivalItemClicked(arrivalInfo, agencyName, blockId);
                }
            });
        }
    }

    private String findAgencyNameByRouteId(ObaReferences refs, String routeId) {
        String agencyId = refs.getRoute(routeId).getAgencyId();
        return refs.getAgency(agencyId).getId();
    }

    private String findBlockIdByTripId(ObaReferences refs, String tripId) {
        ObaTrip trip = refs.getTrip(tripId);
        return trip.getBlockId();
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
    }

    @SuppressWarnings("unused")
    private ArrivalsListLoader getArrivalsLoader() {
        // If the Fragment hasn't been attached to an Activity yet, return null
        if (!isAdded()) {
            return null;
        }
        Loader<ObaArrivalInfoResponse> l =
                getLoaderManager().getLoader(ARRIVALS_LIST_LOADER);
        return (ArrivalsListLoader) l;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }
}
