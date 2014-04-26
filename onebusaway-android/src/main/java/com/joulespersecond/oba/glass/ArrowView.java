/*
 * Copyright (C) 2014 Sean J. Barbeau, University of South Florida
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
package com.joulespersecond.oba.glass;

import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.seattlebusbot.Application;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import java.util.Iterator;
import java.util.List;

/**
 * View that draws an arrow that points towards the given bus mStop
 */
public class ArrowView extends View implements SensorEventListener, LocationListener {

    SensorManager mSensorManager;

    private float[] mRotationMatrix = new float[16];

    private float[] mOrientation = new float[9];

    private float mHeading;

    private float mPitch;

    private Paint mArrowPaint;

    private Paint mArrowFillPaint;

    LocationManager mLocationManager;

    private Location mLastLocation;

    ObaStop mStop;

    public ArrowView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mArrowPaint = new Paint();
        mArrowPaint.setColor(Color.BLACK);
        mArrowPaint.setStyle(Paint.Style.STROKE);
        mArrowPaint.setStrokeWidth(4.0f);
        mArrowPaint.setAntiAlias(true);

        mArrowFillPaint = new Paint();
        mArrowFillPaint.setColor(Color.WHITE);
        mArrowFillPaint.setStyle(Paint.Style.FILL);
        mArrowFillPaint.setStrokeWidth(4.0f);
        mArrowFillPaint.setAntiAlias(true);
    }

    public void setObaStop(ObaStop stop) {
        this.mStop = stop;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Listen for sensor data
        mSensorManager = (SensorManager) Application.get().getBaseContext()
                .getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_UI);
        // Listen for location
        mLocationManager = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSensorManager.unregisterListener(this);
        mLocationManager.removeUpdates(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawArrow(canvas);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
            SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X,
                    SensorManager.AXIS_Z, mRotationMatrix);
            SensorManager.getOrientation(mRotationMatrix, mOrientation);

            mHeading = (float) Math.toDegrees(mOrientation[0]);
            mPitch = (float) Math.toDegrees(mOrientation[1]);

            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        final float ACC_THRESHOLD = 50f;
        // If the new location is the first location, or has an accuracy better than 50m and
        // is newer than the last location, save it
        if (mLastLocation == null || (mLastLocation.getAccuracy() < ACC_THRESHOLD
                && location.getTime() > mLastLocation.getTime())) {
            mLastLocation = location;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void drawArrow(Canvas c) {
        int height = getHeight();
        int width = getWidth();

//        final float ARROW_HEIGHT_SCALE = 1f;
//        final float ARROW_WIDTH_SCALE = 0.5f;

        float x1, y1;  // Tip of arrow
        x1 = width / 2;
        y1 = 0;

        float x2, y2;
        x2 = 0;
        y2 = 0;

        float x3, y3;
        x3 = width;
        y3 = height;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x1, y1);
        path.close();

        // Rotate arrow around center point
        Matrix matrix = new Matrix();
        matrix.postRotate((float) -mHeading, width / 2, height / 2);
        path.transform(matrix);

        c.drawPath(path, mArrowPaint);
        c.drawPath(path, mArrowFillPaint);
    }
}
