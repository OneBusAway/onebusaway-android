package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class FindStopActivity extends ListActivity {
	//private static final String TAG = "FindStopActivity";
	
	private StopsDbAdapter mDbAdapter;
	private View mListHeader;
	private boolean mShortcutMode = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_stop);
		
		Intent myIntent = getIntent();
		if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
			mShortcutMode = true;
		}
		
		mDbAdapter = new StopsDbAdapter(this);
		mDbAdapter.open();
		
		// Inflate the header
		ListView listView = getListView();
		LayoutInflater inflater = getLayoutInflater();
		mListHeader = inflater.inflate(R.layout.find_stop_header, null);
		listView.addHeaderView(mListHeader);
		
		TextView textView = (TextView)mListHeader.findViewById(R.id.search_text);
		textView.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {	
				if (s.length() >= 5) {
					doSearch(s);
				}
			}
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {			
			}
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {				
			}
			
		});
		// If the user clicks the button (and there's text), the do the search
		Button button = (Button)mListHeader.findViewById(R.id.search);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TextView textView = (TextView)mListHeader.findViewById(R.id.search_text);
				doSearch(textView.getText());			
			}
		});
		
		fillFavorites();
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		super.onDestroy();
	}
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	String stopId;
    	String stopName;
    	// Get the adapter (this may or may not be a SimpleCursorAdapter)
    	HeaderViewListAdapter hdrAdapter = (HeaderViewListAdapter)l.getAdapter();
    	ListAdapter adapter = hdrAdapter.getWrappedAdapter();
    	if (adapter instanceof SimpleCursorAdapter) {
    		// Get the cursor and fetch the stop ID from that.
    		SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
    		Cursor c = cursorAdapter.getCursor();
    		c.moveToPosition(position - l.getHeaderViewsCount());
    		stopId = c.getString(StopsDbAdapter.FAVORITE_COL_STOPID);
    		stopName = c.getString(StopsDbAdapter.FAVORITE_COL_NAME);
    	}
    	else {
    		// Simple adapter, search results
    		return;
    	}

		if (mShortcutMode) {
			makeShortcut(stopId, stopName);
		}
		else {
	    	Intent myIntent = new Intent(this, StopInfoActivity.class);
			myIntent.putExtra(StopInfoActivity.STOP_ID, stopId);
			startActivity(myIntent);			
		}
    }
	
	private void fillFavorites() {
		Cursor c = mDbAdapter.getFavoriteStops();
		startManagingCursor(c);
		
		String[] from = new String[] { 
				DbHelper.KEY_NAME,
				DbHelper.KEY_DIRECTION 
		};
		int[] to = new int[] {
				R.id.name,
				R.id.direction
		};
		SimpleCursorAdapter simpleAdapter = 
			new SimpleCursorAdapter(this, R.layout.find_stop_listitem, c, from, to);
		
		// We need to convert the direction text (N/NW/E/etc)
		// to user level text (North/Northwest/etc..)
		simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (columnIndex == StopsDbAdapter.FAVORITE_COL_DIRECTION) {
					TextView direction = (TextView)view.findViewById(R.id.direction);
					direction.setText(
							StopInfoActivity.getStopDirectionText(cursor.getString(columnIndex)));
					return true;
				} 
				return false;
			}
		});
		setListAdapter(simpleAdapter);
	}
	
	private void makeShortcut(String stopId, String stopName) {
		Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
		shortcutIntent.setClass(this, StopInfoActivity.class);
		shortcutIntent.putExtra(StopInfoActivity.STOP_ID, stopId);
		
		// Set up the container intent
		Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, stopName);
		Parcelable iconResource = Intent.ShortcutIconResource.fromContext(
		        this,  R.drawable.icon);
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

		// Now, return the result to the launcher
		setResult(RESULT_OK, intent);
		finish();
	}
	
	// TODO: We want to search for the stop, but the only API we are given requires
	// a latitude and longitude. This activity itself doesn't appear to depend on 
	// the user's position, so we shouldn't really use it for searching.
	// The OBA website allows you to search for a stop like "11014" without the "1_"
	// agency prefix, and it doesn't know your position. I need to look into the OBA code
	// and see what's going on.
	/*
	private class FindStopTask extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
			mDialog = ProgressDialog.show(
					FindStopActivity.this,
					"",
					getResources().getString(R.string.search_stop_searching),
					true, true);
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			String stopId = params[0];
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {

	    	}
	    	else {
	    		// TODO: Set some form of error message.
	    	}
			if (mDialog != null) {
				mDialog.dismiss();
				mDialog = null;
			}
		}
		private ProgressDialog mDialog;
	}
	*/
	
	private void doSearch(CharSequence text) {
		if (text.length() == 0) {
			return;
		}
		//new FindStopTask().execute(text.toString());
	}
}
