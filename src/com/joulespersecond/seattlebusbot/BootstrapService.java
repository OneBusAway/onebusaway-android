package com.joulespersecond.seattlebusbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootstrapService extends BroadcastReceiver {

	//
	// This is solely responsible for starting the TripService
	// when the device starts up.
	//
	@Override
	public void onReceive(Context context, Intent intent) {
		// Run the trip service.
		final Intent tripService = new Intent(context, TripService.class);
		tripService.setAction(TripService.ACTION_SCHEDULE_ALL);
		context.startService(tripService);
	}
}
