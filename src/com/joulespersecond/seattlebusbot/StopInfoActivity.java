package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;
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

public class StopInfoActivity extends ListActivity {
	private static final String TAG = "StopInfoActivity";
	private static final long RefreshPeriod = 60*1000;

	public static final String STOP_ID = ".StopId";
	
	private StopInfoListAdapter mAdapter;
	private View mListHeader;
	private String mStopId;
	private String mStopName;
	// Store this as two doubles, since we only store this to pass
	// it into the ShowOnMap intent, which expects it as doubles anyway.
	private double mStopLat;
	private double mStopLon;
	private Timer mTimer;
	
	private GetArrivalInfoTask mAsyncTask;
	private ProgressDialog mDialog;

	private TripsDbAdapter mTripsDbAdapter;	
	private TripsDbAdapter.TripsForStopSet mTripsForStop;
	
	public static final int getStopDirectionText(String direction) {
		if (direction.equals("N")) {
			return R.string.direction_n;
		} else if (direction.equals("NW")) {
			return R.string.direction_nw;	    			
		} else if (direction.equals("W")) {
			return R.string.direction_w;	    			
		} else if (direction.equals("SW")) {
			return R.string.direction_sw;	
		} else if (direction.equals("S")) {
			return R.string.direction_s;	
		} else if (direction.equals("SE")) {
			return R.string.direction_se;	
		} else if (direction.equals("E")) {
			return R.string.direction_e;	
		} else if (direction.equals("NE")) {
			return R.string.direction_ne; 		    	    		
		} else {
			Log.v(TAG, "Unknown direction: " + direction);
			return R.string.direction_n;
		}	
	}
	// The results of the ArrivalInfo ObaArray aren't sorted the way we want --
	// so we'll process the data and return a list of preprocessed structures.
	//
	final class StopInfoComparator implements Comparator<StopInfo> {
		public int compare(StopInfo lhs, StopInfo rhs) {
			return (int)(lhs.mEta - rhs.mEta);
		}
	}
	final class StopInfo {
		// These are private final but can still be accessed by 
		// subclasses since this is package-private (which is good for performance).
		// For now this is OK, but if we wanted to be really correct
		// we'd make this class private and provide accessors.
		private final ObaArrivalInfo mInfo;
		private final long mEta;
		private final long mDisplayTime;
		private final String mStatusText;
		private final int mColor;
		private final String mTripName;
		
		private static final int ms_in_mins = 60*1000;
		
		public StopInfo(ObaArrivalInfo info, long now) {
			mInfo = info;
			// First, all times have to have to be converted to 'minutes'
			final long nowMins = now/ms_in_mins;
			final long scheduled = info.getScheduledArrivalTime();
			final long predicted = info.getPredictedArrivalTime();
			final long scheduledMins = scheduled/ms_in_mins;
			final long predictedMins = predicted/ms_in_mins;
			mTripName = mTripsForStop.getTripName(info.getTripId());
			
			final Resources res = getResources();
			
			if (predicted != 0) {
				mEta = predictedMins - nowMins;
				mDisplayTime = predicted;
				final long delay = predictedMins - scheduledMins;
				
				if (mEta >= 0) {
					// Bus is arriving
					if (delay > 0) {
						// Arriving delayed
						mColor = R.color.stop_info_delayed;
						if (delay == 1) {
							mStatusText = res.getString(R.string.stop_info_arrive_delayed1);							
						}
						else {
							String fmt = res.getString(R.string.stop_info_arrive_delayed);
							mStatusText = String.format(fmt, delay);						
						}
					}
					else if (delay < 0) {
						// Arriving early
						mColor = R.color.stop_info_early;
						if (delay == -1) {
							mStatusText = res.getString(R.string.stop_info_arrive_early1);							
						}
						else {
							String fmt = res.getString(R.string.stop_info_arrive_early);
							mStatusText = String.format(fmt, -delay);								
						}
					}
					else {
						// Arriving on time
						mColor = R.color.stop_info_ontime;
						mStatusText = res.getString(R.string.stop_info_ontime);
					}
				} 
				else {
					// Bus is departing
					if (delay > 0) {
						// Departing delayed
						mColor = R.color.stop_info_delayed;
						if (delay == 1) {
							mStatusText = res.getString(R.string.stop_info_depart_delayed1);							
						}
						else {
							String fmt = res.getString(R.string.stop_info_depart_delayed);
							mStatusText = String.format(fmt, delay);						
						}
					} 
					else if (delay < 0) {
						// Departing early
						mColor = R.color.stop_info_early;
						if (delay == -1) {
							mStatusText = res.getString(R.string.stop_info_depart_early1);							
						}
						else {
							String fmt = res.getString(R.string.stop_info_depart_early);
							mStatusText = String.format(fmt, -delay);						
						}
					}
					else {
						// Departing on time
						mColor = R.color.stop_info_ontime;
						mStatusText = res.getString(R.string.stop_info_ontime);
					}
				}				
			}
			else {
				mColor = R.color.stop_info_ontime;
				
				mEta = scheduledMins - nowMins;
				mDisplayTime = scheduled;
				if (mEta > 0) {
					mStatusText = res.getString(R.string.stop_info_scheduled_arrival);
				} else {
					mStatusText = res.getString(R.string.stop_info_scheduled_departure);					
				}
			}
		}
	}

	private final ArrayList<StopInfo>
	convertObaArrivalInfo(ObaArray arrivalInfo) {
		int len = arrivalInfo.length();
		ArrayList<StopInfo> result = new ArrayList<StopInfo>(len);
		final long ms = System.currentTimeMillis();
		for (int i=0; i < len; ++i) {
			result.add(new StopInfo(arrivalInfo.getArrivalInfo(i), ms));
		}
		// Sort by ETA
		Collections.sort(result, new StopInfoComparator());
		return result;
	}
	
	final class StopInfoListAdapter extends BaseAdapter {
		private ArrayList<StopInfo> mInfo;
		
		public StopInfoListAdapter() {
			mInfo = new ArrayList<StopInfo>();
		}
		public int getCount() {
			return mInfo.size();
		}
		public Object getItem(int position) {
			// Replace this when we add a real stop info array
			return mInfo.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewGroup newView;
			if (convertView == null) {
				LayoutInflater inflater = getLayoutInflater();
				newView = (ViewGroup)inflater.inflate(R.layout.stop_info_listitem, null);
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
		
		public void setData(ObaResponse response) {
			ObaData data = response.getData();
			mInfo = convertObaArrivalInfo(data.getArrivalsAndDepartures());
			notifyDataSetChanged();
		}
		private void setData(ViewGroup view, int position) {
			TextView route = (TextView)view.findViewById(R.id.route);
			TextView destination = (TextView)view.findViewById(R.id.destination);
			TextView time = (TextView)view.findViewById(R.id.time);
			TextView status = (TextView)view.findViewById(R.id.status);
			TextView etaView = (TextView)view.findViewById(R.id.eta);

			StopInfo stopInfo = mInfo.get(position);
			ObaArrivalInfo arrivalInfo = stopInfo.mInfo;
			
			route.setText(arrivalInfo.getShortName());
			destination.setText(arrivalInfo.getHeadsign());
			status.setText(stopInfo.mStatusText);

			if (stopInfo.mEta == 0) {
				etaView.setText(R.string.stop_info_eta_now);
			}
			else {
				etaView.setText(String.valueOf(stopInfo.mEta));
			}

			int color = getResources().getColor(stopInfo.mColor);
			//status.setTextColor(color); // This just doesn't look very good.
			etaView.setTextColor(color);

			time.setText(DateUtils.formatDateTime(StopInfoActivity.this, 
					stopInfo.mDisplayTime, 
					DateUtils.FORMAT_SHOW_TIME|
					DateUtils.FORMAT_NO_NOON|
					DateUtils.FORMAT_NO_MIDNIGHT));	

			if (stopInfo.mTripName != null) {
				View tripInfo = view.findViewById(R.id.trip_info);
				TextView tripNameView = (TextView)view.findViewById(R.id.trip_name);
				String tripName = stopInfo.mTripName;
				if (tripName.length() == 0) {
					tripName = getResources().getString(R.string.trip_info_noname);
				}
				tripNameView.setText(tripName);
				tripInfo.setVisibility(View.VISIBLE);
			}
			else {
				// Explicitly set this to invisible because we might be reusing this view.
				View tripInfo = view.findViewById(R.id.trip_info);
				tripInfo.setVisibility(View.GONE);
				
			}
		}
	}
	
	class GetArrivalInfoTask extends AsyncTask<String,Void,ObaResponse> {
		public GetArrivalInfoTask(boolean updateDb, boolean silent) {
			super();
			mSilent = silent;
			mUpdateDb = updateDb;
		}
		@Override
		protected void onPreExecute() {
			if (mSilent) {
		        setProgressBarIndeterminateVisibility(true);
			}
			else {
				showLoadingDialog();
			}
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			return ObaApi.getArrivalsDeparturesForStop(params[0]);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		ObaStop stop = result.getData().getStop();
	    		String code = stop.getCode();
	    		String name = stop.getName();
	    		String direction = stop.getDirection();
	    		mStopLat = stop.getLatitude();
	    		mStopLon = stop.getLongitude();
	    		mStopName = name;
	    		
	    		TextView nameText = (TextView)mListHeader.findViewById(R.id.name);
	    		nameText.setText(name);
	    		TextView directionText = (TextView)mListHeader.findViewById(R.id.direction);
	    		directionText.setText(getStopDirectionText(direction));
	    	
	    		mAdapter.setData(result);	    		
				
	    		if (mUpdateDb) {
	    			// Update the database
	    			StopsDbAdapter.addStop(StopInfoActivity.this,
	    					stop.getId(), code, name, direction, 
	    					mStopLat, mStopLon, true);
	    		}
	    	}
	    	else {
	    		TextView empty = (TextView)findViewById(android.R.id.empty);
	    		empty.setText(R.string.generic_comm_error);
	    	}
	    	dismissLoadingDialog();
	        setProgressBarIndeterminateVisibility(false);
		}
		@Override
		protected void onCancelled() {
			dismissLoadingDialog();
	        setProgressBarIndeterminateVisibility(false);
		}

		private boolean mSilent;
		private boolean mUpdateDb;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.stop_info);
		ListView listView = getListView();
		// Add a header view
		LayoutInflater inflater = getLayoutInflater();
		mListHeader = inflater.inflate(R.layout.stop_info_header, null);
		listView.addHeaderView(mListHeader);
		
		mAdapter = new StopInfoListAdapter();
		setListAdapter(mAdapter);
		
		mTripsDbAdapter = new TripsDbAdapter(this);
		mTripsDbAdapter.open();
		
		Bundle bundle = getIntent().getExtras();
		mStopId = bundle.getString(STOP_ID);
		
    	mTripsForStop = mTripsDbAdapter.getTripsForStopId(mStopId);
		refresh(true, false);
	}
	@Override
	public void onDestroy() {
		mTripsForStop.close();
		mTripsDbAdapter.close();
		// Do this before the async task does -- it leaks the window for some reason.
		dismissLoadingDialog();
		if (mAsyncTask != null) {
			mAsyncTask.cancel(true);
		}
		super.onDestroy();
	}
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.stop_info_options, menu);
    	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.show_on_map) {
        	Intent myIntent = new Intent(this, MapViewActivity.class);
        	myIntent.putExtra(MapViewActivity.FOCUS_STOP_ID, mStopId);
        	myIntent.putExtra(MapViewActivity.CENTER_LAT, mStopLat);
        	myIntent.putExtra(MapViewActivity.CENTER_LON, mStopLon);
        	startActivity(myIntent);
    		return true;
    	}
    	else if (item.getItemId() == R.id.refresh) {
    		refresh(false, false);
    		return true;
    	}
    	return false;
    }
    @Override
    public void onPause() {
    	mTimer.cancel();
    	mTimer = null;
    	super.onPause();
    }
    @Override
    public void onResume() {
    	if (mTimer == null) {
    		mTimer = new Timer();
    	}
    	mTripsForStop.refresh();
    	// Always refresh once on resume
    	refresh(false, true);
    	
    	mTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				mRefreshHandler.post(mRefresh);			
			} 		
    	}, RefreshPeriod, RefreshPeriod);
    	super.onResume();
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	final StopInfo stop = (StopInfo)getListView().getItemAtPosition(position);
    	if (stop == null) {
    		return;
    	}
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(R.string.stop_info_item_options_title);
    	int options;
    	if (stop.mTripName != null) {
    		options = R.array.stop_item_options_edit;
    	}
    	else {
    		options = R.array.stop_item_options;
    	}
    	builder.setItems(options, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					goToTrip(stop);
					break;
				case 1:
					goToRoute(stop);
					break;
				case 2:
					filterByRoute(stop);
					break;
				}
			}
    	});
    	AlertDialog dialog = builder.create();
    	dialog.setOwnerActivity(this);
    	dialog.show();
    }
    
    private void goToTrip(StopInfo stop) {
		ObaArrivalInfo stopInfo = stop.mInfo;
		// The TripInfo activity needs:
		// 1. Trip ID
		// 2. Route Name
		// 3. Stop ID
		// 4. Departure Time
		Intent myIntent = new Intent(this, TripInfoActivity.class);
		myIntent.putExtra(TripInfoActivity.TRIP_ID, stopInfo.getTripId());
		myIntent.putExtra(TripInfoActivity.ROUTE_ID, stopInfo.getRouteId());
		myIntent.putExtra(TripInfoActivity.ROUTE_NAME, stopInfo.getShortName());
		myIntent.putExtra(TripInfoActivity.STOP_ID, mStopId);
   		myIntent.putExtra(TripInfoActivity.STOP_NAME, mStopName);
		myIntent.putExtra(TripInfoActivity.DEPARTURE_TIME, 
				stopInfo.getScheduledDepartureTime());
		myIntent.putExtra(TripInfoActivity.HEADSIGN, stopInfo.getHeadsign());
		startActivity(myIntent);    	
    }
    private void goToRoute(StopInfo stop) {
    	Intent myIntent = new Intent(this, RouteInfoActivity.class);
    	myIntent.putExtra(RouteInfoActivity.ROUTE_ID, stop.mInfo.getRouteId());
    	startActivity(myIntent);
    }
    private void filterByRoute(StopInfo stop) {
    	
    }
    
    // Similar to the annoying bit in MapViewActivity, the timer is run
    // in a separate task, so we need to post back to the main thread 
    // to run our AsyncTask. We can't do everything in the timer thread
    // because the progressBar has to be modified in the UI (main) thread.
    final Handler mRefreshHandler = new Handler();
    final Runnable mRefresh = new Runnable() {
    	public void run() {
			refresh(false, true);
    	}
    };
    private void refresh(boolean updateDb, boolean silent) {
		if (mStopId != null) {
			if (mAsyncTask == null || 
					(mAsyncTask.getStatus() == AsyncTask.Status.FINISHED)) {
				mAsyncTask = new GetArrivalInfoTask(updateDb, silent);
				mAsyncTask.execute(mStopId);
			}
		}    	
    }
    
    private void showLoadingDialog() {
    	if (mDialog == null) {
			mDialog = ProgressDialog.show(
					this,
					"",
					getResources().getString(R.string.stop_info_loading),
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
