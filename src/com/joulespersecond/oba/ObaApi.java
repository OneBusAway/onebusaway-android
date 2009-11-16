package com.joulespersecond.oba;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;

import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ObaApi {
    private static final String TAG = "ObaApi";
    // Uninstantiatable
    private ObaApi() { throw new AssertionError(); }
    
    public static final int OBA_OK = 200;
    public static final int OBA_BAD_REQUEST = 400;
    public static final int OBA_UNAUTHORIZED = 401;
    public static final int OBA_NOT_FOUND = 404;
    public static final int OBA_INTERNAL_ERROR = 500;
    
    private static final String API_KEY = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20=";
    // NOTE: This could be provided by Settings to use different versions of the server.
    private static final String OBA_URL = "http://api.onebusaway.org/api/where";
    
    public static final double E6 = 1000*1000;
    
    private static class GsonHolder {
        @SuppressWarnings("unchecked")
        static final Gson gsonObj = new GsonBuilder()
            .registerTypeAdapter(ObaArray.class, new ObaArray.Deserializer())
            
            .registerTypeAdapter(ObaAgency.class, 
                    new JsonHelp.CachingDeserializer<ObaAgency>(
                            new ObaAgency.Deserialize(), "id"))
                            
            .registerTypeAdapter(ObaRoute.class,
                    new JsonHelp.CachingDeserializer<ObaRoute>(
                            new ObaRoute.Deserialize(), "id"))
                            
            .registerTypeAdapter(ObaStop.class,
                    new JsonHelp.CachingDeserializer<ObaStop>(
                            new ObaStop.Deserialize(), "id"))
                            
            .create();
    }

    static Gson getGson() {
        return GsonHolder.gsonObj;
    }
    
    private static ObaResponse doRequest(String urlStr) {
        Log.d(TAG, "Request: "  + urlStr);
        try {
            return ObaResponse.createFromURL(new URL(urlStr));
        } catch (IOException e) {
            e.printStackTrace();
            return ObaResponse.createFromError(e.toString());
        }
    }

    
    /**
     * Retrieves a stop by its full ID.
     * 
     * @param id The stop ID. 
     * @return A response object.
     */
    public static ObaResponse getStopById(String id) {
        // We can do a simple format since we're not expecting the id needs escaping.
        return doRequest(
                String.format("%s/stop/%s.json?key=%s", OBA_URL, id, API_KEY));
    }
    /**
     * Retrieves a route by its full ID.
     * 
     * @param id The route ID.
     * @return A response object.
     */
    public static ObaResponse getRouteById(String id) {
        return doRequest(
                String.format("%s/route/%s.json?key=%s", OBA_URL, id, API_KEY));
    }
    /**
     * Search for stops by a location in a specified radius, 
     * with an optional stop ID query.
     * 
     * @param location The latitude/longitude of the search center.
     * @param radius The optional search radius in meters.
     * @param latSpan Optional latitude height of the search area centered on lat.
     * @param lonSpan Optional longitude width of the search area centered on lon.
     * @param query Optional stop ID to search for.
     * @param maxCount Optional maximum number of stop entries to return.
     * @return A response object.
     */
    public static ObaResponse getStopsByLocation(GeoPoint location, 
            int radius, 
            int latSpan,
            int lonSpan,
            String query,
            int maxCount) {
        String url = String.format("%s/stops-for-location.json?key=%s&lat=%f&lon=%f", 
                OBA_URL, 
                API_KEY,
                (double)location.getLatitudeE6()/E6,
                (double)location.getLongitudeE6()/E6);
        if (radius != 0) {
            url += "&radius=";
            url += String.valueOf(radius);
        }
        if (latSpan != 0) {
            url += "&latSpan=";
            url += String.valueOf(latSpan/E6);
        }
        if (lonSpan != 0) {
            url += "&lonSpan=";
            url += String.valueOf(lonSpan/E6);
        }
        if (query != null) {
            url += "&query=";
            try {
                url += URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null; 
            }
        }
        if (maxCount != 0) {
            url += "&maxCount=";
            url += String.valueOf(maxCount);
        }
         return doRequest(url);
    }
    /**
     * Search for routes by location in a specified radius, 
     * with an optional route name query term.
     * 
     * @param location The location of the search center.
     * @param radius The optional search radius in meters.
     * @param query The optional route name to search for.
     * @return A response object.
     */
    public static ObaResponse getRoutesByLocation(GeoPoint location,
            int radius,
            String query) {
        StringBuilder url = new StringBuilder(
                String.format("%s/routes-for-location.json?key=%s&lat=%f&lon=%f", 
                OBA_URL, 
                API_KEY,
                (double)location.getLatitudeE6()/E6,
                (double)location.getLongitudeE6()/E6));
        if (radius != 0) {
            url.append("&radius=");
            url.append(String.valueOf(radius));
        }
        if (query != null) {
            url.append("&query=");
            try {
                url.append(URLEncoder.encode(query, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null; 
            }
        }
         return doRequest(url.toString());
    }
    /**
     * Get a list of stops that are services by a given route, including 
     * ordered list of stops for each direction of service, and polylines
     * mapping the route where available.
     * 
     * @param id The route ID.
     * @return A response object.
     */
    public static ObaResponse getStopsForRoute(String id) {
        return doRequest(
                String.format("%s/stops-for-route/%s.json?key=%s", OBA_URL, id, API_KEY));
    }
    /**
     * Get current arrivals and departures for routes serving the specified stop.
     * When available, real-time arrival and departure preditions will be
     * provided in addition to static schedule information.
     * 
     * @param id The stop ID.
     * @return true if successful, false otherwise.
     */
    public static ObaResponse getArrivalsDeparturesForStop(String id) {
        return doRequest(
                String.format("%s/arrivals-and-departures-for-stop/%s.json?key=%s", OBA_URL, id, API_KEY));
    }
    
    /**
     * Converts a latitude/longitude to a GeoPoint.
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A GeoPoint representing this latitude/longitude.
     */
    public static final GeoPoint makeGeoPoint(double lat, double lon) {
        return new GeoPoint((int)(lat*E6), (int)(lon*E6));
    }
}
