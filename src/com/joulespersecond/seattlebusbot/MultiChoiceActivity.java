/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;

public final class MultiChoiceActivity extends ListActivity {
    private static final String TITLE = ".Title";
    private static final String LIST_ITEMS = ".Items";
    public  static final String CHECKED_ITEMS = ".CheckedItems";
    private static final String POSITIVE_BUTTON = ".PositiveButton";
    private static final String NEGATIVE_BUTTON = ".NegativeButton";

    public static final class Builder {
        private final Activity mActivity;
        private final Intent mIntent;

        public Builder(Activity activity) {
            mActivity = activity;
            mIntent = new Intent(mActivity, MultiChoiceActivity.class);
        }
        public void start() {
            // Do we really want to provide this? It doesn't mean much
            mActivity.startActivity(mIntent);
        }
        public void startForResult(int requestCode) {
            mActivity.startActivityForResult(mIntent, requestCode);
        }
        public void startFromFragment(Fragment fragment, int requestCode) {
            fragment.startActivityForResult(mIntent, requestCode);
        }

        public Builder setItems(
                int itemsId,
                boolean[] checkedItems) {
            mIntent.putExtra(LIST_ITEMS, mActivity.getResources().getStringArray(itemsId));
            mIntent.putExtra(CHECKED_ITEMS, checkedItems);
            return this;
        }
        public Builder setTitle(int id) {
            mIntent.putExtra(TITLE, id);
            return this;
        }
        public Builder setItems(
                String[] items,
                boolean[] checkedItems) {
            mIntent.putExtra(LIST_ITEMS, items);
            mIntent.putExtra(CHECKED_ITEMS, checkedItems);
            return this;
        }
        public Builder setPositiveButton(int textId) {
            mIntent.putExtra(POSITIVE_BUTTON, textId);
            return this;
        }
        public Builder setNegativeButton(int textId) {
            mIntent.putExtra(NEGATIVE_BUTTON, textId);
            return this;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.multi_choice_activity);

        int positiveButton = android.R.string.ok;
        int negativeButton = android.R.string.cancel;
        int title = R.string.app_name;
        String[] items = null;
        boolean[] checked = null;

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            title = extras.getInt(TITLE, title);
            positiveButton = extras.getInt(POSITIVE_BUTTON, positiveButton);
            negativeButton = extras.getInt(NEGATIVE_BUTTON, negativeButton);
            items = extras.getStringArray(LIST_ITEMS);
            checked = extras.getBooleanArray(CHECKED_ITEMS);
        }
        if (savedInstanceState != null) {
            checked = savedInstanceState.getBooleanArray(CHECKED_ITEMS);
        }

        TextView titleView = (TextView)findViewById(R.id.alertTitle);
        titleView.setText(title);

        Button button1 = (Button)findViewById(android.R.id.button1);
        button1.setText(positiveButton);
        button1.setOnClickListener(mButton1);

        Button button2 = (Button)findViewById(android.R.id.button2);
        button2.setText(negativeButton);
        button2.setOnClickListener(mButton2);

        if (items != null) {
            ListAdapter adapter = new ListAdapter(items, checked);
            setListAdapter(adapter);
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBooleanArray(CHECKED_ITEMS, getChecks());
    }

    private final class ListAdapter extends BaseAdapter {
        final class Entry implements View.OnClickListener {
            private final String mText;
            private boolean mCheck;

            Entry(String text, boolean check) {
                mText = text;
                mCheck = check;
            }
            String getText() {
                return mText;
            }
            boolean isChecked() {
                return mCheck;
            }
            public void onClick(View v) {
                CheckBox check = (CheckBox)v;
                mCheck = check.isChecked();
            }
        }
        ArrayList<Entry> mItems;

        public ListAdapter(String[] text, boolean[] checks) {
            assert(checks != null);
            assert(text.length == checks.length);
            final int len = text.length;
            mItems = new ArrayList<Entry>(len);
            for (int i=0; i < len; ++i) {
                mItems.add(new Entry(text[i], checks[i]));
            }
        }
        public int getCount() {
            return mItems.size();
        }
        public Object getItem(int position) {
            return mItems.get(position);
        }
        public long getItemId(int position) {
            return position;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup newView;
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                newView = (ViewGroup)inflater.inflate(R.layout.multi_choice_listitem, null);
            }
            else {
                newView = (ViewGroup)convertView;
            }
            setData(newView, position);
            return newView;
        }
        public boolean hasStableIds() {
            return true;
        }
        private void setData(ViewGroup view, int position) {
            Entry e = mItems.get(position);
            TextView text = (TextView)view.findViewById(android.R.id.text1);
            text.setText(e.getText());
            CheckBox check = (CheckBox)view.findViewById(android.R.id.checkbox);
            check.setChecked(e.isChecked());
            check.setOnClickListener(e);
        }
    }
    private final View.OnClickListener mButton1 = new View.OnClickListener() {
        public void onClick(View v) {
            Intent result = new Intent();
            result.putExtra(CHECKED_ITEMS, getChecks());
            setResult(Activity.RESULT_OK, result);
            finish();
        }

    };
    private final View.OnClickListener mButton2 = new View.OnClickListener() {
        public void onClick(View v) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }

    };

    private boolean[] getChecks() {
        ListAdapter adapter = (ListAdapter)getListView().getAdapter();
        final int len = adapter.getCount();
        boolean[] checks = new boolean[len];

        for (int i=0; i < len; ++i) {
            ListAdapter.Entry entry = (ListAdapter.Entry)adapter.getItem(i);
            checks[i] = entry.isChecked();
        }
        return checks;
    }
}
