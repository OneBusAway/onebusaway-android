package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.joulespersecond.oba.ObaArray;

class Adapters {
    public abstract static class BaseArrayAdapter extends BaseAdapter {
        private final Context mContext;
        protected final ObaArray mArray;
        private final int mLayout;
        
        public BaseArrayAdapter(Context context, ObaArray array, int layout) {
            mContext = context;
            mArray = array;
            mLayout = layout;
        }
        public int getCount() {
            return mArray.length();
        }
        public long getItemId(int position) {
            return position;
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
    public static abstract class BaseRouteArrayAdapter extends BaseArrayAdapter {
        public BaseRouteArrayAdapter(Context context, ObaArray array, int layout) {
            super(context, array, layout);
        }
        public Object getItem(int position) {
            return mArray.getRoute(position);
        }
    }
    public static abstract class BaseStopArrayAdapter extends BaseArrayAdapter {
        public BaseStopArrayAdapter(Context context, ObaArray array, int layout) {
            super(context, array, layout);
        }
        public Object getItem(int position) {
            return mArray.getStop(position);
        }
    }
}
