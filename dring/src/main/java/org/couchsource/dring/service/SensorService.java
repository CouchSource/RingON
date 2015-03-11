package org.couchsource.dring.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;
import org.couchsource.dring.application.AppContextWrapper;
import org.couchsource.dring.application.Constants;
import org.couchsource.dring.application.DeviceProperty;
import org.couchsource.dring.application.DeviceStatus;
import org.couchsource.dring.listener.phonestate.IncomingCallStateListener;
import org.couchsource.dring.listener.sensor.AccelerometerSensorListener;
import org.couchsource.dring.listener.sensor.LightSensorListener;
import org.couchsource.dring.listener.sensor.ProximitySensorListener;

/**
 * Sticky service that registers Listeners for Accelerometer sensor, proximity sensor and light sensor
 *
 * @author Kunal Sanghavi
 */
public class SensorService extends Service implements DeviceStateListenerCallback, Constants {


    private static final String TAG = SensorService.class.getName();
    private static volatile boolean mIsServiceRunning = false;
    private static boolean mIsAccelerometerAndLightSensorOn = false;
    private static boolean isBroadcastReceiverRegistered = false;
    private AppContextWrapper context;
    private AccelerometerSensorListener mAccelerometerSensorListener;
    private LightSensorListener mLightSensorListener;
    private ProximitySensorListener mProximitySensorListener;
    private DeviceStateListener deviceStateListener;
    private IncomingCallStateListener phoneListener;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPrefsListener;
    private String currentDeviceStatus;
    private final Object currentStatusLock = new Object();
    private int countDownToLowPowerMode;

    /**
     * Returns the current status of the service
     *
     * @return boolean indicating whether the service is running or not
     */
    public static boolean isServiceRunning() {
        return mIsServiceRunning;
    }

    private static synchronized void flagServiceStatus(boolean isServiceRunning) {
        mIsServiceRunning = isServiceRunning;
        Log.d(TAG, "Is SensorService running? " + isServiceRunning);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        context = new AppContextWrapper(this.getBaseContext());
        if (deviceStateListener == null) {
            deviceStateListener = new DeviceStateListener(this);
        }
        if (phoneListener == null) {
            phoneListener = new IncomingCallStateListener(context);
        }
        registerSensorListeners();
        registerSharedPrefsListener();
        flagServiceStatus(true);
        context.setBooleanSharedPref(RING_ON,SENSOR_SERVICE_ON,true);
        Log.d(TAG, "Service Started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        context.setBooleanSharedPref(RING_ON,SENSOR_SERVICE_ON,false);
        flagServiceStatus(false);
        unregisterPhoneListener();
        unregisterProximitySensor();
        unregisterAccelerometerAndLightSensors();
        unregisterSharedPrefsListener();
        deviceStateListener = null;
        context = null;
        super.onDestroy();
    }

    @Override
    public void signalNewDevicePlacement(DeviceStatus deviceStatus) {
        if (!mIsServiceRunning){
            return;
        }
        Log.d(TAG, "Signalled new device status " + deviceStatus);
        if (deviceStatus == null) {
            unregisterPhoneListener();
            resetCountdownToLowPowerMode();
        } else {
            synchronized (currentStatusLock) {
                if (currentDeviceStatus != deviceStatus.name()) {
                    currentDeviceStatus = deviceStatus.name();
                    if (deviceStatus.isStatusValid()) {
                        handleNewDevicePlacement(deviceStatus.name());
                        resetCountdownToLowPowerMode();
                    }
                } else {
                    if (deviceStatus.isStatusValid()) {
                        if (attemptLowPowerMode()) {
                            Log.d(TAG, "Switched off AccelerometerSensorListener and LightSensorListener with current status " + deviceStatus);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void signalDeviceProximityChanged() {
        if (mIsServiceRunning) {
            exitLowPowerMode();
        }
    }

    @Override
    public AppContextWrapper getContext() {
        return context;
    }

    private void handleNewDevicePlacement(String deviceStatus) {
        boolean isActive = context.getBooleanSharedPref(deviceStatus, DeviceProperty.ACTIVE.name(), false);
        boolean doVibrate = false;
        if (isActive) {
            float ringerLevel = context.getFloatSharedPref(deviceStatus, DeviceProperty.RINGER.name(), 0);
            doVibrate = context.getBooleanSharedPref(deviceStatus, DeviceProperty.VIBRATE.name(), false);
            changeRingerLevel(ringerLevel);
        }
        if (doVibrate) {
            registerPhoneListener();
        } else {
            unregisterPhoneListener();
        }
    }

    private synchronized void resetCountdownToLowPowerMode() {
        countDownToLowPowerMode = 10;
    }

    private boolean attemptLowPowerMode() {
        if (countDownToLowPowerMode > 0) {
            countDownToLowPowerMode--;
            return false;
        } else {
            unregisterAccelerometerAndLightSensors();
            countDownToLowPowerMode = 10;
            return true;
        }
    }

    private void signalUserPreferenceChanged() {
        synchronized (currentStatusLock) {
            currentDeviceStatus = null;
        }
        exitLowPowerMode();
    }

    private void exitLowPowerMode() {
        registerAccelerometerAndLightSensors();
    }

    private void registerSensorListeners() {
        registerAccelerometerAndLightSensors();
        registerProximitySensor();
    }

    private synchronized void registerAccelerometerAndLightSensors() {
        if (!mIsAccelerometerAndLightSensorOn) {
            if (mAccelerometerSensorListener == null) {
                mAccelerometerSensorListener = new AccelerometerSensorListener(context, deviceStateListener);
            }
            mAccelerometerSensorListener.register();
            Log.d(TAG, "AccelerometerSensorListener registered");

            if (mLightSensorListener == null) {
                mLightSensorListener = new LightSensorListener(context, deviceStateListener);
            }
            mLightSensorListener.register();
            Log.d(TAG, "LightSensorListener registered");
            mIsAccelerometerAndLightSensorOn = true;
        }
    }

    private void registerProximitySensor() {
        if (mProximitySensorListener == null) {
            mProximitySensorListener = new ProximitySensorListener(context, deviceStateListener);
        }
        mProximitySensorListener.register();
        Log.d(TAG, "ProximitySensorListener registered");
    }

    private void unregisterAccelerometerAndLightSensors() {
        if (mIsAccelerometerAndLightSensorOn) {
            mAccelerometerSensorListener.unregister();
            mAccelerometerSensorListener = null;
            Log.d(TAG, "AccelerometerSensorListener unregistered");
            mLightSensorListener.unregister();
            mLightSensorListener = null;
            Log.d(TAG, "LightSensorListener unregistered");
            mIsAccelerometerAndLightSensorOn = false;
        }
    }

    private void unregisterProximitySensor() {
        mProximitySensorListener.unregister();
        mProximitySensorListener = null;
        Log.d(TAG, "ProximitySensorListener unregistered");
    }

    private void registerSharedPrefsListener() {
        sharedPrefsListener =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                        Log.d(TAG, "Shared Prefs change detected for " + key);
                        signalUserPreferenceChanged();
                    }
                };

        for (DeviceStatus deviceStatus : DeviceStatus.getAllUserPreferences()) {
            context.getSharedPreferences(deviceStatus.name(), Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(sharedPrefsListener);
        }
    }

    private void unregisterSharedPrefsListener() {
        for (DeviceStatus deviceStatus : DeviceStatus.getAllUserPreferences()) {
            context.getSharedPreferences(deviceStatus.name(), Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(sharedPrefsListener);
        }
        sharedPrefsListener = null;
    }

    private void registerPhoneListener() {
        if (!isBroadcastReceiverRegistered) {
            phoneListener.register();
            isBroadcastReceiverRegistered = true;
            Log.d(TAG, "PhoneListener successfully registered");
        }
    }

    private void unregisterPhoneListener() {
        if (isBroadcastReceiverRegistered) {
            isBroadcastReceiverRegistered = false;
            phoneListener.unregister();
            Log.d(TAG, "PhoneListener successfully unregistered");
        }
    }

    private void changeRingerLevel(float ringerLevel) {
        Log.d(TAG, "Ringer " + String.valueOf(ringerLevel));
        AudioManager audioManager = context.getAudioService();
        if (ringerLevel == 0) {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        } else {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, Math.round(ringerLevel / 100 * audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)), AudioManager.FLAG_ALLOW_RINGER_MODES);
        }
    }


}
