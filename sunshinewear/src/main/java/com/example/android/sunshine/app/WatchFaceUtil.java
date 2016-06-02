package com.example.android.sunshine.app;

import android.graphics.Color;

import in.divyamary.sunshinewear.R;

public final class WatchFaceUtil {
    /**
     * Name of the default interactive mode background color and the ambient mode background color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND = "Black";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND);

    /**
     * Name of the default interactive mode hour digits color and the ambient mode hour digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS);

    /**
     * Name of the default interactive mode minute digits color and the ambient mode minute digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);

    /**
     * Name of the default interactive mode second digits color and the ambient mode second digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS = "Gray";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS);

    /**
     * Name of the default interactive mode second digits color and the ambient mode second digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_DATE = "Gray";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_DATE =
            parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_DATE);


    private WatchFaceUtil() {
    }

    private static int parseColor(String colorName) {
        return Color.parseColor(colorName.toLowerCase());
    }

    public static int getArtResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.rain;
        } else if (weatherId == 511) {
            return R.drawable.freezing_rain;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.storm;
        } else if (weatherId == 800) {
            return R.drawable.clear;
        } else if (weatherId == 801) {
            return R.drawable.partly_cloudy;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.clouds;
        }
        return -1;
    }
}
