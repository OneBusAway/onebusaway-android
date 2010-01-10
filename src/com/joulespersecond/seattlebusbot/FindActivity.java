package com.joulespersecond.seattlebusbot;

import java.util.Timer;
import java.util.TimerTask;

import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;

abstract class FindActivity extends ListActivity {
    private boolean mShortcutMode = false;
    
    private ObaResponse mResponse;
    private FindTask mAsyncTask;
    private Timer mSearchTimer;
    
    private static final int DELAYED_SEARCH_TIMEOUT = 1000;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        setContentView(getLayoutId());
        registerForContextMenu(getListView());
        
        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            mShortcutMode = true;
        }
        
        setTitle(getTitleId());
        
        TextView empty = (TextView)findViewById(android.R.id.empty);
        empty.setMovementMethod(LinkMovementMethod.getInstance());
        setNoFavoriteText();
        
        openDB();
                
        Object obj = getLastNonConfigurationInstance();
        if (obj != null) {
            Object[] config = (Object[])obj;
            TextView textView = (TextView)findViewById(R.id.search_text);
            textView.setText((Editable)config[0]);
            setResponse((ObaResponse)config[1]);
        }
        else {
            fillFavorites();
        }
        
        // If the user clicks the button (and there's text), the do the search
        Button button = (Button)findViewById(R.id.search);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView textView = (TextView)findViewById(R.id.search_text);
                doSearch(textView.getText());            
            }
        });
    }
    @Override
    protected void onDestroy() {
        closeDB();
        cancelDelayedSearch();
        cancelSearch();
        super.onDestroy();
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mResponse != null) {
            EditText text = (EditText)findViewById(R.id.search_text);
            return new Object[] { text.getText(), mResponse };
        }
        else {
            return null;
        }
    }
    @Override
    protected void onResume() {
        // This is deferred to onResume because we want it after
        // onRestoreInstanceState.
        
        TextView textView = (TextView)findViewById(R.id.search_text);
        textView.addTextChangedListener(new TextWatcher() {
            private final int mMinSearch = getMinSearchLength();
            
            public void afterTextChanged(Editable s) {
                if (s.length() >= mMinSearch) {
                    doDelayedSearch(s);
                }
                else if (s.length() == 0) {
                    cancelDelayedSearch();
                    cancelSearch();
                    setNoFavoriteText();
                    fillFavorites();
                    mResponse = null;
                }
            }
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {      
            }
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {                
            }
        });  
        super.onResume();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.find_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_favorites) {
            clearFavorites();
            requery();
            return true;
        }
        return false;
    }
    
    /**
     * Cancels any current search.
     */
    protected void cancelSearch() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
            mAsyncTask = null;
        }        
    }
    protected void cancelDelayedSearch() {
        if (mSearchTimer != null) {
            mSearchTimer.cancel();
            mSearchTimer = null;
        }
    }
    
    protected void requery() {
        ListAdapter adapter = getListView().getAdapter();
        if (adapter instanceof SimpleCursorAdapter) {
            ((SimpleCursorAdapter)adapter).getCursor().requery();
        }        
    }
    
    /**
     * Default implementation for creating a shortcut when in shortcut mode.
     * @param name The name of the shortcut.
     * @param destIntent The destination intent.
     */
    protected final void makeShortcut(String name, Intent destIntent) {
        // Set up the container intent
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, destIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
                this,  R.drawable.icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher
        setResult(RESULT_OK, intent);
        finish();
    }
    
    protected final void setResponse(ObaResponse response) {
        mResponse = response;
        TextView empty = (TextView) findViewById(android.R.id.empty);
        if (response.getCode() == ObaApi.OBA_OK) {
            empty.setText(R.string.find_hint_noresults);
            setResultsAdapter(response);
        }
        else {
            empty.setText(R.string.generic_comm_error);
        }  
    }
    
    protected final AsyncTask<?,?,?> getAsyncTask() {
        return mAsyncTask;
    }
    protected final ObaResponse getResponse() {
        return mResponse;
    }
    protected final boolean isShortcutMode() {
        return mShortcutMode;
    }
    
    /**
     * Called when you should perform the search.
     * @param str
     */
    protected void doSearch(CharSequence str) {
        if (str.length() == 0) {
            return;
        }
        cancelDelayedSearch();
        if (!AsyncTasks.isRunning(mAsyncTask)) {
            mAsyncTask = new FindTask();
            mAsyncTask.execute(str.toString());
        }
    }
    
    protected void doDelayedSearch(CharSequence str) {
        cancelDelayedSearch();
        if (str.length() == 0) {
            return;
        }
        final String text = new String(str.toString());
        final Runnable doSearch = new Runnable() {
            public void run() {
                doSearch(text);
           }            
        };
        mSearchTimer = new Timer();
        mSearchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mSearchHandler.post(doSearch);
            }
        }, DELAYED_SEARCH_TIMEOUT);
    }
    final Handler mSearchHandler = new Handler();
    
    protected final AsyncTasks.Progress mTitleProgress 
        = new AsyncTasks.ProgressIndeterminateVisibility(this);
    
    protected class FindTask extends AsyncTasks.StringToResponse {
        public FindTask() {
            super(mTitleProgress);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return doFindInBackground(params[0]);
        }
        @Override
        protected void doResult(ObaResponse result) {
            setResponse(result);
        }
    }
    
    /**
     * @return The resource ID of the activity title.
     */
    abstract protected int getTitleId();
    /**
     * @return The resource ID of the activity layout.
     */
    abstract protected int getLayoutId();
    
    /**
     * @return The minimum number of characters that need to be in the find
     * search box before an automatic search is performed.
     */
    abstract protected int getMinSearchLength();
    
    /**
     * This is called to set the no favorite text in the R.id.empty view.
     */
    abstract protected void setNoFavoriteText();
    
    /**
     * Called when the DB should be opened.
     */
    abstract protected void openDB();
    /**
     * Called when the DB should be closed.
     */
    abstract protected void closeDB();
    
    /**
     * Called when the AsyncTask has produced a non-empty response
     * and you should initialize the list adapter with this response.
     * @param response The response set.
     */
    abstract protected void setResultsAdapter(ObaResponse response);
    
    /**
     * Called when you need to fill the list with the favorites list.
     */
    abstract protected void fillFavorites();
    /**
     * Called when you should delete all favorites from the database.
     */
    abstract protected void clearFavorites();
    

    /**
     * Called from the AsyncTask thread to perform the search.
     * @param param
     */
    abstract protected ObaResponse doFindInBackground(String param);
}
