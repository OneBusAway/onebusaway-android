package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.widget.SimpleCursorAdapter;

abstract class MyBaseListActivity extends ListActivity {
    private final Handler mHandler = new Handler();
    private class MyObserver extends ContentObserver {
        public MyObserver() {
            super(mHandler);
        }
        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
        public void onChange(boolean selfChange) {
            SimpleCursorAdapter adapter = (SimpleCursorAdapter)getListAdapter();
            adapter.getCursor().requery();
        }
    }

    protected boolean mShortcutMode;
    private MyObserver mObserver;

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

        initList(getCursor());
        Uri uri = getObserverUri();
        if (uri != null) {
            ContentResolver cr = getContentResolver();
            mObserver = new MyObserver();
            cr.registerContentObserver(uri, true, mObserver);
        }
    }
    @Override
    public void onDestroy() {
        if (mObserver != null) {
            ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(mObserver);
        }
        super.onDestroy();
    }

    /**
     * Initializes the list with a cursor
     */
    abstract void initList(Cursor c);
    /**
     * @return The cursor of the data to be managed.
     */
    abstract Cursor getCursor();
    /**
     * @return The layout ID of the activity.
     */
    abstract int getLayoutId();
    /**
     * @return The Uri to observe for changes
     */
    abstract Uri getObserverUri();
}
