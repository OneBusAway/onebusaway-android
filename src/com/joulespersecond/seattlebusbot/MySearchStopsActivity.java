package com.joulespersecond.seattlebusbot;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaStop;

public class MySearchStopsActivity extends MySearchActivity {
    //private static final String TAG = "MySearchStopsActivity";

    private UIHelp.StopUserInfoMap mStopUserMap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mStopUserMap = new UIHelp.StopUserInfoMap(this);
        super.onCreate(savedInstanceState);
    }
    @Override
    public void onDestroy() {
    	mStopUserMap.close();
    	super.onDestroy();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ObaStop stop = (ObaStop)l.getAdapter().getItem(position - l.getHeaderViewsCount());
        final String stopId = stop.getId();
        final String stopName = stop.getName();
        final String stopDir = stop.getDirection();
        final String shortcutName = stopName;

        if (mShortcutMode) {
            Intent intent = StopInfoActivity.makeIntent(this, stopId, stopName, stopDir);
            makeShortcut(shortcutName, intent);
        }
        else {
            StopInfoActivity.start(this, stopId, stopName, stopDir);
        }
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.stop_name);
        menu.setHeaderTitle(text.getText());
        if (isShortcutMode()) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_stop_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position, info.id);
            return true;
        case CONTEXT_MENU_SHOW_ON_MAP:
            showOnMap(getListView(), info.position);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void showOnMap(ListView l, int position) {
        ObaStop stop = (ObaStop)l.getAdapter().getItem(position - l.getHeaderViewsCount());
        final String stopId = stop.getId();
        final double lat = stop.getLatitude();
        final double lon = stop.getLongitude();

        MapViewActivity.start(this, stopId, lat, lon);
    }

    private final class SearchResultsListAdapter extends Adapters.BaseArrayAdapter<ObaStop> {
        public SearchResultsListAdapter(ObaResponse response) {
            super(MySearchStopsActivity.this,
                    response.getData().getStops(),
                    R.layout.stop_list_item);
        }
        @Override
        protected void setData(View view, int position) {
            ObaStop stop = mArray.get(position);
            mStopUserMap.setView(view, stop.getId(), stop.getName());
            UIHelp.setStopDirection(view.findViewById(R.id.direction),
                    stop.getDirection(),
                    true);
        }
    }

    private static final String URL_STOPID = MapViewActivity.HELP_URL + "#finding_stop_ids";

    @Override
    protected int getLayoutId() {
        return R.layout.my_search_stop_list;
    }
    @Override
    protected int getEditBoxHintText() {
        return R.string.search_stop_hint;
    }
    @Override
    protected int getMinSearchLength() {
        return 5;
    }
    @Override
    protected void setResultsAdapter(ObaResponse response) {
        setListAdapter(new SearchResultsListAdapter(response));
    }
    @Override
    protected void setHintText() {
        final CharSequence first = getText(R.string.find_hint_nofavoritestops);
        final int firstLen = first.length();
        final CharSequence second = getText(R.string.find_hint_nofavoritestops_link);

        SpannableStringBuilder builder = new SpannableStringBuilder(first);
        builder.append(second);
        builder.setSpan(new URLSpan(URL_STOPID), firstLen, firstLen+second.length(), 0);

        mEmptyText.setText(builder, TextView.BufferType.SPANNABLE);
    }

    @Override
    protected ObaResponse doFindInBackground(String param) {
        return ObaApi.getStopsByLocation(this, UIHelp.getLocation(this), 0, 0, 0, param, 0);
    }
}
