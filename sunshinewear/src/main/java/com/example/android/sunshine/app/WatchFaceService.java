package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import in.divyamary.sunshinewear.R;


public class WatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "DigitalWatchFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        /** Alpha value for drawing time when in mute mode. */
        static final int MUTE_ALPHA = 100;

        /** Alpha value for drawing time when not in mute mode. */
        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /** Handler to update the time periodically in interactive mode. */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        Bitmap mBackgroundBitmap;
        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mAmPmPaint;
        Paint mTempPaint;
        boolean mMute;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        float mYOffset;
        float mLineHeight;
        String mAmString;
        String mPmString;
        String mTempHigh;

        final int mInteractiveBackgroundColor = getResources().getColor(R.color.bg_color);
        final int mInteractiveDateColor = getResources().getColor(android.R.color.white);
        final int mInteractiveHourDigitsColor = getResources().getColor(R.color.time_color);
        final int mInteractiveMinuteDigitsColor = getResources().getColor(R.color.time_color);
        final int mInteractiveSecondDigitsColor = getResources().getColor(R.color.time_color);

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private double high;
        private double low;
        private int weatherId;
        private int mWeatherDrawable;
        private Bitmap mWeatherBitmap;
        Paint mWeatherPaint;
        private String mTempLow;
        private boolean isRound;

        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.d(TAG, "onCreate");
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = WatchFaceService.this.getResources();
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveDateColor);
            mWeatherPaint = new Paint();
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            mDatePaint = createTextPaint(mInteractiveDateColor, NORMAL_TYPEFACE);
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor, BOLD_TYPEFACE);
            mSecondPaint = createTextPaint(mInteractiveSecondDigitsColor, NORMAL_TYPEFACE);
            mSecondPaint.setColor(getResources().getColor(R.color.second_color));
            mAmPmPaint = createTextPaint(mInteractiveMinuteDigitsColor, NORMAL_TYPEFACE);
            mTempPaint = createTextPaint(mInteractiveMinuteDigitsColor, NORMAL_TYPEFACE);
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG, "onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = new SimpleDateFormat("EEE MMM d", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            WatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            WatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            super.onApplyWindowInsets(insets);
            // Load resources that have alternate values for round watches.
            Resources resources = WatchFaceService.this.getResources();
            isRound = insets.isRound();
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float hourSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float minuteSize = resources.getDimension(isRound
                    ? R.dimen.digital_min_size_round : R.dimen.digital_min_size);
            float amPmSize = resources.getDimension(isRound
                    ? R.dimen.digital_am_pm_size_round : R.dimen.digital_am_pm_size);
            float tempSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_size_round : R.dimen.digital_temp_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(hourSize);
            mMinutePaint.setTextSize(minuteSize);
            mSecondPaint.setTextSize(amPmSize);
            mAmPmPaint.setTextSize(amPmSize);
            mTempPaint.setTextSize(tempSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            adjustPaintColorToCurrentMode(mBackgroundPaint, mInteractiveBackgroundColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            adjustPaintColorToCurrentMode(mSecondPaint, mInteractiveSecondDigitsColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);
            adjustPaintColorToCurrentMode(mDatePaint, mInteractiveDateColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_DATE);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mTempPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mAmPmPaint.setAlpha(alpha);
                mTempPaint.setAlpha(alpha);
                mSecondPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;
            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }


        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(WatchFaceService.this);
            // Draw the background.
            if (!isInAmbientMode() && !mMute) {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            // Draw the hours.
            canvas.drawText(hourString, bounds.centerX() - mHourPaint.measureText(hourString), mYOffset, mHourPaint);
            // Draw the minutes.
            canvas.drawText(minuteString, bounds.centerX(), mYOffset-mLineHeight, mMinutePaint);
            //Draw AM/PM
            canvas.drawText(getAmPmString(
                    mCalendar.get(Calendar.AM_PM)), bounds.centerX()+(mMinutePaint.measureText(minuteString)
                    -mAmPmPaint.measureText(getAmPmString(mCalendar.get(Calendar.AM_PM))))/2, mYOffset, mAmPmPaint);
            //Draw the seconds
            String secondString = formatTwoDigitNumber(mCalendar.get(Calendar.SECOND));
            if (!isInAmbientMode() && !mMute) {
                canvas.drawText(secondString, bounds.centerX()+mSecondPaint.measureText(minuteString)+
                        mSecondPaint.measureText(secondString), mYOffset-mLineHeight, mSecondPaint);
            }
            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Date
                canvas.drawText(
                        mDateFormat.format(mDate).toUpperCase(),
                        bounds.centerX() - mDatePaint.measureText(mDateFormat.format(mDate).toUpperCase())/2,
                        mYOffset + mLineHeight, mDatePaint);
            }
            //Draw the weather data
            if (!isInAmbientMode() && !mMute) {
                if (mWeatherBitmap != null) {
                    canvas.drawBitmap(mWeatherBitmap, bounds.centerX() - mWeatherBitmap.getWidth() / 2,
                            mYOffset + mLineHeight + 10, mWeatherPaint);
                }
                if (mTempHigh != null && mTempHigh.length() > 0) {
                    canvas.drawText(mTempHigh, bounds.centerX() - mTempPaint.measureText(mTempHigh) - mWeatherBitmap.getWidth() / 2 - 10,
                            mYOffset + mLineHeight + mWeatherBitmap.getHeight() / 2 + 10, mTempPaint);
                }
                if (mTempLow != null && mTempLow.length() > 0) {
                    canvas.drawText(mTempLow, bounds.centerX() + mWeatherBitmap.getWidth() / 2 + 10,
                            mYOffset + mLineHeight + mWeatherBitmap.getHeight() / 2 + 10, mTempPaint);
                }
            }
        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = dataEvent.getDataItem();
                    if (item.getUri().getPath().equals("/weather")) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        high = dataMap.getDouble("high");
                        low = dataMap.getDouble("low");
                        weatherId = dataMap.getInt("weatherId");
                        updateWeather(high, low, weatherId);
                    }
                }else if (dataEvent.getType() == DataEvent.TYPE_DELETED) {
                    // DataItem deleted
                    Log.d(TAG, "DataItem deleted");
                }
            }
        }

        private void updateWeather(double high, double low, int weatherId) {
            mTempHigh =  String.format(getString(R.string.format_temperature), high);
            mTempLow =  String.format(getString(R.string.format_temperature), low);
            mWeatherDrawable = WatchFaceUtil.getArtResourceForWeatherCondition(weatherId);
            mWeatherBitmap = BitmapFactory.decodeResource(getResources(), mWeatherDrawable);
        }


        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed: " + result.getErrorMessage());
        }
    }
}
