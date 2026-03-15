/*
 * Copyright (C) 2026 Open Transit Software Foundation
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

import android.app.Dialog;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;

import java.util.ArrayList;

public class AddStopToTransitCenterDialog extends DialogFragment {

    private static final String ARG_TRANSIT_CENTER_ID = "transit_center_id";

    private long mTransitCenterId;
    private ArrayList<String> mStopIds = new ArrayList<>();
    private ArrayList<String> mStopNames = new ArrayList<>();
    private ArrayList<String> mExistingStopIds;
    private OnStopsAddedListener mListener;

    public interface OnStopsAddedListener {
        void onStopsAdded();
    }

    public static AddStopToTransitCenterDialog newInstance(long transitCenterId) {
        AddStopToTransitCenterDialog dialog = new AddStopToTransitCenterDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_TRANSIT_CENTER_ID, transitCenterId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnStopsAddedListener(OnStopsAddedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mTransitCenterId = getArguments().getLong(ARG_TRANSIT_CENTER_ID, -1);
        }
        mExistingStopIds = ObaContract.TransitCenterStops.getStopIds(
                requireActivity(), mTransitCenterId);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Query starred stops synchronously for the dialog
        String selection = ObaContract.Stops.FAVORITE + "=1";
        if (Application.get().getCurrentRegion() != null) {
            selection += " AND " + QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                    Application.get().getCurrentRegion().getId());
        }

        Cursor c = requireActivity().getContentResolver().query(
                ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._ID, ObaContract.Stops.UI_NAME},
                selection, null,
                ObaContract.Stops.UI_NAME + " ASC");

        mStopIds.clear();
        mStopNames.clear();
        boolean[] checkedItems = null;

        if (c != null) {
            try {
                checkedItems = new boolean[c.getCount()];
                while (c.moveToNext()) {
                    String stopId = c.getString(0);
                    String stopName = c.getString(1);
                    mStopIds.add(stopId);
                    mStopNames.add(stopName);
                    checkedItems[c.getPosition()] = mExistingStopIds.contains(stopId);
                }
            } finally {
                c.close();
            }
        }

        String[] names = mStopNames.toArray(new String[0]);
        final boolean[] selected = checkedItems != null ? checkedItems : new boolean[0];

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.transit_center_add_stops);
        builder.setMultiChoiceItems(names, selected, (dialog, which, isChecked) -> {
            selected[which] = isChecked;
        });
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            for (int i = 0; i < mStopIds.size(); i++) {
                String stopId = mStopIds.get(i);
                boolean wasExisting = mExistingStopIds.contains(stopId);
                if (selected[i] && !wasExisting) {
                    ObaContract.TransitCenterStops.addStop(
                            requireActivity(), mTransitCenterId, stopId);
                } else if (!selected[i] && wasExisting) {
                    ObaContract.TransitCenterStops.removeStop(
                            requireActivity(), mTransitCenterId, stopId);
                }
            }
            if (mListener != null) {
                mListener.onStopsAdded();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

}
