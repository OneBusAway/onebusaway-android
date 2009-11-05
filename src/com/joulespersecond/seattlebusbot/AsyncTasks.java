package com.joulespersecond.seattlebusbot;

import android.os.AsyncTask;
import android.os.Bundle;

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
        
        public Base(Progress progress) {
            mProgress = progress;
        }
        @Override
        protected void onPreExecute() {
            mProgress.showLoading();
        }
        @Override
        protected void onPostExecute(Result result) {
            doResult(result);
            mProgress.hideLoading();
        }
        protected abstract void doResult(Result result);
    }
    

    public static abstract class 
    ToResponseBase<T> extends Base<T,ObaResponse> {
        public ToResponseBase(Progress progress) {
            super(progress);
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
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaResponse.createFromString(params[0]);
        }
    }
    
    /**
     * This is the base class for converting Bundles to ObaResponses.
     * Subclasses are expected to override doResult.
     * 
     * @author paulw
     */
    public static abstract class BundleToResponse extends ToResponseBase<Bundle> {
        public BundleToResponse(Progress progress) {
            super(progress);
        }
        @Override
        protected ObaResponse doInBackground(Bundle... params) {
            return ObaResponse.createFromBundle(params[0]);
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
