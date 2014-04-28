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

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import android.content.Context;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * Implements sensor-based scrolling of a ListView
 */
public class ListController implements GestureDetector.BaseListener, OrientationManager.Listener {

    static final String TAG = "ListController";

    Context mContext;

    ListView mList;

    boolean mActive = true;

    GestureDetector mGestureDetector;

    public ListController(Context context, ListView list) {
        this.mContext = context;
        this.mList = list;
        mGestureDetector = new GestureDetector(mContext);
        mGestureDetector.setBaseListener(this);
    }

    /**
     * Receive pass-through of event from Activity
     */
    public boolean onMotionEvent(MotionEvent event) {
        return mGestureDetector.onMotionEvent(event);
    }

    @Override
    public boolean onGesture(Gesture gesture) {
        switch (gesture) {
            case TWO_LONG_PRESS:
                // Toggle on and off accelerometer control of the list by long press
                playSuccessSound();
                toggleActive();
                return true;
            case TWO_TAP:
                // Go to top of the list
                playSuccessSound();
                scrollToTop();
                return true;
        }
        return false;
    }

    /**
     * Toggles whether the controller modifies the view
     */
    public void toggleActive() {
        mActive = !mActive;
    }

    @Override
    public void onOrientationChanged(float heading, float pitch, float xDelta, float yDelta) {
        if (mList == null || !mActive) {
            return;
        }

        float Y_DELTA_THRESHOLD = 0.13f;
        // Log.d(TAG, "Y Delta = " + yDelta);

        int scrollHeight = mList.getHeight()
                / 19; // 4 items per page, scroll almost 1/5 an item

        // Log.d(TAG, "ScrollHeight = " + scrollHeight);

        if (yDelta > Y_DELTA_THRESHOLD) {
            // Log.d(TAG, "Detected change in pitch up...");
            mList.smoothScrollBy(-scrollHeight, 0);
        } else if (yDelta < -Y_DELTA_THRESHOLD) {
            // Log.d(TAG, "Detected change in pitch down...");
            mList.smoothScrollBy(scrollHeight, 0);
        }

    }

    private void scrollToTop() {
        mList.smoothScrollToPosition(0);
    }

    private void playSuccessSound() {
        // Play sound to acknowledge action
        AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audio.playSoundEffect(Sounds.SUCCESS);
    }
}
