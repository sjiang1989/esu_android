/*
 * Copyright 2015-2017 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.services;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class AuthenticateUserIntentService extends IntentService {
	public static final String ACTION_UPDATE_MAIN = "com.ellucian.mobile.android.client.services.AuthenticateUserIntentService.action.updateMain";
	public static final String ACTION_BACKGROUND_AUTH = "com.ellucian.mobile.android.client.services.AuthenticateUserIntentService.action.backgroundAuth";
	public static final String ACTION_SUCCESS = "com.ellucian.mobile.android.client.services.AuthenticateUserIntentService.action.success";
	private static final String ACTION_FAILED = "com.ellucian.mobile.android.client.services.AuthenticateUserIntentService.action.failed";
	private static final String TAG = AuthenticateUserIntentService.class.getSimpleName();

	public AuthenticateUserIntentService() {
		super("AuthenticateUserIntentService");
		
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		EllucianApplication ellucianApp = (EllucianApplication) this.getApplication();
		
		String securityUrl = PreferencesUtils.getStringFromPreferences(this, Utils.SECURITY, Utils.SECURITY_URL, "");
		String loginUsername = intent.getStringExtra(Extra.LOGIN_USERNAME);
		String loginPassword = intent.getStringExtra(Extra.LOGIN_PASSWORD);
		boolean saveUser = intent.getBooleanExtra(Extra.LOGIN_SAVE_USER, false);
		boolean useFingerprint = intent.getBooleanExtra(Extra.LOGIN_USE_FINGERPRINT, false);
		boolean backgroundAuth = intent.getBooleanExtra(Extra.LOGIN_BACKGROUND, false);
		boolean refreshOnly = intent.getBooleanExtra(Extra.REFRESH, false);
		
		boolean success = false;
		
		if(refreshOnly) {
			securityUrl += "?refresh=true";
		}

		MobileClient client = new MobileClient(this);
        if (!intent.getBooleanExtra(Extra.SEND_UNAUTH_BROADCAST, true)) {
            client.setSendUnauthenticatedBroadcast(false);
        }

		String response = client.authenticateUser(securityUrl, loginUsername, loginPassword);

		if (!TextUtils.isEmpty(response)) {
			JSONObject userInfoJson;
			String status;
			try {
				userInfoJson = new JSONObject(response);
				status = userInfoJson.getString("status");

				if (status.equals("success")) {

					String userId = userInfoJson.getString("userId");
					String username = userInfoJson.getString("authId");
					ArrayList<String> roleList = new ArrayList<>();
					if (userInfoJson.has("roles")) {
						JSONArray roles = userInfoJson.getJSONArray("roles");
						if (roles != null) {
							int rolesLength = roles.length();
							if (rolesLength > 0) {
								for (int i = 0; i < rolesLength; i++) {
									roleList.add((String) roles.get(i));
								}
							}
						}
					}

					ellucianApp.createAppUser(userId, username, loginPassword,
							roleList);

                    ellucianApp.setLastAuthRefresh();

					// Save User Info into shared preferences if the user chooses to.
					if (saveUser || useFingerprint) {
						UserUtils.saveUserInfo(this, userId, username,
								loginPassword, roleList, useFingerprint);

					}
					success = true;
				}
			} catch (JSONException e) {
				Log.e(TAG, "JSONException parsing userInfoJson");
			}
		}		
		LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
		Intent broadcastIntent = new Intent();
		
		if (backgroundAuth) {
			broadcastIntent.setAction(ACTION_BACKGROUND_AUTH);
		} else {
			broadcastIntent.setAction(ACTION_UPDATE_MAIN);
		}
		
		if (success) {
			broadcastIntent.putExtra(Extra.LOGIN_SUCCESS, ACTION_SUCCESS); 
		} else {
			broadcastIntent.putExtra(Extra.LOGIN_SUCCESS, ACTION_FAILED); 
		}
		
		bm.sendBroadcast(broadcastIntent);
		
		// login was successful, make sure this user is registered with the mobile server
		ellucianApp.registerWithGcmIfNeeded();
	}
	
}
