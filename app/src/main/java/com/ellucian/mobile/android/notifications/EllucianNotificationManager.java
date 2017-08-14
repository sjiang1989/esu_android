/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.notifications;

import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.util.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.gson.Gson;

import java.io.IOException;

public class EllucianNotificationManager {
	private static final String TAG = EllucianNotificationManager.class.getSimpleName();

	private final EllucianApplication ellucianApplication;
	
	private long lastRegisterWithGCMTime;
	private final long MIN_REGISTRATION_REFRESH_TIME = 1000L * 60 * 60 * 24;
	private static final String REGISTERED_DEVICE_ID_KEY = "deviceId";
	private static final String REGISTERED_APP_VERSION_KEY = "registeredAppVersion";
	private static final String REGISTERED_USER_ID_KEY = "registeredUserId";
	private static String GCM_SENDER_ID;
	
	public EllucianNotificationManager(EllucianApplication ellucianApplication) {
		this.ellucianApplication = ellucianApplication;
		GCM_SENDER_ID = ellucianApplication.getConfigurationProperties().gcmSenderId;
	}
	
	private boolean isPlayServicesAvailable() {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ellucianApplication.getApplicationContext());
		
		if (code != ConnectionResult.SUCCESS) {
			Log.d(TAG, "Google Play Services are not available");
		}
		
		return code == ConnectionResult.SUCCESS;
	}
	
	private String getConfigValue(String key, String defaultValue) {
		SharedPreferences preferences = getPreferences();
		String url = preferences.getString(key, defaultValue);
		
		return url;
	}

	public void registerWithGcmIfNeeded() {
		// only do attempt for logged in user
		if (ellucianApplication.isUserAuthenticated() &&
			getConfigValue(Utils.NOTIFICATION_REGISTRATION_URL, null) != null) {
			SharedPreferences preferences = getPreferences();
			
			// check if user change since last registered
			String currentUserId = ellucianApplication.getAppUserId();
			String storedUserId = preferences.getString(REGISTERED_USER_ID_KEY, "");
			boolean userIdChanged = currentUserId != null && !currentUserId.equals(storedUserId);

			// check if application version changed
			String currentVersion = null;
			try {
				currentVersion = ellucianApplication.getPackageManager().
					getPackageInfo(ellucianApplication.getPackageName(), 0).versionName;
			} catch(NameNotFoundException e) {
				// should not happen!
			}
			String storedVersion = preferences.getString(REGISTERED_APP_VERSION_KEY, "");
			boolean versionChanged = !storedVersion.equals(currentVersion);
			
			long now = System.currentTimeMillis();
			boolean timeTofetch = now - lastRegisterWithGCMTime > MIN_REGISTRATION_REFRESH_TIME;
			
			// if the version changed or if it has been at least a day since last fetch of device id, then fetch it
			if (isPlayServicesAvailable() && (userIdChanged || versionChanged || timeTofetch) ){
				Log.d(TAG, "Starting task to register with GCM and Mobile Server");
				
				if (!TextUtils.isEmpty(GCM_SENDER_ID)) {
					GcmRegisterTask gcmRegisterTask = new GcmRegisterTask();
    				gcmRegisterTask.execute(preferences, (Boolean) versionChanged, currentVersion, (Boolean) userIdChanged, currentUserId);
    				
    			} else {
    				Log.e(TAG, "GCM_SENDER_ID was not properly set! Cannot start register task. Please check the configuration_properties.xml");
    			}
			}
		}
	}
	
	private class GcmRegisterTask extends AsyncTask<Object, Object, String> {
        @Override
        protected String doInBackground(Object... params) {
        	String deviceId = null;
        	try{
	        	SharedPreferences preferences = (SharedPreferences)params[0];
	        	Boolean versionChanged = (Boolean)params[1];
	        	String currentVersion = (String)params[2];
	        	Boolean userIdChanged = (Boolean)params[3];
	        	String currentUserId = (String)params[4];
	
	        	boolean registeredWithMobileServer = false;
	            try {
//	    			GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(ellucianApplication.getApplicationContext());
//                    deviceId = gcm.register(GCM_SENDER_ID);
//                    Log.d(TAG, "GCM deviceId: " + deviceId);
	    			InstanceID instanceID = InstanceID.getInstance(ellucianApplication.getApplicationContext());
                    deviceId = instanceID.getToken(GCM_SENDER_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
	                Log.d(TAG, "GCM token: " + deviceId);
	                
	                registeredWithMobileServer = registerWithMobileServer(deviceId);
	            } catch (IOException e) {
	            	Log.e(TAG, "Exception while registering with GCM", e);
	            } catch (Exception e) {
	            	Log.e(TAG, "Exception while registering with GCM", e);
	            }
	            
				if (registeredWithMobileServer && deviceId != null) {
					// successfully registered, write some preference
					
					lastRegisterWithGCMTime = System.currentTimeMillis();
					
					SharedPreferences.Editor prefEditor = preferences.edit();
	
					// save the currentVersion if it changed
					if (versionChanged) {
					    prefEditor.putString(REGISTERED_APP_VERSION_KEY, currentVersion);
					}
					
					// save the user id if it changed
					if (userIdChanged) {
					    prefEditor.putString(REGISTERED_USER_ID_KEY, currentUserId);
					}
	
					// save device id to prefs
					prefEditor.putString(REGISTERED_DEVICE_ID_KEY,  deviceId);
					
					prefEditor.apply();
				}
            } catch (Exception e) {
            	Log.e(TAG, "Exception while registering with GCM", e);
            }
            return deviceId;
        }
        
    	private boolean registerWithMobileServer(String deviceId) {
    		boolean result = false;
    		
    		try{
    		
    		SharedPreferences preferences = getPreferences();
    		String url = getConfigValue(Utils.NOTIFICATION_REGISTRATION_URL, null);
    		Boolean notificationEnabled = preferences.contains(Utils.NOTIFICATION_ENABLED) ?
    				preferences.getBoolean(Utils.NOTIFICATION_ENABLED, false) : null;
    				
    	    // if url is set and either notificationEnabled hasn't been determined or is true, then attempt to register
    	    if (url != null && (notificationEnabled == null || notificationEnabled )) {    	    	
    	    	Gson gson = new Gson();
    	    	DeviceRegister deviceRegister = new DeviceRegister();
    	    	deviceRegister.devicePushId = deviceId;
    	    	deviceRegister.platform = "android";
    	    	deviceRegister.applicationName = ellucianApplication.getPackageName();
    	    	deviceRegister.loginId = ellucianApplication.getAppUserName();
    	    	deviceRegister.sisId = ellucianApplication.getAppUserId();
    	    	String jsonDeviceRegister = gson.toJson(deviceRegister);

    			MobileClient client = new MobileClient(ellucianApplication);
    			
    			String response = client.makeAuthenticatedServerRequest(url, 
    					MobileClient.REQUEST_POST, true, jsonDeviceRegister);
    			
    			// check for good response
    			if (response != null && !response.equals("401") && !response.equals("403") && !response.equals("404")  ) {
    				DeviceRegisterResponse registerResponse = gson.fromJson(response, DeviceRegisterResponse.class);
    				
    				notificationEnabled = registerResponse.status.equals("success");
    				result = true;
    			} else {
    				notificationEnabled = false;
    			}

    			// save notificationEnabled
    			SharedPreferences.Editor prefEditor = preferences.edit();
    			prefEditor.putBoolean(Utils.NOTIFICATION_ENABLED,  notificationEnabled);
    			prefEditor.apply();
    	    }
    	    
            } catch (Exception e) {
            	Log.e(TAG, "Exception while registering with Mobile Server", e);
            }
    	    return result;    	    
    	}
	}

	private class DeviceRegister {
		public String devicePushId;
		public String platform;
		public String applicationName;
		public String loginId;
		public String sisId;
		// public String email; not available yet
	}

	private class DeviceRegisterResponse {
		public String status;
		public String error;
	}


	private SharedPreferences getPreferences() {
		return ellucianApplication.getSharedPreferences(Utils.NOTIFICATION, EllucianApplication.MODE_PRIVATE);
	}
}
