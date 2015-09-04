/*
 * Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * View that animates a circle expanding and contracting to indicate real-time information
 */
public class RealtimeIndicatorView extends View {

    private Paint mFillPaint;

    private Paint mLinePaint;

    private Animation mAnimation1;

    private float mNewRadius;

    /**
     * Percentage of total view size that the circle should be drawn
     */
    private final static float SCALE = 0.45f;

    /**
     * Default fill color
     */
    public int mFillColor = 0xbbFFFFFF;

    /**
     * Default line color
     */
    public int mLineColor = 0xFFFFFFFF;

    /**
     * Default duration
     */
    private int mDuration = 1000;  // ms

    /**
     * Set to true after the animation has been initialized
     */
    private boolean mInitComplete = false;

    public RealtimeIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Get the custom attributes defined in XML
        TypedArray a = context
                .obtainStyledAttributes(attrs, R.styleable.RealtimeIndicatorView, 0, 0);
        mFillColor = a.getColor(R.styleable.RealtimeIndicatorView_fillColor, mFillColor);
        mLineColor = a.getColor(R.styleable.RealtimeIndicatorView_lineColor, mLineColor);
        mDuration = a.getInteger(R.styleable.RealtimeIndicatorView_duration, mDuration);
        a.recycle();

        mFillPaint = new Paint();
        mFillPaint.setColor(mFillColor);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAntiAlias(true);

        mLinePaint = new Paint();
        mLinePaint.setStrokeWidth(3);
        mLinePaint.setColor(mLineColor);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setAntiAlias(true);

        setOnMeasureCallback();
        ensureInit();
    }

    private void setOnMeasureCallback() {
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        removeOnGlobalLayoutListener(this);
                        initAnimation();
                    }
                });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void removeOnGlobalLayoutListener(ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT < 16) {
            getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        } else {
            getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    /**
     * Makes sure the animation has been initialized.  This is a workaround to make sure
     * the animation is shown when this view is used within a list with an adapter.
     */
    private synchronized void ensureInit() {
        initAnimation();
        this.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mInitComplete) {
                    initAnimation();
                }
            }
        }, 500);
    }

    /**
     * Setup the animation
     */
    private synchronized void initAnimation() {
        // Animate circle expand/contract
        mAnimation1 = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int height = getHeight();
                mNewRadius = height * interpolatedTime;
                invalidate();
            }
        };

        mAnimation1.setDuration(mDuration);
        mAnimation1.setRepeatMode(Animation.REVERSE);
        mAnimation1.setInterpolator(new FastOutLinearInInterpolator());
        mAnimation1.setRepeatCount(Animation.INFINITE);
        startAnimation(mAnimation1);
        mInitComplete = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();

        float x = width / 2.0f;
        float y = height / 2.0f;

        canvas.drawCircle(x, y, mNewRadius * SCALE, mFillPaint);
        canvas.drawCircle(x, y, mNewRadius * SCALE, mLinePaint);
    }

    /**
     * Set the fill color to be used when drawing the expanding/contracting circle, in the format
     * 0xbbFFFFFF.
     *
     * @param color the fill color to be used when drawing the expanding/contracting circle, in the
     *              format
     *              0xbbFFFFFF.
     */
    public void setFillColor(int color) {
        mFillColor = color;
        mFillPaint.setColor(color);
    }

    /**
     * Set the line (i.e., stroke) color to be used when drawing the expanding/contracting circle,
     * in the format
     * 0xbbFFFFFF.
     *
     * @param color the line (i.e., stroke) to be used when drawing the expanding/contracting
     *              circle, in the format
     *              0xbbFFFFFF.
     */
    public void setLineColor(int color) {
        mLineColor = color;
        mLinePaint.setColor(color);
    }
}