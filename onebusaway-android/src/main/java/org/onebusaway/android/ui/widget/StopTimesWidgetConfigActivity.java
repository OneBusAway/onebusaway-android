/*
 * Copyright (C) 2025 Rob Godfrey (rob_godfrey@outlook.com)
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
package org.onebusaway.android.ui.widget;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class StopTimesWidgetConfigActivity extends AppCompatActivity {

    private static class Route {
        final String id;
        final String shortName;

        Route(String id, String shortName) {
            this.id = id;
            this.shortName = shortName;
        }
    }

    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";
    private static final int MAX_MINUTES_AFTER = 1440;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private EditText widgetNameInput;
    private MaterialButton selectStopButton;
    private MaterialButton saveButton;
    private TextView routesLoadingText;
    private ChipGroup chipGroup;

    private String selectedStopId = null;
    private String selectedStopName = null;
    private String existingWidgetName = null;
    private Set<String> existingRouteFilter = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger mLoadGeneration = new AtomicInteger();

    private final ActivityResultLauncher<Intent> mStopPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedStopId = result.getData().getStringExtra(StopPickerActivity.EXTRA_STOP_ID);
                    selectedStopName = result.getData().getStringExtra(StopPickerActivity.EXTRA_STOP_NAME);
                    updateStopButton();
                    widgetNameInput.setText(selectedStopName);
                    updateSaveButton();
                    loadRoutesForStop(selectedStopId);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setResult(RESULT_CANCELED); // widget is not added unless user hits the "save" button
        setContentView(R.layout.stop_times_widget_config);

        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        selectedStopId = getIntent().getStringExtra(EXTRA_STOP_ID);
        selectedStopName = getIntent().getStringExtra(EXTRA_STOP_NAME);

        // If editing an existing widget, load the saved config to populate fields
        if (selectedStopId == null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            final WidgetConfig existing = WidgetPrefs.loadConfig(this, appWidgetId);
            if (existing != null) {
                selectedStopId = existing.getStopId();
                selectedStopName = existing.getStopName();
                existingWidgetName = existing.getWidgetName();
                existingRouteFilter = existing.getRoutes();
            }
        }

        setupUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // Hide keyboard when user taps screen if focus is on the 'Widget name' EditText
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            final View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                final Rect rect = new Rect();
                focused.getGlobalVisibleRect(rect);
                if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    focused.clearFocus();
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(focused.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void setupUi() {
        widgetNameInput = findViewById(R.id.widget_name_input);
        selectStopButton = findViewById(R.id.select_stop);
        saveButton = findViewById(R.id.save_button);
        routesLoadingText = findViewById(R.id.routes_loading);
        chipGroup = findViewById(R.id.route_chips);

        if (selectedStopName != null) {
            updateStopButton();
            widgetNameInput.setText(existingWidgetName != null ? existingWidgetName : selectedStopName);
        }

        widgetNameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) widgetNameInput.post(widgetNameInput::selectAll);
        });

        selectStopButton.setOnClickListener(v ->
                mStopPickerLauncher.launch(new Intent(this, StopPickerActivity.class)));

        if (selectedStopId != null) {
            loadRoutesForStop(selectedStopId);
        }

        updateSaveButton();
        saveButton.setOnClickListener(v -> onSave());
    }

    private void updateStopButton() {
        selectStopButton.setText(selectedStopName);
        selectStopButton.setIconResource(R.drawable.ic_close);
    }

    private void updateSaveButton() {
        saveButton.setEnabled(selectedStopId != null);
    }

    private void loadRoutesForStop(String stopId) {
        chipGroup.removeAllViews();
        routesLoadingText.setVisibility(View.VISIBLE);

        // Increment the generation so if the user picks a different stop before the current request
        // finishes, the stale result is discarded instead of populating the chips
        final int generation = mLoadGeneration.incrementAndGet();

        executor.execute(() -> {
            final ObaArrivalInfoResponse response =
                    ObaArrivalInfoRequest.newRequest(this, stopId, MAX_MINUTES_AFTER).call();

            final List<Route> routes = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            if (response != null && response.getArrivalInfo() != null) {
                for (ObaArrivalInfo info : response.getArrivalInfo()) {
                    if (seen.add(info.getRouteId())) {
                        routes.add(new Route(info.getRouteId(), info.getShortName()));
                    }
                }
            }
            Collections.sort(routes, (a, b) -> compareRouteNames(a.shortName, b.shortName));

            if (mLoadGeneration.get() == generation) {
                mainHandler.post(() -> populateRouteChips(routes));
            }
        });
    }

    // Sorts route names numerically if both are integers, otherwise alphabetically
    private static int compareRouteNames(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private void populateRouteChips(List<Route> routes) {
        final int green = ContextCompat.getColor(this, R.color.theme_primary);
        final int grey = ContextCompat.getColor(this, R.color.theme_muted);
        final int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{-android.R.attr.state_checked}};
        final ColorStateList bgColors = new ColorStateList(states, new int[]{green, grey});
        final ColorStateList textColors = new ColorStateList(states, new int[]{Color.WHITE, Color.WHITE});

        // transparent icon to prevent chips from jumping around when the check mark appears/disappears
        final float iconSizePx = 14 * getResources().getDisplayMetrics().density;
        final android.graphics.drawable.GradientDrawable transparentIcon = new android.graphics.drawable.GradientDrawable();
        transparentIcon.setColor(Color.TRANSPARENT);
        transparentIcon.setSize((int) iconSizePx, (int) iconSizePx);

        routesLoadingText.setVisibility(View.GONE);
        chipGroup.removeAllViews();

        for (final Route route : routes) {
            final Chip chip = new Chip(this);
            chip.setText(route.shortName);
            chip.setTag(route.id);
            chip.setCheckable(true);
            chip.setChipIcon(transparentIcon);
            chip.setChipIconVisible(true);
            chip.setChipIconSize(iconSizePx);
            chip.setCheckedIconVisible(true);
            chip.setCheckedIcon(ContextCompat.getDrawable(this, R.drawable.ic_chip_check));
            chip.setChipStrokeWidth(0);
            chip.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT)); // hide ripple effect
            chip.setChipBackgroundColor(bgColors);
            chip.setTextColor(textColors);
            chip.setChipEndPadding(chip.getChipStartPadding() + iconSizePx);
            chip.ensureAccessibleTouchTarget(0);
            // Restore previous selection if editing widget, otherwise default to none selected
            boolean checked = existingRouteFilter != null && existingRouteFilter.contains(route.id);
            chip.setChecked(checked);
            chipGroup.addView(chip);
        }
    }

    private void onSave() {
        if (selectedStopId == null) return;

        final Map<String, String> routeShortNames = getSelectedRouteShortNames();
        if (routeShortNames.isEmpty()) {
            Toast.makeText(this, R.string.widget_config_select_at_least_one_route, Toast.LENGTH_SHORT).show();
            return;
        }
        if (routeShortNames.size() > 3) {
            Toast.makeText(this, R.string.widget_config_select_no_more_than_three_routes, Toast.LENGTH_SHORT).show();
            return;
        }

        final String enteredName = widgetNameInput.getText().toString().trim();
        final String widgetName = enteredName.isEmpty() ? selectedStopName : enteredName;

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Launched from within the app before pinning, request pin with config in callback
            pinWidgetWithConfig(widgetName, routeShortNames);
            return;
        }

        final WidgetConfig config = new WidgetConfig(selectedStopId, selectedStopName, widgetName, routeShortNames);
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        WidgetPrefs.saveConfig(this, appWidgetId, config);
        StopTimesWidget.refreshWidget(this, appWidgetManager, appWidgetId);

        final Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private Map<String, String> getSelectedRouteShortNames() {
        final Map<String, String> selected = new HashMap<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            final Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                selected.put((String) chip.getTag(), chip.getText().toString());
            }
        }
        return selected;
    }

    private void pinWidgetWithConfig(String widgetName, Map<String, String> routeShortNames) {
        final AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !mgr.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, R.string.widget_config_add_manually, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Save config before requesting pin. On some Android versions, the system launches the
        // configure activity after placement, which we don't want. This lets the launch detect
        // and silently apply the already-configured settings instead of showing the config screen
        WidgetPrefs.savePendingPinConfig(this, new WidgetConfig(selectedStopId, selectedStopName, widgetName, routeShortNames));

        final Intent callbackIntent = new Intent(this, StopTimesWidget.class);
        callbackIntent.setAction(StopTimesWidget.ACTION_APPLY_PENDING_CONFIG);
        callbackIntent.putExtra(StopTimesWidget.EXTRA_STOP_ID, selectedStopId);
        callbackIntent.putExtra(StopTimesWidget.EXTRA_STOP_NAME, selectedStopName);
        callbackIntent.putExtra(StopTimesWidget.EXTRA_WIDGET_NAME, widgetName);
        callbackIntent.putStringArrayListExtra(StopTimesWidget.EXTRA_ROUTE_IDS, new ArrayList<>(routeShortNames.keySet()));
        callbackIntent.putStringArrayListExtra(StopTimesWidget.EXTRA_ROUTE_NAMES, new ArrayList<>(routeShortNames.values()));

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE; // required so system can inject EXTRA_APPWIDGET_ID
        }

        final PendingIntent callbackPi = PendingIntent.getBroadcast(this, 0, callbackIntent, flags);

        Bundle previewExtras = null;
        // add stop name & route ID to widget preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews preview = new RemoteViews(getPackageName(), R.layout.stop_times_widget_preview);
            preview.setTextViewText(R.id.preview_stop_name, widgetName);
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                final Chip chip = (Chip) chipGroup.getChildAt(i);
                if (chip.isChecked()) {
                    preview.setTextViewText(R.id.widget_route_1_title, chip.getText());
                    break;
                }
            }
            previewExtras = new Bundle();
            previewExtras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview);
        }

        mgr.requestPinAppWidget(new ComponentName(this, StopTimesWidget.class), previewExtras, callbackPi);
        finish();
    }
}
