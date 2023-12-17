/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html
   Source - https://gist.github.com/broady/6314689
   Video - https://www.youtube.com/watch?v=WKfZsCKSXVQ&feature=youtu.be
   */

package org.onebusaway.android.map.googlemapsv2;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Property;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Animation utilities for markers with Maps API.
 *
 * Note - this class must remain in this .map.googleMapsv2 package so that the Google build flavors
 * work correctly.
 */
public class AnimationUtil {

    /**
     * Animates a marker from it's current position to the provided finalPosition
     *
     * @param marker        marker to animate
     * @param finalPosition the final position of the marker after the animation
     */
    public static void animateMarkerTo(final Marker marker, final LatLng finalPosition) {
        // Use the appropriate implementation per API Level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            animateMarkerToICS(marker, finalPosition);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            animateMarkerToHC(marker, finalPosition);
        } else {
            animateMarkerToGB(marker, finalPosition);
        }
    }

    private static void animateMarkerToGB(final Marker marker, final LatLng finalPosition) {
        final LatLngInterpolator latLngInterpolator = new LatLngInterpolator.Linear();
        final LatLng startPosition = marker.getPosition();
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        final float durationInMs = 3000;

        handler.post(new Runnable() {
            long elapsed;

            float t;

            float v;

            @Override
            public void run() {
                // Calculate progress using interpolator
                elapsed = SystemClock.uptimeMillis() - start;
                t = elapsed / durationInMs;
                v = interpolator.getInterpolation(t);

                marker.setPosition(latLngInterpolator.interpolate(v, startPosition, finalPosition));

                // Repeat till progress is complete.
                if (t < 1) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private static void animateMarkerToHC(final Marker marker, final LatLng finalPosition) {
        final LatLngInterpolator latLngInterpolator = new LatLngInterpolator.Linear();
        final LatLng startPosition = marker.getPosition();

        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                LatLng newPosition = latLngInterpolator
                        .interpolate(v, startPosition, finalPosition);
                marker.setPosition(newPosition);
            }
        });
        valueAnimator.setFloatValues(0, 1); // Ignored.
        valueAnimator.setDuration(3000);
        valueAnimator.start();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static void animateMarkerToICS(Marker marker, LatLng finalPosition) {
        final LatLngInterpolator latLngInterpolator = new LatLngInterpolator.Linear();
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return latLngInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator
                .ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(3000);
        animator.start();
    }

    /**
     * For other LatLngInterpolator interpolators, see https://gist.github.com/broady/6314689
     */
    interface LatLngInterpolator {

        LatLng interpolate(float fraction, LatLng a, LatLng b);

        class Linear implements LatLngInterpolator {

            @Override
            public LatLng interpolate(float fraction, LatLng a, LatLng b) {
                double lat = (b.latitude - a.latitude) * fraction + a.latitude;
                double lngDelta = b.longitude - a.longitude;

                // Take the shortest path across the 180th meridian.
                if (Math.abs(lngDelta) > 180) {
                    lngDelta -= Math.signum(lngDelta) * 360;
                }
                double lng = lngDelta * fraction + a.longitude;
                return new LatLng(lat, lng);
            }
        }
    }
}
