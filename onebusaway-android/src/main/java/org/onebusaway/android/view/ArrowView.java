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

package org.onebusaway.android.view;

import org.onebusaway.android.R;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.MathUtils;
import org.onebusaway.android.util.OrientationHelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/**
 * View that draws an arrow that points towards the given bus mStop
 */
public class ArrowView extends View implements OrientationHelper.Listener, LocationHelper.Listener {

    public interface Listener {

        /**
         * Called when the ArrowView is showing information to the user
         */
        void onInitializationComplete();
    }

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    private float mHeading;

    private Paint mArrowPaint;

    private Paint mArrowFillPaint;

    private Location mLastLocation;

    Location mStopLocation = new Location("stopLocation");

    float mBearingToStop;

    boolean mInitialized = false;

    public ArrowView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mArrowPaint = new Paint();
        mArrowPaint.setColor(Color.WHITE);
        mArrowPaint.setStyle(Paint.Style.STROKE);
        mArrowPaint.setStrokeWidth(4.0f);
        mArrowPaint.setAntiAlias(true);

        mArrowFillPaint = new Paint();
        mArrowFillPaint.setColor(Color.WHITE);
        mArrowFillPaint.setStyle(Paint.Style.FILL);
        mArrowFillPaint.setStrokeWidth(4.0f);
        mArrowFillPaint.setAntiAlias(true);

    }

    public void setStopLocation(Location location) {
        mStopLocation = location;
    }

    public synchronized void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void unregisterListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    /**
     * Returns true if the view is initialized and ready to draw to the screen, false if it is not
     *
     * @return true if the view is initialized and ready to draw to the screen, false if it is not
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mStopLocation == null || mLastLocation == null) {
            return;
        }
        drawArrow(canvas);
    }

    @Override
    public void onOrientationChanged(float heading, float pitch, float xDelta, float yDelta) {
        mHeading = heading;
        invalidate();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        if (mStopLocation != null) {
            if (!mInitialized) {
                mInitialized = true;
                // Notify listeners that we have both stop and real-time location and can draw
                for (Listener l : mListeners) {
                    l.onInitializationComplete();
                }
            }

            mBearingToStop = location.bearingTo(mStopLocation);

            // Result of bearingTo() can be from -180 to 180. If negative, convert to 181-360 range
            // See http://stackoverflow.com/a/8043485/937715
            if (mBearingToStop < 0) {
                mBearingToStop += 360;
            }
            invalidate();
        }
    }

    private void drawArrow(Canvas c) {
        int height = getHeight();
        int width = getWidth();

        // Create a buffer around the arrow so when it rotates it doesn't get clipped by view edge
        final float BUFFER = width / 5;

        // Height of the cutout in the bottom of the triangle that makes it an arrow (0=triangle)
        final float CUTOUT_HEIGHT = getHeight() / 5;

        float x1, y1;  // Tip of arrow
        x1 = width / 2;
        y1 = BUFFER;

        float x2, y2;  // lower left
        x2 = BUFFER;
        y2 = height - BUFFER;

        float x3, y3; // cutout in arrow bottom
        x3 = width / 2;
        y3 = height - CUTOUT_HEIGHT - BUFFER;

        float x4, y4; // lower right
        x4 = width - BUFFER;
        y4 = height - BUFFER;

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.lineTo(x1, y1);
        path.close();

        float direction = mHeading - mBearingToStop;
        // Make sure value is between 0-360
        direction = MathUtils.mod(direction, 360.0f);

        // Rotate arrow around center point
        Matrix matrix = new Matrix();
        matrix.postRotate((float) -direction, width / 2, height / 2);
        path.transform(matrix);

        c.drawPath(path, mArrowPaint);
        c.drawPath(path, mArrowFillPaint);

        // Update content description, so screen readers can announce direction to stop
        String[] spokenDirections = getResources()
                .getStringArray(R.array.spoken_compass_directions);
        String directionName = spokenDirections[MathUtils.getHalfWindIndex(direction)];
        setContentDescription(directionName);
    }
}