package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Window;
import android.widget.SimpleCursorAdapter;

abstract class MyBaseListActivity extends ListActivity {
    protected boolean mShortcutMode;

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
    }
    protected void requery() {
        SimpleCursorAdapter adapter = (SimpleCursorAdapter)getListAdapter();
        adapter.getCursor().requery();
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
}
