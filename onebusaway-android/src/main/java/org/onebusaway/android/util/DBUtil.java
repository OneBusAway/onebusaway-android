package org.onebusaway.android.util;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalInfo;

import android.content.ContentValues;
import android.content.Context;


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
        ContentValues routeValues = new ContentValues();

        routeValues.put(ObaContract.Routes.SHORTNAME, arrivalInfo.getInfo().getShortName());
        routeValues.put(ObaContract.Routes.LONGNAME, arrivalInfo.getInfo().getRouteLongName());

        if (Application.get().getCurrentRegion() != null) {
            routeValues.put(ObaContract.Routes.REGION_ID,
                    Application.get().getCurrentRegion().getId());
        }
        ObaContract.Routes.insertOrUpdate(ctx, arrivalInfo.getInfo().getRouteId(), routeValues, true);
    }

    public static void addRouteToDB(Context ctx, ObaRoute route){
        ContentValues routeValues = new ContentValues();

        routeValues.put(ObaContract.Routes.SHORTNAME, route.getShortName());
        routeValues.put(ObaContract.Routes.LONGNAME, route.getLongName());

        if (Application.get().getCurrentRegion() != null) {
            routeValues.put(ObaContract.Routes.REGION_ID,
                    Application.get().getCurrentRegion().getId());
        }
        ObaContract.Routes.insertOrUpdate(ctx, route.getId(), routeValues, true);
    }
}
