package com.joulespersecond.seattlebusbot;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;

abstract class MySearchActivity extends ListActivity {
    protected boolean mShortcutMode = false;

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

        TextView empty = (TextView)findViewById(android.R.id.empty);
        empty.setMovementMethod(LinkMovementMethod.getInstance());
        setHintText();

        Object obj = getLastNonConfigurationInstance();
        if (obj != null) {
            Object[] config = (Object[])obj;
            TextView textView = (TextView)findViewById(R.id.search_text);
            textView.setText((Editable)config[0]);
            setResponse((ObaResponse)config[1]);
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
                    setHintText();
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

    /**
     * Default implementation for creating a shortcut when in shortcut mode.
     * @param name The name of the shortcut.
     * @param destIntent The destination intent.
     */
    protected final void makeShortcut(String name, Intent destIntent) {
        final Intent shortcut = UIHelp.makeShortcut(this, name, destIntent);

        if (isChild()) {
            // Is there a way to do this more generically?
            final Activity parent = getParent();
            if (parent instanceof MyStopsActivity) {
                MyStopsActivity myStops = (MyStopsActivity)parent;
                myStops.returnShortcut(shortcut);
            }
            else if (parent instanceof MyRoutesActivity) {
                MyRoutesActivity myRoutes = (MyRoutesActivity)parent;
                myRoutes.returnShortcut(shortcut);
            }
        }
        else {
            setResult(RESULT_OK, shortcut);
            finish();
        }
    }

    protected final void setResponse(ObaResponse response) {
        mResponse = response;
        TextView empty = (TextView) findViewById(android.R.id.empty);
        final int code = response.getCode();
        if (code == ObaApi.OBA_OK) {
            empty.setText(R.string.find_hint_noresults);
            setResultsAdapter(response);
        }
        else if (code != 0) {
            // If we get anything other than a '0' error, that means
            // the server actually returned something to us,
            // (even if it was an error) so we shouldn't show
            // a 'communication' error. Just fake no results.
            empty.setText(R.string.find_hint_noresults);
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
     * @return The resource ID of the activity layout.
     */
    abstract protected int getLayoutId();

    /**
     * @return The minimum number of characters that need to be in the find
     * search box before an automatic search is performed.
     */
    abstract protected int getMinSearchLength();

    /**
     * This is called to set the hint text in the R.id.empty view.
     */
    abstract protected void setHintText();

    /**
     * Called when the AsyncTask has produced a non-empty response
     * and you should initialize the list adapter with this response.
     * @param response The response set.
     */
    abstract protected void setResultsAdapter(ObaResponse response);

    /**
     * Called from the AsyncTask thread to perform the search.
     * @param param
     */
    abstract protected ObaResponse doFindInBackground(String param);
}
