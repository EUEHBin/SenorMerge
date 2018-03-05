package com.sosee.mysenorr.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;


import java.util.Iterator;

/**
 * This class is used to determine the start location
 * and to get location for the autocorrect feature
 *
 * @author Christian Henke
 *         www.smartnavi-app.com
 */
public class Locationer  {

    public static double startLat;
    public static double startLon;
    public static double errorGPS;
    public static float lastErrorGPS = 9999999999.0f;
    private onLocationUpdateListener locationListener;
    private int satellitesInRange = 0;
    private Handler mHandler = new Handler();
    private int allowedErrorGps = 10;
    private boolean autoCorrectSuccess = true;
    private int additionalSecondsAutocorrect = 0;
    private boolean giveGpsMoreTime = true;
    private long lastLocationTime = 0L;
    private LocationManager mLocationManager;
    private Context mContext;
    private Listener mGpsStatusListener = new Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    updateSats();
                    break;
            }
        }
    };
    private Runnable deaktivateTask = new Runnable() {
        public void run() {
            deactivateLocationer();
        }
    };
    private LocationListener gpsAutocorrectLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            if (location.getLatitude() != 0) {
                location.getProvider();
                startLat = location.getLatitude();
                startLon = location.getLongitude();
                errorGPS = location.getAccuracy();
                if (errorGPS <= allowedErrorGps) {

                    locationListener.onLocationUpdate(8);

                    allowedErrorGps = 10;
                    autoCorrectSuccess = true;
                    additionalSecondsAutocorrect = 0;
                } else {
                    if (giveGpsMoreTime) {
                        //Positions are coming in, but they are to inaccurate
                        //so give some extra time
                        mHandler.removeCallbacks(autoStopTask);
                        mHandler.postDelayed(autoStopTask,
                                10000 + additionalSecondsAutocorrect * 1000);
                        giveGpsMoreTime = false;
                    }

                }
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }

    };
    private Runnable autoStopTask = new Runnable() {
        public void run() {

            stopAutocorrect();
        }
    };
    private Runnable satelitesInRangeTest = new Runnable() {
        public void run() {
            if (satellitesInRange < 5) {
                stopAutocorrect();

            }
        }
    };


    // LocationClient
    // **************

    public Locationer(Context context) {
        super();
        if (context instanceof onLocationUpdateListener) {
            locationListener = (onLocationUpdateListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mContext = context;


    }

    public void deactivateLocationer() {
        //ProgressBar must be made invisible (GONE)
        locationListener.onLocationUpdate(12);
        try {

        } catch (Exception e) {
            //nothing
        }
        try {
      //      mLocationManager.removeUpdates(this);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

    }

    public void startLocationUpdates() {

    }

    private void updateSats() {
        try {
            final GpsStatus gs = this.mLocationManager.getGpsStatus(null);
            int i = 0;
            final Iterator<GpsSatellite> it = gs.getSatellites().iterator();
            while (it.hasNext()) {
                it.next();
                i += 1;
            }

            satellitesInRange = i;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // ******************************************************************
    // ******************** AutoCorrection with GPS ******************
    // ******************************************************************


    public void starteAutocorrect() {
        if (autoCorrectSuccess) {
            autoCorrectSuccess = false;
        } else if (additionalSecondsAutocorrect <= 30) {
            additionalSecondsAutocorrect = additionalSecondsAutocorrect + 7;
            allowedErrorGps = allowedErrorGps + 8;

        }
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    100, 0, gpsAutocorrectLocationListener);
            mHandler.postDelayed(autoStopTask,
                    10000 + additionalSecondsAutocorrect * 1000);
            mHandler.postDelayed(satelitesInRangeTest, 10000);
            mLocationManager.addGpsStatusListener(mGpsStatusListener);
            giveGpsMoreTime = true;
        } catch (SecurityException e) {
            e.printStackTrace();
        }

    }

    public void stopAutocorrect() {
        try {
            mLocationManager.removeGpsStatusListener(mGpsStatusListener);
            mLocationManager.removeUpdates(gpsAutocorrectLocationListener);
            mHandler.removeCallbacks(autoStopTask);
            mHandler.removeCallbacks(satelitesInRangeTest);

        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }


    public interface onLocationUpdateListener {
        void onLocationUpdate(int event);
    }

    private class writeSettings extends AsyncTask<Void, Void, Void> {

        private String key;
        private int dataType;
        private boolean setting1;

        private writeSettings(String key, boolean setting1) {
            this.key = key;
            this.setting1 = setting1;
            dataType = 0;
        }

        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences settings = mContext.getSharedPreferences(mContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            if (dataType == 0) {
                settings.edit().putBoolean(key, setting1).commit();
            }
            return null;
        }
    }
}
