package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.provider.ObaContract;

import android.content.ContentQueryMap;
import android.content.ContentValues;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;


public class ArrivalsListAdapter extends ArrayAdapter<ArrivalInfo> {
    private ContentQueryMap mTripsForStop;

    public ArrivalsListAdapter(Context context) {
        super(context, R.layout.arrivals_list_item);
    }

    public void setTripsForStop(ContentQueryMap tripsForStop) {
        mTripsForStop = tripsForStop;
        notifyDataSetChanged();
    }

    public void setData(ObaArrivalInfo[] arrivals, ArrayList<String> routesFilter) {
        if (arrivals != null) {
            ArrayList<ArrivalInfo> list =
                    ArrivalInfo.convertObaArrivalInfo(getContext(),
                            arrivals, routesFilter);
            setData(list);
        } else {
            setData(null);
        }
    }

    @Override
    protected void initView(View view, ArrivalInfo stopInfo) {
        TextView route = (TextView)view.findViewById(R.id.route);
        TextView destination = (TextView)view.findViewById(R.id.destination);
        TextView time = (TextView)view.findViewById(R.id.time);
        TextView status = (TextView)view.findViewById(R.id.status);
        TextView etaView = (TextView)view.findViewById(R.id.eta);

        final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();
        final Context context = getContext();

        route.setText(arrivalInfo.getShortName());
        destination.setText(MyTextUtils.toTitleCase(arrivalInfo.getHeadsign()));
        status.setText(stopInfo.getStatusText());

        long eta = stopInfo.getEta();
        if (eta == 0) {
            etaView.setText(R.string.stop_info_eta_now);
        } else {
            etaView.setText(String.valueOf(eta));
        }

        int color = context.getResources().getColor(stopInfo.getColor());
        // status.setTextColor(color); // This just doesn't look very good.
        etaView.setTextColor(color);

        time.setText(DateUtils.formatDateTime(context,
                stopInfo.getDisplayTime(),
                DateUtils.FORMAT_SHOW_TIME|
                DateUtils.FORMAT_NO_NOON|
                DateUtils.FORMAT_NO_MIDNIGHT));

        ContentValues values = null;
        if (mTripsForStop != null) {
            values = mTripsForStop.getValues(arrivalInfo.getTripId());
        }
        if (values != null) {
            String tripName = values.getAsString(ObaContract.Trips.NAME);

            TextView tripInfo = (TextView)view.findViewById(R.id.trip_info);
            if (tripName.length() == 0) {
                tripName = context.getString(R.string.trip_info_noname);
            }
            tripInfo.setText(tripName);
            tripInfo.setVisibility(View.VISIBLE);
        } else {
            // Explicitly set this to invisible because we might be reusing
            // this view.
            View tripInfo = view.findViewById(R.id.trip_info);
            tripInfo.setVisibility(View.GONE);
        }
    }
}
