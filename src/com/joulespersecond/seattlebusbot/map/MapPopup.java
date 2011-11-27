package com.joulespersecond.seattlebusbot.map;

import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.text.style.ClickableSpan;
import android.view.View;
import android.view.View.OnClickListener;

class MapPopup {

    private View mView;

    void initView(View view) {
        mView = view;
        // Make sure clicks on the popup don't leak to the map.
        mView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) { /* no-op */ }
        });

        // Initialize the links
        UIHelp.setChildClickable(mView, R.id.show_arrival_info, mOnShowArrivals);
        UIHelp.setChildClickable(mView, R.id.show_routes, mOnShowRoutes);
    }

    final ClickableSpan mOnShowArrivals = new ClickableSpan() {
        public void onClick(View v) {
            /*
            // This really shouldn't happen, but it does sometimes.
            if (mStopOverlay == null) {
                return;
            }
            StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
            if (item != null) {
                goToStop(MapViewActivity.this, item.getStop());
            }
            */
        }
    };

    private final ClickableSpan mOnShowRoutes = new ClickableSpan() {
        public void onClick(View v) {
            /*
            showRoutes((TextView)v, !mShowRoutes);
            */
        }
    };

    private final ClickableSpan mOnHideRoute = new ClickableSpan() {
        public void onClick(View v) {
            /*
            mRouteOverlay.clearRoute();
            getStops();
            */
        }
    };

}
