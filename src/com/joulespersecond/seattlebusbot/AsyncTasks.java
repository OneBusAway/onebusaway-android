package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.os.AsyncTask;

import com.joulespersecond.oba.ObaResponse;

/**
 * This provides a bunch of helper base classes that provide much
 * of the boilerplate functionality of our AsyncTask classes.
 * 
 * @author paulw
 *
 */
final class AsyncTasks {
    //private static final String TAG = "AsyncTasks";
    // Uninstantiatable
    private AsyncTasks() { throw new AssertionError(); }

    public interface Progress {
        void showLoading();
        void hideLoading();
    }
    public interface Handler<Result> {
        void handleResult(Result result);
    }
    
    /**
     * Base class for AsyncTask that convert types to ObaResponses
     * Handles the basic task of showing the indeterminate progress bar
     * (a piece of UI logic that makes this unfit for the Oba package,
     * but very useful for us).
     * 
     * Subclasses of this class are expected to implement doResult and
     * in certain cases doInBackground. 
     * 
     * In general, subclasses won't need to override onPreExecute and
     * onPostExecute, but if they do they are expected to call the
     * superclass methods.
     * 
     * @author paulw
     *
     * @param <T> The Input type for AsyncTask.
     */
    public static abstract class
    Base<T,Result> extends AsyncTask<T,Void,Result> {
        protected final Progress mProgress;
        protected final Handler<Result> mHandler;
        
        public Base(Progress progress) {
            assert(progress != null);
            mProgress = progress;
            mHandler = null;
        }
        public Base(Progress progress, Handler<Result> handler) {
            assert(progress != null);
            mProgress = progress;
            mHandler = handler;
        }
        @Override
        protected void onPreExecute() {
            mProgress.showLoading();
        }
        @Override
        protected void onPostExecute(Result result) {
            if (mHandler != null) {
                mHandler.handleResult(result);
            }
            else {
                doResult(result);
            }
            mProgress.hideLoading();
        }
        @Override
        protected void onCancelled() {
            mProgress.hideLoading();
        }
        protected abstract void doResult(Result result);
    }
    

    public static abstract class 
    ToResponseBase<T> extends Base<T,ObaResponse> {
        public ToResponseBase(Progress progress) {
            super(progress);
        }
        public ToResponseBase(Progress progress, Handler<ObaResponse> handler) {
            super(progress, handler);
        }
    }    
    
    /**
     * This is the base class for converting a string to an ObaResponse.
     * Subclasses are expected to override doResult.
     * 
     * @author paulw
     */
    public static abstract class StringToResponse extends ToResponseBase<String> {
        public StringToResponse(Progress progress) {
            super(progress);
        }
        public StringToResponse(Progress progress, Handler<ObaResponse> handler) {
            super(progress, handler);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaResponse.createFromString(params[0]);
        }
    }
    
    /**
     * This is a helper that set the IndeterminateVisibility 
     * on an activity.
     */
    public static final class ProgressIndeterminateVisibility implements Progress {
        final Activity mActivity;
        
        ProgressIndeterminateVisibility(Activity activity) {
            mActivity = activity;
        }
        public void showLoading() {
            mActivity.setProgressBarIndeterminateVisibility(true);       
        }
        public void hideLoading() {
            mActivity.setProgressBarIndeterminateVisibility(false);       
        }
    }
    
    /**
     * This is a helper to tell whether or not a task is finished.
     * It is acceptable to pass null to this, in which case it returns false.
     * @param task The task to check.
     * @return Whether or not this task is currently running
     */
    public static boolean isRunning(AsyncTask<?,?,?> task) {
        return (task != null && task.getStatus() != AsyncTask.Status.FINISHED);
    }
}
