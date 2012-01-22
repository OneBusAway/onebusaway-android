package com.joulespersecond.seattlebusbot;

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
 *
 * @param <T>
 */
public abstract class ArrayAdapter<T> extends android.widget.ArrayAdapter<T> {
    private final LayoutInflater mInflater;
    private final int mLayoutId;

    public ArrayAdapter(Context context, int layout) {
        super(context, layout);
        mLayoutId = layout;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setData(List<T> data) {
        clear();
        if (data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                addAll(data);
            } else {
                for (T info: data) {
                    add(info);
                }
            }
        }
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
