package com.joulespersecond.seattlebusbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
	private static final String TAG = "AlarmReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		// NOTE: For now, just forward anything to the TripService.
		// Eventually, we can distinguish by action or Content URI.
		// Also, handle CPU wake locking..
		PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		lock.acquire(10*1000);
		Intent tripService = new Intent(context, TripService.class);
		tripService.setAction(intent.getAction());
		tripService.setData(intent.getData());
		context.startService(tripService);
	}
}
