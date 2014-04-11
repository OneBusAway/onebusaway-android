package com.google.glass.widget;

import com.andrusiv.google.glass.progressbar.R;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class SliderView extends FrameLayout {

    private static final long HIDE_SLIDER_TIMEOUT_MILLIS = 1000L;

    private static final int MIN_SLIDER_WIDTH_PX = 40;

    private static final long SHOW_HIDE_ANIMATION_DURATION_MILLIS = 300L;

    private static final long SLIDER_BAR_RESIZE_ANIMATION_DURATION_MILLIS = 300L;

    private float animatedCount = 0.0F;

    private int count = 0;

    private ObjectAnimator countAnimator;

    private Runnable hideSliderRunnable = new Runnable() {
        public void run() {
            SliderView.this.hideSlider(true);
        }
    };

    private final ImageView indeterminateSlider;

    private float index = 0.0F;

    private float slideableScale = 1.0F;

    private final View slider;

    private boolean sliderShowing = true;

    private OnAnimateListener mProgressListener;

    private ViewPropertyAnimator mProgressAnimator;

    public SliderView(Context paramContext) {
        this(paramContext, null);
    }

    public SliderView(Context paramContext, AttributeSet paramAttributeSet) {
        this(paramContext, paramAttributeSet, 0);
    }

    public SliderView(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
        LayoutInflater.from(getContext()).inflate(R.layout.slider, this);
        this.slider = findViewById(R.id.slider_control);
        this.indeterminateSlider = ((ImageView) findViewById(R.id.indeterminate_slider));
        hideSlider(false);
        hideIndeterminateSlider(false);
    }

    private void animateCountTo(float paramFloat) {
        if ((this.countAnimator != null) && (this.countAnimator.isRunning())) {
            this.countAnimator.cancel();
        }
        float[] arrayOfFloat = new float[2];
        arrayOfFloat[0] = this.animatedCount;
        arrayOfFloat[1] = paramFloat;
        this.countAnimator = ObjectAnimator.ofFloat(this, "animatedCount", arrayOfFloat);
        this.countAnimator.setDuration(300L);
        this.countAnimator.start();
    }

    private int getBaseSliderWidth() {
        return Math.max((int) (getResources().getDisplayMetrics().widthPixels / this.animatedCount),
                40);
    }

    private void hideIndeterminateSlider(boolean paramBoolean) {
        int i = getResources().getDimensionPixelSize(R.dimen.slider_bar_height);
        if (paramBoolean) {
            this.indeterminateSlider.animate().translationY(i).setDuration(300L);
            return;
        }
        this.indeterminateSlider.setTranslationY(i);
    }

    private void hideSlider(boolean paramBoolean) {
        if (!this.sliderShowing) {
            return;
        }
        int i = getResources().getDimensionPixelSize(R.dimen.slider_bar_height);
        if (paramBoolean) {
            this.slider.animate().translationY(i).setDuration(300L);
        }
        this.sliderShowing = false;
        this.slider.setTranslationY(i);
    }

    private void hideSliderAfterTimeout() {
        removeCallbacks(this.hideSliderRunnable);
        postDelayed(this.hideSliderRunnable, 1000L);
    }

    private void showIndeterminateSlider(boolean paramBoolean) {
        if (paramBoolean) {
            this.indeterminateSlider.animate().translationY(0.0F).setDuration(300L);
            return;
        }
        this.indeterminateSlider.setTranslationY(0.0F);
    }

    private void showSlider(boolean show) {
        removeCallbacks(this.hideSliderRunnable);
        if (this.sliderShowing) {
            return;
        }
        if (show) {
            this.slider.animate().translationY(0.0F).setDuration(300L);
        }
        this.sliderShowing = true;
        this.slider.setTranslationY(0.0F);
    }

    private void updateSliderWidth() {
        if (this.count < 2) {
            hideSlider(true);
            return;
        }
        FrameLayout.LayoutParams localLayoutParams = (FrameLayout.LayoutParams) this.slider
                .getLayoutParams();
        localLayoutParams.width = ((int) (1.0F / this.slideableScale * getBaseSliderWidth()));
        localLayoutParams.leftMargin = 0;
        this.slider.setLayoutParams(localLayoutParams);
        showSlider(true);
        setProportionalIndex(this.index);
    }

    public void dismissManualProgress() {
        hideSlider(true);
    }

    float getAnimatedCount() {
        return this.animatedCount;
    }

    void setAnimatedCount(float paramFloat) {
        this.animatedCount = paramFloat;
        updateSliderWidth();
    }

    public void setCount(int paramInt) {
        hideIndeterminateSlider(true);
        hideSlider(true);
        this.count = paramInt;
        this.index = Math.max(Math.min(this.index, paramInt - 1), 0.0F);
        animateCountTo(paramInt);
    }

    public void setManualProgress(float paramFloat) {
        setManualProgress(paramFloat, false);
    }

    public void setManualProgress(float paramFloat, boolean paramBoolean) {
        hideIndeterminateSlider(true);
        showSlider(false);
        int i = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams localLayoutParams = (FrameLayout.LayoutParams) this.slider
                .getLayoutParams();
        localLayoutParams.width = i;
        localLayoutParams.setMargins(-i, 0, 0, 0);
        this.slider.setLayoutParams(localLayoutParams);
        if (paramBoolean) {
            this.slider.animate().translationX(paramFloat * i);
            return;
        }
        this.slider.setTranslationX(paramFloat * i);
    }

    public void setProportionalIndex(float paramFloat) {
        setProportionalIndex(paramFloat, 0);
    }

    public void setProportionalIndex(float paramFloat, int paramInt) {
        if (this.count < 2) {
            hideSlider(true);
            return;
        }
        this.index = paramFloat;
        float f1 = 1.0F / this.slideableScale;
        float f2 = (0.5F + this.index - f1 / 2.0F) * (getResources().getDisplayMetrics().widthPixels
                / this.count);
        if (paramInt != 0) {
            this.slider.animate().translationX(f2).setDuration(paramInt)
                    .setInterpolator(new AccelerateDecelerateInterpolator());
        }
        showSlider(true);
        hideSliderAfterTimeout();
        this.slider.setTranslationX(f2);
    }

    public void setScale(float paramFloat) {
        this.slideableScale = paramFloat;
        updateSliderWidth();
    }

    public void startIndeterminate() {
        int i = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams localLayoutParams = (FrameLayout.LayoutParams) this.slider
                .getLayoutParams();
        localLayoutParams.width = i;
        localLayoutParams.setMargins(0, 0, 0, 0);
        this.slider.setLayoutParams(localLayoutParams);
        hideSlider(true);
        showIndeterminateSlider(true);
        ((AnimationDrawable) this.indeterminateSlider.getBackground()).start();
    }

    public void startProgress(long millis) {
        startProgress(millis, new AccelerateDecelerateInterpolator());
    }

    public void startProgress(long paramLong, TimeInterpolator paramTimeInterpolator) {
        hideIndeterminateSlider(true);
        showSlider(false);
        int i = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams localLayoutParams = (FrameLayout.LayoutParams) this.slider
                .getLayoutParams();
        localLayoutParams.width = i;
        localLayoutParams.setMargins(-i, 0, 0, 0);
        this.slider.setLayoutParams(localLayoutParams);

        if (mProgressListener != null) {
            mProgressAnimator = this.slider.animate().translationX(i).setDuration(paramLong)
                    .setInterpolator(paramTimeInterpolator)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mProgressListener.onAnimateFinishedListener();
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            mProgressListener.onAnimateCancelledListener();
                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {
                        }
                    });
        } else {
            mProgressAnimator = this.slider.animate().translationX(i).setDuration(paramLong)
                    .setInterpolator(paramTimeInterpolator);
        }
    }

    public void stopIndeterminate() {
        showSlider(true);
        ((AnimationDrawable) this.indeterminateSlider.getBackground()).stop();
        hideIndeterminateSlider(true);
    }

    public void stopProgress() {
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
        }
        hideSlider(true);
    }

    public void setOnAnimateListener(OnAnimateListener listener) {
        this.mProgressListener = listener;
    }

    public interface OnAnimateListener {

        void onAnimateFinishedListener();

        void onAnimateCancelledListener();
    }
}