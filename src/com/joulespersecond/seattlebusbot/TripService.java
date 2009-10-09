package com.joulespersecond.seattlebusbot;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

// How the Trip Service will work:
//		Constant:
//			LookaheadDuration = the number of seconds before the reminder time to begin checking
//
//   	When it starts, it looks in the trips DB and finds the soonest reminder
//			time for the trip:
//				EarliestTripTime = min((DepartureTime - ReminderDuration) for all trips)
//				(This is complicated more because we need to check 
//
//		If there are no trips:
//			Exit
//
//		If EarliestTripTime - LookaheadDuration > Now:
//			Schedule a new service for EarliestTrip - LookaheadDuration
//			Exit
//
//		Otherwise, continually poll OBA until: 
//			PredictedArrivalTime - ReminderDuration <= Now
//		Notify the user.
//		Go to the beginning.
//
//		ALSO: Do periodic cleanup of one-time events that occur in the past.
//

public class TripService extends Service {
	public static final String TAG = "TripService";
	
	//private static final int LOOKAHEAD_DURATION = 10*60;
	
    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        Thread thr = new Thread(null, mTask, TAG);
        thr.start();
    }
	
    Runnable mTask = new Runnable() {
        public void run() {
        	
        	
            // Normally we would do some work here...  for our sample, we will
            // just sleep for 30 seconds.
        	/*
            long endTime = System.currentTimeMillis() + 15*1000;
            while (System.currentTimeMillis() < endTime) {
                synchronized (mBinder) {
                    try {
                        mBinder.wait(endTime - System.currentTimeMillis());
                    } catch (Exception e) {
                    }
                }
            }
            */

            // Done with our work...  stop the service!
            TripService.this.stopSelf();
        }
    };
    
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
