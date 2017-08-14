/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.login.Fingerprint;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat.CryptoObject;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.EllucianDialogFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.login.LoginDialogFragment;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;

import java.util.List;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
@TargetApi(24)
public class FingerprintDialogFragment extends EllucianDialogFragment
        implements TextView.OnEditorActionListener, FingerprintUiHelper.Callback {

    public static final String TAG = FingerprintDialogFragment.class.getSimpleName();
    public static final String FINGERPRINT_DIALOG = "fingerprint_dialog";
    private Intent queuedIntent;
    private boolean refreshRoles = false;
    private List<String> roles;
    private MainAuthenticationReceiver mainAuthenticationReceiver;
    private String userName;
    private String password;

    private CryptoObject mCryptoObject;
    private FingerprintUiHelper mFingerprintUiHelper;
    private EllucianActivity activity;

    public FingerprintDialogFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (EllucianActivity) getActivity();
    }

    @SuppressLint("InlinedApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.dialog_sign_in));
        View v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false);

        //  Cancel button
        Button cancelButton = (Button) v.findViewById(R.id.cancel_button);
        cancelButton.setText(R.string.fingerprint_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                doCancel();
            }
        });

        // Use Password button (appears after failed fingerprint attempt)
        Button passwordButton = (Button) v.findViewById(R.id.use_password_button);
        passwordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usePasswordInstead();
            }
        });
        ImageView fingerPrintIcon = (ImageView) v.findViewById(R.id.fingerprint_icon);
        TextView fingerPrintText = (TextView) v.findViewById(R.id.fingerprint_status);

        FingerprintManagerCompat mFingerprintManager = FingerprintManagerCompat.from(getContext());
        FingerprintUiHelper.FingerprintUiHelperBuilder mFingerprintUiHelperBuilder = new FingerprintUiHelper.FingerprintUiHelperBuilder(mFingerprintManager);
        mFingerprintUiHelper = mFingerprintUiHelperBuilder.build(
                fingerPrintIcon, fingerPrintText, this, passwordButton);

        // If fingerprint authentication is not available, switch immediately to the password screen.
        if (!mFingerprintUiHelper.isFingerprintAuthAvailable()) {
            usePasswordInstead();
        }
        return v;
    }

    private void doCancel() {
        sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_CANCEL, "Click Cancel", null, null);
        // Make sure queue is empty in case of another login attempt
        queuedIntent = null;
    }

    public void queueIntent(Intent intent, List<String> roles) {
        queuedIntent = intent;
        this.roles = roles;
    }

    @Override
    public void onResume() {
        super.onResume();
        mFingerprintUiHelper.startListening(mCryptoObject);
        mainAuthenticationReceiver = new MainAuthenticationReceiver();
        LocalBroadcastManager.getInstance(activity).registerReceiver(mainAuthenticationReceiver, new IntentFilter(AuthenticateUserIntentService.ACTION_UPDATE_MAIN));
    }

    @Override
    public void onPause() {
        super.onPause();
        mFingerprintUiHelper.stopListening();
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(mainAuthenticationReceiver);
    }

    /**
     * Sets the crypto object to be passed in when authenticating with fingerprint.
     */
    @SuppressWarnings("unused")
    public void setCryptoObject(CryptoObject cryptoObject) {
        mCryptoObject = cryptoObject;
    }

    /**
     * Switches to password login.
     * Terminate active user's session.
     */
    private void usePasswordInstead() {
        // Fingerprint is not used anymore. Stop listening for it.
        mFingerprintUiHelper.stopListening();
        dismiss();

        LoginDialogFragment loginFragment = LoginDialogFragment.newInstance(getResources().getConfiguration());
        loginFragment.queueIntent(queuedIntent, roles);
        loginFragment.setPreviousUserName(UserUtils.getSavedUserName(getContext()));
        loginFragment.show(activity.getSupportFragmentManager(),
                LoginDialogFragment.LOGIN_DIALOG);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            return true;
        }
        return false;
    }

    /**
     * Success Callback from FingerprintUiHelper. Start the queued intent
     */
    @Override
    public void onAuthenticated() {
        PreferencesUtils.addBooleanToPreferences(activity, UserUtils.USER, UserUtils.USER_FINGERPRINT_NEEDED, false);
        sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_LOGIN, "Fingerprint authentication", null, null);
        if (refreshRoles) {
            // even if there's a queued intent, we cannot start it until we refresh the users roles.
            UserUtils.reAuthenticateUser(activity, userName, password, false, true);
        } else {
            LoginDialogFragment.startQueuedIntent(activity, queuedIntent, roles, false);
            dismiss();
        }
        EllucianApplication ellucianApp = activity.getEllucianApp();
        ellucianApp.resetModuleMenuAdapter();
        activity.configureNavigationDrawer();
    }

    @Override
    public void onError() {
        usePasswordInstead();
    }

    /** Tracking Google issue 192513.
     *  https://code.google.com/p/android/issues/detail?id=192513
     *  The lockscreen fingerprint manager onSuccess callback is
     *  interfering with our subsequent fingerprint manager instance.
     */
    @Override
    public void onRedisplay() {
        Log.e(TAG, "onRedisplay() called");
        dismiss();

        FragmentManager manager = activity.getSupportFragmentManager();
        FingerprintDialogFragment fingerprintDialogFragment =
                (FingerprintDialogFragment) manager.findFragmentByTag(FingerprintDialogFragment.FINGERPRINT_DIALOG);

        if (fingerprintDialogFragment != null) {
            fingerprintDialogFragment.show(manager, FingerprintDialogFragment.FINGERPRINT_DIALOG);
        }
    }

    public void setRefreshRoles(boolean refreshRoles) {
        this.refreshRoles = refreshRoles;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public class MainAuthenticationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent incomingIntent) {
            String result = incomingIntent.getStringExtra(Extra.LOGIN_SUCCESS);

            if(!TextUtils.isEmpty(result) &&
                    result.equals(AuthenticateUserIntentService.ACTION_SUCCESS)) {

                LocalBroadcastManager.getInstance(context).unregisterReceiver(mainAuthenticationReceiver);
                EllucianApplication ellucianApp = activity.getEllucianApp();

                ellucianApp.startNotifications();

                // Checks to see if the dialog was opened by a request for an auth-necessary activity
                LoginDialogFragment.startQueuedIntent(activity, queuedIntent, roles, false);
                queuedIntent = null;
            } else {
                usePasswordInstead();
            }
            dismiss();

        }
    }

    @Override
    public void onDestroyView() {
        // Trick to keep dialog open on rotate
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }
}
