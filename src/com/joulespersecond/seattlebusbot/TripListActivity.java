package com.joulespersecond.seattlebusbot;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class TripListActivity extends ListActivity {
    //private static final String TAG = "TripListActivity";
        
    public TripsDbAdapter mDbAdapter;
    public RoutesDbAdapter mRoutesDbAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.trip_list);
        registerForContextMenu(getListView());
        
        mDbAdapter = new TripsDbAdapter(this);
        mDbAdapter.open();
        mRoutesDbAdapter = new RoutesDbAdapter(this);
        mRoutesDbAdapter.open();
        
        fillTrips();
    }
    
    private void fillTrips() {
        Cursor c = mDbAdapter.getTrips();
        startManagingCursor(c);
        
        final String[] from = { 
                DbHelper.KEY_NAME,
                DbHelper.KEY_HEADSIGN,
                DbHelper.KEY_DEPARTURE,
                DbHelper.KEY_ROUTE
        };
        final int[] to = {
                R.id.name,
                R.id.headsign,
                R.id.departure_time,
                R.id.route_name
        };
        SimpleCursorAdapter simpleAdapter = 
            new SimpleCursorAdapter(this, R.layout.trip_list_listitem, c, from, to);
        
        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == TripsDbAdapter.TRIP_COL_NAME) {
                    TextView text = (TextView)view;
                    String name = cursor.getString(columnIndex);
                    if (name.length() == 0) {
                        name = getResources().getString(R.string.trip_info_noname);
                    }
                    text.setText(name);
                    return true;
                }
                else if (columnIndex == TripsDbAdapter.TRIP_COL_DEPARTURE) {
                    TextView text = (TextView)view;
                    text.setText(TripInfoActivity.getDepartureTime(
                            TripListActivity.this,
                            TripsDbAdapter.convertDBToTime(cursor.getInt(columnIndex))));
                    return true;
                } 
                else if (columnIndex == TripsDbAdapter.TRIP_COL_ROUTEID) {
                    // 
                    // Translate the Route ID into the Route Name by looking
                    // it up in the Routes table.
                    //
                    TextView text = (TextView)view;
                    final String routeId = cursor.getString(columnIndex);
                    final String routeName = mRoutesDbAdapter.getRouteShortName(routeId);
                    if (routeName != null) {
                        String fmt = getResources().getString(R.string.trip_info_route);
                        text.setText(String.format(fmt, routeName));
                    }
                    return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);
    }
    @Override
    protected void onDestroy() {
        mDbAdapter.close();
        mRoutesDbAdapter.close();
        super.onDestroy();
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String[] ids = getIds(l, position);

        TripInfoActivity.start(this, ids[0], ids[1]);
    }
    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_SHOWSTOP = 3;
    private static final int CONTEXT_MENU_SHOWROUTE = 4;
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.trip_list_context_edit);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.trip_list_context_delete);
        menu.add(0, CONTEXT_MENU_SHOWSTOP, 0, R.string.trip_list_context_showstop);
        menu.add(0, CONTEXT_MENU_SHOWROUTE, 0, R.string.trip_list_context_showroute);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position, info.id);
            return true;
        case CONTEXT_MENU_DELETE:
            deleteTrip(getListView(), info.position);
            return true;
        case CONTEXT_MENU_SHOWSTOP:
            goToStop(getListView(), info.position);
            return true;
        case CONTEXT_MENU_SHOWROUTE:
            goToRoute(getListView(), info.position);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void deleteTrip(ListView l, int position) {
        String[] ids = getIds(l, position);
        
        // TODO: Confirmation dialog?
        mDbAdapter.deleteTrip(ids[0], ids[1]);
        SimpleCursorAdapter adapter = (SimpleCursorAdapter)getListView().getAdapter();
        adapter.getCursor().requery();
    }
    private void goToStop(ListView l, int position) {
        String[] ids = getIds(l, position);
        StopInfoActivity.start(this, ids[1]);        
    }
    private void goToRoute(ListView l, int position) {
        String[] ids = getIds(l, position);
        RouteInfoActivity.start(this, ids[2]);        
    }
    private String[] getIds(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        final Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String[] result = new String[] { 
                c.getString(TripsDbAdapter.TRIP_COL_TRIPID),
                c.getString(TripsDbAdapter.TRIP_COL_STOPID),
                c.getString(TripsDbAdapter.TRIP_COL_ROUTEID)
        };
        return result;
    }
}
