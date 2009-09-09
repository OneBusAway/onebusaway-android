package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TabHost;

public class FindActivity extends TabActivity {
	public static final String CURRENT_TAB = "current_tab";
	public static final String TAB_FIND_STOP = "find_stop";
	public static final String TAB_FIND_ROUTE = "find_route";
	
	private TabHost mTabHost;
	
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
	    setContentView(R.layout.find);

	    mTabHost = getTabHost();
	    
	    mTabHost.addTab(mTabHost.newTabSpec(TAB_FIND_STOP)
	    			.setIndicator(getResources().getString(R.string.find_stop))
	    			.setContent(new Intent(this, FindStopActivity.class)));
	    mTabHost.addTab(mTabHost.newTabSpec(TAB_FIND_ROUTE)
	    			.setIndicator(getResources().getString(R.string.find_route))
	    			.setContent(new Intent(this, FindRouteActivity.class)));
	    	    
		Bundle bundle = getIntent().getExtras();
		String tab = bundle.getString(CURRENT_TAB);
		if (tab.equals(TAB_FIND_STOP)) {
		    mTabHost.setCurrentTab(0);			
		}
		else if (tab.equals(TAB_FIND_ROUTE)) {
			mTabHost.setCurrentTab(1);
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
    		clearFavorites();
    		return true;
    	}
    	return false;
    }
    
    private void clearFavorites() {
    	RoutesDbAdapter.clearFavorites(this);
    	StopsDbAdapter.clearFavorites(this);
    }
}
