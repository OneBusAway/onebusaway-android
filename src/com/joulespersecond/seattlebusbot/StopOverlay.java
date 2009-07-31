package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class StopOverlay extends ItemizedOverlay<OverlayItem> {
	private ObaArray mStops;
	private Activity mActivity;
	
	private static final int getResourceIdForDirection(String direction) {
		if (direction.equals("N")) {
			return R.drawable.stop_n;
		} else if (direction.equals("NW")) {
			return R.drawable.stop_nw;	    			
		} else if (direction.equals("W")) {
			return R.drawable.stop_w;	    			
		} else if (direction.equals("SW")) {
			return R.drawable.stop_sw;	
		} else if (direction.equals("S")) {
			return R.drawable.stop_s;	
		} else if (direction.equals("SE")) {
			return R.drawable.stop_se;	
		} else if (direction.equals("E")) {
			return R.drawable.stop_e;	
		} else if (direction.equals("NE")) {
			return R.drawable.stop_ne; 		    	    		
		} else {
			Log.v("TransitBotStopOverlay", "Unknown direction: " + direction);
			return R.drawable.stop_n;
		}		
	}
	
	public StopOverlay(ObaArray stops, 
			Activity activity) {
		super(boundCenterBottom(activity.getResources().getDrawable(R.drawable.stop_n)));
		mStops = stops;
		mActivity = activity;
		populate();
	}
	@Override
	protected OverlayItem 
	createItem(int i) {
		ObaStop stop = mStops.getStop(i);
		OverlayItem item = new OverlayItem(stop.getLocation(), 
				stop.getName(),
				"");
		int res = getResourceIdForDirection(stop.getDirection());
		Drawable marker = mActivity.getResources().getDrawable(res);
		item.setMarker(boundCenterBottom(marker));
		return item;
	}
	@Override
	public int size() {
		return mStops.length();
	}
	@Override
	protected boolean onTap(int index) {
		ObaStop stop = mStops.getStop(index);
		Intent myIntent = new Intent(mActivity, StopInfoActivity.class);
		myIntent.putExtra(StopInfoActivity.STOP_ID, stop.getId());
		mActivity.startActivity(myIntent);
		return true;
	}

}
