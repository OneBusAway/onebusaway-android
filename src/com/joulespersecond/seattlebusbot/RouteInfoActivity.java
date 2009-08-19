package com.joulespersecond.seattlebusbot;

import org.json.JSONArray;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class RouteInfoActivity extends ListActivity {
	//private static final String TAG = "RouteInfoActivity";
	private static final int LOADING_DIALOG_KEY = 0;
	private static final int LOADING_DIALOG2_KEY = 1;
	
	public static final String ROUTE_ID = ".RouteId";

	private RouteInfoListAdapter mAdapter;
	private View mListHeader;
	private String mRouteId;
	
	private class RouteInfoListAdapter extends BaseAdapter {
		private ObaArray mStops;
		
		public RouteInfoListAdapter() {
			mStops = new ObaArray(new JSONArray());
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
			View newView;
			if (convertView == null) {
				LayoutInflater inflater = getLayoutInflater();
				newView = inflater.inflate(R.layout.route_info_listitem, null);
			}
			else {
				newView = convertView;
			}
			setData(newView, position);
			return newView;
		}
		public boolean hasStableIds() {
			return false;
		}
		
		public void setData(ObaResponse response) {
			mStops = response.getData().getStops();
			notifyDataSetChanged();
		}
		private void setData(View view, int position) {
			TextView name = (TextView)view.findViewById(R.id.name);
			TextView direction = (TextView)view.findViewById(R.id.direction);
			
			ObaStop stop = mStops.getStop(position);
			name.setText(stop.getName());
			direction.setText(StopInfoActivity.getStopDirectionText(stop.getDirection()));		
		}

	}	
	
	private class GetRouteInfoTask extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	        showDialog(LOADING_DIALOG_KEY);
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			return ObaApi.getRouteById(params[0]);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		ObaRoute stop = result.getData().getThisRoute();
	    		TextView shortName = (TextView)mListHeader.findViewById(R.id.short_name);
	    		TextView longName = (TextView)mListHeader.findViewById(R.id.long_name);
	    		TextView agency = (TextView)mListHeader.findViewById(R.id.agency);
	    	
	    		shortName.setText(stop.getShortName());
	    		longName.setText(stop.getLongName());
	    		agency.setText(stop.getAgencyName());
	    	}
	    	else {
	    		// TODO: Show some error text in the "empty" field.
	    	}
	        dismissDialog(LOADING_DIALOG_KEY);
		}
	}
	
	private class GetStopsForRouteTask extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	        showDialog(LOADING_DIALOG2_KEY);
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			return ObaApi.getStopsForRoute(params[0]);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		mAdapter.setData(result);
	    	} else {
	    		// TODO: Show some error text in the "empty" field.
	    	}
	        dismissDialog(LOADING_DIALOG2_KEY);		
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.route_info);
		ListView listView = getListView();
		LayoutInflater inflater = getLayoutInflater();
		mListHeader = inflater.inflate(R.layout.route_info_header, null);
		listView.addHeaderView(mListHeader);
		
		mAdapter = new RouteInfoListAdapter();
		setListAdapter(mAdapter);
		
		Bundle bundle = getIntent().getExtras();
		mRouteId = bundle.getString(ROUTE_ID);
		new GetRouteInfoTask().execute(mRouteId);
		new GetStopsForRouteTask().execute(mRouteId);
	}
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.route_info_options, menu);
    	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.show_on_map) {
    		return true;
    	}
    	return false;
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	// Go to the Stop Information Activity
    	ObaStop stop = (ObaStop)getListView().getItemAtPosition(position);
		Intent myIntent = new Intent(this, StopInfoActivity.class);
		myIntent.putExtra(StopInfoActivity.STOP_ID, stop.getId());
		startActivity(myIntent);
    }
    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id) {
    	case LOADING_DIALOG_KEY:
    	case LOADING_DIALOG2_KEY:
    		ProgressDialog dialog = new ProgressDialog(this);
    		dialog.setMessage(getResources().getString(R.string.route_info_loading));
    		dialog.setIndeterminate(true);
    		dialog.setCancelable(true);
    		return dialog;
    	}
    	return null;
    }
}
