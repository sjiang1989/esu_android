/*
 * Copyright 2015-2017 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.webframe;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.util.VersionSupportUtils;

import static com.ellucian.mobile.android.EllucianApplication.AUTH_REFRESH_TIME;

public class WebframeActivity extends EllucianActivity {

    private static final String TAG = WebframeActivity.class.getSimpleName();
	private WebView webView;
	private SecurityDialogFragment securityDialogFragment;
	private SslErrorHandler handler;
    private Bundle savedInstanceState;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;

        boolean reAuthNeeded = false;
        // If moduleId is null, this isn't a WebModule. It's just a URL opening inside the app.
        if (moduleId != null) {
            boolean secure = Boolean.parseBoolean(isModuleSecure());
            String loginType = PreferencesUtils.getStringFromPreferences(this, Utils.SECURITY, Utils.LOGIN_TYPE, Utils.NATIVE_LOGIN_TYPE);

            if (loginType.equals(Utils.NATIVE_LOGIN_TYPE) && secure) {
                //do if basic authentication only; web login will be handled by cookies
                reAuthNeeded = reAuthenticationNeeded();
            }
        }

        if (reAuthNeeded) {
            Log.i(TAG, "onCreate: Closing activity. Waiting for Re-Authentication.");
            finish(); // close the activity, it will be re-launched by successful Authentication
        } else {
            loadpage();
        }

	}

    private String isModuleSecure() {
        final ContentResolver contentResolver = getApplication().getContentResolver();

        String secureString = "false";
        try {
            Cursor modulesCursor = contentResolver.query(EllucianContract.Modules.CONTENT_URI,
                    new String[]{BaseColumns._ID, EllucianContract.Modules.MODULE_TYPE,
                            EllucianContract.Modules.MODULE_SUB_TYPE, EllucianContract.Modules.MODULE_NAME,
                            EllucianContract.Modules.MODULES_ICON_URL, EllucianContract.Modules.MODULES_ID,
                            EllucianContract.Modules.MODULE_SECURE, EllucianContract.Modules.MODULE_SHOW_FOR_GUEST},
                    EllucianContract.Modules.MODULES_ID + " = ?",
                    new String[]{moduleId}, EllucianContract.Modules.DEFAULT_SORT);

            if (modulesCursor != null && modulesCursor.moveToFirst()) {
                int secureIndex = modulesCursor
                        .getColumnIndex(EllucianContract.Modules.MODULE_SECURE);
                secureString = modulesCursor.getString(secureIndex);
                modulesCursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "isModuleSecure: Exception ", e);
        }

        return secureString;

    }

    private boolean reAuthenticationNeeded() {
        EllucianApplication ellucianApp = (EllucianApplication) getApplication();

        long lastAuthRefresh = ellucianApp.getLastAuthRefresh();
        long authExpiredTime =  lastAuthRefresh + AUTH_REFRESH_TIME;
        boolean reAuthenticationNeeded = false;
        if (System.currentTimeMillis() > authExpiredTime) {
            reAuthenticationNeeded = true;
        }
        if (reAuthenticationNeeded) {
            Log.i(TAG, "Re-authentication needed");
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
            BackgroundAuthenticationReceiver backgroundAuthenticationReceiver = new BackgroundAuthenticationReceiver(this);
            backgroundAuthenticationReceiver.setQueuedIntent(getIntent());
            backgroundAuthenticationReceiver.setBackgroundAuthenticationReceiver(backgroundAuthenticationReceiver);
            lbm.registerReceiver(backgroundAuthenticationReceiver,
                    new IntentFilter(AuthenticateUserIntentService.ACTION_BACKGROUND_AUTH));

            sendEvent(
                    GoogleAnalyticsConstants.CATEGORY_AUTHENTICATION,
                    GoogleAnalyticsConstants.ACTION_LOGIN,
                    "Background re-authenticate", null,
                    null);
            Toast signInMessage = Toast.makeText(this,
                    R.string.dialog_re_authenticate,
                    Toast.LENGTH_LONG);
            signInMessage.setGravity(Gravity.CENTER, 0, 0);
            signInMessage.show();

            Intent loginIntent = new Intent(this, AuthenticateUserIntentService.class);
            loginIntent.putExtra(Extra.LOGIN_USERNAME, ellucianApp.getAppUserName());
            loginIntent.putExtra(Extra.LOGIN_PASSWORD, ellucianApp.getAppUserPassword());
            loginIntent.putExtra(Extra.LOGIN_BACKGROUND, true);
            startService(loginIntent);
            return true;
        } else {
            Log.i(TAG, "Re-authentication NOT needed");
            return false;
        }

    }

    private void loadpage() {
        setContentView(R.layout.activity_webframe);

        if (!TextUtils.isEmpty(moduleName)) {
            setTitle(moduleName);
        }

        webView = new WebView(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (this.getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
        webView.addJavascriptInterface(new WebframeJavascriptInterface(this),
                "EllucianMobileDevice");
        FrameLayout layout = (FrameLayout) findViewById(R.id.web_frame);
        layout.addView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.setWebChromeClient(new WebChromeClient() {
                public boolean onConsoleMessage(ConsoleMessage cm) {
                   Log.d("onConsole",
                           cm.message() + " -- From line " + cm.lineNumber()
                                   + " of " + cm.sourceId());
                   return true;
                }
            }

            );

            setWebViewClient();

			WebSettings webSettings = webView.getSettings();
			webSettings.setJavaScriptEnabled(true);
			// webSettings.setBuiltInZoomControls(true); //removed because of
			// bug http://code.google.com/p/android/issues/detail?id=15694
			webSettings.setUseWideViewPort(true);

			// Enable HTML 5 local storage
			String databasePath = webView.getContext()
					.getDir("databases", Context.MODE_PRIVATE).getPath();
			webSettings.setDatabaseEnabled(true);
            VersionSupportUtils.setDatabasePath(webSettings, databasePath);
			webSettings.setDomStorageEnabled(true);

			Log.d("WebframeActivity", "Making request at: " + requestUrl);

			webView.loadUrl(requestUrl);
		}
	}

	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		webView.saveState(outState);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    // Check if the key event was the Back button and if there's history
	    if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
	    	webView.goBack();
	        return true;
	    }
	    // If it wasn't the Back key or there's no web page history, bubble up to the default
	    // system behavior (probably exit the activity)
	    return super.onKeyDown(keyCode, event);
	}
	
	
	private void handleError(SslErrorHandler handler) {
		this.handler = handler;
		securityDialogFragment = new SecurityDialogFragment();
		securityDialogFragment.show(getSupportFragmentManager(), SecurityDialogFragment.SECURITY_DIALOG);
	}
	
	void onContinueClicked() {
		handler.proceed();
	}
	
	void onGoBackClicked() {
		dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
		dispatchKeyEvent(new KeyEvent (KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webframe, menu);

        Intent intent = getIntent();
        String url = intent.getStringExtra(Extra.REQUEST_URL);
              
        MenuItem sharedMenuItem = menu.findItem(R.id.share);
        
        // Getting the actionprovider associated with the menu item whose id is share
        ShareActionProvider shareActionProvider = 
        		(ShareActionProvider) MenuItemCompat.getActionProvider(sharedMenuItem);
        shareActionProvider.setOnShareTargetSelectedListener(new OnShareTargetSelectedListener() {
			
			@Override
			public boolean onShareTargetSelected(ShareActionProvider source,
					Intent intent) {
				String label = "Tap Share Icon - " + intent.getComponent().flattenToShortString();
				sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, label, null, WebframeActivity.this.moduleName);
				return false;
			}
		});
               
        // Getting the target intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
 
        // Setting a share intent
        if(Utils.isIntentAvailable(this, shareIntent)) {
            shareActionProvider.setShareIntent(shareIntent);
        } else {
        	sharedMenuItem.setVisible(false).setEnabled(false);
        }

        MenuItem viewMenuItem = menu.findItem(R.id.view_target);
        viewMenuItem.setIcon(R.drawable.ic_menu_browser);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setData(Uri.parse(url));
        if (Utils.isIntentAvailable(this, viewIntent)) {
        	viewMenuItem.setIntent(viewIntent);
        } else {
        	viewMenuItem.setVisible(false).setEnabled(false);
        }
        
        return super.onCreateOptionsMenu(menu);
    }
	
	@Override
	public void onStart() {
		super.onStart();
		sendView("Display web frame", moduleName);
	}
	
	private void sendToExternalBrowser(String url) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
	    startActivity( intent );
	}

    private void sendToExternalBrowser(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity( intent );
    }

	WebView getWebView() {
		return this.webView;
	}

    private void setWebViewClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webView.setWebViewClient(new WebViewClient() {

                @Override
                public void onReceivedSslError(WebView view,
                                               SslErrorHandler handler, SslError error) {
                    handleError(handler);
                }

                @Override
                @TargetApi(Build.VERSION_CODES.M)
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String urlScheme = request.getUrl().getScheme();
                    if (urlScheme.startsWith("http")) {
                        return false;
                    }

                    // Otherwise allow the OS to handle it
                    sendToExternalBrowser(request.getUrl());
                    return true;

                }
            });

        } else {
            webView.setWebViewClient(new WebViewClient() {

                @Override
                public void onReceivedSslError(WebView view,
                                               SslErrorHandler handler, SslError error) {
                    handleError(handler);
                }

                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.startsWith("http:") || url.startsWith("https:")) {
                        return false;
                    }

                    // Otherwise allow the OS to handle it
                    sendToExternalBrowser(url);
                    return true;
                }
            });
        }
    }

    private static class BackgroundAuthenticationReceiver extends BroadcastReceiver {

        private Intent queuedIntent;
        private Activity activity;
        private BackgroundAuthenticationReceiver backgroundAuthenticationReceiver;

        public BackgroundAuthenticationReceiver(Activity activity) {
            this.activity = activity;
        }

        public void setBackgroundAuthenticationReceiver(BackgroundAuthenticationReceiver backgroundAuthenticationReceiver) {
            this.backgroundAuthenticationReceiver = backgroundAuthenticationReceiver;
        }

        @Override
        public void onReceive(Context context, Intent incomingIntent) {
            String result = incomingIntent.getStringExtra(Extra.LOGIN_SUCCESS);

            if (!TextUtils.isEmpty(result)
                    && result.equals(AuthenticateUserIntentService.ACTION_SUCCESS)) {
                activity.startActivity(queuedIntent);
            } else {

                Toast signInMessage = Toast.makeText(activity,
                        R.string.dialog_sign_in_failed, Toast.LENGTH_LONG);
                signInMessage.setGravity(Gravity.CENTER, 0, 0);
                signInMessage.show();
            }
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(
                    backgroundAuthenticationReceiver);

        }

        public void setQueuedIntent(Intent intent) {
            queuedIntent = intent;
        }
    }
}
