/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.dataview

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.onebusaway.android.R
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.MPS_TO_MPH
import org.onebusaway.android.extrapolation.data.Trip
import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.extrapolation.data.TripDetailsPoller
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.util.UIUtils

private const val TAG = "VehicleLocationDataAct"
private const val PAD_H = 12
private const val PAD_V = 6
private const val TEXT_SIZE = 12f
private const val DASH = "\u2014"
private const val UI_REFRESH_MS = 1_000L

/**
 * Debug activity that displays all collected location data for a vehicle's trip in a scrollable
 * table and a distance-time graph.
 */
class VehicleLocationDataActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TRIP_ID = ".TripId"
        private const val EXTRA_VEHICLE_ID = ".VehicleId"
        private const val EXTRA_STOP_ID = ".StopId"

        @JvmStatic
        fun start(context: Context, tripId: String?, vehicleId: String?) =
                start(context, tripId, vehicleId, null)

        @JvmStatic
        fun start(context: Context, tripId: String?, vehicleId: String?, stopId: String?) {
            context.startActivity(
                    Intent(context, VehicleLocationDataActivity::class.java).apply {
                        putExtra(EXTRA_TRIP_ID, tripId)
                        putExtra(EXTRA_VEHICLE_ID, vehicleId)
                        putExtra(EXTRA_STOP_ID, stopId)
                    }
            )
        }
    }

    private lateinit var tripId: String
    private var vehicleId: String? = null
    private var stopId: String? = null
    private var trip: Trip? = null
    private var poller: TripDetailsPoller? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var lastRowCount = -1

    private lateinit var tableContainer: View
    private lateinit var graphView: TrajectoryGraphView
    private val dataRows = mutableListOf<TableRow>()
    private var currentHistory: List<ObaTripStatus> = emptyList()
    private var selectedIndex: Int? = null
    private val selectedRowColor = 0xFF444400.toInt()

    private val refreshRunnable =
            object : Runnable {
                override fun run() {
                    refreshData()
                    refreshHandler.postDelayed(this, UI_REFRESH_MS)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_location_data)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        UIUtils.setupActionBar(this)
        supportActionBar?.title = getString(R.string.debug_trip_data_title)

        tripId =
                intent.getStringExtra(EXTRA_TRIP_ID)
                        ?: run {
                            finish()
                            return
                        }
        vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        stopId = intent.getStringExtra(EXTRA_STOP_ID)

        tableContainer = findViewById(R.id.location_data_table_container)
        graphView = findViewById(R.id.location_data_graph)
        graphView.setHighlightedStopId(stopId)
        graphView.onDataPointSelected = { index -> selectDataPoint(index) }

        tableContainer.visibility = View.GONE
        graphView.visibility = View.VISIBLE

        setupTabs()
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        poller = TripDetailsPoller(tripId).also { it.start() }
        refreshData()
        refreshHandler.postDelayed(refreshRunnable, UI_REFRESH_MS)
    }

    override fun onPause() {
        poller?.stop()
        poller = null
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Tab setup ---

    private fun setupTabs() {
        val tabs: TabLayout = findViewById(R.id.location_data_tabs)
        tabs.addTab(tabs.newTab().setText(getString(R.string.debug_tab_graph)))
        tabs.addTab(tabs.newTab().setText(getString(R.string.debug_tab_table)))
        tabs.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        tableContainer.visibility = View.GONE
                        graphView.visibility = View.GONE
                        when (tab.position) {
                            0 -> graphView.visibility = View.VISIBLE
                            1 -> tableContainer.visibility = View.VISIBLE
                        }
                        refreshData()
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab) {}
                    override fun onTabReselected(tab: TabLayout.Tab) {}
                }
        )
    }

    // --- Data refresh ---

    private fun refreshData() {
        val snapshot = TripDataManager.getHistorySnapshot(tripId)
        val activeTripId = TripDataManager.getLastActiveTripId(tripId)
        val tripEnded = activeTripId != null && tripId != activeTripId
        if (trip == null) trip = TripDataManager.getOrCreateTrip(tripId)

        updateHeader(snapshot.history.size, tripEnded)

        if (snapshot.history.size != lastRowCount) {
            lastRowCount = snapshot.history.size
            val table: TableLayout = findViewById(R.id.location_data_table)
            table.removeAllViews()
            buildTable(table, snapshot, trip?.anchor)
        }

        if (graphView.visibility == View.VISIBLE) {
            refreshGraph(snapshot.history, tripEnded)
        }
    }

    private fun updateHeader(sampleCount: Int, tripEnded: Boolean) {
        val header: TextView = findViewById(R.id.location_data_header)
        header.text = buildString {
            append("Trip: $tripId")
            vehicleId?.let { append("\nVehicle: $it") }
            append("\nSamples: $sampleCount")
            if (tripEnded) append("  |  Vehicle no longer serving trip")
        }
    }

    private fun refreshGraph(history: List<ObaTripStatus>, tripEnded: Boolean) {
        val schedule = TripDataManager.getSchedule(tripId)
        val serviceDate = TripDataManager.getServiceDate(tripId) ?: 0L
        val distribution: ProbDistribution? =
                if (!tripEnded) {
                    (trip?.extrapolate(System.currentTimeMillis()) as? ExtrapolationResult.Success)
                            ?.distribution
                } else null
        graphView.setData(
                history,
                schedule,
                serviceDate,
                distribution,
                trip?.anchor,
                trip?.anchorTimeMs ?: 0L
        )
    }

    private fun selectDataPoint(index: Int?) {
        val prev = selectedIndex
        selectedIndex = index
        graphView.selectedIndex = index

        // Update table row highlights
        if (prev != null && prev < dataRows.size) {
            dataRows[prev].setBackgroundColor(rowBgColor(prev))
        }
        if (index != null && index < dataRows.size) {
            dataRows[index].setBackgroundColor(selectedRowColor)
            // Scroll to the selected row
            val scrollView = tableContainer.parent as? android.widget.ScrollView
            scrollView?.post { scrollView.smoothScrollTo(0, dataRows[index].top) }
        }
    }

    // --- Table rendering ---

    private val tableHeaders =
            arrayOf(
                    "#",
                    // Timestamps
                    "Local fetch",
                    "Server clock",
                    "Vehicle msg",
                    "GPS time",
                    // Raw GPS
                    "Lat",
                    "Lon",
                    "Last dist (m)",
                    "Orientation",
                    // Server estimate
                    "Pos lat",
                    "Pos lon",
                    "Dist (m)",
                    "Sched dist (m)",
                    // Trip info
                    "Vehicle",
                    "Predicted",
                    "Deviation (s)",
                    "Phase",
                    "Status",
                    "Next stop",
                    "Next stop \u0394t (s)",
                    // Derived deltas
                    "\u0394t (s)",
                    "\u0394dist (m)",
                    "Speed (mph)",
                    "Geo \u0394 (m)"
            )
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun buildTable(
            table: TableLayout,
            snapshot: TripDataManager.HistorySnapshot,
            anchor: ObaTripStatus?
    ) {
        currentHistory = snapshot.history
        dataRows.clear()
        selectedIndex = null
        addHeaderRow(table)
        addDivider(table)

        if (snapshot.history.isEmpty()) {
            addEmptyRow(table)
            return
        }

        var prev: ObaTripStatus? = null
        for ((i, entry) in snapshot.history.withIndex()) {
            val fetchTime = snapshot.fetchTimes.getOrElse(i) { 0L }
            val localFetchTime = snapshot.localFetchTimes.getOrElse(i) { 0L }
            val isAnchor = anchor != null && entry === anchor
            addDataRow(table, i, entry, prev, fetchTime, localFetchTime, isAnchor)
            prev = entry
        }
    }

    private fun addHeaderRow(table: TableLayout) {
        val row = TableRow(this).apply { setBackgroundColor(0xFF424242.toInt()) }
        tableHeaders.forEach { row.addView(createCell(it, isHeader = true)) }
        table.addView(row)
    }

    private fun addDivider(table: TableLayout) {
        table.addView(
                View(this).apply {
                    layoutParams =
                            TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 2)
                    setBackgroundColor(0xFF888888.toInt())
                }
        )
    }

    private fun addEmptyRow(table: TableLayout) {
        val row = TableRow(this)
        val cell =
                createCell("No data collected yet \u2014 waiting for updates...", isHeader = false)
        cell.layoutParams = TableRow.LayoutParams().apply { span = tableHeaders.size }
        row.addView(cell)
        table.addView(row)
    }

    private fun rowBgColor(index: Int, isAnchor: Boolean = false) =
            when {
                isAnchor -> 0xFF1A2A1A.toInt()
                index % 2 == 0 -> 0xFF1A1A1A.toInt()
                else -> 0xFF262626.toInt()
            }

    private fun addDataRow(
            table: TableLayout,
            index: Int,
            entry: ObaTripStatus,
            prev: ObaTripStatus?,
            fetchTime: Long,
            localFetchTime: Long,
            isAnchor: Boolean = false
    ) {
        val row =
                TableRow(this).apply {
                    setBackgroundColor(rowBgColor(index, isAnchor))
                    setOnClickListener {
                        if (selectedIndex == index) showStatusJson(index)
                        else selectDataPoint(index)
                    }
                }
        dataRows.add(row)
        val raw = entry.lastKnownLocation
        val pos = entry.position
        val entryDist = entry.distanceAlongTrip
        val updateTime = entry.lastUpdateTime
        val avlTime = entry.lastLocationUpdateTime

        // #
        row.addView(cell(if (isAnchor) "\u2693 ${index + 1}" else "${index + 1}"))
        // Timestamps
        row.addView(cell(fmtTime(localFetchTime)))
        row.addView(cell(fmtTime(fetchTime)))
        row.addView(cell(fmtTime(updateTime)))
        row.addView(cell(fmtTime(avlTime)))
        // Raw GPS
        row.addView(cell(raw?.let { "%.6f".format(it.latitude) } ?: DASH))
        row.addView(cell(raw?.let { "%.6f".format(it.longitude) } ?: DASH))
        row.addView(cell(entry.lastKnownDistanceAlongTrip?.let { "%.1f".format(it) } ?: DASH))
        row.addView(cell(entry.lastKnownOrientation?.let { "%.0f\u00B0".format(it) } ?: DASH))
        // Server estimate
        row.addView(cell(pos?.let { "%.6f".format(it.latitude) } ?: DASH))
        row.addView(cell(pos?.let { "%.6f".format(it.longitude) } ?: DASH))
        row.addView(cell(entryDist?.let { "%.1f".format(it) } ?: DASH))
        row.addView(cell(entry.scheduledDistanceAlongTrip?.let { "%.1f".format(it) } ?: DASH))
        // Trip info
        row.addView(cell(entry.vehicleId ?: DASH))
        row.addView(cell(if (entry.isPredicted) "Y" else "N"))
        row.addView(cell("${entry.scheduleDeviation}"))
        row.addView(cell(entry.phase ?: DASH))
        row.addView(cell(entry.status?.toString() ?: DASH))
        row.addView(cell(entry.nextStop ?: DASH))
        row.addView(cell(entry.nextStopTimeOffset?.let { "$it" } ?: DASH))
        // Derived deltas
        if (prev != null) {
            addDeltaCells(row, entry, prev)
        } else {
            repeat(4) { row.addView(cell("")) }
        }

        table.addView(row)
    }

    private fun addDeltaCells(row: TableRow, entry: ObaTripStatus, prev: ObaTripStatus) {
        val dtMs = entry.lastUpdateTime - prev.lastUpdateTime
        row.addView(cell("%.1f".format(dtMs / 1000.0)))

        val prevDist = prev.distanceAlongTrip
        val entryDist = entry.distanceAlongTrip
        if (prevDist != null && entryDist != null) {
            val dd = entryDist - prevDist
            row.addView(cell("%.1f".format(dd)))
            row.addView(
                    cell(
                            if (dtMs > 0)
                                    "%.1f".format(maxOf(0.0, dd) / (dtMs / 1000.0) * MPS_TO_MPH)
                            else DASH
                    )
            )
        } else {
            row.addView(cell(DASH))
            row.addView(cell(DASH))
        }

        val rawPrev = prev.lastKnownLocation
        val rawCur = entry.lastKnownLocation
        row.addView(
                cell(
                        if (rawPrev != null && rawCur != null)
                                "%.1f".format(rawPrev.distanceTo(rawCur))
                        else DASH
                )
        )
    }

    private fun fmtTime(millis: Long) = if (millis > 0) timeFmt.format(Date(millis)) else DASH

    private fun showStatusJson(index: Int) {
        if (index >= currentHistory.size) return
        val status = currentHistory[index]
        val json =
                JSONObject().apply {
                    put("serviceDate", status.serviceDate)
                    put("predicted", status.isPredicted)
                    put("scheduleDeviation", status.scheduleDeviation)
                    put("vehicleId", status.vehicleId)
                    put("activeTripId", status.activeTripId)
                    put("closestStop", status.closestStop)
                    put("closestStopTimeOffset", status.closestStopTimeOffset)
                    put("nextStop", status.nextStop)
                    put("nextStopTimeOffset", status.nextStopTimeOffset)
                    put("phase", status.phase)
                    put("status", status.status?.toString())
                    put("distanceAlongTrip", status.distanceAlongTrip)
                    put("scheduledDistanceAlongTrip", status.scheduledDistanceAlongTrip)
                    put("totalDistanceAlongTrip", status.totalDistanceAlongTrip)
                    put("orientation", status.orientation)
                    put("lastUpdateTime", status.lastUpdateTime)
                    put("lastLocationUpdateTime", status.lastLocationUpdateTime)
                    put("lastKnownDistanceAlongTrip", status.lastKnownDistanceAlongTrip)
                    put("lastKnownOrientation", status.lastKnownOrientation)
                    put("blockTripSequence", status.blockTripSequence)
                    put("occupancyStatus", status.occupancyStatus?.toString())
                    status.position?.let {
                        put(
                                "position",
                                JSONObject().put("lat", it.latitude).put("lon", it.longitude)
                        )
                    }
                    status.lastKnownLocation?.let {
                        put(
                                "lastKnownLocation",
                                JSONObject().put("lat", it.latitude).put("lon", it.longitude)
                        )
                    }
                }
        val tv =
                TextView(this).apply {
                    text = json.toString(2)
                    setPadding(32, 24, 32, 24)
                    textSize = 11f
                    setTextIsSelectable(true)
                    typeface = Typeface.MONOSPACE
                }
        AlertDialog.Builder(this)
                .setTitle("Entry #${index + 1}")
                .setView(tv)
                .setPositiveButton("Close", null)
                .show()
    }

    private fun cell(text: String) = createCell(text, isHeader = false)

    private fun createCell(text: String, isHeader: Boolean) =
            TextView(this).apply {
                this.text = text
                setPadding(PAD_H, PAD_V, PAD_H, PAD_V)
                textSize = TEXT_SIZE
                gravity = if (isHeader) Gravity.CENTER else Gravity.END
                isSingleLine = true
                setTextColor(Color.WHITE)
                if (isHeader) setTypeface(null, Typeface.BOLD)
            }
}
