/*
 * Copyright 2016-2017 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.client.notifications.Notification;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.login.LoginDialogFragment;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.settings.SettingsUtils;

import java.util.List;

import javax.crypto.SecretKey;

/**
 * A Utility class with static methods for retrieving and updating values
 * regarding the authenticated User.
 */
public class UserUtils {
    public static final String TAG = UserUtils.class.getSimpleName();

    public static final String USER = "user";
    public static final String USER_TIMED_OUT = "userTimedOut";
    public static final String USER_FINGERPRINT_OPT_IN = EllucianApplication.getContext().getString(R.string.pref_key_user_fingerprint_opt_in);
    public static final String USER_FINGERPRINT_NEEDED = "userFingerprintNeeded";
    private static final String USER_ID = "userId";
    private static final String USER_NAME = "userName";
    private static final String USER_PASSWORD = "userPassword";
    private static final String USER_PASSWORD_KEY = "userPasswordKey";
    private static final String USER_ROLES = "userRoles";

    public static void saveUserInfo(Context context, String userId, String username, String password,
                                    List<String> roleList, Boolean useFingerprint) {

		StringBuilder roleBuilder = new StringBuilder();
		int rolesLength = roleList.size();
		String role;
		for (int i = 0; i < rolesLength; i++) {
			if (i > 0) {
				roleBuilder.append(",");
			}
			role = roleList.get(i);
			roleBuilder.append(role);

		}

		SharedPreferences preferences = context.getSharedPreferences(USER, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();

		if (!TextUtils.isEmpty(userId)) {
			editor.putString(USER_ID, userId);
			Log.d(TAG+".saveUserInfo", "User saved with id: " + userId);
		}
		if (!TextUtils.isEmpty(username)) {
			editor.putString(USER_NAME, username);
			Log.d(TAG+".saveUserInfo", "User saved with username: " + username);
		}
		if (!TextUtils.isEmpty(password)) {
			//Encrypt password before saving
			String encryptedPassword = null;
            String keyString= null;
            try {
                SecretKey key = Encrypt.generateKey();
                encryptedPassword = Encrypt.encrypt(key, password);
                keyString = Encrypt.keyToString(key);
			} catch (Exception e) {
				Log.d(TAG+".saveUserInfo", "Encryption Failed");
				e.printStackTrace();
			}

			editor.putString(USER_PASSWORD, encryptedPassword);
            editor.putString(USER_PASSWORD_KEY, keyString);
        }
		String rolesString = roleBuilder.toString();
		if (!TextUtils.isEmpty(rolesString)) {
			editor.putString(USER_ROLES, rolesString);
			Log.d(TAG+".saveUserInfo", "User saved with roles: " + rolesString);
		}
        if (useFingerprint) {
            SettingsUtils.addBooleanToPreferences(context, USER_FINGERPRINT_OPT_IN, true);
            editor.putBoolean(USER_FINGERPRINT_NEEDED, false);
            Log.d(TAG+".saveUserInfo", "User saved with use fingerprint enabled");
        }

		editor.apply();

	}

    public static String getSavedUserId(Context context) {
		return PreferencesUtils.getStringFromPreferences(context, USER, USER_ID, null);
	}

    public static String getSavedUserName(Context context) {
		return PreferencesUtils.getStringFromPreferences(context, USER, USER_NAME, null);
	}

    public static String getSavedUserPassword(Context context) {
        String encryptedPassword = getSavedEncryptedPassword(context);
        String keyString = getSavedUserPasswordKey(context);

        String password = null;
        if (encryptedPassword != null) {
            password = decryptPasswordWithKey(encryptedPassword, keyString, context);
        }
        return password;
	}

    private static String getSavedEncryptedPassword(Context context) {
        return PreferencesUtils.getStringFromPreferences(context, USER, USER_PASSWORD, null);
    }

    private static String getSavedUserPasswordKey(Context context) {
        return PreferencesUtils.getStringFromPreferences(context, USER, USER_PASSWORD_KEY, null);
    }

    private static String decryptPasswordWithKey(String encryptedPassword, String keyString,
                                                 Context context) {
        String password = "";
        try {
            // if the user had a saved password from an older version, migrate that
            if (TextUtils.isEmpty(keyString)) {
                password = Encrypt.legacyDecrypt(encryptedPassword);
                migratePassword(password, context);
            } else {
                SecretKey key = Encrypt.stringToKey(keyString);
                password = Encrypt.decrypt(key, encryptedPassword);
            }
        } catch (Exception e) {
            Log.e("EllucianApplication.loadSavedUser",
                    "Decrypting on password failed, user not created.");
        }
        return password;
    }

    // Current encrypted password using an encryption algorithm that goes away with Android N.
    private static void migratePassword(String password, Context context) {
        SharedPreferences preferences = context.getSharedPreferences(USER, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        try {
            SecretKey key = Encrypt.generateKey();
            String encryptedPassword = Encrypt.encrypt(key, password);
            String keyString = Encrypt.keyToString(key);
            editor.putString(USER_PASSWORD, encryptedPassword);
            editor.putString(USER_PASSWORD_KEY, keyString);

        } catch (Exception e) {
            Log.d(TAG+".saveUserInfo", "Encryption Failed");
            e.printStackTrace();
        }

        editor.apply();

    }

    public static String getSavedUserRoles(Context context) {
		return PreferencesUtils.getStringFromPreferences(context, USER, USER_ROLES, null);
	}

    public static Boolean getUseFingerprintEnabled(Context context) {
        return SettingsUtils.getBooleanFromPreferences(context, USER_FINGERPRINT_OPT_IN, false);
    }

    public static void removeSavedUser(Context context) {
		SharedPreferences preferences = context.getSharedPreferences(USER, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.remove(USER_ID);
        editor.remove(USER_ROLES);
        editor.remove(USER_FINGERPRINT_NEEDED);
        editor.remove(USER_TIMED_OUT);

        if (!getUseFingerprintEnabled(context)) {
            removeSavedUserLogin(context);
        }

        editor.apply();
	}

    private static void removeSavedUserLogin(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(USER, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.remove(USER_NAME);
        removeSavedUserPassword(context);
        editor.apply();

        // reset the "use fingerprint" flag to false
        SettingsUtils.addBooleanToPreferences(context, USER_FINGERPRINT_OPT_IN, false);
    }

    private static void removeSavedUserPassword(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(USER, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.remove(USER_PASSWORD);
        editor.remove(USER_PASSWORD_KEY);
        editor.apply();
    }

    /** If the user can unlock the device with a fingerprint
     we have to manage the expiration of the fingerprint.
     It expires {@code FINGERPRINT_VALID_MILLISECONDS} after the app is no longer
     in the foreground. If the app is being used, keep updating the fingerprint timestamp.
     */
    public static void manageFingerprintTimeout(EllucianActivity activity) {
        if (getUseFingerprintEnabled(activity)) {
            if (activity.getEllucianApp().wasInBackground()) {
                if (activity.getEllucianApp().hasFingerprintExpired()) {
                    Log.d(TAG, "Fingerprint has expired. Setting Flag. Put locks on secure modules");
                    PreferencesUtils.addBooleanToPreferences(activity, USER, USER_FINGERPRINT_NEEDED, true);
                    activity.getEllucianApp().resetModuleMenuAdapter();
                    activity.configureNavigationDrawer();
                } else {
                    activity.getEllucianApp().resetFingerprintValidTime();
                }
            } else {
                if (!activity.getEllucianApp().isFingerprintUpdateNeeded()) {
                    activity.getEllucianApp().resetFingerprintValidTime();
                }
            }
        }
    }

    public static int getUnreadNotificationsCount(Context context) {
        //Get the unread notification count
        Cursor unreadNotificationsCursor = context.getContentResolver().query(EllucianContract.Notifications.CONTENT_URI,
                new String[]{EllucianContract.Notifications.NOTIFICATIONS_ID},
                EllucianContract.Notifications.NOTIFICATIONS_STATUSES + " not like ? or " + EllucianContract.Notifications.NOTIFICATIONS_STATUSES + " is null",
                new String[]{"%" + Notification.STATUS_READ + "%"},
                EllucianContract.Notifications.DEFAULT_SORT);
        int count = 0;
        if (unreadNotificationsCursor != null) {
            count = unreadNotificationsCursor.getCount();
        }

        if (unreadNotificationsCursor != null) {
            unreadNotificationsCursor.close();
        }
        Log.d(TAG, "getUnreadNotificationsCount: " + count);
        return count;
    }

    public static void reAuthenticateUser(Activity activity, String username, String password, boolean saveUser,
                                    boolean useFingerprint) {
        Intent intent = new Intent(activity, AuthenticateUserIntentService.class);
        intent.putExtra(Extra.LOGIN_USERNAME, username);
        intent.putExtra(Extra.LOGIN_PASSWORD, password);
        intent.putExtra(Extra.LOGIN_SAVE_USER, saveUser);
        intent.putExtra(Extra.LOGIN_USE_FINGERPRINT, useFingerprint);
        intent.putExtra(Extra.SEND_UNAUTH_BROADCAST, false);
        activity.startService(intent);
    }

    public static boolean isFingerprintAuthAvailable(FingerprintManagerCompat mFingerprintManager) {
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    public static boolean isFingerprintOptionEnabled(Context context) {

        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Utils.FINGERPRINT_SENSOR_PRESENT, false)) {
            FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(context);

            boolean fingerprintAuthAvailable = isFingerprintAuthAvailable(fingerprintManager)
                    && LoginDialogFragment.doesDeviceHaveScreenLockOn(context);
            if (fingerprintAuthAvailable) {
                String loginType = PreferencesUtils.getStringFromPreferences(context, Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);
                if(loginType.equals(Utils.NATIVE_LOGIN_TYPE)) {
                    return true;
                } else {
                    // Web based login cannot use fingerprint. Set it false if it was previously set true
                    // by a previous configuration.
                    if (SettingsUtils.getBooleanFromPreferences(context, UserUtils.USER_FINGERPRINT_OPT_IN, false)) {
                        SettingsUtils.addBooleanToPreferences(context, UserUtils.USER_FINGERPRINT_OPT_IN, false);
                    }
                    return false;
                }
            } else {
                Log.d(TAG, "User hasn't enrolled fingerprints OR has not screen lock.");
                SettingsUtils.addBooleanToPreferences(context, UserUtils.USER_FINGERPRINT_OPT_IN, false);
                return false;
            }

        } else {
            // no fingerprint sensor on device.
            SettingsUtils.addBooleanToPreferences(context, UserUtils.USER_FINGERPRINT_OPT_IN, false);
            return false;
        }

    }
}
