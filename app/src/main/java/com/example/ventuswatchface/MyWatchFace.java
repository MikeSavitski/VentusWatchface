package com.example.ventuswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.core.content.res.ResourcesCompat;
import androidx.palette.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MyWatchFace extends CanvasWatchFaceService
{

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis( 1 );

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine()
    {
        return new Engine();
    }

    private static class EngineHandler extends Handler
    {
        private final WeakReference< MyWatchFace.Engine > mWeakReference;

        public EngineHandler( MyWatchFace.Engine reference )
        {
            mWeakReference = new WeakReference<>( reference );
        }

        @Override
        public void handleMessage( Message msg )
        {
            MyWatchFace.Engine engine = mWeakReference.get();
            if ( engine != null )
            {
                switch ( msg.what )
                {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
    {
        private static final float HOUR_STROKE_WIDTH        = 5f;
        private static final float MINUTE_STROKE_WIDTH      = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler( this );
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive( Context context, Intent intent )
            {
                mCalendar.setTimeZone( TimeZone.getDefault() );
                invalidate();
            }
        };
        private boolean  mRegisteredTimeZoneReceiver = false;
        private boolean  mMuteMode;
        private Paint  mBackgroundPaint;
        private Paint mDigitalTimePaint;
        private Paint mDigitalStrokePaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mBackgroundAmbientBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        @Override
        public void onCreate( SurfaceHolder holder )
        {
            super.onCreate( holder );

            setWatchFaceStyle( new WatchFaceStyle.Builder( MyWatchFace.this )
                    .setAcceptsTapEvents( true )
                    .build() );

            mCalendar = Calendar.getInstance();

            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground()
        {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor( Color.BLACK );
            mBackgroundBitmap = BitmapFactory.decodeResource( getResources(), R.drawable.ventus );
            mBackgroundAmbientBitmap = BitmapFactory.decodeResource( getResources(), R.drawable.ventus_ambient );

            /* Extracts colors from background image to improve watchface style. */
            Palette.from( mBackgroundBitmap ).generate( new Palette.PaletteAsyncListener()
            {
                @Override
                public void onGenerated( Palette palette )
                {
                    if ( palette != null )
                    {
                        updateWatchHandStyle();
                    }
                }
            } );
        }

        private void initializeWatchFace()
        {
            Typeface typeface = ResourcesCompat.getFont( getApplicationContext(), R.font.eurostyle_normal );

            mDigitalTimePaint = new Paint();
            mDigitalTimePaint.setColor( Color.rgb( 0, 0,0 ) );
            mDigitalTimePaint.setStyle( Paint.Style.FILL );
            mDigitalTimePaint.setTextSize( 70 );
            mDigitalTimePaint.setTypeface( typeface );

            mDigitalStrokePaint = new Paint();
            mDigitalStrokePaint.setColor( Color.rgb( 157, 200,109 ) );
            mDigitalStrokePaint.setStyle( Paint.Style.STROKE );
            mDigitalStrokePaint.setTextSize( 70 );
            mDigitalStrokePaint.setStrokeWidth( 6 );
            mDigitalStrokePaint.setTypeface( typeface );
        }

        @Override
        public void onDestroy()
        {
            mUpdateTimeHandler.removeMessages( MSG_UPDATE_TIME );
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged( Bundle properties )
        {
            super.onPropertiesChanged( properties );
            mLowBitAmbient = properties.getBoolean( PROPERTY_LOW_BIT_AMBIENT, false );
            mBurnInProtection = properties.getBoolean( PROPERTY_BURN_IN_PROTECTION, false );
        }

        @Override
        public void onTimeTick()
        {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged( boolean inAmbientMode )
        {
            super.onAmbientModeChanged( inAmbientMode );
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle()
        {
            if ( mAmbient )
            {
                mDigitalTimePaint.setColor( Color.BLACK );
                mDigitalStrokePaint.setColor( Color.WHITE );
            }
            else
            {
                mDigitalTimePaint.setColor( Color.rgb( 0, 0,0 ) );
                mDigitalStrokePaint.setColor( Color.rgb( 157, 200,109 ) );
            }
        }

        @Override
        public void onInterruptionFilterChanged( int interruptionFilter )
        {
            super.onInterruptionFilterChanged( interruptionFilter );
            boolean inMuteMode = ( interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE );

            /* Dim display in mute mode. */
            if ( mMuteMode != inMuteMode )
            {
                mMuteMode = inMuteMode;
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged( SurfaceHolder holder, int format, int width, int height )
        {
            super.onSurfaceChanged( holder, format, width, height );

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ( ( float ) width ) / ( float ) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap( mBackgroundBitmap,
                    ( int ) ( mBackgroundBitmap.getWidth() * scale ),
                    ( int ) ( mBackgroundBitmap.getHeight() * scale ), true );

            mBackgroundAmbientBitmap = Bitmap.createScaledBitmap( mBackgroundAmbientBitmap,
                    ( int ) ( mBackgroundAmbientBitmap.getWidth() * scale ),
                    ( int ) ( mBackgroundAmbientBitmap.getHeight() * scale ), true );

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if ( ! mBurnInProtection && ! mLowBitAmbient )
            {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap()
        {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundAmbientBitmap.getWidth(),
                    mBackgroundAmbientBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888 );
            Canvas canvas = new Canvas( mGrayBackgroundBitmap );
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation( 0 );
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter( colorMatrix );
            grayPaint.setColorFilter( filter );
            canvas.drawBitmap( mBackgroundAmbientBitmap, 0, 0, grayPaint );
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand( int tapType, int x, int y, long eventTime )
        {
            switch ( tapType )
            {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw( Canvas canvas, Rect bounds )
        {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis( now );

            drawBackground( canvas );
            drawWatchFace( canvas );
        }

        private void drawBackground( Canvas canvas )
        {

            if ( mAmbient && ( mLowBitAmbient || mBurnInProtection ) )
            {
                canvas.drawBitmap( mBackgroundAmbientBitmap, 0, 0, mBackgroundPaint );
            }
            else if ( mAmbient )
            {
                canvas.drawBitmap( mBackgroundAmbientBitmap, 0, 0, mBackgroundPaint );
            }
            else
            {
                canvas.drawBitmap( mBackgroundBitmap, 0, 0, mBackgroundPaint );
            }
        }

        private void drawWatchFace( Canvas canvas )
        {
            //TODO: Fix centering/scaling of text

            int hours = mCalendar.get( Calendar.HOUR );
            int minutes = mCalendar.get( Calendar.MINUTE );
            String hourString = "";
            String minuteString = "";

            if (minutes < 10) {
                minuteString = "0" + minutes;
            }
            else {
                minuteString = Integer.toString( minutes );
            }

            if (hours < 10) {
                hourString = " " + hours;
            }
            else {
                hourString = Integer.toString( hours );
            }

            String time = hourString + ":" + minuteString;

            float textCenterX = (canvas.getHeight() / 9.5f);
            float textCenterY = (canvas.getWidth() / 2.25f);
            canvas.drawText( time, textCenterX, textCenterY, mDigitalStrokePaint );
            canvas.drawText( time, textCenterX, textCenterY, mDigitalTimePaint );

        }

        @Override
        public void onVisibilityChanged( boolean visible )
        {
            super.onVisibilityChanged( visible );

            if ( visible )
            {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone( TimeZone.getDefault() );
                invalidate();
            }
            else
            {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver()
        {
            if ( mRegisteredTimeZoneReceiver )
            {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter( Intent.ACTION_TIMEZONE_CHANGED );
            MyWatchFace.this.registerReceiver( mTimeZoneReceiver, filter );
        }

        private void unregisterReceiver()
        {
            if ( ! mRegisteredTimeZoneReceiver )
            {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver( mTimeZoneReceiver );
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer()
        {
            mUpdateTimeHandler.removeMessages( MSG_UPDATE_TIME );
            if ( shouldTimerBeRunning() )
            {
                mUpdateTimeHandler.sendEmptyMessage( MSG_UPDATE_TIME );
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning()
        {
            return isVisible() && ! mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage()
        {
            invalidate();
            if ( shouldTimerBeRunning() )
            {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - ( timeMs % INTERACTIVE_UPDATE_RATE_MS );
                mUpdateTimeHandler.sendEmptyMessageDelayed( MSG_UPDATE_TIME, delayMs );
            }
        }
    }
}
