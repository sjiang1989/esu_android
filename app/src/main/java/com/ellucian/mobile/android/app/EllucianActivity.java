/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.MainActivity;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.client.configuration.LastUpdatedResponse;
import com.ellucian.mobile.android.client.locations.EllucianBeaconManager;
import com.ellucian.mobile.android.client.locations.PermissionsDialogFragment;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.client.services.ConfigurationUpdateService;
import com.ellucian.mobile.android.client.services.NotificationsIntentService;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.ellucian.mobile.android.util.ConfigurationProperties;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.UserUtils;
import com.ellucian.mobile.android.util.Utils;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

@SuppressWarnings("JavaDoc")
public abstract class EllucianActivity extends AppCompatActivity implements DrawerLayoutActivity, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = EllucianActivity.class.getSimpleName();

    private static final long REFRESH_INTERVAL = 20 * Utils.ONE_MINUTE;  // 20 minutes
	public String moduleId;
	public String moduleName;
	public String requestUrl;
	private DrawerLayoutHelper drawerLayoutHelper;
	private MainAuthenticationReceiver mainAuthenticationReceiver;
    private NotificationUpdateReceiver notificationUpdateReceiver;
	private ConfigurationUpdateReceiver configReceiver;
	private SendToSelectionReceiver resetReceiver;
	private OutdatedReceiver outdatedReceiver;
	private UnauthenticatedUserReceiver unauthenticatedUserReceiver;
    private NoConnectivityReceiver noConnectivityReceiver;

    private int mPrimaryColor;
    private int mHeaderTextColor;

    private static final int LOCATION_REQUEST_ID = 1;
    public static final int LOCATION_ALERT_DIALOG = 1;
    public static final int BLUETOOTH_ALERT_DIALOG = 2;
    private static final long DAY_IN_MILLISECS = 86400000;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String tag = getClass().getName();

        Intent incomingIntent = getIntent();
        if (incomingIntent.hasExtra(Extra.MODULE_ID)) {
            moduleId = incomingIntent.getStringExtra(Extra.MODULE_ID);
            Log.d(tag, "Activity moduleId set to: " + moduleId);
        }

        if (incomingIntent.hasExtra(Extra.MODULE_NAME)) {
            moduleName = incomingIntent.getStringExtra(Extra.MODULE_NAME);
            Log.d(tag, "Activity moduleId set to: " + moduleId);
        }

        if (incomingIntent.hasExtra(Extra.REQUEST_URL)) {
            requestUrl = incomingIntent.getStringExtra(Extra.REQUEST_URL);
            Log.d(tag, "Activity requestUrl set to: " + requestUrl);
        } else {
            requestUrl = "";
        }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        configureActionBar();
        configureNavigationDrawer();
    }

    @Override
    public void setContentView(View view, LayoutParams params) {
        super.setContentView(view, params);
        configureActionBar();
        configureNavigationDrawer();
    }

    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);
        configureActionBar();
        configureNavigationDrawer();
    }

    public void setContentViewHomeScreen(int layoutResId) {
        super.setContentView(layoutResId);
        configureTransparentToolbar(Utils.getPrimaryColor(this));
        configureNavigationDrawer();
    }

    public void configureNavigationDrawer() {
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        if (drawerLayout != null && drawerList != null) {
            drawerLayoutHelper = new DrawerLayoutHelper(this, getEllucianApp().getModuleMenuAdapter());
        }
    }

    /*
	 * 'standard' method that reads preferences for these values
	 * valid after a configuration has been loaded
	 */
    protected void configureActionBar() {
        int primaryColor = Utils.getPrimaryColor(this);
        int headerTextColor = Utils.getHeaderTextColor(this);

        configureActionBarDirect(primaryColor, headerTextColor);
    }

    /*
	 * a 'direct' method that bypasses the preferences (used by 
	 * school selection, which can be called before any config
	 * has ever been loaded on the device
	 */
    protected void configureActionBarDirect(int primaryColor, int headerTextColor) {

        mPrimaryColor = primaryColor;
        mHeaderTextColor = headerTextColor;
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setBackgroundDrawable(new ColorDrawable(primaryColor));
            bar.setSplitBackgroundDrawable(new ColorDrawable(primaryColor));
            bar.setStackedBackgroundDrawable(new ColorDrawable(primaryColor));
            TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
            if (tabLayout != null) {
                tabLayout.setBackgroundColor(primaryColor);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float[] hsv = new float[3];
            int darkerColor = primaryColor;
            Color.colorToHSV(darkerColor, hsv);
            hsv[2] *= 0.8f; // value component
            darkerColor = Color.HSVToColor(hsv);

            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(darkerColor);
        }

        setTitle(bar.getTitle());

        invalidateOptionsMenu();
    }

    private void configureTransparentToolbar(int primaryColor) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            bar.setSplitBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            bar.setStackedBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            bar.setDisplayShowTitleEnabled(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float[] hsv = new float[3];
            int darkerColor = primaryColor;
            Color.colorToHSV(darkerColor, hsv);
            hsv[2] *= 0.8f; // value component
            darkerColor = Color.HSVToColor(hsv);

            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(darkerColor);
        }

        invalidateOptionsMenu();
    }

    public EllucianApplication getEllucianApp() {
        return (EllucianApplication) this.getApplication();
    }

    public ConfigurationProperties getConfigurationProperties() {
        return getEllucianApp().getConfigurationProperties();
    }

    public DrawerLayoutHelper getDrawerLayoutHelper() {
        return drawerLayoutHelper;
    }

    @Override
    public void setTitle(CharSequence title) {
        if (!TextUtils.isEmpty(title)) {
            Spannable titleText = new SpannableString(title);
            titleText.setSpan(new ForegroundColorSpan(mHeaderTextColor), 0, titleText.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            getSupportActionBar().setTitle(titleText);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        getEllucianApp().touch();
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
    public void sendEvent(String category, String action, String label, Long value, String moduleName) {
        getEllucianApp().sendEvent(category, action, label, value, moduleName);
    }

    /**
     * Send event to google analytics for just tracker 1
     *
     * @param category
     * @param action
     * @param label
     * @param value
     * @param moduleName
     */
    public void sendEventToTracker1(String category, String action, String label, Long value, String moduleName) {
        getEllucianApp().sendEventToTracker1(category, action, label, value, moduleName);
    }

    /**
     * Send event to google analytics for just tracker 2
     *
     * @param category
     * @param action
     * @param label
     * @param value
     * @param moduleName
     */
    public void sendEventToTracker2(String category, String action, String label, Long value, String moduleName) {
        getEllucianApp().sendEventToTracker2(category, action, label, value, moduleName);
    }


    /**
     * Send view to google analytics
     *
     * @param appScreen
     */
    public void sendView(String appScreen, String moduleName) {
        getEllucianApp().sendView(appScreen, moduleName);
    }

    /**
     * Send view to google analytics for just tracker 1
     *
     * @param appScreen
     */
    public void sendViewToTracker1(String appScreen, String moduleName) {
        getEllucianApp().sendViewToTracker1(appScreen, moduleName);
    }

    /**
     * Send view to google analytics for just tracker 2
     *
     * @param appScreen
     */
    public void sendViewToTracker2(String appScreen, String moduleName) {
        getEllucianApp().sendViewToTracker2(appScreen, moduleName);
    }

    /**
     * Send timing to google analytics
     *
     * @param category
     * @param value
     * @param name
     * @param label
     * @param moduleName
     */
    protected void sendUserTiming(String category, long value, String name, String label, String moduleName) {
        getEllucianApp().sendUserTiming(category, value, name, label, moduleName);
    }

    /**
     * Send timing to google analytics for just tracker 1
     *
     * @param category
     * @param value
     * @param name
     * @param label
     * @param moduleName
     */
    public void sendUserTimingToTracker1(String category, long value, String name, String label, String moduleName) {
        getEllucianApp().sendUserTimingToTracker1(category, value, name, label, moduleName);
    }

    /**
     * Send timing to google analytics for just tracker 2
     *
     * @param category
     * @param value
     * @param name
     * @param label
     * @param moduleName
     */
    public void sendUserTimingToTracker2(String category, long value, String name, String label, String moduleName) {
        getEllucianApp().sendUserTimingToTracker2(category, value, name, label, moduleName);
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content
        // view
        if (drawerLayoutHelper != null) {
            drawerLayoutHelper.removeMenuItems(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (drawerLayoutHelper.drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerLayoutHelper != null) {
            drawerLayoutHelper.drawerToggle.syncState();
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerLayoutHelper != null) {
            drawerLayoutHelper.onConfigurationChanged(newConfig);
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);

        lbm.unregisterReceiver(mainAuthenticationReceiver);
        lbm.unregisterReceiver(configReceiver);
        lbm.unregisterReceiver(resetReceiver);
        lbm.unregisterReceiver(outdatedReceiver);
        lbm.unregisterReceiver(unauthenticatedUserReceiver);
        lbm.unregisterReceiver(notificationUpdateReceiver);
        lbm.unregisterReceiver(noConnectivityReceiver);

        getEllucianApp().startActivityTransitionTimer();

    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences preferences = getSharedPreferences(Utils.CONFIGURATION, MODE_PRIVATE);
        SharedPreferences userPreferences = getSharedPreferences(UserUtils.USER, MODE_PRIVATE);
        Boolean timedOut = userPreferences.getBoolean(UserUtils.USER_TIMED_OUT, false);

        if (timedOut) {
            PreferencesUtils.removeValuesFromPreferences(this, UserUtils.USER, UserUtils.USER_TIMED_OUT);
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
        }

        refreshConfig(preferences);

        if (getEllucianApp().isUserAuthenticated()) {
            UserUtils.manageFingerprintTimeout(this);

            //notifications
            if (System.currentTimeMillis() > getEllucianApp().getLastNotificationsCheck() + EllucianApplication.DEFAULT_NOTIFICATIONS_REFRESH) {
                Log.d(TAG, "startingNotifications");
                getEllucianApp().startNotifications();
            }
        }

        getEllucianApp().stopActivityTransitionTimer();

		getEllucianApp().registerWithGcmIfNeeded();

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		
		configReceiver = new ConfigurationUpdateReceiver(this);
		lbm.registerReceiver(configReceiver, new IntentFilter(ConfigurationUpdateService.ACTION_SUCCESS));
		
		resetReceiver = new SendToSelectionReceiver(this);
		lbm.registerReceiver(resetReceiver, new IntentFilter(ConfigurationUpdateService.ACTION_SEND_TO_SELECTION));
		
		outdatedReceiver = new OutdatedReceiver(this);
		lbm.registerReceiver(outdatedReceiver, new IntentFilter(ConfigurationUpdateService.ACTION_OUTDATED));
		
		mainAuthenticationReceiver = new MainAuthenticationReceiver(this);
		lbm.registerReceiver(mainAuthenticationReceiver, new IntentFilter(AuthenticateUserIntentService.ACTION_UPDATE_MAIN));

        notificationUpdateReceiver = new NotificationUpdateReceiver(this);
        lbm.registerReceiver(notificationUpdateReceiver, new IntentFilter(NotificationsIntentService.ACTION_FINISHED));

		unauthenticatedUserReceiver = new UnauthenticatedUserReceiver(this, moduleId);
		lbm.registerReceiver(unauthenticatedUserReceiver, new IntentFilter(MobileClient.ACTION_UNAUTHENTICATED_USER));

        noConnectivityReceiver = new NoConnectivityReceiver(this);
        lbm.registerReceiver(noConnectivityReceiver, new IntentFilter(MobileClient.ACTION_NO_CONNECTIVITY));

        if (getEllucianApp().configUsesBeacons()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { permissionsCheck(); }
            verifyBluetooth();
            EllucianBeaconManager.getInstance().startBeaconManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            EllucianBeaconManager.getInstance().stopBeaconManager();
        }
    }

	/**
	 * Called to process touch screen events. At the very least your
	 * implementation must call superDispatchTouchEvent(MotionEvent) to do the
	 * standard touch screen processing. Overriding to capture EditText
	 * objects. If the user touches outside the EditText, dismiss the keyboard
	 * 
	 * @param event	The touch screen event.
	 * @return boolean Return true if this event was consumed.
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {

		View v = getCurrentFocus();
		boolean ret = super.dispatchTouchEvent(event);

		if (v instanceof EditText) {
			View w = getCurrentFocus();
			int scrcoords[] = new int[2];
			w.getLocationOnScreen(scrcoords);
			float x = event.getRawX() + w.getLeft() - scrcoords[0];
			float y = event.getRawY() + w.getTop() - scrcoords[1];

			Log.d("Activity",
					"Touch event " + event.getRawX() + "," + event.getRawY()
							+ " " + x + "," + y + " rect " + w.getLeft() + ","
							+ w.getTop() + "," + w.getRight() + ","
							+ w.getBottom() + " coords " + scrcoords[0] + ","
							+ scrcoords[1]);
			if (event.getAction() == MotionEvent.ACTION_UP
					&& (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w
							.getBottom())) {

				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(getWindow().getCurrentFocus()
						.getWindowToken(), 0);
			}
		}
		return ret;
	}

    private void refreshConfig(SharedPreferences preferences) {
        String cloudConfigUrl = preferences.getString(Utils.CONFIGURATION_URL, null);
        String mobileServerConfigUrl = preferences.getString(Utils.MOBILESERVER_CONFIG_URL, null);

        // Check for updated configs every [CONFIG_REFRESH_CHECK] millis
        long configLastChecked = preferences.getLong(Utils.CONFIGURATION_LAST_CHECKED, 0);
        Log.d(TAG, "config last checked: " + configLastChecked);

        if ((configLastChecked + REFRESH_INTERVAL) < System.currentTimeMillis()) {
            Log.d(TAG, "Go see if config has been updated.");
            if (!TextUtils.isEmpty(cloudConfigUrl)) {
                updateCloudConfigIfNecessary(cloudConfigUrl, this);
            }
            if (!TextUtils.isEmpty(mobileServerConfigUrl)) {
                updateMobileServerConfigIfNecessary(mobileServerConfigUrl, this);
            }

            // update config last checked time to current time.
            long updateCheckedTime = System.currentTimeMillis();
            PreferencesUtils.addLongToPreferences(this, Utils.CONFIGURATION, Utils.CONFIGURATION_LAST_CHECKED,
                    updateCheckedTime);
            Log.d(TAG, "Configuration last checked: " + updateCheckedTime);
        }
    }

    private void updateCloudConfigIfNecessary(final String cloudConfigUrl, final Activity activity) {
        Observable<String> fetchLastUpdated = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                MobileClient client = new MobileClient(activity);
                try {
                    LastUpdatedResponse response = client.getLastUpdated(cloudConfigUrl + "?onlyLastUpdated=true");
                    if (response != null) {
                        if (response.lastUpdated != null) {
                            subscriber.onNext(response.lastUpdated); // Emit the contents of the URL
                        }
                    }
                    subscriber.onCompleted(); // Nothing more to emit
                } catch (Exception e) {
                    subscriber.onError(e); // In case there are network errors
                }
            }
        });

        fetchLastUpdated.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String currentLastUpdated) {
                        Log.d(TAG, "Live Cloud Config lastUpdated:  " + currentLastUpdated);
                        String savedLastUpdated = getSharedPreferences(Utils.CONFIGURATION, MODE_PRIVATE)
                                .getString(Utils.CONFIGURATION_LAST_UPDATED, null);

                        Log.d(TAG, "Cached Cloud Config lastUpdated: " + savedLastUpdated);
                        if (!TextUtils.equals(currentLastUpdated, savedLastUpdated)) {
                            EllucianApplication ea = (EllucianApplication) getApplication();
                            if (!ea.isServiceRunning(ConfigurationUpdateService.class)) {
                                Log.d(TAG, "Cloud Config out of date. Begin update.");
                                Intent intent = new Intent(activity, ConfigurationUpdateService.class);
                                intent.putExtra(Utils.CONFIGURATION_URL, cloudConfigUrl);
                                intent.putExtra(ConfigurationUpdateService.REFRESH, true);
                                startService(intent);
                            } else {
                                Log.v(TAG, "Can't start ConfigurationUpdateService because it is already running");
                            }
                        }
                    }
                });

    }

    private void updateMobileServerConfigIfNecessary(final String mobileServerConfigUrl, final Activity activity) {
        Observable<String> fetchLastUpdated = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                MobileClient client = new MobileClient(activity);
                try {
                    LastUpdatedResponse response = client.getLastUpdated(mobileServerConfigUrl + "?onlyLastUpdated=true");
                    if (response != null) {
                        if (response.lastUpdated != null) {
                            subscriber.onNext(response.lastUpdated); // Emit the contents of the URL
                        }
                    }
                    subscriber.onCompleted(); // Nothing more to emit
                } catch (Exception e) {
                    subscriber.onError(e); // In case there are network errors
                }
            }
        });

        fetchLastUpdated.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String currentLastUpdated) {
                        Log.d(TAG, "Live MobileServer Config lastUpdated:  " + currentLastUpdated);
                        String savedLastUpdated = getSharedPreferences(Utils.CONFIGURATION, MODE_PRIVATE)
                                .getString(Utils.MOBILESERVER_CONFIG_LAST_UPDATE, null);

                        Log.d(TAG, "Cached MobileServer Config lastUpdated: " + savedLastUpdated);
                        if (!TextUtils.equals(currentLastUpdated, savedLastUpdated)) {
                            EllucianApplication ea = (EllucianApplication) getApplication();
                            if (!ea.isServiceRunning(ConfigurationUpdateService.class)) {
                                Log.d(TAG, "MobileServer Config out of date. Begin update.");
                                Intent intent = new Intent(activity, ConfigurationUpdateService.class);
                                intent.putExtra(ConfigurationUpdateService.REFRESH_MOBILESERVER_ONLY, true);
                                startService(intent);
                            } else {
                                Log.v(TAG, "Can't start ConfigurationUpdateService because it is already running");
                            }
                        }
                    }
                });
    }

    private void permissionsCheck() {
        // Make sure user is reminded to allow locations once a day at most
        long timeOfLastNotification = PreferenceManager.getDefaultSharedPreferences(this).getLong(Utils.LOCATIONS_NOTIF_TIMESTAMP, 0);
        String locationsNotifRemind = PreferenceManager.getDefaultSharedPreferences(this).getString(Utils.LOCATIONS_NOTIF_REMIND, "0");
        boolean locationsPermissionAsked = SettingsUtils.hasUserBeenAskedForPermission(this, Utils.PERMISSIONS_ASKED_FOR_LOCATION);
        boolean shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (shouldShow || !locationsPermissionAsked) {
            if (((System.currentTimeMillis() - timeOfLastNotification >= DAY_IN_MILLISECS)) && !locationsNotifRemind.equals("false")) {
                if (System.currentTimeMillis() - Long.valueOf(locationsNotifRemind) >= DAY_IN_MILLISECS) {
                    if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                        SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                        editor.putLong(Utils.LOCATIONS_NOTIF_TIMESTAMP, System.currentTimeMillis());
                        editor.apply();

                        DialogFragment newFragment = PermissionsDialogFragment.newInstance(LOCATION_ALERT_DIALOG);
                        newFragment.show(getSupportFragmentManager(), TAG);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (!(requestCode == LOCATION_REQUEST_ID)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void verifyBluetooth() {
        // Make sure user is reminded to turn on bluetooth once a day at most
        long timeOfLastNotification = PreferenceManager.getDefaultSharedPreferences(this).getLong(Utils.BLUETOOTH_NOTIF_TIMESTAMP, 0);
        String bluetoothNotifRemind = PreferenceManager.getDefaultSharedPreferences(this).getString(Utils.BLUETOOTH_NOTIF_REMIND, "0");

        if ((System.currentTimeMillis() - timeOfLastNotification >= DAY_IN_MILLISECS) && !bluetoothNotifRemind.equals("false")) {
            if (System.currentTimeMillis() - Long.valueOf(bluetoothNotifRemind) >= DAY_IN_MILLISECS) {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {
                    SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                    editor.putLong(Utils.BLUETOOTH_NOTIF_TIMESTAMP, System.currentTimeMillis());
                    editor.apply();

                    DialogFragment newFragment = PermissionsDialogFragment.newInstance(BLUETOOTH_ALERT_DIALOG);
                    newFragment.show(getSupportFragmentManager(), TAG);
                }
            }
        }
    }

}


