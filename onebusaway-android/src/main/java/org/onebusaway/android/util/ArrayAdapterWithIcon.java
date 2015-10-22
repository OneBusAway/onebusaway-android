/*
 * Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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

import org.onebusaway.android.R;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * An array adapter used to create an AlertDialog lists with icons
 *
 * Loosely based on http://stackoverflow.com/a/15453996/937715
 */
public class ArrayAdapterWithIcon extends ArrayAdapter {

    private List<Integer> mImages;

    private List<String> mItems;

    public ArrayAdapterWithIcon(Context context, List<String> items, List<Integer> images) {
        super(context, R.layout.bus_options_item, items);
        mImages = images;
        mItems = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.bus_options_item, null);
        }
        ImageView image = (ImageView) convertView.findViewById(R.id.bus_option_image);
        Drawable d = ContextCompat.getDrawable(getContext(), mImages.get(position));
        image.setImageDrawable(d);
        image.setColorFilter(getContext().getResources().getColor(R.color.navdrawer_icon_tint));

        TextView text = (TextView) convertView.findViewById(R.id.bus_option_text);
        text.setText(mItems.get(position));
        return convertView;
    }
}
