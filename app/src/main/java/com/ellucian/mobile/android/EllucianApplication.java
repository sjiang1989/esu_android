/*
 * Copyright 2015-2017 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Application;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;

import com.ellucian.mobile.android.adapter.ModuleMenuAdapter;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.client.locations.EllucianBeaconManager;
import com.ellucian.mobile.android.client.services.ConfigurationUpdateService;
import com.ellucian.mobile.android.client.services.NotificationsIntentService;
import com.ellucian.mobile.android.client.services.UpdateAssignmentIntentService;
import com.ellucian.mobile.android.ilp.widget.AssignmentsWidgetProvider;
import com.ellucian.mobile.android.login.IdleTimer;
import com.ellucian.mobile.android.login.User;
import com.ellucian.mobile.android.notifications.DeviceNotifications;
import com.ellucian.mobile.android.notifications.EllucianNotificationManager;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.ellucian.mobile.android.util.ConfigurationProperties;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.ModuleConfiguration;
import com.ellucian.mobile.android.util.PRNGFixes;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.util.WebkitCookieManagerProxy;
import com.ellucian.mobile.android.util.XmlParser;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.HitBuilders.EventBuilder;
import com.google.android.gms.analytics.HitBuilders.ScreenViewBuilder;
import com.google.android.gms.analytics.Tracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

@SuppressWarnings("JavaDoc")
public class EllucianApplication extends Application {
	private static final String TAG = EllucianApplication.class.getSimpleName();

    private static EllucianApplication instance;
	private final HashMap<String, Object> liveObjects = new HashMap<>();
	private User user;
	private IdleTimer idleTimer;
	private final long idleTime = 30 * Utils.ONE_MINUTE; // 30 Minutes
	private static final long FINGERPRINT_VALID_MILLISECONDS = 5 * Utils.ONE_MINUTE; // 5 Minutes
    public static final long AUTH_REFRESH_TIME = 30 * Utils.ONE_MINUTE; // 30 Minutes
    private long fingerprintValidTime;
    private DeviceNotifications deviceNotifications;
	private long lastNotificationsCheck;
	public static final long DEFAULT_NOTIFICATIONS_REFRESH = Utils.ONE_HOUR; // 60 minutes
    public static final long DEFAULT_ASSIGNMENTS_REFRESH = Utils.ONE_HOUR; // 60 minutes
	private long lastAuthRefresh;
	private ConfigurationProperties configurationProperties;
	private HashMap<String, ModuleConfiguration> moduleConfigMap;
	private EllucianNotificationManager ellucianNotificationManager;
	private ModuleMenuAdapter moduleMenuAdapter;
    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;

    private boolean wasInBackground = true;
    private static final long MAX_ACTIVITY_TRANSITION_TIME_MS = 5000;

	// Google Analytics trackers
	private Tracker gaTracker1;
	private Tracker gaTracker2;

    public EllucianApplication() {
        instance = this;
    }

    public static Context getContext() {
        return instance;
    }

    @Override
	public void onCreate() {
		super.onCreate();

		PRNGFixes.apply();

		idleTimer = new IdleTimer(this, idleTime);
		deviceNotifications = new DeviceNotifications(this);
		lastAuthRefresh = 0;

        setupCookieManagement();
		WebkitCookieManagerProxy coreCookieManager = new WebkitCookieManagerProxy(
				null, java.net.CookiePolicy.ACCEPT_ALL);
		java.net.CookieHandler.setDefault(coreCookieManager);

		loadSavedUser();

		// Creating objects with configuration information
		configurationProperties = XmlParser
				.createConfigurationPropertiesFromXml(this);
		moduleConfigMap = XmlParser.createModuleConfigMapFromXml(this, 0);

		ellucianNotificationManager = new EllucianNotificationManager(this);

        SharedPreferences preferences = getSharedPreferences(Utils.CONFIGURATION, MODE_PRIVATE);
        String cloudConfigUrl = preferences.getString(Utils.CONFIGURATION_URL, null);
        int lastDeviceVersionCode = SettingsUtils.getIntFromPreferences(this, Utils.LAST_DEVICE_VERSION, 0);
        int configAppVersionCode = 0;
        try {
            configAppVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "onCreate: errorMessage:" + e.getMessage());
        }
        Log.d(TAG, "lastVersionCode:" + lastDeviceVersionCode + "  currVersionCode:" + configAppVersionCode);

        // If app has been updated, re-fetch the config from the appropriate source.
        // Updated device code might reference vales that were previously ignored.
        if (lastDeviceVersionCode != configAppVersionCode) {
            String newDefaultConfigurationUrl = getConfigurationProperties().defaultConfigurationUrl;
            Log.d(TAG, "Default Config URL: " + newDefaultConfigurationUrl);
            Log.d(TAG, "Current Config: " + cloudConfigUrl );

            SettingsUtils.addIntToPreferences(this, Utils.LAST_DEVICE_VERSION, configAppVersionCode);
            Log.d(TAG, "Update internal device version to " + configAppVersionCode);

            if (configurationProperties.allowSwitchSchool) {
                // If the user is allowed to switch school, re-fetch the last config they used. If
                // that is empty (they've never picked one) load the default config.
                if (!TextUtils.isEmpty(cloudConfigUrl)) {
                    refreshConfig(cloudConfigUrl);
                } else {
                    switchToConfig(newDefaultConfigurationUrl);
                }

            } else {
                // For most Platform Clients, user is NOT allowed to switch school.
                // If the new default URL is the same as what they already have loaded, do a
                // refresh config, so that they don't lose their cached data and authenticated sessions.
                // If the default config URL has changed, switch to that new URL.
                if (TextUtils.equals(cloudConfigUrl, newDefaultConfigurationUrl)) {
                    refreshConfig(cloudConfigUrl);
                } else {
                    switchToConfig(newDefaultConfigurationUrl);
                }
            }
        }

		if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            EllucianBeaconManager ellucianBeaconManager = EllucianBeaconManager.getInstance();
            ellucianBeaconManager.initializer(this);
        }
	}

    private void refreshConfig(String configUrl) {
        Log.d(TAG, "refreshConfig: " + configUrl );
        if (!isServiceRunning(ConfigurationUpdateService.class)) {
            Intent intent = new Intent(this, ConfigurationUpdateService.class);
            intent.putExtra(Utils.CONFIGURATION_URL, configUrl);
            intent.putExtra(ConfigurationUpdateService.REFRESH, true);
            startService(intent);
        } else {
            Log.v(TAG, "Can't start ConfigurationUpdateService because it is already running");
        }
    }

    private void switchToConfig(String configUrl) {
        Log.d(TAG, "switchToConfig: " + configUrl);
        if (!isServiceRunning(ConfigurationUpdateService.class)) {
            // when switching to a different config, stop any ongoing refreshes
            // before starting a new one.
            stopService(new Intent(this, ConfigurationUpdateService.class));
        }
        Utils.changeConfiguration(this, this, configUrl, null, null);
        refreshConfig(configUrl);
    }

    public Object getCachedObject(String key) {
		return liveObjects.get(key);
	}

	public void putCachedObject(String key, Object value) {
		liveObjects.put(key, value);
	}

	public void createAppUser(String userId, String username, String password,
			List<String> roles) {
		user = new User();
		user.setId(userId);
		user.setName(username);
		user.setPassword(password);
		user.setRoles(roles);

		String logString = "App User created:\n" + "userId: " + userId;
		logString += "\n" + "username:" + username;
		if(roles != null && roles.size() > 0) {
			logString += "\n" + "roles:" + roles.toString();
		}
		Log.d("EllucianApplication.createAppUser", logString);

        if (widgetInstalled()) {
            Intent assignmentIntent = new Intent(this, UpdateAssignmentIntentService.class);
            startService(assignmentIntent);
        }
	}

    private boolean widgetInstalled() {
        int ids[] = AppWidgetManager.getInstance(this).getAppWidgetIds(
                new ComponentName(this, AssignmentsWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            return true;
        } else {
            return false;
        }
    }

    public void removeAppUser(Boolean explicitSignOut) {
        if (explicitSignOut) {
            // Assignment data is deleted on explicit Sign out, but kept on a time out.
            getContentResolver().delete(EllucianContract.CourseAssignments.CONTENT_URI, null,
                    null);

            final String logoutUrl = PreferencesUtils.getStringFromPreferences(this,
                    Utils.SECURITY, Utils.LOGOUT_URL, null);
            if (!TextUtils.isEmpty(logoutUrl)) {
                Observable.just(logoutUrl)
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<String>() {
                            @Override
                            public void call(String logoutUrlString) {
                                MobileClient client = new MobileClient(instance);
                                client.setSendUnauthenticatedBroadcast(false);
                                client.makeServerRequest(logoutUrlString, true);
                            }
                        });
            }

        }
        removeAppUser();
    }

	public void removeAppUser() {
		user = null;
		getContentResolver().delete(EllucianContract.SECURED_CONTENT_URI, null,
				null);
		UserUtils.removeSavedUser(this);

        removeCookies();

		stopIdleTimer();
        if (widgetInstalled()) {
            Intent assignmentIntent = new Intent(this, UpdateAssignmentIntentService.class);
            startService(assignmentIntent);
        }
    }

    // Removed saved cookies on user logout
    @SuppressWarnings("deprecation")
    private void removeCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

	private void loadSavedUser() {
		String userId = UserUtils.getSavedUserId(this);
		if (userId != null) {
			String username = UserUtils.getSavedUserName(this);
			String password = UserUtils.getSavedUserPassword(this);

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                removeAppUser();
            } else {
                String roles = UserUtils.getSavedUserRoles(this);
                List<String> roleList = new ArrayList<>();
                if (roles != null) {
                    roleList = Arrays.asList(roles.split(","));
                }
                createAppUser(userId, username, password, roleList);
            }
		} else {
			Log.d("EllucianApplication.loadSavedUser", "No saved user to load");
            removeCookies();
		}
	}

	public String getAppUserId() {
		if (user == null) {
			return null;
		}
		return user.getId();
	}

	public String getAppUserName() {
		if (user == null) {
			return null;
		}
		return user.getName();
	}

	public String getAppUserPassword() {
		if (user == null) {
			return null;
		}
		return user.getPassword();
	}

	public List<String> getAppUserRoles() {
		if (user == null) {
			return null;
		}
		return user.getRoles();
	}

	public boolean isUserAuthenticated() {
		if (user != null) {
			return true;
		} else {
			return false;
		}
	}

	public void startIdleTimer() {
		idleTimer = new IdleTimer(this, idleTime);
		idleTimer.start();
	}

	private void stopIdleTimer() {
		idleTimer.stopTimer();
	}

	public void touch() {
		idleTimer.touch();

	}

    /**
     * Once the app is no longer in the foreground, a user's fingerprint expires
     * after {@code FINGERPRINT_VALID_MILLISECONDS} milliseconds.
     *
     * @return Yes if the user needs to supply their touch Id again
     */
    public boolean hasFingerprintExpired() {
        return (System.currentTimeMillis()  >
                fingerprintValidTime + FINGERPRINT_VALID_MILLISECONDS);
    }

    /**
     *
     * @return True if fingerprint has been enabled and it has expired.
     */
    public boolean isFingerprintUpdateNeeded() {
        if (UserUtils.getUseFingerprintEnabled(getContext()) && UserUtils.isFingerprintOptionEnabled(getContext())) {
            boolean fingerprintNeeded = PreferencesUtils.getBooleanFromPreferences(this, UserUtils.USER, UserUtils.USER_FINGERPRINT_NEEDED, true);
            return fingerprintNeeded;
        }
        return false;
    }

    /**
     * User has provided proof of authentication. Reset timer.
     */
    public void resetFingerprintValidTime() {
        this.fingerprintValidTime = System.currentTimeMillis();
    }

    public boolean isSignInNeeded() {
        if (isUserAuthenticated()) {
            if (isFingerprintUpdateNeeded()) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

	public void startNotifications() {
		if (Utils.isNotificationsPresent(this) && isUserAuthenticated()) {
			Log.d(TAG, "Starting Notifications");
			resetLastNotificationsCheck();
			Intent intent = new Intent(this, NotificationsIntentService.class);
			intent.putExtra(Extra.REQUEST_URL, getNotificationsUrl());
            startService(intent);
		}
	}

	synchronized public EllucianNotificationManager getNotificationManager() {
		return ellucianNotificationManager;
	}

	public void registerWithGcmIfNeeded() {
		ellucianNotificationManager.registerWithGcmIfNeeded();
	}

	public DeviceNotifications getDeviceNotifications() {
		return deviceNotifications;
	}

	public long getLastNotificationsCheck() {
		return lastNotificationsCheck;
	}

	private void resetLastNotificationsCheck() {
		this.lastNotificationsCheck = System.currentTimeMillis();
	}

	private String getNotificationsUrl() {
		return PreferencesUtils.getStringFromPreferences(this, Utils.NOTIFICATION,
                Utils.NOTIFICATION_NOTIFICATIONS_URL, null);
	}

	public String getMobileNotificationsUrl() {
		return PreferencesUtils.getStringFromPreferences(this, Utils.NOTIFICATION,
                Utils.NOTIFICATION_MOBILE_NOTIFICATIONS_URL, null);
	}

	public long getLastAuthRefresh() {
		return lastAuthRefresh;
	}

    public void setLastAuthRefresh(){
        lastAuthRefresh = System.currentTimeMillis();
    }

	public ConfigurationProperties getConfigurationProperties() {
		return configurationProperties;
	}

	public void setModuleConfigMap(
			HashMap<String, ModuleConfiguration> moduleConfigMap) {
		this.moduleConfigMap = moduleConfigMap;
	}

	public HashMap<String, ModuleConfiguration> getModuleConfigMap() {
		return moduleConfigMap;
	}

	public ModuleConfiguration findModuleConfig(String configName) {
		return moduleConfigMap.get(configName);
	}

	public List<String> getModuleConfigTypeList() {
		return new ArrayList<String>(moduleConfigMap.keySet());
	}

	public boolean isServiceRunning(Class<? extends Service> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private synchronized Tracker getTracker1() {
		if (gaTracker1 == null) {
			GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
			String trackerId1 = PreferencesUtils.getStringFromPreferences(this,
                    Utils.GOOGLE_ANALYTICS, Utils.GOOGLE_ANALYTICS_TRACKER1,
                    null);
			if (trackerId1 != null)
				gaTracker1 = analytics.newTracker(trackerId1);
		}
		return gaTracker1;
	}

	private synchronized Tracker getTracker2() {
		if (gaTracker2 == null) {
			GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
			String trackerId2 = PreferencesUtils.getStringFromPreferences(this,
                    Utils.GOOGLE_ANALYTICS, Utils.GOOGLE_ANALYTICS_TRACKER2,
                    null);
			if (trackerId2 != null)
				gaTracker2 = analytics.newTracker(trackerId2);
		}
		return gaTracker2;
	}

	/**
	 * Send event to google analytics
	 *
	 * @param category
	 * @param action
	 * @param label
	 * @param value
	 * @param moduleName
	 */
	public void sendEvent(String category, String action, String label,
			Long value, String moduleName) {
		sendEventToTracker1(category, action, label, value,
                moduleName);
		sendEventToTracker2(category, action, label, value,
                moduleName);
	}

	/**
	 * Send event to google analytics for just tracker 1
	 *
	 */
	public void sendEventToTracker1(String category, String action,
			String label, Long value, String moduleName) {
		sendEventToTracker(getTracker1(), category, action, label, value,
                moduleName);
	}

	/**
	 * Send event to google analytics for just tracker 2
	 *
	 */
	public void sendEventToTracker2(String category, String action,
			String label, Long value, String moduleName) {
		sendEventToTracker(getTracker2(), category, action, label, value,
                moduleName);
	}

	/**
	 * Send event to google analytics
	 *
	 * @param categoryId
	 * @param actionId
	 * @param labelId
	 * @param value
	 * @param moduleName
	 */
	private void sendEventToTracker(Tracker tracker, String categoryId,
									String actionId, String labelId, Long value, String moduleName) {
		if (tracker != null) {
			String configurationName = PreferencesUtils.getStringFromPreferences(this,
                    Utils.CONFIGURATION, Utils.CONFIGURATION_NAME, null);
			EventBuilder eventBuilder = new HitBuilders.EventBuilder();
			eventBuilder.setCategory(categoryId);
			eventBuilder.setAction(actionId);
			eventBuilder.setLabel(labelId);
			if(value != null) {
				eventBuilder.setValue(value);
			}
			eventBuilder.setCustomDimension(1, configurationName);
			if (moduleName != null)
				eventBuilder.setCustomDimension(2, moduleName);

			tracker.send(eventBuilder.build());
		}

	}

	/**
	 * Send view to google analytics
	 *
	 * @param appScreen
	 * @param moduleName
	 */
	public void sendView(String appScreen, String moduleName) {
		sendViewToTracker1(appScreen, moduleName);
		sendViewToTracker2(appScreen, moduleName);
	}

	/**
	 * Send view to google analytics for just tracker 1
	 *
	 * @param appScreen
	 */
	public void sendViewToTracker1(String appScreen, String moduleName) {
		sendViewToTracker(getTracker1(), appScreen, moduleName);
	}

	/**
	 * Send view to google analytics for just tracker 2
	 *
	 * @param appScreen
	 */
	public void sendViewToTracker2(String appScreen, String moduleName) {
		sendViewToTracker(getTracker2(), appScreen, moduleName);
	}

	/**
	 * Send view to google analytics
	 *
	 * @param tracker
	 * @param appScreen
	 * @param moduleName
	 */
	private void sendViewToTracker(Tracker tracker, String appScreen,
								   String moduleName) {
		if (tracker != null) {
			String configurationName = PreferencesUtils.getStringFromPreferences(this,
                    Utils.CONFIGURATION, Utils.CONFIGURATION_NAME, null);
			tracker.setScreenName(appScreen);
            ScreenViewBuilder screenViewBuilder = new ScreenViewBuilder();
            screenViewBuilder.setCustomDimension(1, configurationName);
			if (moduleName != null)
                screenViewBuilder.setCustomDimension(2, moduleName);
			tracker.send(screenViewBuilder.build());
		}
	}

	/**
	 * Send timing to google analytics
	 * @param category
	 * @param value
	 * @param name
	 * @param label
	 * @param moduleName
	 */
	public void sendUserTiming(String category, long value, String name, String label, String moduleName) {
		sendUserTimingToTracker1(category, value, name, label, moduleName);
		sendUserTimingToTracker2(category, value, name, label, moduleName);
	}

	/**
	 * Send timing to google analytics for just tracker 1
	 * @param category
	 * @param value
	 * @param name
	 * @param label
	 * @param moduleName
	 */
	public void sendUserTimingToTracker1(String category, long value, String name, String label, String moduleName) {
		sendUserTimingToTracker(getTracker1(), category, value, name, label, moduleName);
	}

	/**
	 * Send timing to google analytics for just tracker 2
	 * @param category
	 * @param value
	 * @param name
	 * @param label
	 * @param moduleName
	 */
	public void sendUserTimingToTracker2(String category, long value, String name, String label, String moduleName) {
		sendUserTimingToTracker(getTracker2(), category, value, name, label, moduleName);
	}

	/**
	 * Send timing to google analytics
	 * @param category
	 * @param value
	 * @param name
	 * @param label
	 * @param moduleName
	 */
	private void sendUserTimingToTracker(Tracker tracker, String category, long value, String name, String label,
										 String moduleName) {
		if (tracker != null) {
			String configurationName = PreferencesUtils.getStringFromPreferences(this,
                    Utils.CONFIGURATION, Utils.CONFIGURATION_NAME, null);
			HitBuilders.TimingBuilder timingBuilder = new HitBuilders.TimingBuilder();
			timingBuilder.setCategory(category).setValue(value).setVariable(name).setLabel(label);
			timingBuilder.setCustomDimension(1, configurationName);
			if (moduleName != null)
				timingBuilder.setCustomDimension(2, moduleName);
			tracker.send(timingBuilder.build());
		}
	}


	/**
	 * Application will only manage one menu adapter at a time.
	 * Typically ModuleMenuAdapter.buildInstance() will only be called once on app creation
	 * and when a new configuration is requested.
	 */
	public ModuleMenuAdapter getModuleMenuAdapter() {
		if (moduleMenuAdapter == null) {
			moduleMenuAdapter = ModuleMenuAdapter.buildInstance(this);
		}
    	return moduleMenuAdapter;
    }

	public void resetModuleMenuAdapter() {
		moduleMenuAdapter = null;
	}

    /**
     * Method to start a timer of how long this app has been in the
     * background.
     */
    public void startActivityTransitionTimer() {
        mActivityTransitionTimer = new Timer();
        mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                wasInBackground = true;
            }
        };

        mActivityTransitionTimer.schedule(mActivityTransitionTimerTask,
                MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    public void stopActivityTransitionTimer() {
        if (mActivityTransitionTimerTask != null) {
            mActivityTransitionTimerTask.cancel();
        }

        if (mActivityTransitionTimer != null) {
            mActivityTransitionTimer.cancel();
        }
        wasInBackground = false;
    }

    public boolean wasInBackground() {
        return wasInBackground;
    }

    @SuppressWarnings("deprecation")
    private void setupCookieManagement() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieSyncManager.createInstance(this);
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
    }

    // If there are beacons defined AND the device is running L+, return true.
    public boolean configUsesBeacons() {
        final ContentResolver contentResolver = getContentResolver();
        Cursor modulesInterestCursor = contentResolver.query(EllucianContract.Modules.CONTENT_URI,
                null,
                EllucianContract.Modules.MODULE_USE_BEACON_TO_LAUNCH + " = 'true'",
                null,
                EllucianContract.Modules.DEFAULT_SORT);
        if (modulesInterestCursor != null && modulesInterestCursor.moveToFirst()) {
            modulesInterestCursor.close();
            if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return true;
            }
        }
        modulesInterestCursor.close();
        return false;
    }
}
