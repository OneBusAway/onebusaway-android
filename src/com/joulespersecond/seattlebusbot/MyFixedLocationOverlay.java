package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.location.Location;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Projection;

/**
 * This class shouldn't need to exist. It's merely to workaround a crash
 * in the maps library on the Cliq.
 * For reference: http://www.mail-archive.com/android-developers@googlegroups.com/msg65658.html
 *
 * @author paulw
 */
class MyFixedLocationOverlay extends MyLocationOverlay {
    private static final class MyLocationDot {
        private final Paint mAccuracyPaint;

        private final Drawable mDrawable;
        private final int mWidth;
        private final int mHeight;

        MyLocationDot(Context ctx, int drawable) {
            mAccuracyPaint = new Paint();
            mAccuracyPaint.setAntiAlias(true);
            mAccuracyPaint.setStrokeWidth(2.0f);

            mDrawable = ctx.getResources().getDrawable(drawable);
            mWidth = mDrawable.getIntrinsicWidth();
            mHeight = mDrawable.getIntrinsicHeight();
        }
        void draw(Canvas canvas, MapView mapView, Location lastFix,
                   GeoPoint myLoc, long when) {
            final Projection projection = mapView.getProjection();
            final double latitude = lastFix.getLatitude();
            final double longitude = lastFix.getLongitude();
            final float accuracy = lastFix.getAccuracy();
            float[] result = new float[1];

            Location.distanceBetween(latitude, longitude, latitude, longitude + 1, result);

            final float longitudeLineDistance = result[0];

            GeoPoint leftGeo = new GeoPoint((int)(latitude*1e6),
                                (int)((longitude-accuracy/longitudeLineDistance)*1e6));

            Point center = new Point();
            Point left = new Point();
            projection.toPixels(leftGeo, left);
            projection.toPixels(myLoc, center);

            int radius = center.x - left.x;
            mAccuracyPaint.setColor(0xFF6666FF);
            mAccuracyPaint.setStyle(Style.STROKE);
            canvas.drawCircle(center.x, center.y, radius, mAccuracyPaint);

            mAccuracyPaint.setColor(0x186666FF);
            mAccuracyPaint.setStyle(Style.FILL);

            canvas.drawCircle(center.x, center.y, radius, mAccuracyPaint);

            mDrawable.setBounds(center.x - mWidth / 2,
                            center.y - mHeight / 2,
                            center.x + mWidth / 2,
                            center.y + mHeight / 2);
            mDrawable.draw(canvas);
        }
    }
    private MyLocationDot mDot;

    public MyFixedLocationOverlay(Context context, MapView mapView) {
        super(context, mapView);
        // Uncomment this to test:
        //initDot(context);
    }

    @Override
    protected void drawMyLocation(Canvas canvas, MapView mapView, Location lastFix,
                            GeoPoint myLoc, long when) {
        if (mDot == null) {
            try {
                super.drawMyLocation(canvas, mapView, lastFix, myLoc, when);
                return;
            }
            catch (Exception e) {
                initDot(mapView.getContext());
            }
        }
        mDot.draw(canvas, mapView, lastFix, myLoc, when);
    }
    private void initDot(Context context) {
        mDot = new MyLocationDot(context, R.drawable.mylocation);
    }
}
