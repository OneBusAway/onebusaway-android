package com.joulespersecond.seattlebusbot;

import java.util.Iterator;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;

public class FindStopActivity extends ListActivity {
	private static final String TAG = "FindStopActivity";
	
	private StopsDbAdapter mDbAdapter;
	private boolean mShortcutMode = false;
	
	private FindStopTask mAsyncTask;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		setContentView(R.layout.find_stop);
		
		Intent myIntent = getIntent();
		if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
			mShortcutMode = true;
		}
		
		if (!mShortcutMode) {
			setTitle(R.string.find_stop_title);
		}
		
		mDbAdapter = new StopsDbAdapter(this);
		mDbAdapter.open();
		
		TextView textView = (TextView)findViewById(R.id.search_text);
		textView.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {	
				if (s.length() >= 5) {
					doSearch(s);
				}
				else if (s.length() == 0) {
					fillFavorites();
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
		Button button = (Button)findViewById(R.id.search);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TextView textView = (TextView)findViewById(R.id.search_text);
				doSearch(textView.getText());			
			}
		});
		
		fillFavorites();
	}
	@Override
	protected void onDestroy() {
		mDbAdapter.close();
		if (mAsyncTask != null) {
			mAsyncTask.cancel(true);
		}
		super.onDestroy();
	}
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	String stopId;
    	String stopName;
    	// Get the adapter (this may or may not be a SimpleCursorAdapter)
    	ListAdapter adapter = l.getAdapter();
    	if (adapter instanceof SimpleCursorAdapter) {
    		// Get the cursor and fetch the stop ID from that.
    		SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)adapter;
    		Cursor c = cursorAdapter.getCursor();
    		c.moveToPosition(position - l.getHeaderViewsCount());
    		stopId = c.getString(StopsDbAdapter.FAVORITE_COL_STOPID);
    		stopName = c.getString(StopsDbAdapter.FAVORITE_COL_NAME);
    	}
    	else if (adapter instanceof SearchResultsListAdapter) {
    		ObaStop stop = (ObaStop)adapter.getItem(position - l.getHeaderViewsCount());
    		stopId = stop.getId();
    		stopName = stop.getName();
    	}
    	else {
    		Log.e(TAG, "Unknown adapter. Giving up!");
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
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.find_options, menu);
    	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.clear_favorites) {
        	StopsDbAdapter.clearFavorites(this);
    		return true;
    	}
    	return false;
    }
	
	private void fillFavorites() {
		// Cancel any current search.
		if (mAsyncTask != null) {
			mAsyncTask.cancel(true);
			mAsyncTask = null;
		}
		
		Cursor c = mDbAdapter.getFavoriteStops();
		startManagingCursor(c);
		
		// Make sure the "empty" text is correct.
		TextView empty = (TextView) findViewById(android.R.id.empty);
		empty.setText(R.string.find_hint_nofavoritestops);
		
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
	
	private class SearchResultsListAdapter extends BaseAdapter {
		private ObaArray mStops;
		
		public SearchResultsListAdapter(ObaResponse response) {
			mStops = response.getData().getStops();
		}
		public int getCount() {
			return mStops.length();
		}
		public Object getItem(int position) {
			return mStops.getStop(position);
		}
		public long getItemId(int position) {
			return position;
		}
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewGroup newView;
			if (convertView == null) {
				LayoutInflater inflater = getLayoutInflater();
				newView = (ViewGroup)inflater.inflate(R.layout.find_stop_listitem, null);
			}
			else {
				newView = (ViewGroup)convertView;
			}
			setData(newView, position);
			return newView;
		}
		public boolean hasStableIds() {
			return false;
		}
		
		private void setData(ViewGroup view, int position) {
			TextView route = (TextView)view.findViewById(R.id.name);
			TextView direction = (TextView)view.findViewById(R.id.direction);

			ObaStop stop = mStops.getStop(position);
			route.setText(stop.getName());
			direction.setText(StopInfoActivity.getStopDirectionText(stop.getDirection()));
		}
	}
	
	private class FindStopTask extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	    	setProgressBarIndeterminateVisibility(true);
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			String stopId = params[0];
			return ObaApi.getStopsByLocation(
					getLocation(FindStopActivity.this), 0, 0, 0, stopId, 0);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
    		TextView empty = (TextView) findViewById(android.R.id.empty);
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		empty.setText(R.string.find_hint_noresults);
	    		setListAdapter(new SearchResultsListAdapter(result));
	    	}
	    	else {
	    		empty.setText(R.string.generic_comm_error);
	    	}
			setProgressBarIndeterminateVisibility(false);
		}
		@Override
		protected void onCancelled() {	
			setProgressBarIndeterminateVisibility(false);
		}
	}
	
	private void doSearch(CharSequence text) {
		if (text.length() == 0) {
			return;
		}
		if (mAsyncTask != null) {
			// Try to cancel it
			mAsyncTask.cancel(true);
		}
		mAsyncTask = new FindStopTask();
		mAsyncTask.execute(text.toString());
	}
	
	// We need to provide the API for a location used to disambiguate
	// stop IDs in case of collision, or to provide multiple results
	// in the case multiple agencies. But we really don't need it to be very accurate.
	public static GeoPoint getLocation(Context cxt) {
		LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
		List<String> providers = mgr.getProviders(true);
		Location last = null;
		for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
			Location loc = mgr.getLastKnownLocation(i.next());
			// If this provider has a last location, and either:
			// 1. We don't have a last location, 
			// 2. Our last location is older than this location.
			if (loc != null &&
				(last == null || loc.getTime() > last.getTime())) {
				last = loc;
			}
		}
		if (last != null) {
			return ObaApi.makeGeoPoint(last.getLatitude(), last.getLongitude());			
		}
		else {
			// Make up a fake "Seattle" location.
			// ll=47.620975,-122.347355
			return ObaApi.makeGeoPoint(47.620975, -122.347355);
		}
	}
}
