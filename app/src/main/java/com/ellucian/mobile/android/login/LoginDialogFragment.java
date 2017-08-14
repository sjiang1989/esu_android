/*
 * Copyright 2015-2017 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.login;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.MainActivity;
import com.ellucian.mobile.android.adapter.ModuleMenuAdapter;
import com.ellucian.mobile.android.app.DrawerLayoutHelper;
import com.ellucian.mobile.android.app.EllucianDialogFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.util.VersionSupportUtils;

import java.util.ArrayList;
import java.util.List;

import static com.ellucian.mobile.android.settings.SettingsUtils.getBooleanFromPreferences;

public class LoginDialogFragment extends EllucianDialogFragment {
	public static final String TAG = LoginDialogFragment.class.getSimpleName();
	public static final String LOGIN_DIALOG = "login_dialog";
    private Intent queuedIntent;
	private List<String> roles;
    private String previousUserName;
    private View dialogView;

	private MainAuthenticationReceiver mainAuthenticationReceiver;
	private boolean forcedLogin;
    private boolean fullScreenFragment;

    public static LoginDialogFragment newInstance(Configuration configuration) {
        LoginDialogFragment f = new LoginDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("configuration", configuration);
        f.setArguments(args);

        return f;
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        String loginType = PreferencesUtils.getStringFromPreferences(getActivity(), Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);
        if(loginType.equals(Utils.NATIVE_LOGIN_TYPE)) {
            dialogView = inflater.inflate(R.layout.fragment_login_dialog, container, false);
            createBasicAuthenticationLoginDialog();
        } else {
            dialogView = inflater.inflate(R.layout.fragment_login_web_dialog, container, false);
            createWebAuthenticationLoginDialog();
        }
        return dialogView;
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		return dialog;
	}

    @Override
    public void show(FragmentManager manager, String tag) {
        Configuration configuration = getArguments().getParcelable("configuration");

        String screenSizeName = Utils.getSizeName(configuration);

        if (TextUtils.equals(screenSizeName, "large")
                || TextUtils.equals(screenSizeName, "xlarge")) {
            fullScreenFragment = false;
            super.show(manager, tag);
        } else {
            fullScreenFragment = true;
            FragmentTransaction transaction = manager.beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            // To make it fullscreen, use the 'content' root view as the container
            // for the fragment, which is always the root view for the activity
            transaction.add(android.R.id.content, this)
                    .addToBackStack(null).commit();
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
	private void createWebAuthenticationLoginDialog() {
        Button cancelButton = (Button) dialogView.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setEnabled(false);
                doCancel();
            }
        });

		final WebView webView = (WebView) dialogView.findViewById(R.id.login_webview);

        if (fullScreenFragment) {
            webView.setVisibility(View.VISIBLE);
        }
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);

        //Enable HTML 5 local storage
        String databasePath = webView.getContext().getDir("databases",
                Context.MODE_PRIVATE).getPath();
        webSettings.setDatabaseEnabled(true);
        VersionSupportUtils.setDatabasePath(webSettings, databasePath);
        webSettings.setDomStorageEnabled(true);

		webView.setWebChromeClient(new WebChromeClient());
		webView.setWebViewClient(new WebViewClient() {

			@Override
			public void onPageFinished(WebView view, String url) {
                Utils.hideProgressIndicator(dialogView);
                webView.setVisibility(View.VISIBLE);
				String title = view.getTitle();
				if ("Authentication Success".equals(title)) {
					sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_LOGIN, "Authentication using web login", null, null);

                    loginUser(null, null, false, false);
				}
			}
		});

		String loginUrl = PreferencesUtils.getStringFromPreferences(getActivity(), Utils.SECURITY, Utils.LOGIN_URL, "");
		webView.loadUrl(loginUrl);
		
        Utils.showProgressIndicator(dialogView);
	}

	private void createBasicAuthenticationLoginDialog() {
        final String usernameHint = PreferencesUtils.getStringFromPreferences(getContext(), Utils.CONFIGURATION, Utils.LOGIN_USERNAME_HINT, getString(R.string.dialog_username));
        final String passwordHint = PreferencesUtils.getStringFromPreferences(getContext(), Utils.CONFIGURATION, Utils.LOGIN_PASSWORD_HINT, getString(R.string.dialog_password));
        final String instructions = PreferencesUtils.getStringFromPreferences(getContext(), Utils.CONFIGURATION, Utils.LOGIN_INSTRUCTIONS, getString(R.string.dialog_login_instructions));
        final String helpDisplayLabel = PreferencesUtils.getStringFromPreferences(getContext(), Utils.CONFIGURATION, Utils.LOGIN_HELP_LABEL, null);
        final String helpUrl = PreferencesUtils.getStringFromPreferences(getContext(), Utils.CONFIGURATION, Utils.LOGIN_HELP_URL, null);

        TextInputLayout usernameWrapper = (TextInputLayout) dialogView.findViewById(R.id.login_dialog_username_wrapper);
        usernameWrapper.setHint(usernameHint);
        final EditText usernameView = (EditText) dialogView.findViewById(R.id.login_dialog_username);
        if (!TextUtils.isEmpty(previousUserName)) {
            usernameView.setText(previousUserName);
        }

        final Button positive = (Button) dialogView.findViewById(R.id.sign_in_button);
        Button negative = (Button) dialogView.findViewById(R.id.cancel_button);
        negative.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setEnabled(false);
                doCancel();
            }
        });

        TextInputLayout passwordWrapper = (TextInputLayout) dialogView.findViewById(R.id.login_dialog_password_wrapper);
        passwordWrapper.setHint(passwordHint);
        final EditText passwordView = (EditText) dialogView.findViewById(R.id.login_dialog_password);
        passwordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    hideKeyboard(v);
                    positive.callOnClick();
                    return true;
                }
                return false;
            }
        });

        // Hide keyboard if user tapped outside Username or Password Edit Text views.
        usernameView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if (!passwordView.hasFocus()) {
                        hideKeyboard(v);
                    }
                }
            }
        });

        passwordView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if (!usernameView.hasFocus()) {
                        hideKeyboard(v);
                    }
                }
            }
        });

        final CheckBox useFingerprint = (CheckBox) dialogView.findViewById(R.id.fingerprint_login_checkbox);
        final CheckBox staySignedIn = (CheckBox) dialogView.findViewById(R.id.stay_signed_in_checkbox);
        final ColorStateList enabledTextColor = staySignedIn.getTextColors();

        enableStaySignedIn(staySignedIn, enabledTextColor);

        final CompoundButton.OnCheckedChangeListener staySignedInChangeListener =
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            useFingerprint.setChecked(false);
                            useFingerprint.setEnabled(false);
                            useFingerprint.setTextColor(VersionSupportUtils.getColorHelper(getContext(), R.color.disabled_text_color));
                        } else {
                            useFingerprint.setEnabled(true);
                            useFingerprint.setTextColor(enabledTextColor);
                        }
                    }
                };

        staySignedIn.setOnCheckedChangeListener(staySignedInChangeListener);

        useFingerprint.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // When use fingerprint is selected, null out the Stay Signed In change listener,
                // so that we can check the box, but not disable Use Fingerprint. Then set it again at the end.
                staySignedIn.setOnCheckedChangeListener(null);
                if (isChecked) {
                    staySignedIn.setChecked(true);
                    staySignedIn.setEnabled(false);
                    staySignedIn.setTextColor(VersionSupportUtils.getColorHelper(getContext(), R.color.disabled_text_color));
                } else {
                    staySignedIn.setChecked(false);
                    enableStaySignedIn(staySignedIn, enabledTextColor);
                }
                staySignedIn.setOnCheckedChangeListener(staySignedInChangeListener);

            }
        });

        // Do not display fingerprint option if it can't be used
        if (UserUtils.isFingerprintOptionEnabled(getContext())) {
            boolean fingerprintOptIn = getBooleanFromPreferences(getContext(), UserUtils.USER_FINGERPRINT_OPT_IN, false);
            useFingerprint.setChecked(fingerprintOptIn);
        } else {
            useFingerprint.setEnabled(false);
            useFingerprint.setVisibility(View.GONE);
        }

        final TextView loginInstructions = (TextView) dialogView.findViewById(R.id.login_instructions);
        if (TextUtils.isEmpty(instructions)) {
            loginInstructions.setVisibility(View.GONE);
        } else {
            loginInstructions.setText(instructions);
            Linkify.addLinks(loginInstructions, Linkify.ALL);
        }

        TextView loginHelp = (TextView) dialogView.findViewById(R.id.login_help);
        if (!TextUtils.isEmpty(helpDisplayLabel) && !TextUtils.isEmpty(helpUrl)) {
            loginHelp.setVisibility(View.VISIBLE);
            loginHelp.setText(helpDisplayLabel);
            // for displaying TextView as a link
            Utils.makeTextViewHyperlink(loginHelp);

            loginHelp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Open login help url", null, getEllucianActivity().moduleName);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(helpUrl));
                    startActivity(intent);
                }
            });

        } else {
            loginHelp.setVisibility(View.GONE);
        }

        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setEnabled(false);
                hideKeyboard(view);
                EditText usernameView = (EditText) dialogView.findViewById(R.id.login_dialog_username);
                String username = usernameView.getText().toString();
                String password = passwordView.getText().toString();

                if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                    Toast emptyMessage = Toast.makeText(LoginDialogFragment.this.getActivity(), R.string.dialog_sign_in_empty, Toast.LENGTH_LONG);
                    emptyMessage.setGravity(Gravity.CENTER, 0, 0);
                    emptyMessage.show();
                    view.setEnabled(true);
                } else {
                    boolean staySignedInChecked = staySignedIn.isChecked();
                    boolean useFingerprintChecked = useFingerprint.isChecked();
                    dialogView.findViewById(R.id.progress_spinner).setVisibility(View.VISIBLE);
                    loginUser(username, password, staySignedInChecked, useFingerprintChecked);
                }

                    }
        });

	}

	@Override
	public void onStart() {
		super.onStart();
		sendView("Sign In Page", null);
	}
	
	private void doCancel() {
		sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_CANCEL, "Click Cancel", null, null);
        closeLoginDialog();
		// Make sure queue is empty in case of another login attempt
		clearQueuedIntent();
        SettingsUtils.addBooleanToPreferences(getContext(), UserUtils.USER_FINGERPRINT_OPT_IN, false);
		getEllucianActivity().getEllucianApp().removeAppUser();
        ((EllucianApplication)getActivity().getApplication()).resetModuleMenuAdapter();
        getEllucianActivity().configureNavigationDrawer();
        if(forcedLogin) {
			goHome(getActivity());
		}
	}

	private static void goHome(Activity activity) {
		Intent mainIntent = new Intent(activity, MainActivity.class);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		activity.startActivity(mainIntent);
		activity.finish();
	}
	
	
	public void queueIntent(Intent intent, List<String> roles) {
		queuedIntent = intent;	
		this.roles = roles;
	}
	
	private void clearQueuedIntent() {
		queuedIntent = null;
	}
	
	public static void startQueuedIntent(final Activity activity, Intent queuedIntent,
                         List<String> roles, boolean forcedLogin) {
		if (queuedIntent != null) {

			boolean authorized = false;
			if(roles != null) {
				Application application = activity.getApplication();
				
				List<String> userRoles;
				if(application instanceof EllucianApplication) {
					EllucianApplication ea = (EllucianApplication)application;
					userRoles = ea.getAppUserRoles();
				} else {
					userRoles = new ArrayList<>();
					userRoles.add(ModuleMenuAdapter.MODULE_ROLE_EVERYONE);
				}
				
				
				for(String role : roles) {
					if(userRoles.contains(role)) {
						authorized = true;
					}
					if(role.equals(ModuleMenuAdapter.MODULE_ROLE_EVERYONE)) {
						authorized = true;
					}
				}
				if(roles.size() == 0) { //3.0 upgrade compatibility
					authorized = true;
				}
			} else {
				authorized = false;
			}
			
			if(authorized) {
                DrawerLayoutHelper.launchIntent(activity, queuedIntent);
				if(forcedLogin) {
					activity.finish();
				}
			} else {
				activity.runOnUiThread(new Runnable() {
				    public void run() {
						Toast unauthorizedToast = Toast.makeText(activity, R.string.unauthorized_feature, Toast.LENGTH_LONG);
						unauthorizedToast.setGravity(Gravity.CENTER, 0, 0);
						unauthorizedToast.show();
				    }
				});
				goHome(activity);
			}
		}
	}
	
	private void loginUser(String username, String password, boolean staySignedInChecked, boolean useFingerprintChecked) {
        if (staySignedInChecked) {
            sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_LOGIN, "Authentication with save credential", null, null);
        } else if (useFingerprintChecked) {
            sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_LOGIN, "Authentication with use fingerprint", null, null);
        } else {
            sendEvent(GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION, GoogleAnalyticsConstants.ACTION_LOGIN, "Authentication without save credential", null, null);
        }

        Intent intent = new Intent(LoginDialogFragment.this.getActivity(), AuthenticateUserIntentService.class);
		intent.putExtra(Extra.LOGIN_USERNAME, username);
		intent.putExtra(Extra.LOGIN_PASSWORD, password);
		intent.putExtra(Extra.LOGIN_SAVE_USER, staySignedInChecked);
		intent.putExtra(Extra.LOGIN_USE_FINGERPRINT, useFingerprintChecked);
        intent.putExtra(Extra.SEND_UNAUTH_BROADCAST, false);
		LoginDialogFragment.this.getActivity().startService(intent);

        SettingsUtils.addBooleanToPreferences(getContext(), UserUtils.USER_FINGERPRINT_OPT_IN, useFingerprintChecked);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mainAuthenticationReceiver);
    }

	@Override
	public void onResume() {
		super.onResume();
        mainAuthenticationReceiver = new MainAuthenticationReceiver();
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mainAuthenticationReceiver, new IntentFilter(AuthenticateUserIntentService.ACTION_UPDATE_MAIN));
	}
	
	public class MainAuthenticationReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent incomingIntent) {
            // Progress spinner only occurs on Native (Basic) Auth login dialog.
            View progressSpinner = dialogView.findViewById(R.id.progress_spinner);
            if (progressSpinner != null) {
                progressSpinner.setVisibility(View.GONE);
            }

            Toast signInMessage = Toast.makeText(LoginDialogFragment.this.getActivity(), R.string.dialog_sign_in_failed, Toast.LENGTH_LONG);
			signInMessage.setGravity(Gravity.CENTER, 0, 0);
            CheckBox useFingerprint = (CheckBox) dialogView.findViewById(R.id.fingerprint_login_checkbox);
            CheckBox staySignedIn = (CheckBox) dialogView.findViewById(R.id.stay_signed_in_checkbox);

			String result = incomingIntent.getStringExtra(Extra.LOGIN_SUCCESS);
			
			if(!TextUtils.isEmpty(result) && result.equals(AuthenticateUserIntentService.ACTION_SUCCESS)) {
				signInMessage.setText(R.string.dialog_signed_in);
				closeLoginDialog();
				EllucianApplication ellucianApp = LoginDialogFragment.this.getEllucianActivity().getEllucianApp();
				String loginType = PreferencesUtils.getStringFromPreferences(getActivity(), Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);
				if(loginType.equals(Utils.NATIVE_LOGIN_TYPE)) {
					if(!staySignedIn.isChecked() && !useFingerprint.isChecked()) {
						ellucianApp.startIdleTimer();
					}
				}
				signInMessage.show();
				//signInButton.setText(R.string.main_sign_out);

				ellucianApp.startNotifications();

				// Checks to see if the dialog was opened by a request for a auth-necessary activity
				startQueuedIntent(getActivity(), queuedIntent, roles, forcedLogin);
                queuedIntent = null;

			} else {
				signInMessage.show();
                dialogView.findViewById(R.id.sign_in_button).setEnabled(true);
			}
			
		}		
	}
	
	private void closeLoginDialog() {
        getFragmentManager().beginTransaction().remove(this).commit();
    }

	/**
	 * If true, call finish after a successful login.
	 * 
	 * This will be used when the user is prompted because of an unauthorized or session timeout, and need to login again.
	 * By finishing the activity, and with the same activity queued, it will restart the activity without the item on the stack.
	 * @param b boolean
	 */
	public void forcedLogin(boolean b) {
		this.forcedLogin = b;
		this.setCancelable(false);
	}
	
	@Override
	public void onDestroyView() {
		// Trick to keep dialog open on rotate
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

    public void setPreviousUserName(String previousUserName) {
        this.previousUserName = previousUserName;
    }

    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager =(InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static boolean doesDeviceHaveScreenLockOn(Context context) {

        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //true if a PIN, pattern or password was set.
            return keyguardManager.isDeviceSecure();
        } else {
            return VersionSupportUtils.doesDeviceHaveScreenLockOn(context, keyguardManager);
        }

    }

    private void enableStaySignedIn(CheckBox staySignedIn, ColorStateList enabledTextColor) {
        if (doesDeviceHaveScreenLockOn(getContext())) {
            staySignedIn.setEnabled(true);
            staySignedIn.setTextColor(enabledTextColor);
        } else {
            staySignedIn.setEnabled(false);
            TextView disabledText = (TextView) dialogView.findViewById(R.id.stay_signed_in_disabled_text);
            disabledText.setVisibility(View.VISIBLE);
            staySignedIn.setTextColor(VersionSupportUtils.getColorHelper(getContext(), R.color.disabled_text_color));
        }
    }

}
