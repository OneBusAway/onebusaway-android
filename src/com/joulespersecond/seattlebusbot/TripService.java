package com.joulespersecond.seattlebusbot;

import java.util.HashSet;
import java.util.List;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.text.format.Time;
import android.util.Log;

// Required operations:
// 
// 1. Given a TripID/StopID combination, poll OBA for the next arrival, 
//		and notify at the scheduled reminder time.
//
// 2. Given a TripID/StopID combination, schedule (1) for the next trip 
//		after a certain time or, if it's a one-time trip, delete it.
//
// 3. Given a trips DB, do (2) for *all* trips in the DB.
//
// 4. Remove past one-time reminders. (where days = 0 and departure < now_in_minutes)
//

public class TripService extends Service {
	public static final String TAG = "TripService";
	
	// Actions
	public static final String ACTION_SCHEDULE_ALL = 
		"com.joulespersecond.seattlebusbot.action.SCHEDULE_ALL";
	public static final String ACTION_SCHEDULE_TRIP = 
		"com.joulespersecond.seattlebusbot.action.SCHEDULE_TRIP";
	public static final String ACTION_POLL_TRIP = 
		"com.joulespersecond.seattlebusbot.action.POLL_TRIP";
	//
	// Bus URI format:
	// com.joulespersecond.seattlebusbot://trip/<tripId>/<stopId>
	//
	// TODO: Move this into a more common location?
	public static final String URI_SCHEME = "content";
	public static final String TRIP_AUTHORITY = "com.joulespersecond.seattlebusbot.trip";
	public static final Uri CONTENT_URI = Uri.parse("content://com.joulespersecond.seattlebusbot.trip");
	
    public static Uri buildTripUri(String tripId, String stopId) {
    	return CONTENT_URI.buildUpon()
    			.appendPath(tripId)
    			.appendPath(stopId)
    			.build();
    }
	
	private static final int LOOKAHEAD_DURATION_MS = 10*60*1000;
	
	// We don't want to stop the service when any particular call to onStart
	// completes: we want to stop the service when *all* tasks that have
	// been started by onStart complete.
	// So we store the startIds here as "task IDs" and each task calls
	// stopTask(), and when there are no more tasks, we call stopSelf().
	private HashSet<Integer> mActiveTasks = new HashSet<Integer>();
	
    @Override
    public void onStart(Intent intent, int startId) {  
    	final String action = intent.getAction();
    	if (ACTION_SCHEDULE_ALL.equals(action)) {
            Thread thr = new Thread(null, 
            		new ScheduleAllTask(startId), 
            		TAG);
            thr.start();   
            return;
    	}
    	else if (ACTION_SCHEDULE_TRIP.equals(action)) {
    		// Decode the content URI.
    		final Uri contentUri = intent.getData();
    		if (contentUri != null) {
	    		List<String> path = contentUri.getPathSegments();
	    		if (path.size() >= 2) {
	                Thread thr = new Thread(null, 
	                		new Schedule1Task(startId, path.get(0), path.get(1)), 
	                		TAG);
	                thr.start();   
	                return;    			
	    		}
    		}
    	}
    	else if (ACTION_POLL_TRIP.equals(action)) {
    		// Decode the content URI.
    		final Uri contentUri = intent.getData();
    		if (contentUri != null) {
	    		List<String> path = contentUri.getPathSegments();
	    		if (path.size() >= 2) {
	                Thread thr = new Thread(null, 
	                		new PollTask(startId, path.get(0), path.get(1)), 
	                		TAG);
	                thr.start();   
	                return;    			
	    		}
    		}
    	}
    	Log.e(TAG, "We should not have gotten here: " + action);
    	stopSelf();
    }

    abstract class TaskBase implements Runnable {
    	private final Integer mStartId;
    	private PowerManager.WakeLock mWakeLock;
    	// Keep the CPU on while we work on these tasks.
    	
    	TaskBase(int startId) {
    		mStartId = startId;
    	}
    	protected void startTask() {
    		synchronized(TripService.this) {
    			PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
    			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    			mWakeLock.acquire();
    			mActiveTasks.add(mStartId);
    		}
    	}
    	protected void endTask() {
    		synchronized(TripService.this) {
    			mActiveTasks.remove(mStartId);
    			if (mActiveTasks.isEmpty()) {
    	        	//Log.d(TAG, "Stopping service");
    				stopSelf();
    			}
    			mWakeLock.release();
    		}
    	}
		public void run() {
        	//Log.d(TAG, "Starting task");
        	startTask();
        	
        	runTask();
        	
            // Done with our work...  stop the service!
        	//Log.d(TAG, "Exiting task");
        	endTask();
		}
		protected void runTask() {
			// Meant to be overridden.
		}
    }
    
    final class Schedule1Task extends TaskBase {
    	private final String mTripId;
    	private final String mStopId;
    	
    	Schedule1Task(int startId, String tripId, String stopId) {
    		super(startId);
    		mTripId = tripId;
    		mStopId = stopId;
    	}
		@Override
		protected void runTask() {
			//Log.d(TAG, "Schedule 1 task");
        	final TripsDbAdapter adapter = new TripsDbAdapter(TripService.this);
        	adapter.open();
        	
        	Schedule(TripService.this, adapter.getTrip(mTripId, mStopId));
        		
        	adapter.close();
		}
    }
    
    final class ScheduleAllTask extends TaskBase {
		ScheduleAllTask(int startId) {
			super(startId);
		}
		@Override
		protected void runTask() {
			//Log.d(TAG, "Schedule all task");
        	final TripsDbAdapter adapter = new TripsDbAdapter(TripService.this);
        	adapter.open();
        	
        	Schedule(TripService.this, adapter.getTrips());
        		
        	adapter.close();
		}
    }
    
    static final int reminderToIndex(int reminder) {
		switch (reminder) {
		case 0:		return 0;
		case 1: 	return 1;
		case 5:		return 2;
		case 10:	return 3;
		case 15:	return 4;
		case 20:	return 5;
		case 25:	return 6;
		case 30:	return 7;
		case 45:	return 8;
		case 60:	return 9;
		default:
			Log.e(TAG, "Invalid reminder value in DB: " + reminder);
			return 0;
		}
    }
    
    final String getNotifyText(Cursor c, int reminderMS) {
		final String routeName = RoutesDbAdapter.getRouteShortName(this, 
				c.getString(TripsDbAdapter.TRIP_COL_ROUTEID));
		
		final Resources res = getResources();
		final String[] array = res.getStringArray(R.array.reminder_time_notify);
		final String fmt = res.getString(R.string.trip_stat);
		
		return String.format(fmt, routeName, array[reminderToIndex(reminderMS/(60*1000))]);
    }
    
    // This is used by the PollTask in doNotify(), but since it uses the 
    // Context so much it's easier implemented in the service object itself.
    final void doNotification(String tripId, String stopId, int reminderMS, Cursor c) {
		//Log.d(TAG, "Notify for trip: " + tripId);
		Notification notify = 
			new Notification(R.drawable.stat_trip, null, System.currentTimeMillis());
		
		Intent stopActivity = new Intent(this, StopInfoActivity.class);
		stopActivity.putExtra(StopInfoActivity.STOP_ID, stopId);
		PendingIntent pending = PendingIntent.getActivity(this,
				0, stopActivity, PendingIntent.FLAG_ONE_SHOT);
		
		final String title = getResources().getString(R.string.app_name);
		
		notify.setLatestEventInfo(this, title, getNotifyText(c, reminderMS), pending);
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(1, notify);    	
    }
    
    final class PollTask extends TaskBase {
    	private final String mTripId;
    	private final String mStopId;
    	
    	PollTask(int startId, String tripId, String stopId) {
    		super(startId);
    		mTripId = tripId;
    		mStopId = stopId;
    	}
    	
    	// These are states for the doPoll loop.
		private static final int NOT_FOUND = 0;
		private static final int FOUND = 1;
		private static final int NOTIFIED = 2;
    	
    	final void doPoll(Cursor c) {
    		final String tripId = mTripId;
    		final String stopId = mStopId;
    		long now = System.currentTimeMillis();
    		final int reminderMS = c.getInt(TripsDbAdapter.TRIP_COL_REMINDER)*60*1000;
    		final int departMS = c.getInt(TripsDbAdapter.TRIP_COL_DEPARTURE)*60*1000;
    		
    		int state = NOT_FOUND;
	
    		while (state != NOTIFIED) {
        		//Log.d(TAG, tripId + ": get arrivals/departures");
    			ObaResponse response = ObaApi.getArrivalsDeparturesForStop(stopId);
    			
        		now = System.currentTimeMillis();
    			
    			if (response.getCode() == ObaApi.OBA_OK) {
    				// Find the trip.
    				final ObaArray arrivals = response.getData().getArrivalsAndDepartures();
    				final int length = arrivals.length();
    				for (int i=0; i < length; ++i) {
    					ObaArrivalInfo info = arrivals.getArrivalInfo(i);
    					if (tripId.equals(info.getTripId())) {
    		        		//Log.d(TAG, tripId + ": found trip");
    						state = FOUND;
    						// We found the trip. We notify when the reminder time
    						// when calculated with the *predicted* arrival time
    						// is past now.
    						long time = info.getPredictedArrivalTime();
    						if (time == 0) {
    							time = info.getScheduledArrivalTime();
    						}
    						if ((time-reminderMS) < now) {
    							doNotification(tripId, stopId, reminderMS, c);
    							state = NOTIFIED;
    							break;
    						}
    					}
    				}
    				// If we get here, either:
    				// 1. The trip ID wasn't found in the array, or
    				// 2. We haven't yet crossed the reminder time.
    				// In either case, we keep going.
    			}
    			else {
    				// If we get here, the server returned an error.
    				// Keep moving.
    			}
    			// If we haven't found the trip, then give up after
    			// 10 minutes past the scheduled departure time.
    			if (state == NOT_FOUND && ((departMS+LOOKAHEAD_DURATION_MS) > now)) {
    				// Give up.
    				Log.d(TAG, tripId + ": giving up");
    				break;
    			}
    			try {
					Thread.sleep(30*1000);
				} catch (InterruptedException e) {
					// Okay...
				}
    		}    		
    	}
    	
    	@Override
		protected void runTask() {
    		//Log.d(TAG, "Poll task: " + mTripId + "/" + mStopId);
    		
        	final TripsDbAdapter adapter = new TripsDbAdapter(TripService.this);
        	adapter.open();
        	
        	Cursor c = adapter.getTrip(mTripId, mStopId);
        	if (c != null && c.getCount() > 0) {
        		doPoll(c);
        	}
        	else {
        		Log.e(TAG, "No such trip");
        	}
        	if (c != null) {
        		c.close();
        	}
        	adapter.close();
    		// Do poll
		}	
    }
    
    static void Schedule(Context context, Cursor c) {
    	if (c != null && c.getCount() > 0) {
        	Time tNow = new Time();
        	tNow.setToNow();
        	final long now = tNow.toMillis(false);
    		
    		c.moveToFirst();
    		do {
    			Schedule1(context, c, tNow, now);
    		} while (c.moveToNext());
    	}
    	if (c != null) {
    		c.close();
    	}
    }
    
    static void Schedule1(Context context, Cursor c, Time tNow, long now) {
		final int departureMins = c.getInt(TripsDbAdapter.TRIP_COL_DEPARTURE);
		final int reminderMS = c.getInt(TripsDbAdapter.TRIP_COL_REMINDER)*60*1000;
		final int days = c.getInt(TripsDbAdapter.TRIP_COL_DAYS);  
		if (days == 0) {
			final long remindTime = TripsDbAdapter.convertDBToTime(departureMins) - reminderMS;
			if (remindTime > now) {
				final long triggerTime = remindTime - LOOKAHEAD_DURATION_MS;
				SchedulePoll(context, c, triggerTime);
			}
		}
		else {
			final int currentWeekDay = tNow.weekDay;
			for (int i=0; i < 7; ++i) {
				final int day = (currentWeekDay+i)%7;
				final int bit = TripsDbAdapter.getDayBit(day);
				if ((days & bit) == bit) {
					Time tmp = new Time();
					tmp.set(0, departureMins, 0, tNow.monthDay+i, tNow.month, tNow.year);
					tmp.normalize(false);
					final long remindTime = tmp.toMillis(false) - reminderMS;
					if (remindTime > now) {
						final long triggerTime = remindTime - LOOKAHEAD_DURATION_MS;
						SchedulePoll(context, c, triggerTime);
					}
				}
			}
		}
    }
    static void SchedulePoll(Context context, Cursor c, long trigger) {
    	final String tripId = c.getString(TripsDbAdapter.TRIP_COL_TRIPID);
    	final String stopId = c.getString(TripsDbAdapter.TRIP_COL_STOPID);
    	
    	final Uri uri = buildTripUri(tripId, stopId);

    	Intent myIntent = new Intent(ACTION_POLL_TRIP, uri, context, AlarmReceiver.class);
    	PendingIntent alarmIntent = 
    		PendingIntent.getBroadcast(context, 0, myIntent, PendingIntent.FLAG_ONE_SHOT);
    	
    	AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    	alarm.set(AlarmManager.RTC_WAKEUP, trigger, alarmIntent);
    }
    
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

    private final IBinder mBinder = new Binder() {
        @Override
		protected boolean onTransact(int code, Parcel data, Parcel reply,
		        int flags) throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
    };
}
