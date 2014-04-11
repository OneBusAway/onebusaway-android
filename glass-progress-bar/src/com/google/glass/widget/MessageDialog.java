package com.google.glass.widget;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import com.andrusiv.google.glass.progressbar.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public final class MessageDialog extends Dialog {

    // States
    private static final int MSG_DISMISS = 0;

    private static final int MSG_TEMPORARY_MESSAGE_DONE = 1;

    private static final int MSG_ON_DONE = 2;

    private static final long EXPANDED_MESSAGE_DURATION = 5000L;

    private static final long MESSAGE_DURATION = 2500L;

    private static final String TAG = MessageDialog.class.getSimpleName();

    // Shown, when progress bar is switched on.
    private static final long TEMPORARY_MESSAGE_DURATION = 5000L;

    private final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                default:
                case MSG_DISMISS:
                    MessageDialog.this.dismiss();
                    break;
                case MSG_TEMPORARY_MESSAGE_DONE:
                    MessageDialog.this.showNormalContent();
                    break;
                case MSG_ON_DONE:
                    if (MessageDialog.this.params.listener != null) {
                        if (msg.arg1 == 1 && msg.arg2 == 1) {
                            MessageDialog.this.params.listener.onDismissed();
                        } else if (msg.arg1 == 0 && msg.arg2 == 0) {
                            MessageDialog.this.params.listener.onDone();
                        }
                    }
                    break;
            }
        }
    };

    private final Params params;

    // private final SafeBroadcastReceiver screenOffReceiver = new
    // SafeBroadcastReceiver() {
    // protected String getTag() {
    // return MessageDialog.TAG + "/screenOffReceiver";
    // }
    //
    // public void onReceiveInternal(Context paramAnonymousContext,
    // Intent paramAnonymousIntent) {
    // if ("android.intent.action.SCREEN_OFF".equals(paramAnonymousIntent
    // .getAction()))
    // MessageDialog.this.onScreenOff();
    // }
    // };
    // private final TouchDetector touchDetector;
    private GestureDetector mGestureDetector;

    private MessageDialog(Context paramContext, Params paramParams) {
        super(paramContext, getThemeId(paramParams.shouldAnimate));
        this.params = paramParams;
        this.mGestureDetector = createGestureDetector(paramContext);
        setContentView(getLayoutId(paramParams.isExpanded));
        findViewById(R.id.ms_dialog).setKeepScreenOn(
                paramParams.shouldKeepScreenOn);
    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        // Create a base listener for generic gestures
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    MessageDialog.this.dismiss();
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    // do something on two tap
                    return true;
                } else if (gesture == Gesture.SWIPE_RIGHT) {
                    // do something on right (forward) swipe
                    return true;
                } else if (gesture == Gesture.SWIPE_LEFT) {
                    // do something on left (backwards) swipe
                    return true;
                }
                return false;
            }
        });
        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
            @Override
            public void onFingerCountChanged(int previousCount, int currentCount) {

            }
        });
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta,
                    float velocity) {
                // do something on scrolling
                return false;
            }
        });
        return gestureDetector;
    }

    private void cancelTransitions() {
        this.handler.removeMessages(MSG_DISMISS);
        this.handler.removeMessages(MSG_TEMPORARY_MESSAGE_DONE);
    }

    private void checkIsShowing() {
        if (!isShowing()) {
            throw new IllegalStateException(
                    "Method not available when the dialog is not showing.");
        }
    }

    private ImageView getIcon() {
        return (ImageView) findViewById(R.id.icon);
    }

    private TextView getLabel() {
        return (TextView) findViewById(R.id.label);
    }

    private static int getLayoutId(boolean paramBoolean) {
        if (paramBoolean) {
            return R.layout.message_dialog_expanded;
        }
        return R.layout.message_dialog;
    }

    private TextView getSecondaryLabel() {
        return (TextView) findViewById(R.id.secondary_label);
    }

    private SliderView getSlider() {
        return (SliderView) findViewById(R.id.slider);
    }

    private static int getThemeId(boolean paramBoolean) {
        if (paramBoolean) {
            return R.style.ContextualDialogTheme;
        }
        return R.style.ContextualDialogTheme_NoAnimation;
    }

    private boolean hasSlider() {
        return getSlider() != null;
    }

    private void setContent(CharSequence paramCharSequence1,
            CharSequence paramCharSequence2, Drawable paramDrawable) {
        if (paramCharSequence1 != null) {
            getLabel().setText(paramCharSequence1);
            getLabel().setVisibility(View.VISIBLE);
        } else {
            getLabel().setVisibility(View.GONE);
        }

        if (paramCharSequence2 != null) {
            getSecondaryLabel().setText(paramCharSequence2);
            getSecondaryLabel().setVisibility(View.VISIBLE);
        } else {
            getSecondaryLabel().setVisibility(View.GONE);
        }

        if (paramDrawable != null) {
            getIcon().setImageDrawable(paramDrawable);
            getIcon().setVisibility(View.VISIBLE);
        } else {
            getIcon().setVisibility(View.GONE);
        }
    }

    private void showNormalContent() {
        play(DialogSound.SUCCESS);
        // if ((this.params.sound != null) && (this.params.soundManager !=
        // null))
        // this.params.soundManager.playSound(this.params.sound);
        setContent(this.params.message, this.params.secondaryMessage,
                this.params.icon);
        if (hasSlider()) {
            if (this.params.shouldShowProgress) {
                getSlider().setVisibility(View.VISIBLE);
                getSlider().startIndeterminate();
            }
        }
        if (this.params.shouldAutoHide) {
            if (!this.params.isExpanded) {
                this.handler.sendMessageDelayed(
                        Message.obtain(this.handler, MSG_DISMISS), MESSAGE_DURATION);
            }
            getSlider().setVisibility(View.GONE);
        }
    }

    private void showTemporaryContent() {
        setContent(this.params.temporaryMessage,
                this.params.temporarySecondaryMessage,
                this.params.temporaryIcon);
        if (hasSlider()) {
            getSlider().setVisibility(View.VISIBLE);
            getSlider().startProgress(EXPANDED_MESSAGE_DURATION);
        }
        this.handler
                .sendMessageDelayed(Message.obtain(this.handler,
                        MSG_TEMPORARY_MESSAGE_DONE), TEMPORARY_MESSAGE_DURATION);
    }

    public void autoHide() {
        checkIsShowing();
        this.handler.sendMessageDelayed(
                Message.obtain(this.handler, MSG_DISMISS), MESSAGE_DURATION);
    }

    public void cancel() {
        // GlassApplication.from(getContext()).getSoundManager()
        // .playSound(SoundManager.SoundId.DISMISS);
        play(DialogSound.DISMISS);

        if (this.handler.hasMessages(MSG_TEMPORARY_MESSAGE_DONE)) {
            cancelTransitions();
            this.handler.sendMessage(Message.obtain(this.handler, MSG_ON_DONE,
                    1, 1));
            super.dismiss();
        }
    }

    public void clearAutoHide() {
        checkIsShowing();
        this.handler.removeMessages(MSG_DISMISS);
    }

    public void dismiss() {
        cancelTransitions();
        this.handler.sendMessage(Message
                .obtain(this.handler, MSG_ON_DONE, 0, 0));
        super.dismiss();
    }

    // public boolean onCameraButtonPressed() {
    // return false;
    // }

    public boolean onConfirm() {
        if (this.params.shouldHandleConfirm) {
            if (!this.handler.hasMessages(MSG_TEMPORARY_MESSAGE_DONE)
                    && (!TextUtils.isEmpty(this.params.temporaryMessage))) {
                Log.d(TAG,
                        "Temporary message has completed, onDone will be called to listener, do not send onConfirm.");
            }
            if (this.params.listener == null || !this.params.listener.onConfirmed()) {
                cancelTransitions();
            }
            super.dismiss();
            return true;
        }
        if (this.handler.hasMessages(MSG_TEMPORARY_MESSAGE_DONE)) {
            this.handler.removeMessages(MSG_TEMPORARY_MESSAGE_DONE);
            this.handler.sendMessage(Message.obtain(this.handler,
                    MSG_TEMPORARY_MESSAGE_DONE));
        }
        return false;
    }

    public void onDetachedFromWindow() {
        cancelTransitions();
        super.onDetachedFromWindow();
    }

    // public boolean onDismiss(InputListener.DismissAction paramDismissAction)
    // {
    // return false;
    // }
    //
    // public boolean onDoubleTap() {
    // return false;
    // }
    //
    // public boolean onFingerCountChanged(int paramInt, boolean paramBoolean) {
    // return false;
    // }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            mGestureDetector.onMotionEvent(event);
        }
        return true;
    }

    // public boolean onPrepareSwipe(int paramInt1, float paramFloat1,
    // float paramFloat2, float paramFloat3, float paramFloat4,
    // int paramInt2, int paramInt3) {
    // return false;
    // }

    void onScreenOff() {
        if (isShowing()) {
            Log.d(TAG, "Cancelling for screen off event.");
            cancel();
        }
    }

    protected void onStart() {
        super.onStart();
        setCancelable(this.params.isDismissable);
        if ((this.params.hasTemporaryContent()) && (!this.params.isExpanded)) {
            showTemporaryContent();
        } else {
            showNormalContent();
        }
        // while (true) {
        // this.screenOffReceiver.register(getContext(),
        // new String[] { "android.intent.action.SCREEN_OFF" });
        // return;
        // showNormalContent();
        // }
    }

    protected void onStop() {
        // this.screenOffReceiver.unregister(getContext());
        super.onStop();
    }

    // public boolean onSwipe(int paramInt, SwipeDirection paramSwipeDirection)
    // {
    // return false;
    // }
    //
    // public boolean onSwipeCanceled(int paramInt) {
    // return false;
    // }

    public boolean onTouchEvent(MotionEvent paramMotionEvent) {
        return onGenericMotionEvent(paramMotionEvent);
    }

    // public boolean onVerticalHeadScroll(float paramFloat1, float paramFloat2)
    // {
    // return false;
    // }

    public void setDismissable(boolean paramBoolean) {
        setCancelable(paramBoolean);
    }

    public void setListener(Listener paramListener) {
        this.params.listener = paramListener;// Params.access$202(this.params,
        // paramListener);
    }

    public void showProgress(boolean paramBoolean) {
        if (hasSlider()) {
            if (paramBoolean) {
                getSlider().setVisibility(View.VISIBLE);
                getSlider().startIndeterminate();
            }
        } else {
            return;
        }
        getSlider().setVisibility(View.GONE);
    }

    public void updateContent(int paramInt1, int paramInt2) {
        checkIsShowing();
        updateContent(getContext().getResources().getString(paramInt1),
                paramInt2);
    }

    public void updateContent(CharSequence paramCharSequence, int paramInt) {
        checkIsShowing();
        setContent(paramCharSequence, null, getContext().getResources()
                .getDrawable(paramInt));
    }

    public static final class Builder {

        private final Context context;

        private MessageDialog.Params params;

        public Builder(Context paramContext) {
            this.context = paramContext;
            reset();
        }

        private void reset() {
            this.params = new MessageDialog.Params();
            this.params.shouldAutoHide = true;// MessageDialog.Params.access$1702(this.params,
            // true);
            this.params.shouldHandleConfirm = false;// MessageDialog.Params.access$1802(this.params,
            // false);
            this.params.shouldAnimate = true;// MessageDialog.Params.access$402(this.params,
            // true);
            this.params.isDismissable = true;// MessageDialog.Params.access$702(this.params,
            // true);
        }

        public MessageDialog build() {
            MessageDialog.Params localParams = this.params;
            reset();
            return new MessageDialog(this.context, localParams);
        }

        public Builder setAnimated(boolean paramBoolean) {
            this.params.shouldAnimate = paramBoolean;// MessageDialog.Params.access$402(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setAutoHide(boolean paramBoolean) {
            this.params.shouldAutoHide
                    = paramBoolean;// MessageDialog.Params.access$1702(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setDismissable(boolean paramBoolean) {
            this.params.isDismissable = paramBoolean;// MessageDialog.Params.access$702(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setExpanded(boolean paramBoolean) {
            this.params.isExpanded = paramBoolean;// MessageDialog.Params.access$502(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setHandleConfirm(boolean paramBoolean) {
            this.params.shouldHandleConfirm
                    = paramBoolean;// MessageDialog.Params.access$1802(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setIcon(int paramInt) {
            return setIcon(this.context.getResources().getDrawable(paramInt));
        }

        public Builder setIcon(Drawable paramDrawable) {
            this.params.icon = paramDrawable;// MessageDialog.Params.access$1502(this.params,
            // paramDrawable);
            return this;
        }

        public Builder setKeepScreenOn(boolean paramBoolean) {
            this.params.shouldKeepScreenOn
                    = paramBoolean;// MessageDialog.Params.access$602(this.params,
            // paramBoolean);
            return this;
        }

        public Builder setListener(MessageDialog.Listener paramListener) {
            this.params.listener = paramListener;// MessageDialog.Params.access$202(this.params,
            // paramListener);
            return this;
        }

        public Builder setMessage(int paramInt) {
            return setMessage(this.context.getResources().getText(paramInt));
        }

        public Builder setMessage(CharSequence paramCharSequence) {
            this.params.message = paramCharSequence;// MessageDialog.Params.access$1302(this.params,
            // paramCharSequence);
            return this;
        }

        public Builder setSecondaryMessage(int paramInt) {
            return setSecondaryMessage(this.context.getResources().getText(
                    paramInt));
        }

        public Builder setSecondaryMessage(CharSequence paramCharSequence) {
            this.params.secondaryMessage
                    = paramCharSequence;// MessageDialog.Params.access$1402(this.params,
            // paramCharSequence);
            return this;
        }

        public Builder setShowProgress(boolean paramBoolean) {
            this.params.shouldShowProgress
                    = paramBoolean;// MessageDialog.Params.access$1602(this.params,
            // paramBoolean);
            return this;
        }

        // public Builder setSound(SoundManager.SoundId paramSoundId,
        // SoundManager paramSoundManager) {
        // MessageDialog.Params.access$1102(this.params, paramSoundId);
        // MessageDialog.Params.access$1202(this.params, paramSoundManager);
        // return this;
        // }

        public Builder setTemporaryIcon(int paramInt) {
            return setTemporaryIcon(this.context.getResources().getDrawable(
                    paramInt));
        }

        public Builder setTemporaryIcon(Drawable paramDrawable) {
            this.params.temporaryIcon
                    = paramDrawable;// MessageDialog.Params.access$1002(this.params,
            // paramDrawable);
            return this;
        }

        public Builder setTemporaryMessage(int paramInt) {
            return setTemporaryMessage(this.context.getResources().getText(
                    paramInt));
        }

        public Builder setTemporaryMessage(CharSequence paramCharSequence) {
            this.params.temporaryMessage
                    = paramCharSequence;// MessageDialog.Params.access$802(this.params,
            // paramCharSequence);
            return this;
        }

        public Builder setTemporarySecondaryMessage(int paramInt) {
            return setTemporarySecondaryMessage(this.context.getResources()
                    .getText(paramInt));
        }

        public Builder setTemporarySecondaryMessage(
                CharSequence paramCharSequence) {
            this.params.temporarySecondaryMessage
                    = paramCharSequence;// MessageDialog.Params.access$902(this.params,
            // paramCharSequence);
            return this;
        }
    }

    public static interface Listener {

        public abstract boolean onConfirmed();

        public abstract void onDismissed();

        public abstract void onDone();
    }

    private static class Params {

        private Drawable icon;

        private boolean isDismissable;

        private boolean isExpanded;

        private MessageDialog.Listener listener;

        private CharSequence message;

        private CharSequence secondaryMessage;

        private boolean shouldAnimate;

        private boolean shouldAutoHide;

        private boolean shouldHandleConfirm;

        private boolean shouldKeepScreenOn;

        private boolean shouldShowProgress;

        // private int sound;
        private Drawable temporaryIcon;

        private CharSequence temporaryMessage;

        private CharSequence temporarySecondaryMessage;

        boolean hasTemporaryContent() {
            return (this.temporaryMessage != null)
                    || (this.temporaryIcon != null);
        }
    }

    private static enum DialogSound {
        DISMISS, SUCCESS, ERROR
    }

    private MediaPlayer lastPlayer = null;

    private void play(DialogSound sound) {
        int id = R.raw.sound_success;
        switch (sound) {
            case DISMISS:
                id = R.raw.sound_dismiss;
                break;
            case SUCCESS:
                id = R.raw.sound_success;
                break;
            case ERROR:
                id = R.raw.sound_error;
                break;
        }
        if (lastPlayer != null) {
            lastPlayer.release();
        }
        MediaPlayer mediaPlayer = MediaPlayer.create(getContext(), id);
        mediaPlayer.setLooping(false);
        mediaPlayer.start();
        lastPlayer = mediaPlayer;
    }

    public static class SimpleListener implements MessageDialog.Listener {

        public boolean onConfirmed() {
            return false;
        }

        public void onDismissed() {
        }

        public void onDone() {
        }
    }
}