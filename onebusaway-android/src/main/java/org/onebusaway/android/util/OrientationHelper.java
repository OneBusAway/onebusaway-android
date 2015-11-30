/*
 * Copyright (C) 2014 Sean J. Barbeau (sjbarbeau@gmail.com), University of South Florida
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
package org.onebusaway.android.util;

import org.onebusaway.android.app.Application;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;

/**
 * A sensor-based orientation helperclass , which allows listeners to receive orientation updates
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

    private static float[] mRemappedMatrix = new float[16];

    private float[] mOrientation = new float[9];

    private float[] history = new float[2];

    private static float[] mTruncatedRotationVector = new float[4];

    private static boolean mTruncateVector = false;

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

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onSensorChanged(SensorEvent event) {
        float xDelta = 0f;
        float yDelta = 0f;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:

                // Modern rotation vector sensors
                if (!mTruncateVector) {
                    try {
                        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    } catch (IllegalArgumentException e) {
                        // On some Samsung devices, an exception is thrown if this vector > 4
                        // See https://github.com/barbeau/gpstest/issues/39
                        // Truncate the array, since we can deal with only the first four values
                        Log.e(TAG, "Samsung device error? Will truncate vectors - " + e);
                        mTruncateVector = true;
                        // Do the truncation here the first time the exception occurs
                        getRotationMatrixFromTruncatedVector(event.values);
                    }
                } else {
                    // Truncate the array to avoid the exception on some devices (see #39)
                    getRotationMatrixFromTruncatedVector(event.values);
                }

                WindowManager windowManager = (WindowManager) mContext.getSystemService(
                        Context.WINDOW_SERVICE);
                int rot = windowManager.getDefaultDisplay().getRotation();

                switch (rot) {
                    case Surface.ROTATION_0:
                        // No orientation change, use default coordinate system
                        SensorManager.getOrientation(mRotationMatrix, mOrientation);
                        // Log.d(TAG, "Rotation-0");
                        break;
                    case Surface.ROTATION_90:
                        // Log.d(TAG, "Rotation-90");
                        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mOrientation);
                        break;
                    case Surface.ROTATION_180:
                        // Log.d(TAG, "Rotation-180");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_X,
                                        SensorManager.AXIS_MINUS_Y, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mOrientation);
                        break;
                    case Surface.ROTATION_270:
                        // Log.d(TAG, "Rotation-270");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Y,
                                        SensorManager.AXIS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mOrientation);
                        break;
                    default:
                        // This shouldn't happen - assume default orientation
                        SensorManager.getOrientation(mRotationMatrix, mOrientation);
                        // Log.d(TAG, "Rotation-Unknown");
                        break;
                }

                mHeading = (float) Math.toDegrees(mOrientation[0]);
                mPitch = (float) Math.toDegrees(mOrientation[1]);

                xDelta = history[0] - mHeading;
                yDelta = history[1] - mPitch;

                history[0] = mHeading;
                history[1] = mPitch;
                break;
            case Sensor.TYPE_ORIENTATION:
                // Legacy orientation sensors
                mHeading = event.values[0];
                xDelta = history[0] - mHeading;
                break;
            default:
                // A sensor we're not using, so return
                return;
        }

        // Use magnetic field to compute true (geographic) north, if data is available
        Float magneticDeclination = Application.getMagneticDeclination();
        if (magneticDeclination != null) {
            mHeading += magneticDeclination;
        }

        // Make sure value is between 0-360
        mHeading = MathUtils.mod(mHeading, 360.0f);

        for (Listener l : mListeners) {
            l.onOrientationChanged(mHeading, mPitch, xDelta, yDelta);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void getRotationMatrixFromTruncatedVector(float[] vector) {
        System.arraycopy(vector, 0, mTruncatedRotationVector, 0, 4);
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, mTruncatedRotationVector);
    }
}
