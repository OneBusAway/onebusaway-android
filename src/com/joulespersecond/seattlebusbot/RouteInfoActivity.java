package com.joulespersecond.seattlebusbot;

import org.json.JSONArray;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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
	public static final String ROUTE_ID = ".RouteId";

	private RouteInfoListAdapter mAdapter;
	private View mListHeader;
	private String mRouteId;
	// This is saved for the "Show On Map" option -- 
	// As a string since converting the data from an ObaResponse
	// back to a string is expensive.
	private String mStopsResponse;
	
	private GetRouteInfoTask mAsyncTask;
	private ProgressDialog mDialog;
	
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
	
	private class GetRouteInfoTaskReturn {
		public ObaResponse routeInfo;
		public ObaResponse stopsForRoute;
		public String stopsForRouteString;
		
		GetRouteInfoTaskReturn(ObaResponse info, ObaResponse stops) {
			routeInfo = info;
			stopsForRoute = stops;
			stopsForRouteString = stopsForRoute.toString();
		}
	}
	
	private class GetRouteInfoTask extends AsyncTask<String,Void,GetRouteInfoTaskReturn> {
		@Override
		protected void onPreExecute() {
			showLoadingDialog();
		}
		@Override
		protected GetRouteInfoTaskReturn doInBackground(String... params) {
			return new GetRouteInfoTaskReturn(
					ObaApi.getRouteById(params[0]),
					ObaApi.getStopsForRoute(params[0]));
		}
		@Override
		protected void onPostExecute(GetRouteInfoTaskReturn result) {
			ObaResponse routeInfo = result.routeInfo;
			ObaResponse stops = result.stopsForRoute;
			mStopsResponse = result.stopsForRouteString;
			
	    	if (routeInfo.getCode() == ObaApi.OBA_OK) {
	    		ObaRoute route = routeInfo.getData().getThisRoute();
	    		TextView shortNameText = (TextView)mListHeader.findViewById(R.id.short_name);
	    		TextView longNameText = (TextView)mListHeader.findViewById(R.id.long_name);
	    		TextView agencyText = (TextView)mListHeader.findViewById(R.id.agency);
	    	
	    		String shortName = route.getShortName();
	    		String longName = route.getLongName();
	    		
	    		shortNameText.setText(shortName);
	    		longNameText.setText(longName);
	    		agencyText.setText(route.getAgencyName());
	    		
	    		RoutesDbAdapter.addRoute(RouteInfoActivity.this, 
	    				route.getId(), shortName, longName, true);
	    	}
	    	else {
	    		// TODO: Show some error text in the "empty" field.
	    	}
	    	if (stops.getCode() == ObaApi.OBA_OK) {
	    		mAdapter.setData(stops);	    		
	    	} 
	    	else {
	    		// TODO: Show some error text 
	    	}
	    	dismissLoadingDialog();
		}
		@Override
		protected void onCancelled() {
			dismissLoadingDialog();
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
		mAsyncTask = new GetRouteInfoTask();
		mAsyncTask.execute(mRouteId);
	}
	@Override
	public void onDestroy() {
		// Do this before cancelling the async task, 
		// since it doesn't dismiss the dialog properly.
		dismissLoadingDialog();
		mAsyncTask.cancel(true);
		super.onDestroy();
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
        	Intent myIntent = new Intent(this, MapViewActivity.class);
        	myIntent.putExtra(MapViewActivity.ROUTE_ID, mRouteId);
        	myIntent.putExtra(MapViewActivity.STOP_DATA, mStopsResponse);
        	startActivity(myIntent);
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
    
    private void showLoadingDialog() {
    	if (mDialog == null) {
			mDialog = ProgressDialog.show(this, 
					"", 
					getResources().getString(R.string.route_info_loading),
					true,
					true,
					new DialogInterface.OnCancelListener() {
						public void onCancel(DialogInterface arg0) {
							finish();
						}
					});
    	}
    }
    private void dismissLoadingDialog() {
    	if (mDialog != null) {
    		mDialog.dismiss();
    		mDialog = null;
    	}   	
    }
}
