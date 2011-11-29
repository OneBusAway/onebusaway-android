package com.joulespersecond.seattlebusbot.map;

import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.view.View;
import android.widget.TextView;

class RoutePopup {
    private final MapFragment mFragment;
    //private final Context mContext;
    private final View mView;
    private final TextView mRouteShortName;
    private final TextView mRouteLongName;

    RoutePopup(MapFragment fragment, View view) {
        mFragment = fragment;
        //mContext = fragment.getActivity();
        mView = view;
        mRouteShortName = (TextView)mView.findViewById(R.id.short_name);
        mRouteLongName = (TextView)mView.findViewById(R.id.route_long_name);

        View cancel = mView.findViewById(R.id.cancel_route_mode);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.switchToStopMode();
            }
        });
    }

    void show(ObaRoute route) {
        mRouteShortName.setText(UIHelp.getRouteDisplayName(route));
        mRouteLongName.setText(UIHelp.getRouteDescription(route));
        mView.setVisibility(View.VISIBLE);
    }

    void hide() {
        mView.setVisibility(View.GONE);
    }
}
