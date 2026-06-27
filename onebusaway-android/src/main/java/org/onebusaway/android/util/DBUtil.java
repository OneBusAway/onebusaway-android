package org.onebusaway.android.util;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.models.ObaRoute;
import org.onebusaway.android.models.ObaStop;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.arrivals.ArrivalInfo;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

/**
 * Created by azizmb9494 on 2/20/16.
 */
public class DBUtil {
    public static void addToDB(ObaStop stop) {
        addToDB(stop.getId(), stop.getStopCode(), stop.getName(), stop.getDirection(),
                stop.getLatitude(), stop.getLongitude());
    }

    /**
     * Field-based overload, for callers (e.g. the modernized io/client DTOs) that don't have an
     * {@link ObaStop}. The {@link ObaStop} overload delegates here.
     */
    public static void addToDB(String id, String code, String name, String direction,
            double latitude, double longitude) {
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.CODE, code);
        values.put(ObaContract.Stops.NAME, MyTextUtils.formatDisplayText(name));
        values.put(ObaContract.Stops.DIRECTION, direction);
        values.put(ObaContract.Stops.LATITUDE, latitude);
        values.put(ObaContract.Stops.LONGITUDE, longitude);
        if (Application.get().getCurrentRegion() != null) {
            values.put(ObaContract.Stops.REGION_ID, Application.get().getCurrentRegion().getId());
        }
        ObaContract.Stops.insertOrUpdate(id, values, true);
    }

    public static void addRouteToDB(Context ctx, ArrivalInfo arrivalInfo){
        if (Application.get().getCurrentRegion() == null) return;

        ContentValues routeValues = new ContentValues();

        String shortName = arrivalInfo.getShortName();
        String longName = arrivalInfo.getRouteLongName();

        if (TextUtils.isEmpty(longName)) {
            longName = MyTextUtils.formatDisplayText(arrivalInfo.getHeadsign());
        }

        routeValues.put(ObaContract.Routes.SHORTNAME, shortName);
        routeValues.put(ObaContract.Routes.LONGNAME, longName);
        routeValues.put(ObaContract.Routes.REGION_ID, Application.get().getCurrentRegion().getId());

        ObaContract.Routes.insertOrUpdate(ctx, arrivalInfo.getRouteId(), routeValues, true);
    }

    public static void addRouteToDB(Context ctx, ObaRoute route){
        String shortName = route.getShortName();
        String longName = route.getLongName();

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = route.getDescription();
        }

        addRouteToDB(ctx, route.getId(), shortName, longName, route.getUrl());
    }

    /**
     * Registers a route in the recents/search provider from already-resolved display fields.
     * Used by callers that hold a Compose-side route model rather than an {@link ObaRoute}.
     */
    public static void addRouteToDB(Context ctx, String id, String shortName, String longName,
            String url) {
        if (Application.get().getCurrentRegion() == null) return;

        ContentValues routeValues = new ContentValues();
        routeValues.put(ObaContract.Routes.SHORTNAME, shortName);
        routeValues.put(ObaContract.Routes.LONGNAME, longName);
        routeValues.put(ObaContract.Routes.URL, url);
        routeValues.put(ObaContract.Routes.REGION_ID, Application.get().getCurrentRegion().getId());

        ObaContract.Routes.insertOrUpdate(ctx, id, routeValues, true);
    }
}
