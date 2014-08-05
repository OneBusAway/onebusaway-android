/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot.map.googlemapsv2;

import android.app.Activity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.seattlebusbot.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StopOverlay {
    private static final String TAG = "StopOverlay";

    private final List<ObaStop> mStops;

    private GoogleMap mMap;

    private HashMap<String, Marker> markers = new HashMap<String, Marker>();

    private final Activity mActivity;

    private static final int getResourceIdForDirection(String direction) {
        if (direction.equals("N")) {
            return R.drawable.stop_n;
        } else if (direction.equals("NW")) {
            return R.drawable.stop_nw;
        } else if (direction.equals("W")) {
            return R.drawable.stop_w;
        } else if (direction.equals("SW")) {
            return R.drawable.stop_sw;
        } else if (direction.equals("S")) {
            return R.drawable.stop_s;
        } else if (direction.equals("SE")) {
            return R.drawable.stop_se;
        } else if (direction.equals("E")) {
            return R.drawable.stop_e;
        } else if (direction.equals("NE")) {
            return R.drawable.stop_ne;
        } else {
            return R.drawable.stop_u;
        }
    }

    public StopOverlay(List<ObaStop> stops,
                       Activity activity, GoogleMap map) {
        mStops = stops;
        mActivity = activity;
        mMap = map;
        populate();
    }

    private void populate() {
        for (ObaStop stop : mStops) {
            markers.put(stop.getId(), mMap.addMarker(new MarkerOptions()
                            .position(MapHelpV2.makeLatLng(stop.getLocation()
                            )))
            );
        }
    }

    public int size() {
        return mStops.size();
    }

    /**
     * Clears any stop markers from the map
     */
    public void clear() {
        if (markers != null) {
            // Clear all markers from the map
            for (Map.Entry<String, Marker> entry : markers.entrySet()) {
                entry.getValue().remove();
            }

            // Clear the data structures
            markers.clear();
            markers = null;
        }
    }

//    @Override
//    public boolean onTrackballEvent(MotionEvent event, MapView view) {
//        final int action = event.getAction();
//        OverlayItem next = null;
//        //Log.d(TAG, "MotionEvent: " + event);
//
//        if (action == MotionEvent.ACTION_MOVE) {
//            final float xDiff = event.getX();
//            final float yDiff = event.getY();
//            // Up
//            if (yDiff <= -1) {
//                next = findNext(getFocus(), true, true);
//            }
//            // Down
//            else if (yDiff >= 1) {
//                next = findNext(getFocus(), true, false);
//            }
//            // Right
//            else if (xDiff >= 1) {
//                next = findNext(getFocus(), false, true);
//            }
//            // Left
//            else if (xDiff <= -1) {
//                next = findNext(getFocus(), false, false);
//            }
//            if (next != null) {
//                setFocus(next);
//                view.postInvalidate();
//            }
//        } else if (action == MotionEvent.ACTION_UP) {
//            final OverlayItem focus = getFocus();
//            if (focus != null) {
//                ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//            }
//        }
//        return true;
//    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event, MapView view) {
//        //Log.d(TAG, "KeyEvent: " + event);
//        OverlayItem next = null;
//        switch (keyCode) {
//            case KeyEvent.KEYCODE_DPAD_UP:
//                next = findNext(getFocus(), true, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_DOWN:
//                next = findNext(getFocus(), true, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_RIGHT:
//                next = findNext(getFocus(), false, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_LEFT:
//                next = findNext(getFocus(), false, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//                final OverlayItem focus = getFocus();
//                if (focus != null) {
//                    ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//                }
//                break;
//            default:
//                return false;
//        }
//        if (next != null) {
//            setFocus(next);
//            view.postInvalidate();
//        }
//        return true;
//    }

//    boolean setFocusById(String id) {
//        final int size = size();
//        for (int i = 0; i < size; ++i) {
//            StopOverlayItem item = (StopOverlayItem) getItem(i);
//            if (id.equals(item.getStop().getId())) {
//                setFocus(item);
//                return true;
//            }
//        }
//        return false;
//    }
//
//    String getFocusedId() {
//        final OverlayItem focus = getFocus();
//        if (focus != null) {
//            return ((StopOverlayItem) focus).getStop().getId();
//        }
//        return null;
//    }

//    @Override
//    protected boolean onTap(int index) {
//        final OverlayItem item = getItem(index);
//        if (item.equals(getFocus())) {
//            ObaStop stop = mStops.get(index);
//            ArrivalsListActivity.start(mActivity, stop);
//        } else {
//            setFocus(item);
//            // fix odd behavior where previously selected item is not re-highlighted
//            setLastFocusedIndex(-1);
//        }
//        return true;
//    }

    // The find next routines find the closest item along the specified axis.

//    OverlayItem findNext(OverlayItem initial, boolean lat, boolean positive) {
//        if (initial == null) {
//            return null;
//        }
//        final int size = size();
//        final GeoPoint initialPoint = initial.getPoint();
//        OverlayItem min = initial;
//        int minDist = Integer.MAX_VALUE;
//
//        for (int i = 0; i < size; ++i) {
//            OverlayItem item = getItem(i);
//            GeoPoint point = item.getPoint();
//            final int distX = point.getLongitudeE6() - initialPoint.getLongitudeE6();
//            final int distY = point.getLatitudeE6() - initialPoint.getLatitudeE6();
//
//            // We have to eliminate anything that's going in the wrong direction,
//            // or doesn't change in the correct axis (including the initial point)
//            if (lat) {
//                if (positive) {
//                    // Distance must be positive.
//                    if (distY <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must to be negative.
//                else if (distY >= 0) {
//                    continue;
//                }
//            } else {
//                if (positive) {
//                    // Distance must be positive
//                    if (distX <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must be negative
//                else if (distX >= 0) {
//                    continue;
//                }
//            }
//
//            final int distSq = distX * distX + distY * distY;
//
//            if (distSq < minDist) {
//                min = item;
//                minDist = distSq;
//            }
//        }
//        return min;
//    }
}
