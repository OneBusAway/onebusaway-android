/*
 * Copyright (C) 2025 Rob Godfrey (rob_godfrey@outlook.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.widget;

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

/**
 * Activity that lets the user pick a transit stop for the Stop Times widget.
 * <p>
 * Shows two sections loaded from the local OBA database: starred stops and
 * recent stops (capped at 20). When the user taps a stop, the activity returns
 * RESULT_OK with {@link #EXTRA_STOP_ID} and {@link #EXTRA_STOP_NAME} in the
 * result Intent.
 */
public class StopPickerActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";

    private static final int LOADER_STARRED = 0;
    private static final int LOADER_RECENT = 1;

    // Columns from ObaContract.Stops
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_DIRECTION = 2;
    private static final int COL_FAVORITE = 3;
    private static final String[] STOP_PROJECTION = {
            ObaContract.Stops._ID,
            ObaContract.Stops.UI_NAME,
            ObaContract.Stops.DIRECTION,
            ObaContract.Stops.FAVORITE,
    };

    private StopPickerAdapter mAdapter;
    private Cursor mStarredCursor;
    private Cursor mRecentCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stop_picker);

        final ListView listView = findViewById(R.id.stop_list);

        mAdapter = new StopPickerAdapter(this);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            final StopPickerAdapter.StopItem stopItem = mAdapter.getStop(position);
            if (stopItem == null) { // header row
                return;
            }
            final Intent result = new Intent();
            result.putExtra(EXTRA_STOP_ID, stopItem.stopId);
            result.putExtra(EXTRA_STOP_NAME, stopItem.stopName);
            setResult(RESULT_OK, result);
            finish();
        });

        LoaderManager.getInstance(this).initLoader(LOADER_STARRED, null, this);
        LoaderManager.getInstance(this).initLoader(LOADER_RECENT, null, this);
    }

    /// Builds a CursorLoader for either the starred or recent stops section.
    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        final String whereClause;
        final String orderByClause;
        final android.net.Uri uri;

        if (id == LOADER_STARRED) {
            whereClause = String.format("%s = 1", ObaContract.Stops.FAVORITE);
            orderByClause = String.format("%s ASC", ObaContract.Stops.UI_NAME);
            uri = ObaContract.Stops.CONTENT_URI;
        } else {
            whereClause = String.format("%s IS NOT NULL", ObaContract.Stops.ACCESS_TIME);
            orderByClause = String.format("%s DESC", ObaContract.Stops.ACCESS_TIME);
            uri = ObaContract.Stops.CONTENT_URI.buildUpon().appendQueryParameter("limit", "20").build();
        }

        return new CursorLoader(this, uri, STOP_PROJECTION, whereClause, null, orderByClause);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_STARRED) {
            mStarredCursor = data;
        } else {
            mRecentCursor = data;
        }
        mAdapter.setData(mStarredCursor, mRecentCursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == LOADER_STARRED) {
            mStarredCursor = null;
        } else {
            mRecentCursor = null;
        }
        mAdapter.setData(mStarredCursor, mRecentCursor);
    }

    /**
     * Adapter for the stop list. Supports three view types:
     * <p>
     * - HEADER: section header
     * <p>
     * - STOP: stop row
     * <p>
     * - EMPTY: empty row for when there are no starred/recent stops
     */
    private static class StopPickerAdapter extends BaseAdapter {

        enum RowType {HEADER, STOP, EMPTY}

        /// A single row in the list. Either a section header or a stop.
        static class ListItem {
            final RowType rowType;
            final String label;
            final StopItem stop;

            ListItem(String label, RowType rowType) {
                this.rowType = rowType;
                this.label = label;
                this.stop = null;
            }

            ListItem(StopItem stop) {
                this.rowType = RowType.STOP;
                this.label = null;
                this.stop = stop;
            }
        }

        static class StopItem {
            final String stopId;
            final String stopName;
            final String direction;
            final boolean isFavorite;

            StopItem(String stopId, String stopName, String direction, boolean isFavorite) {
                this.stopId = stopId;
                this.stopName = stopName;
                this.direction = direction;
                this.isFavorite = isFavorite;
            }
        }

        private final Context mContext;
        private final String sectionHeaderStarred;
        private final String listItemNoStarred;
        private final String sectionHeaderRecent;
        private final String listItemNoRecents;
        private final List<ListItem> mItems = new ArrayList<>();

        StopPickerAdapter(Context context) {
            mContext = context;
            sectionHeaderStarred = context.getString(R.string.my_starred_title);
            listItemNoStarred = context.getString(R.string.my_no_starred_stops);
            sectionHeaderRecent = context.getString(R.string.my_recent_title);
            listItemNoRecents = context.getString(R.string.my_no_recent_stops);
        }

        void setData(@Nullable Cursor starred, @Nullable Cursor recent) {
            mItems.clear();

            mItems.add(new ListItem(sectionHeaderStarred, RowType.HEADER));
            if (starred != null && starred.getCount() > 0 && starred.moveToFirst()) {
                do {
                    mItems.add(new ListItem(cursorToStop(starred)));
                } while (starred.moveToNext());
            } else {
                mItems.add(new ListItem(listItemNoStarred, RowType.EMPTY));
            }

            mItems.add(new ListItem(sectionHeaderRecent, RowType.HEADER));
            if (recent != null && recent.getCount() > 0 && recent.moveToFirst()) {
                do {
                    mItems.add(new ListItem(cursorToStop(recent)));
                } while (recent.moveToNext());
            } else {
                mItems.add(new ListItem(listItemNoRecents, RowType.EMPTY));
            }

            notifyDataSetChanged();
        }

        private StopItem cursorToStop(Cursor c) {
            return new StopItem(
                    c.getString(COL_ID),
                    c.getString(COL_NAME),
                    c.getString(COL_DIRECTION),
                    c.getInt(COL_FAVORITE) == 1
            );
        }

        @Nullable
        StopItem getStop(int position) {
            if (position < 0 || position >= mItems.size()) {
                return null;
            }
            final ListItem item = mItems.get(position);
            return item.rowType == RowType.STOP ? item.stop : null;
        }

        @Override
        public int getViewTypeCount() {
            return RowType.values().length;
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).rowType.ordinal();
        }

        @Override
        public boolean isEnabled(int position) {
            return mItems.get(position).rowType == RowType.STOP;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ListItem item = mItems.get(position);
            switch (item.rowType) {
                case HEADER:
                    return bindHeader(convertView, parent, item.label);
                case EMPTY:
                    return bindEmpty(convertView, parent, item.label);
                default:
                    return bindStop(convertView, parent, item.stop);
            }
        }

        private View bindHeader(View convertView, ViewGroup parent, String label) {
            convertView = inflate(convertView, parent, R.layout.list_item_section);
            ((TextView) convertView.findViewById(R.id.list_item_section_text)).setText(label);
            return convertView;
        }

        private View bindEmpty(View convertView, ViewGroup parent, String label) {
            convertView = inflate(convertView, parent, R.layout.list_item_empty_section);
            ((TextView) convertView.findViewById(R.id.list_item_empty_text)).setText(label);
            return convertView;
        }

        private View bindStop(View convertView, ViewGroup parent, StopItem stop) {
            convertView = inflate(convertView, parent, R.layout.stop_list_item);
            ((TextView) convertView.findViewById(R.id.stop_name)).setText(stop.stopName);
            UIUtils.setStopDirection(convertView.findViewById(R.id.direction), stop.direction, true);

            final ImageView favoriteIcon = convertView.findViewById(R.id.stop_favorite);
            favoriteIcon.setVisibility(stop.isFavorite ? View.VISIBLE : View.GONE);
            if (stop.isFavorite) {
                favoriteIcon.setColorFilter(ContextCompat.getColor(mContext, R.color.navdrawer_icon_tint));
            }
            return convertView;
        }

        private View inflate(View convertView, ViewGroup parent, int layoutRes) {
            if (convertView != null && Integer.valueOf(layoutRes).equals(convertView.getTag())) {
                return convertView;
            }
            final View view = LayoutInflater.from(mContext).inflate(layoutRes, parent, false);
            view.setTag(layoutRes);
            return view;
        }
    }
}
