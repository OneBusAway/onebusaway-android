package com.joulespersecond.seattlebusbot;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.joulespersecond.oba.ObaResponse;

/**
 * This provides a bunch of helper base classes that provide much
 * of the boilerplate functionality of our AsyncTask classes.
 * 
 * @author paulw
 *
 */
final class AsyncTasks {
    private static final String TAG = "AsyncTasks";
    // Uninstantiatable
    private AsyncTasks() { throw new AssertionError(); }

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
        protected final Activity mActivity;
        
        public Base(Activity activity) {
            mActivity = activity;
        }
        @Override
        protected void onPreExecute() {
            mActivity.setProgressBarIndeterminateVisibility(true);
        }
        @Override
        protected void onPostExecute(Result result) {
            doResult(result);
            mActivity.setProgressBarIndeterminateVisibility(false); 
        }
        protected abstract void doResult(Result result);
    }
    

    public static abstract class 
    ToResponseBase<T> extends Base<T,ObaResponse> {
        public ToResponseBase(Activity activity) {
            super(activity);
        }
    }    
    
    /**
     * This is the base class for converting a JSON string to an ObaResponse.
     * Subclasses are expected to override doResult.
     * 
     * @author paulw
     */
    public static abstract class JSONToResponse extends ToResponseBase<String> {
        public JSONToResponse(Activity activity) {
            super(activity);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            try {
                return new ObaResponse(new JSONObject(params[0]));
            } catch (JSONException e) {
                Log.e(TAG, "Expected JSON data, got something else entirely: " + params[0]);
                e.printStackTrace();
                return new ObaResponse("JSON error");
            }
        }
    }
    
    /**
     * This is the base class for converting Bundles to ObaResponses.
     * Subclasses are expected to override doResult.
     * 
     * @author paulw
     */
    public static abstract class BundleToResponse extends ToResponseBase<Bundle> {
        public BundleToResponse(Activity activity) {
            super(activity);
        }
        @Override
        protected ObaResponse doInBackground(Bundle... params) {
            return new ObaResponse(params[0]);
        }
    }
}
