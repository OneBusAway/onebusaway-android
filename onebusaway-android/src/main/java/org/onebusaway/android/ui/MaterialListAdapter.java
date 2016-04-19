/*
* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Adapter for material design lists
 */
public class MaterialListAdapter extends BaseAdapter {

    Context context;
    List<MaterialListItem> rowItem;

    public MaterialListAdapter(Context context, List<MaterialListItem> rowItem) {
        this.context = context;
        this.rowItem = rowItem;
    }

    @Override
    public int getCount() {
        return rowItem.size();
    }

    @Override
    public Object getItem(int position) {
        return rowItem.get(position);
    }

    @Override
    public long getItemId(int position) {
        return rowItem.indexOf(getItem(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            convertView = mInflater.inflate(R.layout.material_list_item, null);
        }

        ImageView imgIcon = (ImageView) convertView.findViewById(R.id.rtl_icon);
        imgIcon.setColorFilter(context.getResources().getColor(R.color.navdrawer_icon_tint_selected));

        TextView txtTitle = (TextView) convertView.findViewById(R.id.rtl_title);
        TextView txtDesc = (TextView) convertView.findViewById(R.id.rtl_desc);

        MaterialListItem rowPos = rowItem.get(position);
        // setting the image resource and title
        imgIcon.setImageResource(rowPos.getIcon());
        txtTitle.setText(rowPos.getTitle());
        txtDesc.setText(rowPos.getDesc());

        return convertView;
    }
}
