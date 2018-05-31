/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.example.android.sunshine.data.network;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.android.sunshine.AppExecutors;
import com.example.android.sunshine.data.database.WeatherEntry;

import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Provides an API for doing all operations with the server data
 */
public class WeatherNetworkDataSource {
    // The number of days we want our API to return, set to 14 days or two weeks
    public static final int NUM_DAYS = 14;
    private static final String LOG_TAG = WeatherNetworkDataSource.class.getSimpleName();

    // Interval at which to sync with the weather. Use TimeUnit for convenience, rather than
    // writing out a bunch of multiplication ourselves and risk making a silly mistake.
    private static final int SYNC_INTERVAL_HOURS = 3;
    private static final int SYNC_INTERVAL_SECONDS = (int) TimeUnit.HOURS.toSeconds(SYNC_INTERVAL_HOURS);
    private static final int SYNC_FLEXTIME_SECONDS = SYNC_INTERVAL_SECONDS / 3;
    private static final String SUNSHINE_SYNC_TAG = "sunshine-sync";

    // For Singleton instantiation
    private static final Object LOCK = new Object();
    private static WeatherNetworkDataSource sInstance;
    private final Context mContext;

    private final AppExecutors mExecutors;

    private final MutableLiveData<WeatherEntry[]> mDownloadedWeatherForecasts;

    private WeatherNetworkDataSource(Context context, AppExecutors executors) {
        mContext = context;
        mExecutors = executors;

        mDownloadedWeatherForecasts = new MutableLiveData<>();
    }

    /**
     * Get the singleton for this class
     */
    public static WeatherNetworkDataSource getInstance(Context context, AppExecutors executors) {
        Log.d(LOG_TAG, "Getting the network data source");
        if (sInstance == null) {
            synchronized (LOCK) {
                sInstance = new WeatherNetworkDataSource(context.getApplicationContext(), executors);
                Log.d(LOG_TAG, "Made new network data source");
            }
        }
        return sInstance;
    }

    public LiveData<WeatherEntry[]> getCurrentWeatherForecasts() {
        return mDownloadedWeatherForecasts;
    }

    /**
     * Starts an intent service to fetch the weather.
     */
    public void startFetchWeatherService() {
        Intent intentToFetch = new Intent(mContext, SunshineSyncIntentService.class);
        mContext.startService(intentToFetch);
        Log.d(LOG_TAG, "Service created");
    }

    /**
     * Gets the newest weather
     */
    void fetchWeather() {
        Log.d(LOG_TAG, "Fetch weather started");
        mExecutors.networkIO().execute(() -> {
            try {

                // The getUrl method will return the URL that we need to get the forecast JSON for the
                // weather. It will decide whether to create a URL based off of the latitude and
                // longitude or off of a simple location as a String.

                URL weatherRequestUrl = NetworkUtils.getUrl();

                // Use the URL to retrieve the JSON
                String jsonWeatherResponse = NetworkUtils.getResponseFromHttpUrl(weatherRequestUrl);

                // Parse the JSON into a list of weather forecasts
                WeatherResponse response = new OpenWeatherJsonParser().parse(jsonWeatherResponse);
                Log.d(LOG_TAG, "JSON Parsing finished");


                // As long as there are weather forecasts, update the LiveData storing the most recent
                // weather forecasts. This will trigger observers of that LiveData, such as the
                // SunshineRepository.
                if (response != null && response.getWeatherForecast().length != 0) {
                    Log.d(LOG_TAG, "JSON not null and has " + response.getWeatherForecast().length
                            + " values");
                    Log.d(LOG_TAG, String.format("First value is %1.0f and %1.0f",
                            response.getWeatherForecast()[0].getMin(),
                            response.getWeatherForecast()[0].getMax()));

                    // Will eventually do something with the downloaded data
                    mDownloadedWeatherForecasts.postValue(response.getWeatherForecast());
                }
            } catch (Exception e) {
                // Server probably invalid
                e.printStackTrace();
            }
        });
    }

}