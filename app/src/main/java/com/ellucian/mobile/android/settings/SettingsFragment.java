/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.settings;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;


public class SettingsFragment extends PreferenceFragment {
    public static final String TAG = SettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the settings from an XML resource
        addPreferencesFromResource(R.xml.settings);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final SettingsActivity activity = (SettingsActivity) getActivity();
        final EllucianApplication application = (EllucianApplication) activity.getApplication();

        CheckBoxPreference fingerprintOptIn = (CheckBoxPreference) findPreference(UserUtils.USER_FINGERPRINT_OPT_IN);

        if (!UserUtils.isFingerprintOptionEnabled(getActivity())) {
            fingerprintOptIn.setEnabled(false);
        } else {
            fingerprintOptIn.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean useFingerprint = (boolean) o;
                    Log.i(TAG, "Set use fingerprint to " + useFingerprint);
                    if (useFingerprint) { // User enabled fingerprint
                        if (application.isUserAuthenticated()) {
                            // If the user has already authenticated, but didn't have credentials stored
                            // in SharedPrefs, we need to re-authenticate them to capture the credentials.
                            String userName = UserUtils.getSavedUserName(activity);
                            String password = UserUtils.getSavedUserPassword(activity);
                            if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password)) {
                                Log.i(TAG, "SharedPrefs does not have username and/or pw.");
                                userName = application.getAppUserId();
                                password = application.getAppUserPassword();
                                if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password)) {
                                    Log.i(TAG, "User() obj does not have username and/or pw.");
                                    String loginType = PreferencesUtils.getStringFromPreferences(getActivity(), Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);
                                    if (TextUtils.equals(loginType, Utils.NATIVE_LOGIN_TYPE)) {
                                        Log.i(TAG, "Native device login - request login.");
                                        Utils.showLoginDialog(activity);
                                    } else {
                                        Log.i(TAG, "Browser login - do nothing.");
                                    }
                                } else {
                                    Log.i(TAG, "Reauthenticate in order to save username and pw to SharedPrefs.");
                                    UserUtils.reAuthenticateUser(activity, userName, password, false, useFingerprint);
                                }
                            } else {
                                Log.i(TAG, "App already has users credentials.");
                            }
                        } else {
                            Log.i(TAG, "User is not authenticated. Go log in");
                            Utils.showLoginDialog(activity);
                        }
                    } else {
                        // User disabled fingerprint. If the user has already authenticated, we need to
                        // start the idle timer now that they are not using fingerprint anymore.
                        if (application.isUserAuthenticated()) {
                            String loginType = PreferencesUtils.getStringFromPreferences(getActivity(), Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);
                            if(loginType.equals(Utils.NATIVE_LOGIN_TYPE)) {
                                application.startIdleTimer();
                            }
                        }
                    }

                    return true; // return true to update the state of the Preference with the new value.
                }
            });
        }

        if (!application.configUsesBeacons()) {
            ResetLocationsDialog resetLocations = (ResetLocationsDialog) findPreference("pref_key_reset_interested_locations");
            resetLocations.setEnabled(false);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        handleLocationPermission();
    }

    private void handleLocationPermission(){
        EmptyPreference locationPermission = (EmptyPreference) findPreference("pref_key_location_permission");
        PreferenceCategory locationAwarenessCategory = (PreferenceCategory) findPreference("pref_key_location_category");
        final SettingsActivity activity = (SettingsActivity) getActivity();

        // If M+, see if the Location permission has been denied and the user said "don't ask me again"
        // If so, display message that links them to App Settings where they can enable the permission.
        // Otherwise, do not display it.
        boolean showEnableLocationPermissionItem = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (SettingsUtils.hasUserBeenAskedForPermission(getContext(), Utils.PERMISSIONS_ASKED_FOR_LOCATION)) {
                        showEnableLocationPermissionItem = true;
                    }
                }
            }
        }

        if (locationPermission != null && locationAwarenessCategory != null) {
            if (showEnableLocationPermissionItem) {
                Log.d(TAG, "App cannot prompt user for Location permission. Provide link to App Settings.");
                locationPermission.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        return false;
                    }
                });
                locationPermission.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        return false;
                    }
                });
            } else {
                // We must remove this item from the preferences list.
                locationAwarenessCategory.removePreference(locationPermission);
            }
        }

    }

}
