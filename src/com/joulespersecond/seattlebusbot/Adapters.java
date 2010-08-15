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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

class Adapters {
    public abstract static class BaseArrayAdapter2<E> extends BaseAdapter {
        private final Context mContext;
        protected final List<E> mArray;
        private final int mLayout;

        public BaseArrayAdapter2(Context context, List<E> array, int layout) {
            mContext = context;
            mArray = array;
            mLayout = layout;
        }
        public int getCount() {
            return mArray.size();
        }
        public long getItemId(int position) {
            return position;
        }
        public Object getItem(int position) {
            return mArray.get(position);
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            View newView;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                newView = inflater.inflate(mLayout, null);
            }
            else {
                newView = convertView;
            }
            setData(newView, position);
            return newView;
        }
        public boolean hasStableIds() {
            return false;
        }
        abstract protected void setData(View view, int position);
    }
}
