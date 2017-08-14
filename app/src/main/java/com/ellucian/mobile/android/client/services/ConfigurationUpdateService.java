/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.services;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.ModuleType;
import com.ellucian.mobile.android.about.AboutActivity;
import com.ellucian.mobile.android.app.HomescreenBackground;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.client.configuration.ConfigurationBuilder;
import com.ellucian.mobile.android.client.configuration.MobileServerConfigurationBuilder;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class ConfigurationUpdateService extends IntentService {

	private boolean checkVersion = true;
	public static String latestVersionToCauseAlert = null;

	public static final String PARAM_UPGRADE_AVAILABLE = "upgradeAvailable";
	public static final String REFRESH = "refresh";
	public static final String ACTION_SUCCESS = "com.ellucian.mobile.android.client.services.ConfigurationUpdateService.action.success";
	public static final String ACTION_SEND_TO_SELECTION = "com.ellucian.mobile.android.client.services.ConfigurationUpdateService.action.reselect";
	public static final String ACTION_OUTDATED = "com.ellucian.mobile.android.client.services.ConfigurationUpdateService.action.outdated";
	public static final String ACTION_UNABLE_TO_DOWNLOAD = "com.ellucian.mobile.android.client.services.ConfigurationUpdateService.action.unableToDownload";
    public static final String REFRESH_MOBILESERVER_ONLY = "refreshMobileServerOnly";
	private static final String TAG = ConfigurationUpdateService.class.getSimpleName();
	private boolean imagesDone;
	private ImageLoaderReceiver imageReceiver;
	private boolean refresh;

	public ConfigurationUpdateService() {
		super("ConfigurationUpdateService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {

        if (intent.getBooleanExtra(REFRESH_MOBILESERVER_ONLY, false)) {
            refreshMobileServerConfig();
            return;
        }

		EllucianApplication ellucianApp = (EllucianApplication) getApplicationContext();
		// Setting fields from the configuration file. See res/xml/configuration_properties.xml
		checkVersion = ellucianApp.getConfigurationProperties().enableVersionChecking;
		
		LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);

		imageReceiver = new ImageLoaderReceiver();
		IntentFilter imageFilter = new IntentFilter(
				ImageLoaderService.ACTION_FINISHED);
		LocalBroadcastManager.getInstance(this).registerReceiver(imageReceiver,
				imageFilter);

		imagesDone = false;

		String configUrl = intent.getStringExtra(Utils.CONFIGURATION_URL);
		refresh = intent.getBooleanExtra(REFRESH, false);

		MobileClient client = new MobileClient(this);
		String configurationString = client.getConfiguration(configUrl);

		JSONObject jsonConfiguration = null;
		boolean success = false;
		boolean sendToSelection = false;
		boolean outdated = false;
		boolean upgradeAvailable = false;
		try {
			if (configurationString == null) {
				Log.e(TAG, "Configuration not downloaded: " + configUrl);
			} else if (configurationString.equals("401")
					|| configurationString.equals("403")
					|| configurationString.equals("404")) {
				Log.e(TAG, "Return server error: " + configurationString);
				sendToSelection = true;
			} else {
				jsonConfiguration = new JSONObject(configurationString);

				if (checkVersion
						&& jsonConfiguration.has("versions")
						&& jsonConfiguration.getJSONObject("versions").has(
								"android")) {
					ArrayList<String> supportedVersions = new ArrayList<>();
					JSONArray jsonVersions = jsonConfiguration.getJSONObject(
							"versions").getJSONArray("android");
					for (int i = 0; i < jsonVersions.length(); i++) {
						supportedVersions.add(jsonVersions.getString(i));
					}

					try {
						outdated = true;
						String appVersion = getPackageManager().getPackageInfo(
								getPackageName(), 0).versionName;
						String[] appVersionComponents = appVersion.split("\\.");
						String appVersionWithoutBuildNumber = appVersionComponents[0]
								+ "."
								+ appVersionComponents[1]
								+ "."
								+ appVersionComponents[2];
						String latestSupportedVersion = supportedVersions
								.get(supportedVersions.size() - 1);
						String[] latestSupportedVersionComponents = latestSupportedVersion
								.split("\\.");

						if (latestSupportedVersion
								.equals(appVersionWithoutBuildNumber)) {
							// current
							outdated = false;
						} else if (supportedVersions
								.contains(appVersionWithoutBuildNumber)) {
							// supported
							// only alert the user once
							if (!latestSupportedVersion
									.equals(latestVersionToCauseAlert)) {
								upgradeAvailable = true;
								latestVersionToCauseAlert = latestSupportedVersion;
							}
							outdated = false;
						} else if (appVersionComponents.length > 0
								&& latestSupportedVersionComponents.length > 0
								&& Integer.parseInt(appVersionComponents[0]) > Integer
										.parseInt(latestSupportedVersionComponents[0])) {
							// app newer than what server returns
							outdated = false;
						} else if (appVersionComponents.length > 0
								&& latestSupportedVersionComponents.length > 0
								&& Integer.parseInt(appVersionComponents[0]) == Integer
										.parseInt(latestSupportedVersionComponents[0])) {
							if (appVersionComponents.length > 1
									&& latestSupportedVersionComponents.length > 1
									&& Integer
											.parseInt(appVersionComponents[1]) > Integer
											.parseInt(latestSupportedVersionComponents[1])) {
								// app newer than what server returns
								outdated = false;
							} else if (appVersionComponents.length > 1
									&& latestSupportedVersionComponents.length > 1
									&& Integer
											.parseInt(appVersionComponents[1]) == Integer
											.parseInt(latestSupportedVersionComponents[1])) {
								if (appVersionComponents.length > 2
										&& latestSupportedVersionComponents.length > 2
										&& Integer
												.parseInt(appVersionComponents[2]) > Integer
												.parseInt(latestSupportedVersionComponents[2])) {
									// app newer than what server returns
									outdated = false;
								}
							}
						}
						if (outdated) {
							success = false;
							sendToSelection = true;
						}
					} catch (NameNotFoundException e) {
						Log.e("ConfigurationUpdateService",
								"Unable to get versionName");
					}
				}
				if (!outdated) {
					addConfigurationItemsToPreferences(jsonConfiguration);

					// Starting home screen images download early
					ArrayList<String> homeImagesUrlList = collectHomeImageUrls(jsonConfiguration);
					Intent homeImagesIntent = new Intent(getBaseContext(),
							ImageLoaderService.class);
					homeImagesIntent.putExtra(Extra.IMAGE_URL_LIST,
							homeImagesUrlList);
					homeImagesIntent.putExtra(Extra.SEND_BROADCAST, true);
					startService(homeImagesIntent);

					// Menu and other images
					ArrayList<String> otherImagesUrlList = collectOtherImageUrls(jsonConfiguration);
					Intent otherImagesIntent = new Intent(getBaseContext(),
							ImageLoaderService.class);
					otherImagesIntent.putExtra(Extra.IMAGE_URL_LIST,
							otherImagesUrlList);
					startService(otherImagesIntent);

					if (jsonConfiguration.has(ModuleType.MODULE)) {
						JSONObject jsonModules = jsonConfiguration
								.getJSONObject(ModuleType.MODULE);
						ConfigurationBuilder builder = new ConfigurationBuilder(
								this);
						ArrayList<ContentProviderOperation> ops = builder
								.buildOperations(jsonModules);

						if (ops.size() > 0) {
							this.getContentResolver().applyBatch(
									EllucianContract.CONTENT_AUTHORITY, ops);
						}

						// Pulling specific things from modules
						Iterator<?> moduleIds = jsonModules.keys();
						while (moduleIds.hasNext()) {
							String key = (String) moduleIds.next();
							JSONObject moduleObject = jsonModules
									.getJSONObject(key);
							String type = "";
                            try {
                                type = moduleObject.getString("type");
                            } catch (JSONException e) {
                                // Type not found. Corrupt mapp.
                                Log.e(TAG, "JSONException:", e);
                            }

							// Set if Directory are present
							if (type.equals(ModuleType.DIRECTORY)) {
								PreferencesUtils.addBooleanToPreferences(this,
										Utils.CONFIGURATION,
										Utils.DIRECTORY_PRESENT, true);
							}
							// Set if Maps are present
							if (type.equals(ModuleType.MAPS)) {
								PreferencesUtils.addBooleanToPreferences(this,
										Utils.CONFIGURATION, Utils.MAP_PRESENT,
										true);
							}

							// Check to see if notifications are present
							if (type.equals(ModuleType.NOTIFICATIONS)) {								
								PreferencesUtils.addBooleanToPreferences(this, Utils.CONFIGURATION,
								        Utils.NOTIFICATION_PRESENT, true);

								JSONObject urls = moduleObject.getJSONObject("urls");

								PreferencesUtils.addStringToPreferences(this, Utils.NOTIFICATION,
								        Utils.NOTIFICATION_NOTIFICATIONS_URL, urls.getString("notifications"));
	
								PreferencesUtils.addStringToPreferences(this, Utils.NOTIFICATION,
								        Utils.NOTIFICATION_MOBILE_NOTIFICATIONS_URL, urls.getString("mobilenotifications"));

							}

						}
					}
					// Clears the menu adapter so the app knows to recreate it with the new
					// configuration changes
					ellucianApp.resetModuleMenuAdapter();
					
					success = true;
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException:", e);
		} catch (NullPointerException e) {
			Log.e(TAG, "NullPointerException:", e);
		} catch (OperationApplicationException e) {
			Log.e(TAG, "OperationApplicationException:", e);
		} catch (RemoteException e) {
			Log.e(TAG, "RemoteException:", e);
		}

        refreshMobileServerConfig();

		// Make sure the home images are downloaded for display before sending
		// out broadcast to start MainActivity
		while (success && !imagesDone) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (success) {
            long updateCheckedTime = System.currentTimeMillis();
            PreferencesUtils.addLongToPreferences(this, Utils.CONFIGURATION, Utils.CONFIGURATION_LAST_CHECKED,
                    updateCheckedTime);
			Log.d(TAG, "Configuration update time: " + updateCheckedTime);
		}

		if (outdated) {
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(ACTION_OUTDATED);
			bm.sendBroadcast(broadcastIntent);
		} else if (success) {
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(ACTION_SUCCESS);
			broadcastIntent.putExtra(PARAM_UPGRADE_AVAILABLE, upgradeAvailable);
			broadcastIntent.putExtra(REFRESH, refresh);
			bm.sendBroadcast(broadcastIntent);
		} else if (sendToSelection) {
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(ACTION_SEND_TO_SELECTION);
			bm.sendBroadcast(broadcastIntent);
		} else {
			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction(ACTION_UNABLE_TO_DOWNLOAD);
			bm.sendBroadcast(broadcastIntent);
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				imageReceiver);
	}

	private ArrayList<String> collectHomeImageUrls(JSONObject jsonConfiguration) {
		ArrayList<String> imageUrlList = new ArrayList<>();

		// Collecting home background image
		try {
			JSONObject layout = jsonConfiguration.getJSONObject("layout");
			if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= 
			        Configuration.SCREENLAYOUT_SIZE_LARGE && layout.has(Utils.HOME_URL_TABLET)
			        		&& !TextUtils.isEmpty(layout.getString(Utils.HOME_URL_TABLET))) {
				// Large screens use larger image if present			
				imageUrlList.add(layout.getString(Utils.HOME_URL_TABLET));
			} else {
				if (layout.has(Utils.HOME_URL_PHONE)
						&& !TextUtils.isEmpty(layout
								.getString(Utils.HOME_URL_PHONE))) {
					imageUrlList.add(layout.getString(Utils.HOME_URL_PHONE));
				}
			}
			
		} catch (JSONException e) {
			Log.e(TAG + ".collectImageUrls", "JSONException:", e);
		}

		return imageUrlList;
	}

	private ArrayList<String> collectOtherImageUrls(JSONObject jsonConfiguration) {
		ArrayList<String> imageUrlList = new ArrayList<>();

		// Collecting menu icon images
		try {
			if (jsonConfiguration.has(ModuleType.MODULE)) {
				JSONObject jsonModules = jsonConfiguration
						.getJSONObject(ModuleType.MODULE);
				Iterator<?> iter = jsonModules.keys();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					JSONObject value = jsonModules.getJSONObject(key);

					if (value.has("icon")) {
						imageUrlList.add(value.getString("icon"));
					}
				}
			}
		} catch (JSONException e) {
			Log.e(TAG + ".collectImageUrls", "JSONException:", e);
		}

		String aboutIconUrl = PreferencesUtils.getStringFromPreferences(this,
				Utils.APPEARANCE, AboutActivity.PREFERENCES_ICON, null);
		if (!TextUtils.isEmpty(aboutIconUrl)) {
			imageUrlList.add(aboutIconUrl);
		}

		return imageUrlList;
	}

	private void addConfigurationItemsToPreferences(JSONObject jsonConfiguration)
			throws JSONException {

        /** Adding lastUpdated info **/
        if (jsonConfiguration.has("lastUpdated")) {
            PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION, Utils.CONFIGURATION_LAST_UPDATED, jsonConfiguration.getString("lastUpdated"));
        }

        SharedPreferences preferences = this.getSharedPreferences(
				Utils.APPEARANCE, MODE_PRIVATE);
		preferences.edit().clear().apply();
		SharedPreferences.Editor editor = preferences.edit();

		/** Adding Layout Info **/
		JSONObject layout = jsonConfiguration.getJSONObject("layout");

		// Setting information from the configuration. Using defaults if they
		// are not present.
		if (layout.has(Utils.PRIMARY_COLOR)
				&& !TextUtils.isEmpty(layout.getString(Utils.PRIMARY_COLOR))) {
			editor.putString(Utils.PRIMARY_COLOR,
					"#" + layout.getString(Utils.PRIMARY_COLOR));
		} else {
			editor.putString(Utils.PRIMARY_COLOR, "#331640");
		}
		if (layout.has(Utils.HEADER_TEXT_COLOR)
				&& !TextUtils
						.isEmpty(layout.getString(Utils.HEADER_TEXT_COLOR))) {
			editor.putString(Utils.HEADER_TEXT_COLOR,
					"#" + layout.getString(Utils.HEADER_TEXT_COLOR));
		} else {
			editor.putString(Utils.HEADER_TEXT_COLOR, "#FFFFFF");
		}
		if (layout.has(Utils.ACCENT_COLOR)
				&& !TextUtils.isEmpty(layout.getString(Utils.ACCENT_COLOR))) {
			editor.putString(Utils.ACCENT_COLOR,
					"#" + layout.getString(Utils.ACCENT_COLOR));
		} else {
			editor.putString(Utils.ACCENT_COLOR, "#E8E1CD");
		}
		if (layout.has(Utils.SUBHEADER_TEXT_COLOR)
				&& !TextUtils.isEmpty(layout
						.getString(Utils.SUBHEADER_TEXT_COLOR))) {
			editor.putString(Utils.SUBHEADER_TEXT_COLOR,
					"#" + layout.getString(Utils.SUBHEADER_TEXT_COLOR));
		} else {
			editor.putString(Utils.SUBHEADER_TEXT_COLOR, "#736357");
		}
		if (layout.has(Utils.DEFAULT_MENU_ICON)
				&& !TextUtils
						.isEmpty(layout.getString(Utils.DEFAULT_MENU_ICON))) {
			editor.putBoolean(Utils.DEFAULT_MENU_ICON,
					layout.getBoolean(Utils.DEFAULT_MENU_ICON));
		} else {
			editor.putBoolean(Utils.DEFAULT_MENU_ICON, true);
		}

		if (layout.has(Utils.HOME_URL_PHONE)
				&& !TextUtils.isEmpty(layout.getString(Utils.HOME_URL_PHONE))) {
			editor.putString(Utils.HOME_URL_PHONE,
					layout.getString(Utils.HOME_URL_PHONE));
		}
		if (layout.has(Utils.HOME_URL_TABLET)
				&& !TextUtils.isEmpty(layout.getString(Utils.HOME_URL_TABLET))) {
			editor.putString(Utils.HOME_URL_TABLET,
					layout.getString(Utils.HOME_URL_TABLET));
		}

		/** Adding About Info **/
		JSONObject about = jsonConfiguration.getJSONObject("about");
		if (about.has("contact")) {
			editor.putString(AboutActivity.PREFERENCES_CONTACT,
					about.getString("contact"));
		}
		if (about.has("icon")) {
			editor.putString(AboutActivity.PREFERENCES_ICON,
					about.getString("icon"));
		}
		if (about.has("logoUrlPhone")) {
			editor.putString(AboutActivity.PREFERENCES_LOGO_URL_PHONE,
					about.getString("logoUrlPhone"));
		}
		if (about.has("logoUrlTablet")) {
			editor.putString(AboutActivity.PREFERENCES_LOGO_URL_TABLET,
					about.getString("logoUrlTablet"));
		}
		if (about.has("phone")) {
			JSONObject phone = about.getJSONObject("phone");
			if (phone.has("display")) {
				editor.putString(AboutActivity.PREFERENCES_PHONE_DISPLAY,
						phone.getString("display"));
			}
			if (phone.has("number")) {
				editor.putString(AboutActivity.PREFERENCES_PHONE_NUMBER,
						phone.getString("number"));
			}
		}
		if (about.has("email")) {
			JSONObject email = about.getJSONObject("email");
			if (email.has("display")) {
				editor.putString(AboutActivity.PREFERENCES_EMAIL_DISPLAY,
						email.getString("display"));
			}
			if (email.has("address")) {
				editor.putString(AboutActivity.PREFERENCES_EMAIL_ADDRESS,
						email.getString("address"));
			}
		}
		if (about.has("website")) {

			JSONObject website = about.getJSONObject("website");
			if (website.has("display")) {
				editor.putString(AboutActivity.PREFERENCES_WEBSITE_DISPLAY,
						website.getString("display"));
			}
			if (website.has("url")) {
				editor.putString(AboutActivity.PREFERENCES_WEBSITE_URL,
						website.getString("url"));
			}
		}
		if (about.has("privacy")) {
			JSONObject privacy = about.getJSONObject("privacy");
			if (privacy.has("display")) {
				editor.putString(AboutActivity.PREFERENCES_PRIVACY_DISPLAY,
						privacy.getString("display"));
			}
			if (privacy.has("url")) {
				editor.putString(AboutActivity.PREFERENCES_PRIVACY_URL,
						privacy.getString("url"));
			}
		}

		JSONObject version = about.getJSONObject("version");
		if (version.has("url")) {
			editor.putString(AboutActivity.PREFERENCES_VERSION_URL,
					version.getString("url"));
		}
		editor.apply();

		/** Adding Security Info **/
		JSONObject security = jsonConfiguration.getJSONObject("security");
		if (security.has("url")) {
			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.SECURITY_URL, security.getString("url"));
		}
        if (security.has("logoutUrl")) {
            PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
                    Utils.LOGOUT_URL, security.getString("logoutUrl"));
        }
		if (security.has("cas")) {
            PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
                    Utils.AUTHENTICATION_TYPE, Utils.CAS_AUTH);

			JSONObject cas = security.getJSONObject("cas");
			String loginType = null;
			if (cas.has("loginType")) {
				loginType = cas.getString("loginType");
			}
			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.LOGIN_TYPE, loginType);
			String loginUrl = null;
			if (cas.has("loginUrl")) {
				loginUrl = cas.getString("loginUrl");
			}
			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.LOGIN_URL, loginUrl);
			
		} else if (security.has("web")) {
            PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
                    Utils.AUTHENTICATION_TYPE, Utils.WEB_AUTH);

			JSONObject web = security.getJSONObject("web");

			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.LOGIN_TYPE, Utils.BROWSER_LOGIN_TYPE);
			String loginUrl = null;
			if (web.has("loginUrl")) {
				loginUrl = web.getString("loginUrl");
			}
			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.LOGIN_URL, loginUrl);
			
		} else {
            PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
                    Utils.AUTHENTICATION_TYPE, Utils.BASIC_AUTH);
			PreferencesUtils.addStringToPreferences(this, Utils.SECURITY,
					Utils.LOGIN_TYPE, "native");
		}

		/** Adding Notification Info **/
		JSONObject notification = null;
		try {
		    notification = jsonConfiguration.getJSONObject("notification");
		    if (notification != null && notification.has("urls")) {
			    JSONObject urls = notification.getJSONObject("urls");
			    if (urls != null) {
				    PreferencesUtils.addStringToPreferences(this, Utils.NOTIFICATION,
					        Utils.NOTIFICATION_REGISTRATION_URL, urls.getString("registration"));
				    PreferencesUtils.addStringToPreferences(this, Utils.NOTIFICATION,
					        Utils.NOTIFICATION_DELIVERED_URL, urls.getString("delivered"));
			    }
		    }
		} catch(JSONException e) {
            Log.e(TAG, "exception processing NOTIFICATION URLs " + e, e);
			// ignore this for now
		}
		// remove enabled attribute if it exists to ensure we check
		PreferencesUtils.removeValuesFromPreferences(this, Utils.NOTIFICATION,
				Utils.NOTIFICATION_ENABLED);

		/** Adding Map urls */

		if (jsonConfiguration.has("map")) {
			JSONObject map = jsonConfiguration.getJSONObject("map");
			if (map.has("campuses")) {
				PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
						Utils.MAP_CAMPUSES_URL, map.getString("campuses"));
			}
			if (map.has("buildings")) {
				PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
						Utils.MAP_BUILDINGS_URL, map.getString("buildings"));
			}
		}

		/** Adding Directory urls */

		if (jsonConfiguration.has("directory")) {
			JSONObject directory = jsonConfiguration.getJSONObject("directory");
			if (directory.has("allSearch")) {
				PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
						Utils.DIRECTORY_ALL_SEARCH_URL,
						directory.getString("allSearch"));
			}
			if (directory.has("facultySearch")) {
				PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
						Utils.DIRECTORY_FACULTY_SEARCH_URL,
						directory.getString("facultySearch"));
			}
			if (directory.has("studentSearch")) {
				PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
						Utils.DIRECTORY_STUDENT_SEARCH_URL,
						directory.getString("studentSearch"));
			}
            if (directory.has("baseSearch")) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.DIRECTORY_BASE_SEARCH_URL,
                        directory.getString("baseSearch"));
            }
		}

		/** Adding google analytics */
		if (jsonConfiguration.has("analytics")) {
			JSONObject analytics = jsonConfiguration.getJSONObject("analytics");
			String trackerId1 = analytics.has("ellucian") ? analytics.getString("ellucian") : null;
			String trackerId2 = analytics.has("client") ? analytics.getString("client") : null;
			PreferencesUtils.addStringToPreferences(this, Utils.GOOGLE_ANALYTICS, Utils.GOOGLE_ANALYTICS_TRACKER1, trackerId1);
			PreferencesUtils.addStringToPreferences(this, Utils.GOOGLE_ANALYTICS, Utils.GOOGLE_ANALYTICS_TRACKER2, trackerId2);

		}

        /** Adding Mobile Server Configuration url */
        PreferencesUtils.removeValuesFromPreferences(this, Utils.CONFIGURATION, Utils.MOBILESERVER_CONFIG_URL, Utils.MOBILESERVER_CONFIG_LAST_UPDATE, Utils.MOBILESERVER_CODEBASE_VERSION);

        if (jsonConfiguration.has("mobileServerConfig")) {
            JSONObject mobileServerConfig = jsonConfiguration.getJSONObject("mobileServerConfig");
            if (mobileServerConfig.has("url")) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION, Utils.MOBILESERVER_CONFIG_URL, mobileServerConfig.getString("url"));
            }
        }

        /** Adding Login Info **/
        // clear out all existing values first
        PreferencesUtils.removeValuesFromPreferences(this, Utils.CONFIGURATION,
                Utils.LOGIN_USERNAME_HINT,
                Utils.LOGIN_PASSWORD_HINT,
                Utils.LOGIN_INSTRUCTIONS,
                Utils.LOGIN_HELP_LABEL,
                Utils.LOGIN_HELP_URL);

        if (jsonConfiguration.has("login")) {
            JSONObject login = jsonConfiguration.getJSONObject("login");

            if (login.has("usernameHint") && !TextUtils.isEmpty(login.getString("usernameHint"))) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.LOGIN_USERNAME_HINT, login.getString("usernameHint"));
            }

            if (login.has("passwordHint") && !TextUtils.isEmpty(login.getString("passwordHint"))) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.LOGIN_PASSWORD_HINT, login.getString("passwordHint"));
            }

            if (login.has("instructions") && !TextUtils.isEmpty(login.getString("instructions"))) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.LOGIN_INSTRUCTIONS, login.getString("instructions"));
            }

            if (login.has("help")) {
                JSONObject help = login.getJSONObject("help");
                String helpDisplayLabel = help.has("display") ? help.getString("display") : null;
                String helpUrl = help.has("url") ? help.getString("url") : null;

                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.LOGIN_HELP_LABEL, helpDisplayLabel);
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION,
                        Utils.LOGIN_HELP_URL, helpUrl);
            }

        }

        /** Adding Home Screen Shortcuts */
        PreferencesUtils.removeValuesFromPreferences(this, Utils.CONFIGURATION, Utils.HOME_SCREEN_ICONS, Utils.HOME_SCREEN_OVERLAY);

        if (jsonConfiguration.has("home")) {
            JSONObject homeScreenConfig = jsonConfiguration.getJSONObject("home");
            if (homeScreenConfig.has("icons")) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION, Utils.HOME_SCREEN_ICONS, homeScreenConfig.getString("icons"));
            }
            if (homeScreenConfig.has("overlay")) {
                PreferencesUtils.addStringToPreferences(this, Utils.CONFIGURATION, Utils.HOME_SCREEN_OVERLAY, homeScreenConfig.getString("overlay"));
            }
        }

        boolean fingerprintSensorPresent;

        FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(this);

        if (fingerprintManager.isHardwareDetected()) {
            fingerprintSensorPresent = true;
        } else {
            Log.i(TAG, "Device doesn't support fingerprint authentication");
            fingerprintSensorPresent = false;
        }

        SharedPreferences.Editor defaultEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        defaultEditor.putBoolean(Utils.FINGERPRINT_SENSOR_PRESENT, fingerprintSensorPresent);
        defaultEditor.apply();

	}

	private class ImageLoaderReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent incomingIntent) {
			imagesDone = true;
            HomescreenBackground.refresh(context);

		}

	}

    private void refreshMobileServerConfig() {
        String mobileServerConfigUrl = PreferencesUtils.getStringFromPreferences(this, Utils.CONFIGURATION, Utils.MOBILESERVER_CONFIG_URL, null);
        if (!TextUtils.isEmpty(mobileServerConfigUrl)) {
            MobileClient client = new MobileClient(this);
            String configurationString = client.getConfiguration(mobileServerConfigUrl);

            JSONObject jsonConfiguration;
            try {
                if (configurationString == null) {
                    Log.e(TAG, "MobileServer Configuration not downloaded: " + mobileServerConfigUrl);
                } else if (configurationString.equals("401")
                        || configurationString.equals("403")
                        || configurationString.equals("404")) {
                    Log.e(TAG, "Return server error: " + configurationString);
                } else {
                    jsonConfiguration = new JSONObject(configurationString);
                    Log.d(TAG, "response: " + configurationString);

                    MobileServerConfigurationBuilder builder = new MobileServerConfigurationBuilder(this);
                    Log.d(TAG, "Building content provider operations");
                    ArrayList<ContentProviderOperation> ops = builder.buildOperations(jsonConfiguration);

                    if (ops.size() > 0) {
                        this.getContentResolver().applyBatch(
                                EllucianContract.CONTENT_AUTHORITY,ops);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException:", e);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "OperationApplicationException:", e);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException:", e);
            }
        } else {
            Log.d(TAG, "No mobileServer configuration url");
        }
    }

}
