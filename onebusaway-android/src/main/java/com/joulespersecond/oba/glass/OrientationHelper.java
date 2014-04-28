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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;

/**
 * Implements a sensor-based orientation helper for Glass, which allows listeners to receive
 * orientation updates
 */
public class OrientationHelper implements SensorEventListener {

    public interface Listener {

        /**
         * Called every time there is an update to the orientation
         *
         * @param deltaHeading change in heading from last heading value
         * @param deltaPitch   change in pitch from last pitch value
         */
        void onOrientationChanged(float heading, float pitch, float deltaHeading, float deltaPitch);
    }

    static final String TAG = "OrientationHelper";

    Context mContext;

    SensorManager mSensorManager;

    private float[] mRotationMatrix = new float[16];

    private float[] mOrientation = new float[9];

    private float[] history = new float[2];

    private float mHeading;

    private float mPitch;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    public OrientationHelper(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
    }

    public synchronized void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }

        // If this is the first listener, make sure we're monitoring the sensors to provide updates
        if (mListeners.size() == 1) {
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                    SensorManager.SENSOR_DELAY_UI);
        }
    }

    public synchronized void unregisterListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }

        if (mListeners.size() == 0) {
            mSensorManager.unregisterListener(this);
        }
    }

    public synchronized void onResume() {
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_UI);
    }

    public synchronized void onPause() {
        mSensorManager.unregisterListener(this);
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

            float xDelta = history[0] - mHeading;  // Currently unused
            float yDelta = history[1] - mPitch;

            history[0] = mHeading;
            history[1] = mPitch;

            // Use magnetic field to compute true (geographic) north, if data is available
            Float magneticDeclination = LocationHelper.getMagneticDeclination();
            if (magneticDeclination != null) {
                mHeading += magneticDeclination;
            }

            for (Listener l : mListeners) {
                l.onOrientationChanged(mHeading, mPitch, xDelta, yDelta);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
