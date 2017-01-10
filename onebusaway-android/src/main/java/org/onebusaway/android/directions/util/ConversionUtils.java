/*
 * Copyright 2012 University of South Florida
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;

import android.content.Context;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.util.Log;

import org.onebusaway.android.R;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.routing.core.TraverseMode;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;


/**
 * @author Khoa Tran
 * @author Simon Jacobs - conversion for OBA. Added Imperial measurements
 */

public class ConversionUtils {

    private static final String TAG = "ConversionUtils";

    private static final double FEET_PER_METER = 3.281;

    /**
     * Given a date string from an OTP server, parse it into a java Date.
     *
     * @param text string from OTP
     * @return parsed date object, or null if there is an error with parsing.
     */
    public static Date parseOtpDate(String text) {
        try {
            long ms = Long.parseLong(text);
            return new Date(ms);
        } catch (NumberFormatException ex) {
            Log.e(TAG, "Error processing OTP response time text: " + text);
            return null;
        }
    }

    /**
     * Given start time and end time strings, compute the delta between them.
     * Strings should be in the OTP server return format, specified in OTPConstants
     *
     * @param startTimeText start time
     * @param endTimeText end time
     * @param applicationContext context to look up resources
     * @return duration
     */
    public static double getDuration(String startTimeText, String endTimeText,
                                     Context applicationContext) {
        double duration = 0;

        Date startTime = parseOtpDate(startTimeText);
        Date endTime = parseOtpDate(endTimeText);

        if (startTime != null && endTime != null) {
            if (PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    .getInt(OTPConstants.PREFERENCE_KEY_API_VERSION, OTPConstants.API_VERSION_V1)
                    == OTPConstants.API_VERSION_V1){
                duration = (endTime.getTime() - startTime.getTime());
            } else {
                duration = (endTime.getTime() - startTime.getTime()) / 1000;
            }
        }
        return duration;
    }

    /**
     * Return a formatted String for a distance. Should be in proper units according to
     * preferences (either metric or imperial).
     *
     * @param meters distance in meters
     * @param applicationContext context to look up resources
     * @return formatted string of distance
     */
    public static String getFormattedDistance(Double meters, Context applicationContext) {
        String text = "";

        if (PreferenceUtils.getUnitsAreMetricFromPreferences(applicationContext)) {
            if (meters < 1000) {
                text += String.format(OTPConstants.FORMAT_DISTANCE_METERS, meters) + " " + applicationContext
                        .getResources().getString(R.string.meters_abbreviation);
            } else {
                meters = meters / 1000;
                text += String.format(OTPConstants.FORMAT_DISTANCE_KILOMETERS, meters) + " "
                        + applicationContext.getResources().getString(R.string.kilometers_abbreviation);
            }
        } else {
            double feet = meters * 3.281;
            if (feet < 1000) {
                text += String.format(OTPConstants.FORMAT_DISTANCE_METERS, feet) + " " + applicationContext
                        .getResources().getString(R.string.feet_abbreviation);
            } else {
                feet = feet / 5280;
                text += String.format(OTPConstants.FORMAT_DISTANCE_KILOMETERS, feet) + " "
                        + applicationContext.getResources().getString(R.string.miles_abbreviation);
            }
        }
        return text;
    }

    /**
     * Get a formatted string for a duration.
     *
     * @param sec duration in seconds
     * @param applicationContext context to look up resources
     * @return formatted duration string
     */
    public static String getFormattedDurationText(long sec, Context applicationContext) {
        String text = "";
        long h = sec / 3600;
        if (h >= 24) {
            return null;
        }
        long m = (sec % 3600) / 60;
        long s = (sec % 3600) % 60;
        if (h > 0) {
            text += Long.toString(h) + applicationContext.getResources()
                    .getString(R.string.hours_abbreviation);
        }
        text += Long.toString(m) + applicationContext.getResources()
                .getString(R.string.minutes_abbreviation);
        text += Long.toString(s) + applicationContext.getResources()
                .getString(R.string.seconds_abbrevation);
        return text;
    }

    /**
     * Get duration text but disallow "seconds" units.
     *
     * @param sec duration in seconds
     * @param longFormat true for long units ("minutes"), false for short units ("min")
     * @param applicationContext context to look up resources
     * @return formatted duration text
     */
    public static String getFormattedDurationTextNoSeconds(long sec, boolean longFormat, Context applicationContext) {
        String text = "";
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        String longMinutes = applicationContext.getResources()
                .getString(R.string.minutes_full);
        String longMinutesSingular = applicationContext.getResources()
                .getString(R.string.minutes_abbreviation);
        String shortMinutes = applicationContext.getResources()
                .getString(R.string.minutes_abbreviation);
        if (longFormat) {
            longMinutes = applicationContext.getResources()
                    .getString(R.string.minutes_full);
            longMinutesSingular = applicationContext.getResources()
                    .getString(R.string.minute_singular);
        }
        String shortHours = applicationContext.getResources()
                .getString(R.string.hours_abbreviation);
        if (h > 0) {
            text += Long.toString(h) + shortHours;
            text += " " + Long.toString(m) + shortMinutes;
        } else {
            if (m == 0) {
                text += "< 1 " + longMinutes;
            } else if (m == 1 || m == -1) {
                text += Long.toString(m) + " " + longMinutesSingular;
            } else {
                text += Long.toString(m) + " " + longMinutes;
            }
        }
        return text;
    }

    public static List<Itinerary> fixTimezoneOffsets(List<Itinerary> itineraries,
                                                     boolean useDeviceTimezone) {
        int agencyTimeZoneOffset = 0;
        boolean containsTransitLegs = false;

        if ((itineraries != null) && !itineraries.isEmpty()) {
            ArrayList<Itinerary> itinerariesFixed = new ArrayList<Itinerary>(itineraries);

            for (Itinerary it : itinerariesFixed) {
                for (Leg leg : it.legs) {
                    if ((TraverseMode.valueOf((String) leg.mode)).isTransit()
                            && !containsTransitLegs) {
                        containsTransitLegs = true;
                    }
                    if (leg.agencyTimeZoneOffset != 0) {
                        agencyTimeZoneOffset = leg.agencyTimeZoneOffset;
                        //If agencyTimeZoneOffset is different from 0, route contains transit legs
                        containsTransitLegs = true;
                        break;
                    }
                }
            }

            if (useDeviceTimezone || !containsTransitLegs) {
                agencyTimeZoneOffset = TimeZone.getDefault()
                        .getOffset(Long.parseLong(itinerariesFixed.get(0).startTime));
            }

            if (agencyTimeZoneOffset != 0) {
                for (Itinerary it : itinerariesFixed) {
                    for (Leg leg : it.legs) {
                        leg.agencyTimeZoneOffset = agencyTimeZoneOffset;
                    }
                }
            }

            return itinerariesFixed;
        } else {
            return itineraries;
        }
    }

    public static CharSequence getTimeWithContext(Context applicationContext, int offsetGMT, long time,
                                                  boolean inLine) {
        return getTimeWithContext(applicationContext, offsetGMT, time, inLine, -1);
    }

    public static CharSequence getTimeWithContext(Context applicationContext, int offsetGMT, long time,
                                                  boolean inLine, int color) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext);
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext);
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        cal.setTimeInMillis(time);

        String noDeviceTimezoneNote = "";
        if (offsetGMT != TimeZone.getDefault().getOffset(time)) {
            noDeviceTimezoneNote = "GMT";
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000;
            }
        }

        cal.add(Calendar.MILLISECOND, offsetGMT);

        if (cal.get(Calendar.SECOND) >= 30) {
            cal.add(Calendar.MINUTE, 1);
        }

        SpannableString spannableTime = new SpannableString(timeFormat.format(cal.getTime()));
        if (color != -1) {
            spannableTime.setSpan(new ForegroundColorSpan(color), 0,
                    spannableTime.length(), 0);
        }

        if (inLine) {
            if (ConversionUtils.isToday(cal)) {
                return TextUtils.concat(" ",
                        applicationContext
                                .getResources()
                                .getString(R.string.time_connector_before_time), " ", spannableTime,
                        " ", noDeviceTimezoneNote);
            } else if (ConversionUtils.isTomorrow(cal)) {
                return TextUtils.concat(" ",
                        applicationContext
                                .getResources()
                                .getString(R.string.time_connector_next_day), " ",
                        applicationContext.getResources()
                                .getString(R.string.time_connector_before_time), " ", spannableTime,
                        " ", noDeviceTimezoneNote);
            } else {
                return TextUtils.concat(" ", applicationContext
                                .getResources()
                                .getString(R.string.time_connector_before_date), " ",
                        dateFormat.format(cal.getTime()), " ",
                        applicationContext.getResources()
                                .getString(R.string.time_connector_before_time), " ", spannableTime,
                        " ", noDeviceTimezoneNote);
            }
        } else {
            if (ConversionUtils.isToday(cal)) {
                return TextUtils.concat(spannableTime, " ", noDeviceTimezoneNote);
            } else if (ConversionUtils.isTomorrow(cal)) {
                return TextUtils.concat(" ", spannableTime, ", ",
                        applicationContext
                                .getResources()
                                .getString(R.string.time_connector_next_day), " ",
                        noDeviceTimezoneNote);
            } else {
                return TextUtils.concat(spannableTime, ", ", dateFormat.format(cal.getTime()), " ",
                        noDeviceTimezoneNote);
            }
        }
    }

    public static CharSequence getTimeUpdated(Context applicationContext, int offsetGMT, long oldTime,
                                              long newTime) {
        Calendar calOldTime = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Calendar calNewTime = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext);
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext);
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        CharSequence timeUpdatedString;
        CharSequence beforeDateString = "", beforeTimeString = "", newDateString = "", timezone = "";
        SpannableString oldTimeString, oldDateString, newTimeString;
        calOldTime.setTimeInMillis(oldTime);
        calNewTime.setTimeInMillis(newTime);

        String noDeviceTimezoneNote = "";
        if (offsetGMT != TimeZone.getDefault().getOffset(oldTime)) {
            noDeviceTimezoneNote = "GMT";
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000;
            }
        }

        calOldTime.add(Calendar.MILLISECOND, offsetGMT);
        calNewTime.add(Calendar.MILLISECOND, offsetGMT);

        if (ConversionUtils.isTomorrow(calNewTime)) {
            oldDateString = new SpannableString(applicationContext.getResources()
                    .getString(R.string.time_connector_next_day) + " ");
        } else {
            beforeDateString = applicationContext.getResources()
                    .getString(R.string.time_connector_before_date) + " ";
            oldDateString = new SpannableString(dateFormat.format(calNewTime.getTime()) + " ");
        }

        if (calNewTime.get(Calendar.DAY_OF_MONTH) != calOldTime.get(Calendar.DAY_OF_MONTH)) {
            beforeDateString = applicationContext.getResources()
                    .getString(R.string.time_connector_before_date) + " ";
            newDateString = dateFormat.format(calNewTime.getTime()) + " ";
            oldDateString.setSpan(new StrikethroughSpan(), 0, oldDateString.length() - 1, 0);
        }

        beforeTimeString = applicationContext.getResources()
                .getString(R.string.time_connector_before_time) + " ";
        timezone = noDeviceTimezoneNote;

        int color = applicationContext.getResources().getColor(
                ArrivalInfoUtils.computeColorFromDeviation(
                        calNewTime.getTimeInMillis() - calOldTime.getTimeInMillis()));

        newTimeString = new SpannableString(timeFormat.format(calNewTime.getTime()) + " ");
        newTimeString.setSpan(new ForegroundColorSpan(color), 0, newTimeString.length(), 0);
        if (calOldTime.get(Calendar.HOUR_OF_DAY) != calNewTime.get(Calendar.HOUR_OF_DAY)
                || calOldTime.get(Calendar.MINUTE) != calNewTime.get(Calendar.MINUTE)) {
            oldTimeString = new SpannableString(timeFormat.format(calOldTime.getTime()) + " ");
            oldTimeString.setSpan(new StrikethroughSpan(), 0, oldTimeString.length() - 1, 0);
        } else {
            oldTimeString = new SpannableString(" ");
        }
        timeUpdatedString = TextUtils.concat(beforeDateString, newDateString, oldDateString,
                beforeTimeString, oldTimeString, newTimeString, timezone);
        return timeUpdatedString;
    }

    public static boolean isToday(Calendar cal) {
        Calendar actualTime = Calendar.getInstance();
        return (actualTime.get(Calendar.ERA) == cal.get(Calendar.ERA) &&
                actualTime.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                actualTime.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR));
    }

    public static boolean isTomorrow(Calendar cal) {
        Calendar tomorrowTime = Calendar.getInstance();
        tomorrowTime.add(Calendar.DAY_OF_YEAR, 1);
        return (tomorrowTime.get(Calendar.ERA) == cal.get(Calendar.ERA) &&
                tomorrowTime.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                tomorrowTime.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR));
    }

    /**
     * Shows only the last n words of a sentence, being n the number of words to make the longer
     * sentence that still is smaller than maxLength.
     *
     * @param sentence  phrase to shrink
     * @param maxLength max length of the new sentence
     * @return the reduced sentence
     */
    public static String tailAndTruncateSentence(String sentence, int maxLength) {
        String[] words = sentence.split(" ");
        List<String> list = Arrays.asList(words);
        Collections.reverse(list);
        String[] reversedWords = (String[]) list.toArray();

        String modifiedSentence = "";

        for (String word : reversedWords) {
            if (modifiedSentence.length() >= maxLength) {
                return modifiedSentence;
            }
            modifiedSentence = word + " " + modifiedSentence;
        }
        return modifiedSentence;
    }

    /**
     * Always creates a correct value for the short name of the route. Using the routeShortName,
     * processing the long name if the short is null or returning an empty string if both names are
     * null.
     *
     * Route short name will be preceded by adequate connector.
     *
     * @param routeLongName  to convert it to a route short name if necessary
     * @param routeShortName to be returned if is not null
     * @return a valid route short name
     */
    public static String getRouteShortNameSafe(String routeShortName, String routeLongName, Context context) {
        String routeName = "";

        if (routeShortName != null || routeLongName != null) {
            routeName += context.getResources()
                    .getString(R.string.connector_before_route);
            if (routeShortName != null) {
                routeName += " " + routeShortName;
            } else if (routeLongName != null) {
                routeName += " " + tailAndTruncateSentence(routeLongName, OTPConstants.ROUTE_SHORT_NAME_MAX_SIZE);
            }
        }
        return routeName;
    }

    /**
     * Always creates a correct value for the long name of the route. Using the routeLongName,
     * returning the short name if the long is null or returning an empty string if both names are
     * null.
     *
     * @param routeLongName  to be returned if is not null
     * @param routeShortName to use if necessary
     * @return a valid route long name
     */
    public static String getRouteLongNameSafe(String routeLongName, String routeShortName,
                                              boolean includeShortName) {
        String routeName = "";

        if (routeShortName != null || routeLongName != null) {
            if (routeLongName != null) {
                if (includeShortName && routeShortName != null) {
                    routeName = routeShortName + " " + "(" + routeLongName + ")";
                } else {
                    routeName += routeLongName;
                }
            } else if (routeShortName != null) {
                routeName += routeShortName;
            }
        }
        return routeName;
    }

    /**
     * Convert meters to feet.
     *
     * @param meters
     * @return feet
     */
    public static double metersToFeet(double meters) {
        return meters * FEET_PER_METER;
    }

    /**
     * Convert feet to meters.
     *
     * @param feet
     * @return meters
     */
    public static double feetToMeters(double feet) {
        return feet / FEET_PER_METER;
    }
}
