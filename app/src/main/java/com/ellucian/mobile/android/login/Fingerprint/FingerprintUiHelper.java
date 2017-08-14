/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.login.Fingerprint;

import android.annotation.TargetApi;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat.AuthenticationCallback;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat.AuthenticationResult;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat.CryptoObject;
import android.support.v4.os.CancellationSignal;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.util.VersionSupportUtils;

/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
@TargetApi(24)
class FingerprintUiHelper extends AuthenticationCallback {
    public static final String TAG = FingerprintUiHelper.class.getSimpleName();

    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1300;

    private final FingerprintManagerCompat mFingerprintManager;
    private final ImageView mIcon;
    private final TextView mErrorTextView;
    private final Callback mCallback;
    private final Button mPasswordButton;
    private CancellationSignal mCancellationSignal;
    private int attempt = 0;

    boolean mSelfCancelled;

    public static class FingerprintUiHelperBuilder {
        private final FingerprintManagerCompat mFingerPrintManager;

        public FingerprintUiHelperBuilder(FingerprintManagerCompat fingerprintManager) {
            mFingerPrintManager = fingerprintManager;
        }

        public FingerprintUiHelper build(ImageView icon, TextView errorTextView,
                                         Callback callback, Button passwordButton) {
            return new FingerprintUiHelper(mFingerPrintManager, icon, errorTextView,
                    callback, passwordButton);
        }
    }

    /**
     * Constructor for {@link FingerprintUiHelper}. This method is expected to be called from
     * only the {@link FingerprintUiHelperBuilder} class.
     */
    private FingerprintUiHelper(FingerprintManagerCompat fingerprintManager,
                                ImageView icon, TextView errorTextView, Callback callback,
                                Button passwordButton) {
        mFingerprintManager = fingerprintManager;
        mIcon = icon;
        mErrorTextView = errorTextView;
        mCallback = callback;
        mPasswordButton = passwordButton;
    }

    public boolean isFingerprintAuthAvailable() {
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    public void startListening(CryptoObject cryptoObject) {
        if (!isFingerprintAuthAvailable()) {
            return;
        }
        mCancellationSignal = new CancellationSignal();
        mSelfCancelled = false;
        mFingerprintManager
                .authenticate(cryptoObject, 0 /* flags */, mCancellationSignal, this, null);
        mIcon.setImageResource(R.drawable.ic_fingerprint_40px);
    }

    public void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        if (!mSelfCancelled) {
            if (attempt > 1) {
                Log.e(TAG, "onAuthenticationError() called with: errMsgId = [" + errMsgId + "], errString = [" + errString + "]");
                Log.e(TAG, "attempt number: " + attempt);
                showError(errString);
                mIcon.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onError();
                    }
                }, ERROR_TIMEOUT_MILLIS);
            } else {
                Log.e(TAG, "onAuthenticationError() called with: errMsgId = [" + errMsgId + "], errString = [" + errString + "]");
                Log.e(TAG, "a number: " + attempt);
                mCallback.onRedisplay();
            }
        }
        mPasswordButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError(helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        showError(mIcon.getResources().getString(
                R.string.fingerprint_not_recognized));
        mPasswordButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAuthenticationSucceeded(AuthenticationResult result) {
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        mIcon.setImageResource(R.drawable.ic_fingerprint_success);
        mErrorTextView.setTextColor(
                VersionSupportUtils.getColorHelper(mErrorTextView, R.color.success_color));
        mErrorTextView.setText(
                mErrorTextView.getResources().getString(R.string.fingerprint_success));
        mIcon.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallback.onAuthenticated();
            }
        }, SUCCESS_DELAY_MILLIS);
    }

    private void showError(CharSequence error) {
        mIcon.setImageResource(R.drawable.ic_fingerprint_error);
        mErrorTextView.setText(error);
        mErrorTextView.setTextColor(
                VersionSupportUtils.getColorHelper(mErrorTextView, R.color.warning_color));
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        mErrorTextView.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    Runnable mResetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            mErrorTextView.setTextColor(
                    VersionSupportUtils.getColorHelper(mErrorTextView, R.color.hint_color));
            mErrorTextView.setText(
                    mErrorTextView.getResources().getString(R.string.fingerprint_hint));
            mIcon.setImageResource(R.drawable.ic_fingerprint_40px);
        }
    };

    public interface Callback {

        void onAuthenticated();

        void onError();

        void onRedisplay();
    }
}
