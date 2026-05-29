/*
 * Copyright 2012 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;

import org.onebusaway.android.R;
import org.onebusaway.android.directions.model.Direction;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * @author Khoa Tran
 */

public class DirectionExpandableListAdapter extends BaseExpandableListAdapter {

    Context mContext;

    int mDirectionLayoutResourceId;

    int mSubDirectionLayoutResourceId;

    Direction mData[] = null;

    public DirectionExpandableListAdapter(Context context, int directionLayoutResourceId,
                                          int subDirectionLayoutResourceId, Direction[] data) {
        mDirectionLayoutResourceId = directionLayoutResourceId;
        mSubDirectionLayoutResourceId = subDirectionLayoutResourceId;
        mContext = context;
        mData = data;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        ArrayList<Direction> subDirections = mData[groupPosition].getSubDirections();

        if (subDirections != null && !subDirections.isEmpty()) {
            return subDirections.get(childPosition);
        }

        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        ArrayList<Direction> subDirections = mData[groupPosition].getSubDirections();
        if (subDirections != null) {
            return subDirections.size();
        }
        return 0;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {

        View row = convertView;
        DirectionHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(mSubDirectionLayoutResourceId, parent, false);

            holder = new DirectionHolder();
            holder.imgIcon = (ImageView) row.findViewById(R.id.imgIcon);
            holder.txtDirection = (TextView) row.findViewById(R.id.directionText);

            row.setTag(holder);
        } else {
            holder = (DirectionHolder) row.getTag();
        }

        Direction subDirection = (Direction) getChild(groupPosition, childPosition);
        CharSequence text = subDirection == null ? "null here" : subDirection.getDirectionText();
        holder.txtDirection.setText(text);

        if (subDirection.getIcon() != -1) {
            holder.imgIcon.setImageResource(subDirection.getIcon());
            holder.imgIcon.setColorFilter(Color.GRAY);
        }
        else {
            holder.imgIcon.setVisibility(View.INVISIBLE);
        }
        return row;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mData[groupPosition];
    }

    @Override
    public int getGroupCount() {
        return mData.length;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                             ViewGroup parent) {
        View row = convertView;
        DirectionHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            row = inflater.inflate(mDirectionLayoutResourceId, parent, false);

            holder = new DirectionHolder();
            holder.imgIcon = (ImageView) row.findViewById(R.id.imgIcon);
            holder.noIconText = (TextView) row.findViewById(R.id.noIconText);
            holder.txtDirection = (TextView) row.findViewById(R.id.directionText);
            holder.transitLeftBlock = (LinearLayout) row.findViewById(R.id.transitLeftBlock);
            holder.txtTime = (TextView) row.findViewById(R.id.timeText);
            holder.timelineColumn = (LinearLayout) row.findViewById(R.id.timelineColumn);
            holder.timelineLineTop = row.findViewById(R.id.timelineLineTop);
            holder.timelineLineBottom = row.findViewById(R.id.timelineLineBottom);
            holder.timelineDot = row.findViewById(R.id.timelineDot);
            holder.rightIcon = (ImageView) row.findViewById(R.id.rightIcon);

            row.setTag(holder);
        } else {
            holder = (DirectionHolder) row.getTag();
        }

        Direction dir = mData[groupPosition];
        int textColor = mContext.getResources().getColor(R.color.body_text_1);
        holder.txtDirection.setTextColor(textColor);

        if (!dir.isTransit()) {
            // Non-transit (walk/bike) step
            if (holder.transitLeftBlock != null) {
                holder.transitLeftBlock.setVisibility(View.GONE);
            }
            if (holder.txtTime != null) {
                holder.txtTime.setVisibility(View.GONE);
            }
            if (holder.timelineColumn != null) {
                holder.timelineColumn.setVisibility(View.GONE);
            }
            if (holder.rightIcon != null) {
                holder.rightIcon.setVisibility(View.GONE);
            }

            holder.txtDirection.setText(dir.getDirectionIndex() + ". " + dir.getDirectionText());
            holder.imgIcon.setVisibility(View.VISIBLE);
            if (dir.getIcon() != -1) {
                holder.imgIcon.setImageResource(dir.getIcon());
                holder.imgIcon.setColorFilter(Color.GRAY);
            } else {
                holder.imgIcon.setVisibility(View.INVISIBLE);
            }
            if (holder.noIconText != null) {
                holder.noIconText.setVisibility(View.GONE);
            }
        } else {
            // Transit step — show time, timeline, right icon
            if (holder.transitLeftBlock != null) {
                holder.transitLeftBlock.setVisibility(View.VISIBLE);
            }
            holder.imgIcon.setVisibility(View.GONE);
            if (holder.noIconText != null) {
                holder.noIconText.setVisibility(View.GONE);
            }

            // Short time on the left (e.g., "12:22 AM")
            CharSequence shortTime = dir.getShortTime();
            if (holder.txtTime != null && !TextUtils.isEmpty(shortTime)) {
                holder.txtTime.setVisibility(View.VISIBLE);
                holder.txtTime.setText(shortTime);
            } else if (holder.txtTime != null) {
                holder.txtTime.setVisibility(View.GONE);
            }

            // Timeline dot and connecting lines
            if (holder.timelineColumn != null) {
                holder.timelineColumn.setVisibility(View.VISIBLE);

                boolean prevIsTransit = groupPosition > 0
                        && mData[groupPosition - 1].isTransit();
                boolean nextIsTransit = groupPosition < mData.length - 1
                        && mData[groupPosition + 1].isTransit();

                holder.timelineLineTop.setVisibility(
                        prevIsTransit ? View.VISIBLE : View.INVISIBLE);
                holder.timelineLineBottom.setVisibility(
                        nextIsTransit ? View.VISIBLE : View.INVISIBLE);
            }

            // Transit mode icon on the right — for "Alight" steps (icon == -1),
            // inherit the icon from the preceding "Board" step
            int transitIcon = dir.getIcon();
            if (transitIcon == -1 && groupPosition > 0
                    && mData[groupPosition - 1].isTransit()) {
                transitIcon = mData[groupPosition - 1].getIcon();
            }
            if (holder.rightIcon != null) {
                if (transitIcon != -1) {
                    holder.rightIcon.setVisibility(View.VISIBLE);
                    holder.rightIcon.setImageResource(transitIcon);
                    holder.rightIcon.setColorFilter(Color.GRAY);
                } else {
                    holder.rightIcon.setVisibility(View.GONE);
                }
            }

            // Bold service label
            SpannableString serviceSpan = new SpannableString(dir.getService());
            serviceSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, serviceSpan.length(), 0);

            CharSequence text = serviceSpan;
            text = TextUtils.concat(text, "\n", dir.getPlaceAndHeadsign());

            if (!TextUtils.isEmpty(dir.getAgency())) {
                text = TextUtils.concat(text, "\n", dir.getAgency());
            }
            if (!TextUtils.isEmpty(dir.getExtra())) {
                SpannableString extraSpan = new SpannableString(dir.getExtra());
                extraSpan.setSpan(new StyleSpan(Typeface.ITALIC), 0,
                        extraSpan.length(), 0);
                extraSpan.setSpan(
                        new ForegroundColorSpan(
                                mContext.getResources().getColor(R.color.header_stop_info_ontime)),
                        0, extraSpan.length(), 0);
                text = TextUtils.concat(text, "\n", extraSpan);
            }

            holder.txtDirection.setText(text);
        }
        return row;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    static class DirectionHolder {
        ImageView imgIcon;
        TextView noIconText;
        TextView txtDirection;
        LinearLayout transitLeftBlock;
        TextView txtTime;
        LinearLayout timelineColumn;
        View timelineLineTop;
        View timelineDot;
        View timelineLineBottom;
        ImageView rightIcon;
    }
}
