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
 * Implements a sensor-based orientation manager for Glass, which allows listeners to receive
 * orientation updates
 */
public class OrientationManager implements SensorEventListener {

    public interface Listener {

        /**
         * Called every time there is an update to the orientation
         *
         * @param deltaHeading change in heading from last heading value
         * @param deltaPitch   change in pitch from last pitch value
         */
        void onOrientationChanged(float heading, float pitch, float deltaHeading, float deltaPitch);
    }

    static final String TAG = "OrientationManager";

    static Context mContext;

    static SensorManager mSensorManager;

    private float[] mRotationMatrix = new float[16];

    private float[] mOrientation = new float[9];

    private float[] history = new float[2];

    private float mHeading;

    private float mPitch;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    static OrientationManager mOrientationManager;

    public synchronized static OrientationManager getInstance(Context context) {
        if (mOrientationManager == null) {
            mContext = context;
            mOrientationManager = new OrientationManager();
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mSensorManager.registerListener(mOrientationManager,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                    SensorManager.SENSOR_DELAY_UI);
        }
        return mOrientationManager;
    }

    public synchronized void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void removeListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public synchronized void destroy() {
        mSensorManager.unregisterListener(mOrientationManager);
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

            for (Listener l : mListeners) {
                l.onOrientationChanged(mHeading, mPitch, xDelta, yDelta);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
