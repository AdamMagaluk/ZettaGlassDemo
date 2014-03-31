/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.glass.sample.compass;

import com.google.android.glass.sample.compass.util.MathUtils;
import com.google.android.glass.sample.compass.UDPDump;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Collects and communicates information about the user's current orientation and location.
 */
public class OrientationManager {

    /**
     * The minimum distance desired between location notifications.
     */
    private static final long METERS_BETWEEN_LOCATIONS = 2;

    /**
     * The minimum elapsed time desired between location notifications.
     */
    private static final long MILLIS_BETWEEN_LOCATIONS = TimeUnit.SECONDS.toMillis(3);

    /**
     * The maximum age of a location retrieved from the passive location provider before it is
     * considered too old to use when the compass first starts up.
     */
    private static final long MAX_LOCATION_AGE_MILLIS = TimeUnit.MINUTES.toMillis(30);

    /**
     * The sensors used by the compass are mounted in the movable arm on Glass. Depending on how
     * this arm is rotated, it may produce a displacement ranging anywhere from 0 to about 12
     * degrees. Since there is no way to know exactly how far the arm is rotated, we just split the
     * difference.
     */
    private static final int ARM_DISPLACEMENT_DEGREES = 6;
    
    
    /**
     * Classes should implement this interface if they want to be notified of changes in the user's
     * location, orientation, or the accuracy of the compass.
     */
    public interface OnChangedListener {
        /**
         * Called when the user's orientation changes.
         *
         * @param orientationManager the orientation manager that detected the change
         */
        void onOrientationChanged(OrientationManager orientationManager);

        /**
         * Called when the user's location changes.
         *
         * @param orientationManager the orientation manager that detected the change
         */
        void onLocationChanged(OrientationManager orientationManager);

        /**
         * Called when the accuracy of the compass changes.
         *
         * @param orientationManager the orientation manager that detected the change
         */
        void onAccuracyChanged(OrientationManager orientationManager);
    }

    private final SensorManager mSensorManager;
    private final LocationManager mLocationManager;
    private final String mLocationProvider;
    private final float[] mRotationMatrix;
    private final float[] mOrientation;

    private boolean mTracking;
    
    private UDPDump m_udpDump = new UDPDump();
    
    private GeomagneticField mGeomagneticField;
    private boolean mHasInterference;

    /**
     * The sensor listener used by the orientation manager.
     */
    private SensorEventListener mSensorListener = new SensorEventListener() {
    	
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
        	
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // Get the current heading from the sensor, then notify the listeners of the
                // change.
                SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X,
                        SensorManager.AXIS_Z, mRotationMatrix);
                SensorManager.getOrientation(mRotationMatrix, mOrientation);

                // Store the pitch (used to display a message indicating that the user's head
                // angle is too steep to produce reliable results.
                m_udpDump.Pitch = (float) Math.toDegrees(mOrientation[1]);
                
                // Convert the heading (which is relative to magnetic north) to one that is
                // relative to true north, using the user's current location to compute this.
                float magneticHeading = (float) Math.toDegrees(mOrientation[0]);
                m_udpDump.Heading = MathUtils.mod(computeTrueNorth(magneticHeading), 360.0f)
                        - ARM_DISPLACEMENT_DEGREES;

            }
           
            if(event.sensor.getType() == Sensor.TYPE_LIGHT){
            	m_udpDump.LightLevel = event.values[0];
            }
            
            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            	m_udpDump.LinearAcceleration[0] = event.values[0] - m_udpDump.Gravity[0];
            	m_udpDump.LinearAcceleration[1] = event.values[1] - m_udpDump.Gravity[1];
            	m_udpDump.LinearAcceleration[2] = event.values[2] - m_udpDump.Gravity[2];
            }
            
            if(event.sensor.getType() == Sensor.TYPE_GRAVITY){
            	m_udpDump.Gravity[0] = event.values[0];
            	m_udpDump.Gravity[1] = event.values[1];
            	m_udpDump.Gravity[2] = event.values[2];
            }
            
            update();
        }
    };

    /**
     * The location listener used by the orientation manager.
     */
    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
        	m_udpDump.Location = location;
            updateGeomagneticField();
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Don't need to do anything here.
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Don't need to do anything here.
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Don't need to do anything here.
        }
    };

    /**
     * Initializes a new instance of {@code OrientationManager}, using the specified context to
     * access system services.
     */
    public OrientationManager(SensorManager sensorManager, LocationManager locationManager) {
        mRotationMatrix = new float[16];
        mOrientation = new float[9];
        mSensorManager = sensorManager;
        mLocationManager = locationManager;

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);

        mLocationProvider = mLocationManager.getBestProvider(criteria, true /* enabledOnly */);
    }

    /**
     * Starts tracking the user's location and orientation. After calling this method, any
     * {@link OnChangedListener}s added to this object will be notified of these events.
     */
    public void start() {
        if (!mTracking) {
        		
        	int sensors[] = {Sensor.TYPE_ROTATION_VECTOR,
        					 Sensor.TYPE_GRAVITY,
        					 Sensor.TYPE_ACCELEROMETER,
        					 Sensor.TYPE_GRAVITY,
        					 Sensor.TYPE_LIGHT,
        					 Sensor.TYPE_LINEAR_ACCELERATION,
        					 Sensor.TYPE_MAGNETIC_FIELD
        					};
        	
        	for(int i=0;i<sensors.length;i++){
        		mSensorManager.registerListener(mSensorListener,
                        mSensorManager.getDefaultSensor(sensors[i]),
                        SensorManager.SENSOR_DELAY_UI);
        	}
        	
            
            Location lastLocation = mLocationManager
                    .getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (lastLocation != null) {
                long locationAge = lastLocation.getTime() - System.currentTimeMillis();
                if (locationAge < MAX_LOCATION_AGE_MILLIS) {
                	m_udpDump.Location = lastLocation;
                    updateGeomagneticField();
                }
            }

            if (mLocationProvider != null) {
                mLocationManager.requestLocationUpdates(mLocationProvider,
                        MILLIS_BETWEEN_LOCATIONS, METERS_BETWEEN_LOCATIONS, mLocationListener,
                        Looper.getMainLooper());
            }

            mTracking = true;
        }
    }

    /**
     * Stops tracking the user's location and orientation. Listeners will no longer be notified of
     * these events.
     */
    public void stop() {
        if (mTracking) {
            mSensorManager.unregisterListener(mSensorListener);
            mLocationManager.removeUpdates(mLocationListener);
            mTracking = false;
        }
    }

    /**
     * Gets a value indicating whether there is too much magnetic field interference for the
     * compass to be reliable.
     *
     * @return true if there is magnetic interference, otherwise false
     */
    public boolean hasInterference() {
        return mHasInterference;
    }

    /**
     * Gets a value indicating whether the orientation manager knows the user's current location.
     *
     * @return true if the user's location is known, otherwise false
     */
    public boolean hasLocation() {
        return m_udpDump.Location != null;
    }

    /**
     * Gets the user's current heading, in degrees. The result is guaranteed to be between 0 and
     * 360.
     *
     * @return the user's current heading, in degrees
     */
    public float getHeading() {
        return m_udpDump.Heading;
    }

    /**
     * Gets the user's current pitch (head tilt angle), in degrees. The result is guaranteed to be
     * between -90 and 90.
     *
     * @return the user's current pitch angle, in degrees
     */
    public float getPitch() {
        return m_udpDump.Pitch;
    }

    /**
     * Gets the user's current location.
     *
     * @return the user's current location
     */
    public Location getLocation() {
        return m_udpDump.Location;
    }

    /**
     * Updates the cached instance of the geomagnetic field after a location change.
     */
    private void updateGeomagneticField() {
        mGeomagneticField = new GeomagneticField((float) m_udpDump.Location.getLatitude(),
                (float) m_udpDump.Location.getLongitude(), (float) m_udpDump.Location.getAltitude(),
                m_udpDump.Location.getTime());
    }

    /**
     * Use the magnetic field to compute true (geographic) north from the specified heading
     * relative to magnetic north.
     *
     * @param heading the heading (in degrees) relative to magnetic north
     * @return the heading (in degrees) relative to true north
     */
    private float computeTrueNorth(float heading) {
        if (mGeomagneticField != null) {
            return heading + mGeomagneticField.getDeclination();
        } else {
            return heading;
        }
    }
    
    private void update(){
    	m_udpDump.send();
    }
}
