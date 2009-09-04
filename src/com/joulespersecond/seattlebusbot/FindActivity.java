package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.os.Bundle;
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
	    			.setContent(R.id.find_stop));
	    mTabHost.addTab(mTabHost.newTabSpec(TAB_FIND_ROUTE)
	    			.setIndicator(getResources().getString(R.string.find_route))
	    			.setContent(R.id.find_route));
	    
		Bundle bundle = getIntent().getExtras();
		String tab = bundle.getString(CURRENT_TAB);
		if (tab == TAB_FIND_STOP) {
		    mTabHost.setCurrentTab(0);			
		}
		else if (tab == TAB_FIND_ROUTE) {
			mTabHost.setCurrentTab(1);
		}
	}
}
