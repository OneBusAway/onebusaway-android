package org.onebusaway.android.ui.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import java.util.Locale;

public class WeatherUtils {

    public static void setWeatherImage(ImageView imageView, String weatherCondition, Context context) {
        // TODO FIXME: This is temporarily commented out because it was causing a large number
        // of crashes in 2.13.0.
        // See https://github.com/OneBusAway/onebusaway-android/issues/1196

//        String resName = weatherCondition.replace("-", "_");
//
//        int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
//
//        if (resId != 0) {
//            // Adjusting scale for fog and wind icons.
//            if(weatherCondition.equals("fog") || weatherCondition.equals("wind")){
//                imageView.setScaleType(ImageView.ScaleType.CENTER);
//            }
//            imageView.setImageResource(resId);
//        } else {
//            // Default
//            imageView.setImageResource(R.drawable.clear_day);
//        }
    }

    public static void setWeatherTemp(TextView weatherTempTxtView, double temp) {
        Application app = Application.get();
        SharedPreferences sharedPreferences = Application.getPrefs();

        String automatic = app.getString(R.string.preferences_preferred_units_option_automatic);
        String preferredTempUnit = sharedPreferences.getString(app.getString(R.string.preference_key_preferred_temperature_units), automatic);

        String temperatureText;

        if (preferredTempUnit.equals(automatic)) {
            temperatureText = (int) temp + "° " + getDefaultUserTemp();
        } else {
            temperatureText = preferredTempUnit.equals(app.getString(R.string.celsius)) ? (int) convertToCelsius(temp) + "° C" : (int) temp + "° F";
        }
        weatherTempTxtView.setText(temperatureText);
    }


    public static String getDefaultUserTemp() {
        Locale locale = Locale.getDefault();
        String countryCode = locale.getCountry();

        if ("US".equals(countryCode) || "BS".equals(countryCode) ||
                "KY".equals(countryCode) || "LR".equals(countryCode)) {
            return "F";
        } else {
            return "C";
        }
    }

    public static double convertToCelsius(double fahrenheitTemp) {
        return (fahrenheitTemp - 32) * 5 / 9;
    }
}
