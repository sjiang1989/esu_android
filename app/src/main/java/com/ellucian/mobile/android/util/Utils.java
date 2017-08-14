/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.MainActivity;
import com.ellucian.mobile.android.ModuleType;
import com.ellucian.mobile.android.adapter.ModuleMenuAdapter;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.login.Fingerprint.FingerprintDialogFragment;
import com.ellucian.mobile.android.login.LoginDialogFragment;
import com.ellucian.mobile.android.login.QueuedIntentHolder;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.Arrays;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

public class Utils {
    public static final String TAG = Utils.class.getSimpleName();
	public static final String DEFAULT_MENU_ICON = "defaultMenuIcon";
	public static final String SUBHEADER_TEXT_COLOR = "subheaderTextColor";
	public static final String ACCENT_COLOR = "accentColor";
	public static final String HEADER_TEXT_COLOR = "headerTextColor";
	public static final String PRIMARY_COLOR = "primaryColor";
	public static final String HOME_URL_PHONE = "homeUrlPhone";
	public static final String HOME_URL_TABLET = "homeUrlTablet";
	public static final String SECURITY = "security";
	public static final String SECURITY_URL = "securityUrl";
	public static final String NOTIFICATION = "notification";
    public static final String NOTIFICATION_MODULE_NAME = "notificationModuleName";
	public static final String NOTIFICATION_PRESENT = "notificationPresent";
	public static final String NOTIFICATION_NOTIFICATIONS_URL = "notificationNotificationsUrl";
	public static final String NOTIFICATION_MOBILE_NOTIFICATIONS_URL = "notificationMobileNotificationsUrl";
	public static final String NOTIFICATION_REGISTRATION_URL = "notificationRegistrationUrl";
	public static final String NOTIFICATION_DELIVERED_URL = "notificationDeliveredUrl";
	public static final String NOTIFICATION_ENABLED = "notificationEnabled";
	public static final String CONFIGURATION = "configuration";
	public static final String CONFIGURATION_NAME = "configurationName";
	public static final String CONFIGURATION_URL = "configurationUrl";
	public static final String CONFIGURATION_LAST_UPDATED = "configurationLastUpdated";
    public static final String CONFIGURATION_LAST_CHECKED = "configurationLastChecked";
    public static final String LAST_DEVICE_VERSION = "lastDeviceVersion";
    public static final String MOBILESERVER_CONFIG_URL = "mobileServerConfigUrl";
    public static final String MOBILESERVER_CONFIG_LAST_UPDATE = "mobileServerConfigLastUpdate";
    public static final String MOBILESERVER_CODEBASE_VERSION = "mobileServerCodebaseVersion";
    public static final String APPEARANCE = "appearance";
	public static final String DIALOG = "dialog";
    public static final String ID = "id";
	public static final String COURSE_ROSTER_VISIBILITY = "course_roster_visibility";
	public static final String MAP_BUILDINGS_URL = "maBuildingsUrl";
	public static final String MAP_CAMPUSES_URL = "mapCampusesUrl";
	public static final String MAP_PRESENT = "mapPresent";
	public static final String DIRECTORY_PRESENT = "directoryPresent";
	public static final String DIRECTORY_ALL_SEARCH_URL = "directoryAllSearchUrl";
	public static final String DIRECTORY_STUDENT_SEARCH_URL = "directoryStudentSearchUrl";
	public static final String DIRECTORY_FACULTY_SEARCH_URL = "directoryFacultySearchUrl";
    public static final String DIRECTORY_BASE_SEARCH_URL = "directoryBaseSearchUrl";
	public static final String GOOGLE_ANALYTICS = "googleAnalytics";
	public static final String GOOGLE_ANALYTICS_TRACKER1 = "tracker1";
	public static final String GOOGLE_ANALYTICS_TRACKER2 = "tracker2";
	public static final String LOGIN_URL = "loginUrl";
    public static final String LOGOUT_URL = "logoutUrl";
	public static final String LOGIN_TYPE = "loginType";
	public static final String BROWSER_LOGIN_TYPE = "browser";
	public static final String NATIVE_LOGIN_TYPE = "native";
    public static final String AUTHENTICATION_TYPE = "authenticationType";
    public static final String BASIC_AUTH = "basicAuth";
    public static final String CAS_AUTH = "casAuth";
    public static final String WEB_AUTH = "webAuth";
	public static final String MENU = "menu";
	public static final String MENU_HEADER_STATE = "menuHeaderState";
    public static final String ILP_URL = "ilpUrl";
    public static final String ILP_NAME = "ilpName";
    public static final String HOME_SCREEN_ICONS = "homeScreenIcons";
    public static final String HOME_SCREEN_OVERLAY = "homeScreenOverlay";
    public static final String BLUETOOTH_NOTIF_TIMESTAMP = "bluetoothNotifTimestamp";
    public static final String BLUETOOTH_NOTIF_REMIND = "bluetoothNotifRemind";
    public static final String LOCATIONS_NOTIF_TIMESTAMP = "locationsNotifTimestamp";
    public static final String LOCATIONS_NOTIF_REMIND = "locationsNotifRemind";
    public static final String MUTE_LOCATIONS = "muteLocations";
    public static final String FINGERPRINT_SENSOR_PRESENT = "fingerprintSensorPresent";
    public static final String PERMISSIONS_ASKED_FOR_LOCATION = "permissionsAskedForLocation";
    public static final String LOGIN_USERNAME_HINT = "loginUsernameHint";
    public static final String LOGIN_PASSWORD_HINT = "loginPasswordHint";
    public static final String LOGIN_INSTRUCTIONS = "loginInstructions";
    public static final String LOGIN_HELP_LABEL = "loginHelpLabel";
    public static final String LOGIN_HELP_URL = "loginHelpUrl";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 2000;

    // Time
    public static final long ONE_SECOND = 1000;
    public static final long ONE_MINUTE = 60 * ONE_SECOND;
    public static final long ONE_HOUR = 60 * ONE_MINUTE;
    public static final long ONE_DAY = 24 * ONE_HOUR;

	public static boolean isIntentAvailable(Context context, Intent intent) {
		if(intent == null) return false;
	    final PackageManager packageManager = context.getPackageManager();
	    List<ResolveInfo> resolveInfo =
	            packageManager.queryIntentActivities(intent,
	                    PackageManager.MATCH_DEFAULT_ONLY);
	   if (resolveInfo.size() > 0) {
	   		return true;
	   	}
	   return false;
	}
	
    private Utils() {}  // Prevents instantiation

	public static int getPrimaryColor(Context context) {
		return getColor(context, PRIMARY_COLOR);
	}
	
	public static int getHeaderTextColor(Context context) {
		return getColor(context, HEADER_TEXT_COLOR);
	}

	public static int getAccentColor(Context context) {
		return getColor(context, ACCENT_COLOR);
	}

	public static int getSubheaderTextColor(Context context) {
		return getColor(context, SUBHEADER_TEXT_COLOR);
	}

    public static Drawable resize(Drawable image, int height, int width, Context context) {
        Bitmap b = ((BitmapDrawable)image).getBitmap();
        int ht_px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, context.getResources().getDisplayMetrics()));
        int wt_px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, context.getResources().getDisplayMetrics()));
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, wt_px, ht_px, false);
        return new BitmapDrawable(context.getResources(), bitmapResized);
    }

	private static int getColor(Context context, String key) {
		SharedPreferences preferences = context.getSharedPreferences(APPEARANCE, MODE_PRIVATE);
		String color = preferences.getString(key, "#000000");
		try {
			return Color.parseColor(color);
		} catch (IllegalArgumentException e) {
			return Color.TRANSPARENT;
		}
	}


    public static boolean isMapPresent(Context context) {
        return PreferencesUtils.getBooleanFromPreferences(context, CONFIGURATION, MAP_PRESENT, false);
	}
	 
	public static boolean isDirectoryPresent(Context context) {
		return PreferencesUtils.getBooleanFromPreferences(context, CONFIGURATION, DIRECTORY_PRESENT, false);
	}
	
	public static boolean isNotificationsPresent(Context context) {
		return PreferencesUtils.getBooleanFromPreferences(context, CONFIGURATION, NOTIFICATION_PRESENT, false);
	}
	
	public static boolean isGoogleMapsInstalled(Context context) {
	    try {
	        @SuppressWarnings("unused")
			ApplicationInfo info = context.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
	        return true;
	    } catch(PackageManager.NameNotFoundException e) {
	        return false;
	    }
	}
	
	private static boolean isPhoneIntentAvailable(Context context) {
		Uri uri = Uri.parse("tel:222-333-4444");
    	Intent intent = new Intent(Intent.ACTION_DIAL, uri);
		return isIntentAvailable(context, intent);
	}
	
	private static boolean isEmailIntentAvailable(Context context) {
		Uri uri = Uri.parse("mailto:test@test.com");
    	Intent intent = new Intent(Intent.ACTION_SENDTO);
    	intent.setData(uri);
		return isIntentAvailable(context, intent);
	}
	
	private static boolean isWebIntentAvailable(Context context) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("http://www.google.com"));
		return isIntentAvailable(context, intent);
	}

	public static int getAvailableLinkMasks(Context context, Integer... linkTypes) {
		int mask = 0;
		
		List<Integer> typeList;
		if (linkTypes != null) {
			typeList = Arrays.asList(linkTypes);
		
			if (typeList.contains(Linkify.ALL) || typeList.contains(Linkify.MAP_ADDRESSES)) {
				if (isGoogleMapsInstalled(context)) {
					mask = mask | Linkify.MAP_ADDRESSES;
				}
			}
			if (typeList.contains(Linkify.ALL) || typeList.contains(Linkify.EMAIL_ADDRESSES)) {
				if (isEmailIntentAvailable(context)) {
					mask = mask | Linkify.EMAIL_ADDRESSES;
				}
			}
			if (typeList.contains(Linkify.ALL) || typeList.contains(Linkify.PHONE_NUMBERS)) {
				if (isPhoneIntentAvailable(context)) {
					mask = mask | Linkify.PHONE_NUMBERS;
				}
			}
			if (typeList.contains(Linkify.ALL) || typeList.contains(Linkify.WEB_URLS)) {
				if (isWebIntentAvailable(context)) {
					mask = mask | Linkify.WEB_URLS;
				}
			}
		}
		
		return mask;
	}
	
	public static void sendMarketIntent(Activity activity, boolean setFlags) {
		String packageName = activity.getApplicationContext().getPackageName();
		Intent marketIntent = new Intent(Intent.ACTION_VIEW);
		marketIntent.setData(Uri.parse("market://details?id="
						+ packageName));

		if (isIntentAvailable(activity, marketIntent)) {
			if (setFlags) {
				marketIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				marketIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			}
			activity.startActivity(marketIntent);
			activity.finish();
		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setData(Uri.parse("http://play.google.com/store/apps/details?id="
							+ packageName));
			if (setFlags) {
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			}
			activity.startActivity(intent);
		}
		
	}
	
	public static boolean isAuthenticationNeededForType(String type) {
		List<String> authTypeList = Arrays
				.asList(ModuleType.AUTHENTICATION_NEEDED);
		return authTypeList.contains(type);
	}

    public static boolean isAuthenticationNeededForDirectory(ContentResolver resolver, String moduleId) {
        Cursor directoryCursor = resolver.query(
                EllucianContract.ModulesProperties.CONTENT_URI,
                new String[]{EllucianContract.ModulesProperties.MODULE_PROPERTIES_VALUE},
                EllucianContract.Modules.MODULES_ID + "=? AND " +
                        EllucianContract.ModulesProperties.MODULE_PROPERTIES_NAME + "=?",
                new String[]{moduleId, ModuleMenuAdapter.DIRECTORY_MODULE_VERSION},
                null);

        String directoryModuleVersion="";
        if (directoryCursor != null) {
            while (directoryCursor.moveToNext()) {
                directoryModuleVersion = directoryCursor.getString(
                        directoryCursor.getColumnIndex(EllucianContract.ModulesProperties.MODULE_PROPERTIES_VALUE));
            }
        }
        if (directoryCursor != null) {
            directoryCursor.close();
        }

        if (TextUtils.isEmpty(directoryModuleVersion)) {
            return true; // legacy should be secure
        } else {
            return false;
        }

    }
	
	public static boolean isAuthenticationNeededForSubType(Context context, String subType) {
		EllucianApplication ellucianApp = (EllucianApplication) context.getApplicationContext();
		ModuleConfiguration moduleConfig = ellucianApp.findModuleConfig(subType);
		
		return moduleConfig != null ? moduleConfig.secure : false;
	}

    public static void hideProgressIndicator(Activity activity) {
        View progressSpinner = activity.findViewById(R.id.progress_spinner);
        if (progressSpinner != null) {
            progressSpinner.setVisibility(View.GONE);
        }
    }

    public static void showProgressIndicator(Activity activity) {
        View progressSpinner = activity.findViewById(R.id.progress_spinner);
        if (progressSpinner != null) {
            progressSpinner.setVisibility(View.VISIBLE);
        }
    }

    public static void hideProgressIndicator(View view) {
        View progressSpinner = view.findViewById(R.id.progress_spinner);
        if (progressSpinner != null) {
            progressSpinner.setVisibility(View.GONE);
        }
    }

    public static void showProgressIndicator(View view) {
        View progressSpinner = view.findViewById(R.id.progress_spinner);
        if (progressSpinner != null) {
            progressSpinner.setVisibility(View.VISIBLE);
        }
    }

    public static boolean allowMaps(Context context) {
        // check if google play services is present
        try {
            context.getPackageManager().getApplicationInfo(
                    "com.google.android.gms", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void showLoginDialog(AppCompatActivity activity) {
        showLoginDialog(activity, null, null);
    }

    public static void showLoginDialog(AppCompatActivity activity, Intent intent,
                                        List<String> roles) {


        EllucianApplication application = ((EllucianActivity)activity).getEllucianApp();
        // If the user enabled fingerprint auth, let them re-log in with that.
        if (UserUtils.getUseFingerprintEnabled(application)) {
            String previousUserName = UserUtils.getSavedUserName(application);
            String previousPassword = UserUtils.getSavedUserPassword(application);

            if (!TextUtils.isEmpty(previousUserName) &&
                    !TextUtils.isEmpty(previousPassword)) {
                showFingerprintDialog(activity, intent, roles, true, previousUserName, previousPassword);
                return;
            }
        }

        LoginDialogFragment loginFragment = LoginDialogFragment.newInstance(activity.getResources().getConfiguration());
        if (intent != null) {
            loginFragment.queueIntent(intent, roles);
        }
        loginFragment.show(activity.getSupportFragmentManager(),
                LoginDialogFragment.LOGIN_DIALOG);

    }

    public static void showFingerprintDialog(AppCompatActivity activity, Intent intent,
                                             List<String> roles) {
        showFingerprintDialog(activity, intent, roles, false, null, null);
    }

    private static void showFingerprintDialog(AppCompatActivity activity, Intent intent,
                                             List<String> roles, boolean refreshRoles,
                                             String userName, String password) {
        FingerprintDialogFragment fingerprintFragment = new FingerprintDialogFragment();
        if (intent != null) {
            fingerprintFragment.queueIntent(intent, roles);
        }
        fingerprintFragment.setRefreshRoles(refreshRoles);
        if (!TextUtils.isEmpty(userName) && !TextUtils.isEmpty(password)) {
            fingerprintFragment.setUserName(userName);
            fingerprintFragment.setPassword(password);
        }
        fingerprintFragment.show(activity.getSupportFragmentManager(),
                FingerprintDialogFragment.FINGERPRINT_DIALOG);
    }

    public static void showLoginForQueuedIntent(Activity activity, String moduleId, String moduleType) {
        // Pass the incoming Intent as an extra, so after authentication
        // user is directed back here.

        QueuedIntentHolder queuedIntentHolder = buildQueuedIntentHolder(activity, moduleId, moduleType);

        Intent mainIntent = new Intent(activity, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (UserUtils.getUseFingerprintEnabled(activity)) {
            mainIntent.putExtra(MainActivity.REQUEST_FINGERPRINT, true);
        } else {
            mainIntent.putExtra(MainActivity.SHOW_LOGIN, true);
        }

        mainIntent.putExtra(QueuedIntentHolder.QUEUED_INTENT_HOLDER, queuedIntentHolder);
        activity.startActivity(mainIntent);
        activity.finish();
    }

    private static QueuedIntentHolder buildQueuedIntentHolder(Activity activity, String moduleId, String moduleType) {
        Intent queuedIntent = activity.getIntent();
        if (moduleId == null) {
            Cursor cursor = activity.getContentResolver().query(EllucianContract.Modules.CONTENT_URI,
                    new String[] {EllucianContract.Modules.MODULES_ID},
                    EllucianContract.Modules.MODULE_TYPE + "= ?",
                    new String[]{moduleType},
                    null);
            if (cursor.moveToFirst()) {
                moduleId = cursor.getString(cursor.getColumnIndex(EllucianContract.Modules.MODULES_ID));
            }
            cursor.close();
        }

        return new QueuedIntentHolder(moduleId, queuedIntent);
    }

    @SuppressWarnings("deprecation")
    public static boolean isLocationEnabled(Context context) {
        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }

    }

    public static boolean hasPlayServicesAvailable(Activity activity) {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();

        int status = googleAPI.isGooglePlayServicesAvailable(activity);

        if (status != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(status)) {
                googleAPI.showErrorDialogFragment(activity, status, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                Toast.makeText(activity, activity.getString(R.string.services_feature_not_supported), Toast.LENGTH_LONG).show();
            }
            return false;
        }

        return true;
    }

    public static void makeTextViewHyperlink(TextView tv) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(tv.getText());
        ssb.setSpan(new URLSpan("#"), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(ssb, TextView.BufferType.SPANNABLE);
    }

    public static String getSizeName(Configuration configuration) {
        int screenLayout = Configuration.SCREENLAYOUT_UNDEFINED;
        if (configuration != null) {
            screenLayout = configuration.screenLayout;
            screenLayout &= Configuration.SCREENLAYOUT_SIZE_MASK;
        }

        switch (screenLayout) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                return "small";
            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                return "normal";
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                return "large";
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                return "xlarge";
            default:
                return "undefined";
        }
    }

    public static void changeConfiguration(Context context, EllucianApplication ellucianApplication,
                       String configurationUrl, String configurationName, String id) {
        // delete before retrieving the new configuration
        context.getContentResolver().delete(EllucianContract.BASE_CONTENT_URI,
                null, null);

        context.getSharedPreferences(
                Utils.GOOGLE_ANALYTICS, MODE_PRIVATE).edit().clear().apply();

        final SharedPreferences preferences = context.getSharedPreferences(
                Utils.CONFIGURATION, MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        // Clear all Configuration Preferences
        editor.clear().apply();

        // Clear all User Preferences
        SettingsUtils.addBooleanToPreferences(context, UserUtils.USER_FINGERPRINT_OPT_IN, false);
        ellucianApplication.removeAppUser();

        editor.putString(Utils.CONFIGURATION_URL, configurationUrl);
        editor.putString(Utils.CONFIGURATION_NAME, configurationName);
        editor.putString(Utils.ID, id);
        editor.commit();

    }
}
