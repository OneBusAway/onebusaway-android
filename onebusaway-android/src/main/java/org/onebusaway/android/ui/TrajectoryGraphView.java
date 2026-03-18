/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
package org.onebusaway.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaTripStatusExtensionsKt;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTrackerKt;
import org.onebusaway.android.extrapolation.math.SpeedDistribution;
import org.onebusaway.android.util.PreferenceUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Custom View that draws a distance-time graph comparing scheduled vs actual vehicle trajectory.
 */
public class TrajectoryGraphView extends View {

    private static final long TICK_INTERVAL_MS = 1000;
    private static final int BG_COLOR = Color.parseColor("#1A1A1A");

    private List<ObaTripStatus> mHistory = new ArrayList<>();
    private ObaTripSchedule mSchedule;
    private long mServiceDate;
    private long mCurrentTime = System.currentTimeMillis();
    private double mEstimatedSpeedMps;
    private SpeedDistribution mDistribution;
    private double mCachedCiLoMps;
    private double mCachedCiHiMps;
    private String mHighlightedStopId;

    // Per-draw coordinate transform state (set at top of onDraw, used by toPixelX/toPixelY)
    private float mGraphW, mGraphH;
    private double mVisMinDist, mVisDistRange;
    private long mVisMinTime, mVisTimeRange;

    private final Paint mSchedulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mScheduleDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrajectoryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrajectoryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNowLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNowLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExtrapolatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExtrapolateDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExtrapolateLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDeviationDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDeviationLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDeviationLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mConfidenceBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPdfFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mSchedulePath = new Path();
    private final Path mTrajectoryPath = new Path();
    private final Path mPdfPath = new Path();

    private static final int PDF_NUM_BINS = 160;
    private final double[] mPdfValues = new double[PDF_NUM_BINS];
    private final Date mReusableDate = new Date();

    private final float mDensity;
    private final SimpleDateFormat mTimeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final boolean mUseImperial;
    private boolean mTickingActive;

    // Zoom & pan state
    private float mScaleX = 1f;
    private float mScaleY = 1f;
    private double mOffsetDist = 0;
    private long mOffsetTime = 0;

    // Full data bounds (set during onDraw, used by gesture handlers)
    private double mFullMinDist = 0;
    private double mFullMaxDist = 0;
    private long mFullMinTime = 0;
    private long mFullMaxTime = 0;

    // Graph margins for gesture coordinate conversion
    private final float mMarginLeft;
    private final float mMarginTop;
    private final float mMarginRight;
    private final float mMarginBottom;

    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;

    private final Handler mTickHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            mCurrentTime = System.currentTimeMillis();
            invalidate();
            mTickHandler.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    public TrajectoryGraphView(Context context) {
        this(context, null);
    }

    public TrajectoryGraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TrajectoryGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = context.getResources().getDisplayMetrics().density;
        mUseImperial = !PreferenceUtils.getUnitsAreMetricFromPreferences(context);
        mMarginLeft = 65 * mDensity;
        mMarginBottom = 35 * mDensity;
        mMarginTop = 15 * mDensity;
        mMarginRight = 15 * mDensity;
        initPaints();

        mScaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();

                        float graphW = getWidth() - mMarginLeft - mMarginRight;
                        float graphH = getHeight() - mMarginTop - mMarginBottom;
                        if (graphW <= 0 || graphH <= 0) return true;

                        double fullDistRange = mFullMaxDist - mFullMinDist;
                        long fullTimeRange = mFullMaxTime - mFullMinTime;
                        if (fullDistRange <= 0 || fullTimeRange <= 0) return true;

                        // Data-space point under the focal point before scaling
                        double visDistRange = fullDistRange / mScaleX;
                        long visTimeRange = (long) (fullTimeRange / mScaleY);
                        double focalDist = mFullMinDist + mOffsetDist
                                + visDistRange * ((focusX - mMarginLeft) / graphW);
                        long focalTime = mFullMinTime + mOffsetTime
                                + visTimeRange - (long) (visTimeRange * ((focusY - mMarginTop) / graphH));

                        // Apply scale
                        float newScaleX = Math.max(1f, Math.min(20f, mScaleX * factor));
                        float newScaleY = Math.max(1f, Math.min(20f, mScaleY * factor));
                        mScaleX = newScaleX;
                        mScaleY = newScaleY;

                        // Adjust offsets so the focal data point stays under the finger
                        double newVisDistRange = fullDistRange / mScaleX;
                        long newVisTimeRange = (long) (fullTimeRange / mScaleY);
                        mOffsetDist = focalDist - mFullMinDist
                                - newVisDistRange * ((focusX - mMarginLeft) / graphW);
                        mOffsetTime = focalTime - mFullMinTime
                                - newVisTimeRange + (long) (newVisTimeRange * ((focusY - mMarginTop) / graphH));

                        clampOffsets();
                        invalidate();
                        return true;
                    }
                });

        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float distanceX, float distanceY) {
                        float graphW = getWidth() - mMarginLeft - mMarginRight;
                        float graphH = getHeight() - mMarginTop - mMarginBottom;
                        if (graphW <= 0 || graphH <= 0) return true;

                        double fullDistRange = mFullMaxDist - mFullMinDist;
                        long fullTimeRange = mFullMaxTime - mFullMinTime;

                        double visDistRange = fullDistRange / mScaleX;
                        long visTimeRange = (long) (fullTimeRange / mScaleY);

                        // Convert pixel delta to data-space delta
                        mOffsetDist += visDistRange * (distanceX / graphW);
                        // Y is inverted (up = higher time, pixel up = negative distanceY)
                        mOffsetTime -= (long) (visTimeRange * (distanceY / graphH));

                        clampOffsets();
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        mScaleX = 1f;
                        mScaleY = 1f;
                        mOffsetDist = 0;
                        mOffsetTime = 0;
                        invalidate();
                        return true;
                    }
                });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        if (mScaleX > 1f || mScaleY > 1f || mScaleDetector.isInProgress()) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    private void clampOffsets() {
        double fullDistRange = mFullMaxDist - mFullMinDist;
        long fullTimeRange = mFullMaxTime - mFullMinTime;
        if (fullDistRange <= 0 || fullTimeRange <= 0) return;

        double visDistRange = fullDistRange / mScaleX;
        long visTimeRange = (long) (fullTimeRange / mScaleY);

        mOffsetDist = Math.max(0, Math.min(mOffsetDist, fullDistRange - visDistRange));
        mOffsetTime = Math.max(0, Math.min(mOffsetTime, fullTimeRange - visTimeRange));
    }

    private void initPaints() {
        mSchedulePaint.setColor(Color.parseColor("#4488FF"));
        mSchedulePaint.setStyle(Paint.Style.STROKE);
        mSchedulePaint.setStrokeWidth(2 * mDensity);

        mScheduleDotPaint.setColor(Color.parseColor("#4488FF"));
        mScheduleDotPaint.setStyle(Paint.Style.FILL);

        mTrajectoryPaint.setColor(Color.parseColor("#44CC44"));
        mTrajectoryPaint.setStyle(Paint.Style.STROKE);
        mTrajectoryPaint.setStrokeWidth(3 * mDensity);

        mTrajectoryDotPaint.setColor(Color.parseColor("#44CC44"));
        mTrajectoryDotPaint.setStyle(Paint.Style.FILL);

        mNowLinePaint.setColor(Color.parseColor("#FF4444"));
        mNowLinePaint.setStyle(Paint.Style.STROKE);
        mNowLinePaint.setStrokeWidth(1 * mDensity);
        mNowLinePaint.setPathEffect(new DashPathEffect(
                new float[]{8 * mDensity, 4 * mDensity}, 0));

        mAxisPaint.setColor(Color.parseColor("#888888"));
        mAxisPaint.setStyle(Paint.Style.STROKE);
        mAxisPaint.setStrokeWidth(1 * mDensity);

        mLabelPaint.setColor(Color.parseColor("#AAAAAA"));
        mLabelPaint.setTextSize(10 * mDensity);

        mGridPaint.setColor(Color.parseColor("#333333"));
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(0.5f * mDensity);

        mNowLabelPaint.setColor(Color.parseColor("#FF4444"));
        mNowLabelPaint.setTextSize(10 * mDensity);

        mExtrapolatePaint.setColor(Color.parseColor("#BBBBBB"));
        mExtrapolatePaint.setStyle(Paint.Style.STROKE);
        mExtrapolatePaint.setStrokeWidth(1.5f * mDensity);

        mExtrapolateDashPaint.setColor(Color.parseColor("#BBBBBB"));
        mExtrapolateDashPaint.setStyle(Paint.Style.STROKE);
        mExtrapolateDashPaint.setStrokeWidth(1.5f * mDensity);
        mExtrapolateDashPaint.setPathEffect(new DashPathEffect(
                new float[]{6 * mDensity, 4 * mDensity}, 0));

        mExtrapolateLabelPaint.setColor(Color.parseColor("#BBBBBB"));
        mExtrapolateLabelPaint.setTextSize(10 * mDensity);

        mDeviationDotPaint.setColor(Color.parseColor("#FFAA00"));
        mDeviationDotPaint.setStyle(Paint.Style.FILL);

        mDeviationLabelPaint.setColor(Color.parseColor("#FFAA00"));
        mDeviationLabelPaint.setTextSize(11 * mDensity);
        mDeviationLabelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        mDeviationLinePaint.setColor(Color.parseColor("#FFAA00"));
        mDeviationLinePaint.setStyle(Paint.Style.STROKE);
        mDeviationLinePaint.setStrokeWidth(1.5f * mDensity);
        mDeviationLinePaint.setPathEffect(new DashPathEffect(
                new float[]{4 * mDensity, 3 * mDensity}, 0));

        mConfidenceBandPaint.setColor(Color.parseColor("#66BBBBBB"));
        mConfidenceBandPaint.setStyle(Paint.Style.STROKE);
        mConfidenceBandPaint.setStrokeWidth(1f * mDensity);
        mConfidenceBandPaint.setPathEffect(new DashPathEffect(
                new float[]{4 * mDensity, 4 * mDensity}, 0));

        mPdfFillPaint.setColor(Color.parseColor("#40BBBBBB"));
        mPdfFillPaint.setStyle(Paint.Style.FILL);
    }

    public void setHighlightedStopId(String stopId) {
        mHighlightedStopId = stopId;
        invalidate();
    }

    public void setData(List<ObaTripStatus> history, ObaTripSchedule schedule,
                        long serviceDate,
                        SpeedDistribution distribution) {
        mHistory = history != null ? new ArrayList<>(history) : new ArrayList<>();
        mSchedule = schedule;
        mServiceDate = serviceDate;
        mDistribution = distribution;
        mEstimatedSpeedMps = distribution != null ? distribution.getMean() : 0;
        if (distribution != null) {
            mCachedCiLoMps = distribution.quantile(0.10);
            mCachedCiHiMps = distribution.quantile(0.90);
        } else {
            mCachedCiLoMps = 0;
            mCachedCiHiMps = 0;
        }
        mCurrentTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTicking();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicking();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateTicking();
    }

    private void updateTicking() {
        if (isAttachedToWindow() && getVisibility() == VISIBLE) {
            startTicking();
        } else {
            stopTicking();
        }
    }

    private void startTicking() {
        if (!mTickingActive) {
            mTickingActive = true;
            mTickHandler.postDelayed(mTickRunnable, TICK_INTERVAL_MS);
        }
    }

    private void stopTicking() {
        if (mTickingActive) {
            mTickingActive = false;
            mTickHandler.removeCallbacks(mTickRunnable);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG_COLOR);

        float graphW = getWidth() - mMarginLeft - mMarginRight;
        float graphH = getHeight() - mMarginTop - mMarginBottom;
        if (graphW <= 0 || graphH <= 0) return;

        if (!computeDataBounds(canvas)) return;

        setupVisibleWindow(graphW, graphH);

        drawAxesAndLabels(canvas);

        canvas.save();
        canvas.clipRect(mMarginLeft, mMarginTop, getWidth() - mMarginRight,
                getHeight() - mMarginBottom);

        drawGridLines(canvas);
        drawScheduleLine(canvas);
        drawTrajectoryDots(canvas);

        ObaTripStatus newestValid = null;
        for (int i = mHistory.size() - 1; i >= 0; i--) {
            ObaTripStatus s = mHistory.get(i);
            if (ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(s) != null
                    && s.getLastLocationUpdateTime() > 0) {
                newestValid = s;
                break;
            }
        }
        Double lastDist = newestValid != null
                ? ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(newestValid) : null;
        long lastTime = newestValid != null ? newestValid.getLastLocationUpdateTime() : 0;

        drawExtrapolationAndDeviation(canvas, lastDist, lastTime);
        drawGammaPdfAndBands(canvas, lastDist, lastTime);
        drawNowLine(canvas);

        canvas.restore();

        drawLegend(canvas);
    }

    /**
     * Computes full data bounds from schedule and trajectory.
     * Returns false (and draws a "no data" message) if there is nothing to show.
     */
    private boolean computeDataBounds(Canvas canvas) {
        double minDist = 0, maxDist = 0;
        long minTime = Long.MAX_VALUE, maxTime = Long.MIN_VALUE;
        boolean hasData = false;

        if (mSchedule != null && mSchedule.getStopTimes() != null && mServiceDate > 0) {
            for (ObaTripSchedule.StopTime st : mSchedule.getStopTimes()) {
                double d = st.getDistanceAlongTrip();
                long t = mServiceDate + st.getArrivalTime() * 1000;
                if (d > maxDist) maxDist = d;
                if (t < minTime) minTime = t;
                if (t > maxTime) maxTime = t;
                hasData = true;
            }
        }

        for (ObaTripStatus e : mHistory) {
            Double d = ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(e);
            if (d != null) {
                if (d > maxDist) maxDist = d;
                if (d < minDist) minDist = d;
            }
            long t = e.getLastLocationUpdateTime();
            if (t > 0) {
                if (t < minTime) minTime = t;
                if (t > maxTime) maxTime = t;
                hasData = true;
            }
        }

        if (!hasData) {
            mNowLabelPaint.setTextSize(14 * mDensity);
            canvas.drawText("No data available", mMarginLeft + 10 * mDensity,
                    getHeight() / 2f, mNowLabelPaint);
            mNowLabelPaint.setTextSize(10 * mDensity);
            return false;
        }

        if (mCurrentTime < minTime) minTime = mCurrentTime;
        if (mCurrentTime + 60_000 > maxTime) maxTime = mCurrentTime + 60_000;

        double distRange = maxDist - minDist;
        if (distRange < 100) distRange = 100;
        maxDist = minDist + distRange * 1.05;
        minDist = Math.max(0, minDist - distRange * 0.02);

        long timeRange = maxTime - minTime;
        if (timeRange < 60_000) timeRange = 60_000;
        minTime -= timeRange / 20;
        maxTime += timeRange / 20;

        mFullMinDist = minDist;
        mFullMaxDist = maxDist;
        mFullMinTime = minTime;
        mFullMaxTime = maxTime;
        clampOffsets();
        return true;
    }

    private void setupVisibleWindow(float graphW, float graphH) {
        double fullDistRange = mFullMaxDist - mFullMinDist;
        long fullTimeRange = mFullMaxTime - mFullMinTime;

        mGraphW = graphW;
        mGraphH = graphH;
        mVisDistRange = fullDistRange / mScaleX;
        mVisTimeRange = (long) (fullTimeRange / mScaleY);
        mVisMinDist = mFullMinDist + mOffsetDist;
        mVisMinTime = mFullMinTime + mOffsetTime;
    }

    private void drawAxesAndLabels(Canvas canvas) {
        canvas.drawLine(mMarginLeft, mMarginTop, mMarginLeft, getHeight() - mMarginBottom,
                mAxisPaint);
        canvas.drawLine(mMarginLeft, getHeight() - mMarginBottom,
                getWidth() - mMarginRight, getHeight() - mMarginBottom, mAxisPaint);

        long visMaxTime = mVisMinTime + mVisTimeRange;
        long timeStep = Math.max(10_000, (long) niceStep(mVisTimeRange / 5));
        long firstTimeTick = ((mVisMinTime / timeStep) + 1) * timeStep;
        for (long t = firstTimeTick; t < visMaxTime; t += timeStep) {
            float y = toPixelY(t);
            if (y >= mMarginTop && y <= getHeight() - mMarginBottom) {
                mReusableDate.setTime(t);
                canvas.drawText(mTimeFmt.format(mReusableDate),
                        4 * mDensity, y + 4 * mDensity, mLabelPaint);
            }
        }

        double visMaxDist = mVisMinDist + mVisDistRange;
        double distStep = niceStep(mVisDistRange / 5);
        double firstDistTick = Math.ceil(mVisMinDist / distStep) * distStep;
        for (double d = firstDistTick; d < visMaxDist; d += distStep) {
            float x = toPixelX(d);
            if (x >= mMarginLeft && x <= getWidth() - mMarginRight) {
                canvas.drawText(formatDist(d), x - 10 * mDensity,
                        getHeight() - mMarginBottom + 15 * mDensity, mLabelPaint);
            }
        }
    }

    private void drawGridLines(Canvas canvas) {
        long visMaxTime = mVisMinTime + mVisTimeRange;
        long timeStep = Math.max(10_000, (long) niceStep(mVisTimeRange / 5));
        long firstTimeTick = ((mVisMinTime / timeStep) + 1) * timeStep;
        for (long t = firstTimeTick; t < visMaxTime; t += timeStep) {
            canvas.drawLine(mMarginLeft, toPixelY(t), getWidth() - mMarginRight,
                    toPixelY(t), mGridPaint);
        }

        double visMaxDist = mVisMinDist + mVisDistRange;
        double distStep = niceStep(mVisDistRange / 5);
        double firstDistTick = Math.ceil(mVisMinDist / distStep) * distStep;
        for (double d = firstDistTick; d < visMaxDist; d += distStep) {
            float x = toPixelX(d);
            canvas.drawLine(x, mMarginTop, x, getHeight() - mMarginBottom, mGridPaint);
        }
    }

    private void drawScheduleLine(Canvas canvas) {
        if (mSchedule == null || mSchedule.getStopTimes() == null || mServiceDate <= 0) return;
        ObaTripSchedule.StopTime[] stops = mSchedule.getStopTimes();
        if (stops.length == 0) return;

        mSchedulePath.reset();
        boolean first = true;
        for (ObaTripSchedule.StopTime st : stops) {
            float x = toPixelX(st.getDistanceAlongTrip());
            float y = toPixelY(mServiceDate + st.getArrivalTime() * 1000);
            if (first) {
                mSchedulePath.moveTo(x, y);
                first = false;
            } else {
                mSchedulePath.lineTo(x, y);
            }
            float dotRadius = (mHighlightedStopId != null
                    && mHighlightedStopId.equals(st.getStopId()))
                    ? 8 * mDensity : 4 * mDensity;
            canvas.drawCircle(x, y, dotRadius, mScheduleDotPaint);
        }
        canvas.drawPath(mSchedulePath, mSchedulePaint);
    }

    private void drawTrajectoryDots(Canvas canvas) {
        if (mHistory.isEmpty()) return;
        mTrajectoryPath.reset();
        boolean first = true;
        for (ObaTripStatus e : mHistory) {
            Double d = ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(e);
            long t = e.getLastLocationUpdateTime();
            if (d == null || t <= 0) continue;
            float x = toPixelX(d);
            float y = toPixelY(t);
            if (first) {
                mTrajectoryPath.moveTo(x, y);
                first = false;
            } else {
                mTrajectoryPath.lineTo(x, y);
            }
            canvas.drawCircle(x, y, 3 * mDensity, mTrajectoryDotPaint);
        }
        canvas.drawPath(mTrajectoryPath, mTrajectoryPaint);
    }

    private void drawExtrapolationAndDeviation(Canvas canvas, Double lastDist, long lastTime) {
        if (mEstimatedSpeedMps <= 0 || lastDist == null || mCurrentTime <= lastTime) return;

        Double extrapolated = VehicleTrajectoryTrackerKt.extrapolateDistance(
                mHistory, mEstimatedSpeedMps, mCurrentTime);
        double extrapolatedDist = extrapolated != null ? extrapolated : lastDist;
        float x1 = toPixelX(lastDist);
        float y1 = toPixelY(lastTime);
        float x2 = toPixelX(extrapolatedDist);
        float y2 = toPixelY(mCurrentTime);
        canvas.drawLine(x1, y1, x2, y2, mExtrapolatePaint);

        float xAxisY = getHeight() - mMarginBottom;
        canvas.drawLine(x2, y2, x2, xAxisY, mExtrapolateDashPaint);
        canvas.drawText("~" + formatDistPrecise(extrapolatedDist),
                x2 - 10 * mDensity, xAxisY - 5 * mDensity, mExtrapolateLabelPaint);

        long scheduledTime = interpolateScheduleTime(extrapolatedDist);
        if (scheduledTime > 0) {
            float schedY = toPixelY(scheduledTime);
            canvas.drawCircle(x2, schedY, 5 * mDensity, mDeviationDotPaint);
            canvas.drawLine(x2, schedY, x2, y2, mDeviationLinePaint);
            String devLabel = formatDeviationLabel((mCurrentTime - scheduledTime) / 1000);
            float labelX = x2 + 5 * mDensity;
            float labelY = (schedY + y2) / 2 + 4 * mDensity;
            canvas.drawText(devLabel, labelX, labelY, mDeviationLabelPaint);
        }
    }

    private static String formatDeviationLabel(long devSeconds) {
        if (devSeconds == 0) return "on time";
        long absSeconds = Math.abs(devSeconds);
        String magnitude;
        if (absSeconds >= 60) {
            long mins = absSeconds / 60;
            long secs = absSeconds % 60;
            magnitude = mins + "m" + (secs > 0 ? secs + "s" : "");
        } else {
            magnitude = absSeconds + "s";
        }
        return magnitude + (devSeconds > 0 ? " late" : " early");
    }

    private void drawGammaPdfAndBands(Canvas canvas, Double lastDist, long lastTime) {
        if (mDistribution == null || lastDist == null || mCurrentTime <= lastTime) return;
        double dtSec = (mCurrentTime - lastTime) / 1000.0;
        if (dtSec < 1.0) return;

        float xAxisY = getHeight() - mMarginBottom;
        float maxHeightPx = 105 * mDensity;

        double speedLoMps = mDistribution.quantile(0.001);
        double speedHiMps = mDistribution.quantile(0.999);
        double posMin = lastDist + speedLoMps * dtSec;
        double posMax = lastDist + speedHiMps * dtSec;

        if (posMax <= posMin) return;
        double binWidth = (posMax - posMin) / PDF_NUM_BINS;

        double maxVal = 0;
        for (int i = 0; i < PDF_NUM_BINS; i++) {
            double speedMps = ((posMin + (i + 0.5) * binWidth) - lastDist) / dtSec;
            double val = mDistribution.pdf(speedMps);
            mPdfValues[i] = val;
            if (val > maxVal) maxVal = val;
        }

        if (maxVal > 0) {
            mPdfPath.reset();
            mPdfPath.moveTo(toPixelX(posMin), xAxisY);
            for (int i = 0; i < PDF_NUM_BINS; i++) {
                double pos = posMin + (i + 0.5) * binWidth;
                float h = (float) (mPdfValues[i] / maxVal * maxHeightPx);
                mPdfPath.lineTo(toPixelX(pos), xAxisY - h);
            }
            mPdfPath.lineTo(toPixelX(posMax), xAxisY);
            mPdfPath.close();
            canvas.drawPath(mPdfPath, mPdfFillPaint);
        }

        // 80% CI bands using cached quantile results
        float xStart = toPixelX(lastDist);
        float yStart = toPixelY(lastTime);
        float yNow = toPixelY(mCurrentTime);
        canvas.drawLine(xStart, yStart,
                toPixelX(lastDist + mCachedCiLoMps * dtSec), yNow, mConfidenceBandPaint);
        canvas.drawLine(xStart, yStart,
                toPixelX(lastDist + mCachedCiHiMps * dtSec), yNow, mConfidenceBandPaint);
    }

    private void drawNowLine(Canvas canvas) {
        float nowY = toPixelY(mCurrentTime);
        if (nowY >= mMarginTop && nowY <= getHeight() - mMarginBottom) {
            canvas.drawLine(mMarginLeft, nowY, getWidth() - mMarginRight, nowY, mNowLinePaint);
            mReusableDate.setTime(mCurrentTime);
            canvas.drawText("now " + mTimeFmt.format(mReusableDate),
                    mMarginLeft + 5 * mDensity, nowY - 4 * mDensity, mNowLabelPaint);
        }
    }

    private void drawLegend(Canvas canvas) {
        float legendX = mMarginLeft + 10 * mDensity;
        float legendY = mMarginTop + 15 * mDensity;
        canvas.drawLine(legendX, legendY, legendX + 20 * mDensity, legendY, mSchedulePaint);
        canvas.drawText("Schedule", legendX + 25 * mDensity, legendY + 4 * mDensity, mLabelPaint);
        legendY += 18 * mDensity;
        canvas.drawLine(legendX, legendY, legendX + 20 * mDensity, legendY, mTrajectoryPaint);
        canvas.drawCircle(legendX + 10 * mDensity, legendY, 3 * mDensity, mTrajectoryDotPaint);
        canvas.drawText("Actual (GPS)", legendX + 25 * mDensity, legendY + 4 * mDensity, mLabelPaint);
        legendY += 18 * mDensity;
        canvas.drawLine(legendX, legendY, legendX + 20 * mDensity, legendY, mExtrapolateDashPaint);
        canvas.drawText("Estimated", legendX + 25 * mDensity, legendY + 4 * mDensity, mLabelPaint);
        legendY += 18 * mDensity;
        canvas.drawLine(legendX, legendY, legendX + 20 * mDensity, legendY, mConfidenceBandPaint);
        canvas.drawText("80% CI", legendX + 25 * mDensity, legendY + 4 * mDensity, mLabelPaint);
        legendY += 18 * mDensity;
        canvas.drawRect(legendX, legendY - 5 * mDensity, legendX + 20 * mDensity,
                legendY + 5 * mDensity, mPdfFillPaint);
        canvas.drawText("Position PDF", legendX + 25 * mDensity, legendY + 4 * mDensity, mLabelPaint);
    }

    /** Converts a distance-along-trip value to pixel X coordinate. */
    private float toPixelX(double dist) {
        return mMarginLeft + mGraphW * (float) ((dist - mVisMinDist) / mVisDistRange);
    }

    /** Converts a timestamp to pixel Y coordinate. */
    private float toPixelY(long time) {
        return mMarginTop + mGraphH * (1f - (float) (time - mVisMinTime) / mVisTimeRange);
    }

    /**
     * Interpolates the schedule to find the expected time at a given distance along the trip.
     * Returns 0 if the schedule is unavailable or the distance is out of range.
     */
    private long interpolateScheduleTime(double distanceMeters) {
        if (mSchedule == null) return 0;
        ObaTripSchedule.StopTime[] stops = mSchedule.getStopTimes();
        if (stops == null || stops.length < 2) return 0;

        for (int i = 1; i < stops.length; i++) {
            double d0 = stops[i - 1].getDistanceAlongTrip();
            double d1 = stops[i].getDistanceAlongTrip();
            if (distanceMeters >= d0 && distanceMeters <= d1 && d1 > d0) {
                double fraction = (distanceMeters - d0) / (d1 - d0);
                long t0 = mServiceDate + stops[i - 1].getArrivalTime() * 1000L;
                long t1 = mServiceDate + stops[i].getArrivalTime() * 1000L;
                return t0 + (long) (fraction * (t1 - t0));
            }
        }
        return 0;
    }

    private static final double METERS_PER_FOOT = 0.3048;
    private static final double FEET_PER_MILE = 5280;

    private String formatDist(double meters) {
        if (mUseImperial) {
            double feet = meters / METERS_PER_FOOT;
            if (feet >= FEET_PER_MILE) {
                return String.format(Locale.US, "%.1fmi", feet / FEET_PER_MILE);
            }
            return String.format(Locale.US, "%.0fft", feet);
        } else {
            if (meters >= 1000) {
                return String.format(Locale.US, "%.1fkm", meters / 1000.0);
            }
            return String.format(Locale.US, "%.0fm", meters);
        }
    }

    private String formatDistPrecise(double meters) {
        if (mUseImperial) {
            double feet = meters / METERS_PER_FOOT;
            return String.format(Locale.US, "%.0fft", feet);
        } else {
            return String.format(Locale.US, "%.0fm", meters);
        }
    }

    private static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double residual = raw / magnitude;
        if (residual <= 1.5) return magnitude;
        if (residual <= 3.5) return 2 * magnitude;
        if (residual <= 7.5) return 5 * magnitude;
        return 10 * magnitude;
    }
}
