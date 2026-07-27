/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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
package org.onebusaway.android.util;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.api.bridge.RegionsClient;
import org.onebusaway.android.app.di.PreferencesEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.region.Region;

/** A class containing utility methods related to handling multiple regions in OneBusAway */
public class RegionUtils {

  private static final String TAG = "RegionUtils";

  public static final int TAMPA_REGION_ID = 0;

  public static final int PUGET_SOUND_REGION_ID = 1;

  public static final int ATLANTA_REGION_ID = 3;

  public static final double METERS_TO_MILES = 0.000621371;

  public static final double METERS_TO_FEET = 3.28084;

  private static final int DISTANCE_LIMITER = 100; // miles

  /**
   * Get the closest region from a list of regions and a given location
   *
   * <p>This method also enforces the constraints in isRegionUsable() to ensure the returned region
   * is actually usable by the app
   *
   * @param regions list of regions
   * @param loc location
   * @param enforceThreshold true if the DISTANCE_LIMITER threshold should be enforced, false if it
   *     should not
   * @return the closest region to the given location from the list of regions, or null if a
   *     enforceThreshold is true and the closest region exceeded DISTANCE_LIMITER threshold or a
   *     region couldn't be found
   */
  public static @Nullable Region getClosestRegion(
      @NonNull Context context,
      @NonNull List<Region> regions,
      @Nullable Location loc,
      boolean enforceThreshold) {
    if (loc == null) {
      return null;
    }
    float minDist = Float.MAX_VALUE;
    Region closestRegion = null;
    Float distToRegion;

    NumberFormat fmt = NumberFormat.getInstance();
    if (fmt instanceof DecimalFormat) {
      ((DecimalFormat) fmt).setMaximumFractionDigits(1);
    }
    double miles;

    if (BuildConfig.DEBUG)
      Log.d(TAG, "Finding region closest to " + loc.getLatitude() + "," + loc.getLongitude());

    for (Region region : regions) {
      if (!isRegionUsable(context, region)) {
        Log.d(TAG, "Excluding '" + region.getName() + "' from 'closest region' consideration");
        continue;
      }

      distToRegion = getDistanceAway(region, loc.getLatitude(), loc.getLongitude());
      if (distToRegion == null) {
        Log.e(TAG, "Couldn't measure distance to region '" + region.getName() + "'");
        continue;
      }
      miles = distToRegion * METERS_TO_MILES;
      if (BuildConfig.DEBUG)
        Log.d(TAG, "Region '" + region.getName() + "' is " + fmt.format(miles) + " miles away");
      if (distToRegion < minDist) {
        closestRegion = region;
        minDist = distToRegion;
      }
    }

    if (enforceThreshold) {
      if (minDist * METERS_TO_MILES < DISTANCE_LIMITER) {
        return closestRegion;
      } else {
        return null;
      }
    }
    return closestRegion;
  }

  /**
   * Get the region name if it is available. If there is a custom url instead of a region from the
   * region api, then hash the custom url and return it.
   *
   * @return regionName
   */
  public static @Nullable String getObaRegionName(@NonNull Context context) {
    String regionName = null;
    Region region = RegionEntryPoint.get(context).currentRegion();
    if (region != null && region.getName() != null) {
      regionName = region.getName();
    } else {
      String customApiUrl =
          PreferencesEntryPoint.get(context)
              .getString(context.getString(R.string.preference_key_oba_api_url), (String) null);
      if (customApiUrl != null) {
        regionName = CustomApiUrlLabel.forUrl(context, customApiUrl);
      }
    }
    return regionName;
  }

  /**
   * Returns the distance from the specified location to the center of the closest bound in this
   * region.
   *
   * @return distance from the specified location to the center of the closest bound in this region,
   *     in meters
   */
  public static @Nullable Float getDistanceAway(@NonNull Region region, double lat, double lon) {
    Region.Bounds[] bounds = region.getBounds();
    // No bounds at all means the distance is unknown, not enormous. An *empty* array used to fall
    // through the loop below and return the Float.MAX_VALUE seed, which is 2.1e35 miles once
    // converted — the regions list rendered that verbatim, and getClosestRegion's "couldn't measure
    // the distance" branch was unreachable for such a region. A custom region added by an
    // `onebusaway://add-region` link (#2027) carries no bounds, which is how that surfaced.
    if (bounds == null || bounds.length == 0) {
      return null;
    }
    float[] results = new float[1];
    float minDistance = Float.MAX_VALUE;
    for (Region.Bounds bound : bounds) {
      Location.distanceBetween(lat, lon, bound.getLat(), bound.getLon(), results);
      if (results[0] < minDistance) {
        minDistance = results[0];
      }
    }
    return minDistance;
  }

  public static @Nullable Float getDistanceAway(@NonNull Region region, @NonNull Location loc) {
    return getDistanceAway(region, loc.getLatitude(), loc.getLongitude());
  }

  /**
   * Returns the center and lat/lon span for the entire region.
   *
   * @param results Array to receive results. results[0] == latSpan of region results[1] == lonSpan
   *     of region results[2] == lat center of region results[3] == lon center of region
   */
  public static void getRegionSpan(@NonNull Region region, @NonNull double[] results) {
    if (results.length < 4) {
      throw new IllegalArgumentException("Results array is < 4");
    }
    if (region == null) {
      throw new IllegalArgumentException("Region is null");
    }
    double latMin = 90;
    double latMax = -90;
    double lonMin = 180;
    double lonMax = -180;

    // This is fairly simplistic
    for (Region.Bounds bound : region.getBounds()) {
      // Get the top bound
      double lat = bound.getLat();
      double latSpanHalf = bound.getLatSpan() / 2.0;
      double lat1 = lat - latSpanHalf;
      double lat2 = lat + latSpanHalf;
      if (lat1 < latMin) {
        latMin = lat1;
      }
      if (lat2 > latMax) {
        latMax = lat2;
      }

      double lon = bound.getLon();
      double lonSpanHalf = bound.getLonSpan() / 2.0;
      double lon1 = lon - lonSpanHalf;
      double lon2 = lon + lonSpanHalf;
      if (lon1 < lonMin) {
        lonMin = lon1;
      }
      if (lon2 > lonMax) {
        lonMax = lon2;
      }
    }

    results[0] = latMax - latMin;
    results[1] = lonMax - lonMin;
    results[2] = latMin + ((latMax - latMin) / 2.0);
    results[3] = lonMin + ((lonMax - lonMin) / 2.0);
  }

  /**
   * Determines if the provided location is within the provided region span
   *
   * <p>Note: This does not handle cases when the region span crosses the International Date Line
   * properly
   *
   * @param location that will be compared to the provided regionSpan
   * @param regionSpan span information for the region regionSpan[0] == latSpan of region
   *     regionSpan[1] == lonSpan of region regionSpan[2] == lat center of region regionSpan[3] ==
   *     lon center of region
   * @return true if the location is within the region span, false if it is not
   */
  public static boolean isLocationWithinRegion(
      @NonNull Location location, @NonNull double[] regionSpan) {
    if (regionSpan == null || regionSpan.length < 4) {
      throw new IllegalArgumentException("regionSpan is null or has length < 4");
    }

    if (location == null
        || location.getLongitude() > 180.0
        || location.getLongitude() < -180.0
        || location.getLatitude() > 90
        || location.getLatitude() < -90) {
      throw new IllegalArgumentException("Location must be a valid location");
    }

    double minLat = regionSpan[2] - (regionSpan[0] / 2);
    double minLon = regionSpan[3] - (regionSpan[1] / 2);
    double maxLat = regionSpan[2] + (regionSpan[0] / 2);
    double maxLon = regionSpan[3] + (regionSpan[1] / 2);

    return minLat <= location.getLatitude()
        && location.getLatitude() <= maxLat
        && minLon <= location.getLongitude()
        && location.getLongitude() <= maxLon;
  }

  /**
   * Determines if the provided location is within the provided region
   *
   * <p>Note: This does not handle cases when the region span crosses the International Date Line
   * properly
   *
   * @param location that will be compared to the provided region
   * @param region provided region
   * @return true if the location is within the region, false if it is not
   */
  public static boolean isLocationWithinRegion(@NonNull Location location, @NonNull Region region) {
    double[] regionSpan = new double[4];
    getRegionSpan(region, regionSpan);
    return isLocationWithinRegion(location, regionSpan);
  }

  /**
   * Checks if the given region is usable by the app, based on what this app supports - Is the
   * region active? - Does the region support the OBA Discovery APIs? - Does the region support the
   * OBA Realtime APIs? - Is the region experimental, and if so, did the user opt-in via
   * preferences?
   *
   * @param region region to be checked
   * @return true if the region is usable by this application, false if it is not
   */
  public static boolean isRegionUsable(@NonNull Context context, @NonNull Region region) {
    if (!region.getActive()) {
      Log.d(TAG, "Region '" + region.getName() + "' is not active.");
      return false;
    }
    if (!region.getSupportsObaDiscoveryApis()) {
      Log.d(TAG, "Region '" + region.getName() + "' does not support OBA Discovery APIs.");
      return false;
    }
    if (!region.getSupportsObaRealtimeApis()) {
      Log.d(TAG, "Region '" + region.getName() + "' does not support OBA Realtime APIs.");
      return false;
    }
    if (region.getExperimental()
        && !PreferenceUtils.getBoolean(
            context.getString(R.string.preference_key_experimental_regions), false)) {
      Log.d(TAG, "Region '" + region.getName() + "' is experimental and user hasn't opted in.");
      return false;
    }

    return true;
  }

  /**
   * Format the OTP base URL so query parameters can be added safely.
   *
   * @param baseUrl OpenTripPlanner base URL from the Region
   * @return OTP server URL with trailing slash trimmed.
   */
  public static @NonNull String formatOtpBaseUrl(@NonNull String baseUrl) {
    return baseUrl.replaceFirst("/$", "");
  }

  public static synchronized @NonNull ArrayList<Region> getRegionsFromServer(
      @NonNull Context context) {
    return new ArrayList<Region>(RegionsClient.fetchRegionsFromServer(context));
  }

  /**
   * Retrieves region information from a regions file bundled within the app APK
   *
   * <p>IMPORTANT - this should be a last resort, and we should always try to pull regions info from
   * the cached regions or the Regions REST API instead of from the bundled file.
   *
   * <p>This method is only intended to be a fail-safe in case the Regions REST API goes offline and
   * a user downloads and installs OBA Android during that period (i.e., local OBA servers are
   * available, but Regions REST API failure would block initial execution of the app). This avoids
   * a potential central point of failure for OBA Android installations on devices in multiple
   * regions.
   *
   * @return list of regions retrieved from the regions file in app resources
   */
  public static @NonNull ArrayList<Region> getRegionsFromResources(@NonNull Context context) {
    return new ArrayList<Region>(RegionsClient.parseBundledRegions(context));
  }

  /**
   * The region's Open311 servers, or an <em>empty</em> array when the flavor configures no Open311
   * base URL.
   *
   * <p>Empty rather than null on purpose: {@link Region#getOpen311Servers()} is a non-null Kotlin
   * property (defaulting to {@code emptyArray()}), so handing it null crashes the constructor. A
   * flavor omitting {@code FIXED_REGION_OPEN311_BASE_URL} is an ordinary configuration — "this
   * agency has no Open311 endpoint" — not an error, so it must produce a region with no Open311
   * servers rather than no region at all.
   *
   * <p>Blank counts as unconfigured alongside null. A {@code buildConfigField} is written by hand
   * in a Groovy flavor file, where "no endpoint" is spelled {@code "null"} by convention but {@code
   * "\"\""} is the equally natural typo, and the two must not mean different things — an empty base
   * URL would otherwise register a live-but-broken endpoint with {@code Open311Manager}. This is
   * normalizing two spellings of "unset" at the config boundary, not inferring intent from the
   * value: the same rule is already applied to the sibling field one layer down (Open311Subsystem's
   * {@code jurisdictionId?.takeIf { it.isNotEmpty() }}) and to the OTP2 endpoint's configured/not
   * test ({@code Region.usesOtp2}).
   */
  @VisibleForTesting
  static @NonNull Region.Open311Server[] open311ServersFrom(
      @Nullable String jurisdictionId, @Nullable String apiKey, @Nullable String baseUrl) {
    if (baseUrl == null || baseUrl.trim().isEmpty()) {
      return new Region.Open311Server[0];
    }
    return new Region.Open311Server[] {new Region.Open311Server(jurisdictionId, apiKey, baseUrl)};
  }

  /**
   * Retrieves hard-coded region information from the build flavor defined in build.gradle. If a
   * fixed region is defined in a build flavor, it does not allow region roaming.
   *
   * <p>Only reached when {@code USE_FIXED_REGION} is true, so every {@code FIXED_REGION_*} field
   * the non-null half of {@link Region} depends on must actually be configured. The one that isn't
   * nullable in {@code Region} — and is literally {@code null} in the flavors that don't use a
   * fixed region — is the name, so it is checked by hand: R8 rewrites Kotlin's parameter null-check
   * into {@code Object.getClass()}, which in a release build reports only "Attempt to invoke
   * virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference" and
   * names no field. A rebrander who forgets a {@code buildConfigField} should be told which one.
   *
   * @return hard-coded region information from the build flavor defined in build.gradle
   * @throws NullPointerException if the flavor sets USE_FIXED_REGION without a region name
   */
  public static @NonNull Region getRegionFromBuildFlavor() {
    final int regionId = Integer.MAX_VALUE; // This doesn't get used, but needs to be positive
    final String name =
        Objects.requireNonNull(
            BuildConfig.FIXED_REGION_NAME,
            "FIXED_REGION_NAME must be set in the build flavor when USE_FIXED_REGION is true");
    Region.Bounds[] boundsArray = new Region.Bounds[1];
    Region.Bounds bounds =
        new Region.Bounds(
            BuildConfig.FIXED_REGION_BOUNDS_LAT, BuildConfig.FIXED_REGION_BOUNDS_LON,
            BuildConfig.FIXED_REGION_BOUNDS_LAT_SPAN, BuildConfig.FIXED_REGION_BOUNDS_LON_SPAN);
    boundsArray[0] = bounds;

    Region.Open311Server[] open311Array =
        open311ServersFrom(
            BuildConfig.FIXED_REGION_OPEN311_JURISDICTION_ID,
            BuildConfig.FIXED_REGION_OPEN311_API_KEY,
            BuildConfig.FIXED_REGION_OPEN311_BASE_URL);

    Region region =
        new Region(
            regionId,
            name,
            true,
            BuildConfig.FIXED_REGION_OBA_BASE_URL,
            BuildConfig.FIXED_REGION_SIRI_BASE_URL,
            boundsArray,
            open311Array,
            BuildConfig.FIXED_REGION_LANG,
            BuildConfig.FIXED_REGION_CONTACT_EMAIL,
            BuildConfig.FIXED_REGION_SUPPORTS_OBA_DISCOVERY_APIS,
            BuildConfig.FIXED_REGION_SUPPORTS_OBA_REALTIME_APIS,
            BuildConfig.FIXED_REGION_SUPPORTS_SIRI_REALTIME_APIS,
            BuildConfig.FIXED_REGION_TWITTER_URL,
            false,
            BuildConfig.FIXED_REGION_STOP_INFO_URL,
            BuildConfig.FIXED_REGION_OTP_BASE_URL,
            BuildConfig.FIXED_REGION_OTP_CONTACT_EMAIL,
            BuildConfig.FIXED_REGION_SUPPORTS_OTP_BIKESHARE,
            null, // otpBaseGraphqlUrl: the fixed-region build flavor doesn't support OTP2 yet
            false, // supportsOtpGraphqlBikeshare: moot without an OTP2 endpoint, above
            false,
            BuildConfig.FIXED_REGION_PAYMENT_ANDROID_APP_ID,
            BuildConfig.FIXED_REGION_PAYMENT_WARNING_TITLE,
            BuildConfig.FIXED_REGION_PAYMENT_WARNING_BODY,
            BuildConfig.FIXED_REGION_SIDECAR_BASE_URL,
            BuildConfig.FIXED_REGION_PLAUSIBLE_ANALYTICS_SERVER_URL,
            null, // No Umami config for the fixed-region build flavor
            false); // custom: this is the build flavor's own region, not one a rider added (#2027)
    return region;
  }
}
