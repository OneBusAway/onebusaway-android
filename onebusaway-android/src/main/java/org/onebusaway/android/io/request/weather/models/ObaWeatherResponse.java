package org.onebusaway.android.io.request.weather.models;

import java.util.List;

public class ObaWeatherResponse {

    private CurrentForecast current_forecast;
    private List<HourlyForecast> hourly_forecast;
    private double latitude;
    private double longitude;
    private int region_identifier;
    private String region_name;
    private String retrieved_at;
    private String today_summary;
    private String units;

    public CurrentForecast getCurrent_forecast() {
        return current_forecast;
    }

    public List<HourlyForecast> getHourly_forecast() {
        return hourly_forecast;
    }


    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getRegion_identifier() {
        return region_identifier;
    }


    public String getRegion_name() {
        return region_name;
    }


    public String getRetrieved_at() {
        return retrieved_at;
    }


    public String getToday_summary() {
        return today_summary;
    }


    public String getUnits() {
        return units;
    }

    @Override
    public String toString() {
        return "ObaWeatherResponse{" +
                "current_forecast=" + current_forecast +
                ", hourly_forecast=" + hourly_forecast +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", region_identifier=" + region_identifier +
                ", region_name='" + region_name + '\'' +
                ", retrieved_at='" + retrieved_at + '\'' +
                ", today_summary='" + today_summary + '\'' +
                ", units='" + units + '\'' +
                '}';
    }
}