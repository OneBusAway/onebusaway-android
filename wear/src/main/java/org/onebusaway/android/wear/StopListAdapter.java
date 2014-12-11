package org.onebusaway.android.wear;

import android.content.Context;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.core.StopData;

import java.util.List;

public final class StopListAdapter extends WearableListView.Adapter {

    private List<StopData> mDataset;
    private final LayoutInflater mInflater;

    public StopListAdapter(Context context, List<StopData> stopDataSet) {
        mInflater = LayoutInflater.from(context);
        mDataset = stopDataSet;
    }

    public static class ItemViewHolder extends WearableListView.ViewHolder {
        private TextView textView;
        public ItemViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.stop_name);
        }
    }

    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {
        return new ItemViewHolder(mInflater.inflate(R.layout.list_item_stop, null));
    }

    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder,
                                 int position) {
        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        TextView view = itemHolder.textView;
        if (position >= mDataset.size()) {
            view.setText("Load More");
        } else {
            view.setText(mDataset.get(position).getUiName());
        }
        itemHolder.itemView.setTag(position);
    }

    // (invoked by the WearableListView's layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size() + 1;
    }
}
