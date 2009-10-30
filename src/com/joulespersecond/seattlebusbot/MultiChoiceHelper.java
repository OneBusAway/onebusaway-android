package com.joulespersecond.seattlebusbot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

//
// This class shouldn't really need to exist, except for the fact that
// AlertDialog.Builder.setMultiChoiceItems causes the dialog box 
// to have white-one-white text. So we mimic its functionality here.
//

public class MultiChoiceHelper {
    private static final String TAG = "MultiChoiceHelper";
    
    static class Adapter extends ArrayAdapter<String> {
        final boolean[] mValues;
        final OnMultiChoiceClickListener mListener;
        
        class CheckListener implements View.OnClickListener {
            final OnMultiChoiceClickListener mListener;
            final int mPosition;
            
            CheckListener(OnMultiChoiceClickListener listener, int position) {
                mListener = listener;
                mPosition = position;
            }
            
            public void onClick(View v) {
                CheckBox box = (CheckBox)v;
                // TODO: Provide a dialog interface/AlertDialog class
                mListener.onClick(null, mPosition, box.isChecked());                
            }
        }
        
        public Adapter(Context context, 
                int resource,
                int textViewResourceId, 
                String[] objects, 
                boolean[] values,
                OnMultiChoiceClickListener listener) {
            super(context, resource, textViewResourceId, objects);
            assert(objects.length == values.length);
            mValues = values;
            mListener = listener;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View child = super.getView(position, convertView, parent);
            // Set the checkbox:
            CheckBox box = (CheckBox)child.findViewById(android.R.id.checkbox);
            if (box == null) {
                Log.e(TAG, "No checkbox in child view! position=" + position);
                return child;
            }
            box.setChecked(mValues[position]);
            if (mListener != null) {
                box.setOnClickListener(new CheckListener(mListener, position));
            }
            return child;
        }
    }

    public static final AlertDialog.Builder setMultiChoiceItems(
            AlertDialog.Builder builder,
            Context context,
            int itemsId,
            boolean checkedItems[],
            DialogInterface.OnMultiChoiceClickListener listener) {
    
        return builder.setAdapter(new Adapter(context,
                R.layout.select_dialog_multichoice,
                android.R.id.text1,
                context.getResources().getStringArray(itemsId),
                checkedItems,
                listener),
            null);
    }
}
