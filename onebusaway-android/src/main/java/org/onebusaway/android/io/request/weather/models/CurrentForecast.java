package org.onebusaway.android.io.request.weather.models;

public class CurrentForecast {
    public String icon;
    public double precip_per_hour;
    public double precip_probability;
    public String summary;
    public double temperature;
    public double temperature_feels_like;
    public int time;
    public double wind_speed;

    public String getIcon() {
        return icon;
    }

    public double getPrecip_per_hour() {
        return precip_per_hour;
    }

    public double getPrecip_probability() {
        return precip_probability;
    }

    public String getSummary() {
        return summary;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTemperature_feels_like() {
        return temperature_feels_like;
    }

    public int getTime() {
        return time;
    }

    public double getWind_speed() {
        return wind_speed;
    }
}