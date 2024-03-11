package org.onebusaway.android.util;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalInfo;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

/**
 * Created by azizmb9494 on 2/20/16.
 */
public class DBUtil {
    public static void addToDB(ObaStop stop) {
        String name = UIUtils.formatDisplayText(stop.getName());

        // Update the database
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.CODE, stop.getStopCode());
        values.put(ObaContract.Stops.NAME, name);
        values.put(ObaContract.Stops.DIRECTION, stop.getDirection());
        values.put(ObaContract.Stops.LATITUDE, stop.getLatitude());
        values.put(ObaContract.Stops.LONGITUDE, stop.getLongitude());
        if (Application.get().getCurrentRegion() != null) {
            values.put(ObaContract.Stops.REGION_ID, Application.get().getCurrentRegion().getId());
        }
        ObaContract.Stops.insertOrUpdate(stop.getId(), values, true);
    }

    public static void addRouteToDB(Context ctx, ArrivalInfo arrivalInfo){
        if (Application.get().getCurrentRegion() == null) return;

        ContentValues routeValues = new ContentValues();

        String shortName = arrivalInfo.getInfo().getShortName();
        String longName = arrivalInfo.getInfo().getRouteLongName();

        if (TextUtils.isEmpty(longName)) {
            longName = UIUtils.formatDisplayText(arrivalInfo.getInfo().getHeadsign());
        }

        routeValues.put(ObaContract.Routes.SHORTNAME, shortName);
        routeValues.put(ObaContract.Routes.LONGNAME, longName);
        routeValues.put(ObaContract.Routes.REGION_ID, Application.get().getCurrentRegion().getId());

        ObaContract.Routes.insertOrUpdate(ctx, arrivalInfo.getInfo().getRouteId(), routeValues, true);
    }

    public static void addRouteToDB(Context ctx, ObaRoute route){
        if (Application.get().getCurrentRegion() == null) return;

        ContentValues routeValues = new ContentValues();

        String shortName = route.getShortName();
        String longName = route.getLongName();

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = route.getDescription();
        }

        routeValues.put(ObaContract.Routes.SHORTNAME, shortName);
        routeValues.put(ObaContract.Routes.LONGNAME, longName);
        routeValues.put(ObaContract.Routes.URL, route.getUrl());
        routeValues.put(ObaContract.Routes.REGION_ID, Application.get().getCurrentRegion().getId());

        ObaContract.Routes.insertOrUpdate(ctx, route.getId(), routeValues, true);
    }
}
