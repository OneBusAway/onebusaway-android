/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html
   Source - https://gist.github.com/broady/6314689
   Video - https://www.youtube.com/watch?v=WKfZsCKSXVQ&feature=youtu.be
   */

package org.onebusaway.android.map.googlemapsv2;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.util.Property;

/**
 * Animation utilities for markers with Maps API.
 */
public class AnimationUtil {

    private static final int DEFAULT_DURATION_MS = 3000;

    /**
     * Animates a marker from its current position to the provided finalPosition.
     */
    public static void animateMarkerTo(Marker marker, LatLng finalPosition, int durationMs,
                                        Runnable onComplete) {
        TypeEvaluator<LatLng> typeEvaluator = (fraction, a, b) -> {
            double lat = (b.latitude - a.latitude) * fraction + a.latitude;
            double lngDelta = b.longitude - a.longitude;
            if (Math.abs(lngDelta) > 180) {
                lngDelta -= Math.signum(lngDelta) * 360;
            }
            double lng = lngDelta * fraction + a.longitude;
            return new LatLng(lat, lng);
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator
                .ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(durationMs);
        if (onComplete != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { onComplete.run(); }
            });
        }
        animator.start();
    }

    public static void animateMarkerTo(Marker marker, LatLng finalPosition, int durationMs) {
        animateMarkerTo(marker, finalPosition, durationMs, null);
    }

    public static void animateMarkerTo(Marker marker, LatLng finalPosition) {
        animateMarkerTo(marker, finalPosition, DEFAULT_DURATION_MS, null);
    }
}
