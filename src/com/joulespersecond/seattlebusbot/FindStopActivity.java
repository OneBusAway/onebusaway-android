package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class FindStopActivity extends ListActivity {
	private StopsDbAdapter mDbAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_stop);
		
		mDbAdapter = new StopsDbAdapter(this);
		mDbAdapter.open();
		// TODO: Inflate the header
		
		fillFavorites();
	}
	
	// We need to convert the direction text (N/NW/E/etc)
	// to user level text (North/Northwest/etc..)
	private class MyViewBinder implements SimpleCursorAdapter.ViewBinder {
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			if (columnIndex == 3) { // TODO: Put this constant somewhere
				TextView direction = (TextView)view.findViewById(R.id.direction);
				direction.setText(
						StopInfoActivity.getStopDirectionText(cursor.getString(columnIndex)));
				return true;
			} 
			return false;
		}
		
	}
	
	private void fillFavorites() {
		Cursor c = mDbAdapter.getFavoriteStops();
		startManagingCursor(c);
		
		String[] from = new String[] { 
				DbHelper.KEY_NAME,
				DbHelper.KEY_DIRECTION 
		};
		int[] to = new int[] {
				R.id.name,
				R.id.direction
		};
		SimpleCursorAdapter simpleAdapter = 
			new SimpleCursorAdapter(this, R.layout.find_stop_listitem, c, from, to);
		simpleAdapter.setViewBinder(new MyViewBinder());
		setListAdapter(simpleAdapter);
	}
}
