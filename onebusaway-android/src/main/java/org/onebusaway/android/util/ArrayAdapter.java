/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * A helper that provides a bit more common functionality than the base ArrayAdapter,
 * and provides an addAll() on non-Honeycomb.
 *
 * @author paulw
 */
public abstract class ArrayAdapter<T> extends android.widget.ArrayAdapter<T> {

    private final LayoutInflater mInflater;

    private final int mLayoutId;

    public ArrayAdapter(Context context, int layout) {
        super(context, layout);
        mLayoutId = layout;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @TargetApi(11)
    public void setData(List<T> data) {
        setNotifyOnChange(false);  // This prevents list from scrolling back to top on clear()
        clear();
        if (data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                addAll(data);
            } else {
                for (T info : data) {
                    add(info);
                }
            }
        }
        // Since we're calling setNotifyOnChange(false), we need to call notifyDataSetChanged() ourselves
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(mLayoutId, parent, false);
        } else {
            view = convertView;
        }

        T item = getItem(position);
        initView(view, item);
        return view;
    }

    protected LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    abstract protected void initView(View view, T t);
}
