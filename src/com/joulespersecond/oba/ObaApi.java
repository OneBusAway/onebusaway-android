package com.joulespersecond.oba;

import java.io.*;
import java.net.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.android.maps.GeoPoint;

public final class ObaApi {
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
    
    private static ObaResponse doRequest(String urlStr) {
        Log.d("ObaApi", "Request: "  + urlStr);
        ObaResponse response = null;
        try {
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection();
            conn.connect();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()),
                    8*1024);
            
            StringBuffer data;
            int len = conn.getContentLength();
            if (len == -1) {
                data = new StringBuffer(); // default size
            }
            else {
                data = new StringBuffer(len);
            }
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                data.append(inputLine);
            }
            response = new ObaResponse(new JSONObject(data.toString()));

        } catch (IOException e) {
            response = new ObaResponse("Unable to connect: " + e.toString());
        } catch (JSONException e) {
            response = new ObaResponse("Invalid response");
        }
        return response;
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
     * Returns the child object as a JSONObject if it exists and is an object,
     * otherwise returns an empty object.
     * 
     * @param obj The JSON parent object.
     * @param key The associated key.
     * @return The JSON object associated with the key, or an empty object.
     */
    static JSONObject getChildObj(JSONObject obj, String key) {
        try {
            return obj.getJSONObject(key);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    /**
     * Returns the object at the specified index if it exists and is an object,
     * otherwise returns an empty object.
     * 
     * @param array The JSON parent array.
     * @param index The index.
     * @return The JSON object associated with the index, or an empty object.
     */
    static JSONObject getChildObj(JSONArray array, int index) {
        try {
            return array.getJSONObject(index);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    /**
     * Returns the child object as a JSONArray if it exists and is an array,
     * otherwise returns an empty array.
     * 
     * @param obj The JSON parent object.
     * @param key The associated key.
     * @return The JSON array associated with the key, or an empty array.
     */
    static JSONArray getChildArray(JSONObject obj, String key) {
        try {
            return obj.getJSONArray(key);
        } catch (JSONException e) {
            return new JSONArray();
        }
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
