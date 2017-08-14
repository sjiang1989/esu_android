/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.adapter.ModuleMenuAdapter;
import com.ellucian.mobile.android.app.DrawerLayoutHelper;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.HomescreenBackground;
import com.ellucian.mobile.android.app.ShortcutListFragment;
import com.ellucian.mobile.android.client.services.AuthenticateUserIntentService;
import com.ellucian.mobile.android.client.services.ConfigurationUpdateService;
import com.ellucian.mobile.android.login.Fingerprint.FingerprintDialogFragment;
import com.ellucian.mobile.android.login.LoginDialogFragment;
import com.ellucian.mobile.android.login.QueuedIntentHolder;
import com.ellucian.mobile.android.provider.EllucianContract;
import com.ellucian.mobile.android.schoolselector.ConfigurationLoadingActivity;
import com.ellucian.mobile.android.schoolselector.SchoolSelectionActivity;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends EllucianActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final String SHOW_LOGIN = "showLogin";
    public static final String REQUEST_FINGERPRINT = "requestFingerprint";
    private static final String SHORTCUT_LIST_FRAGMENT = "shortcutListFragment";

    ShortcutListFragment homeScreenFragment;
	private boolean useDefaultConfiguration;
	private String defaultConfigurationUrl;

    private MainAuthenticationReceiver mainAuthenticationReceiver;
	private BackgroundAuthenticationReceiver backgroundAuthenticationReceiver;
    private ConfigurationUpdateReceiver configurationUpdateReceiver;

	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentViewHomeScreen(R.layout.activity_main);
        // Setting fields from the configuration file. See res/xml/configuration_properties.xml
        useDefaultConfiguration = getConfigurationProperties().useDefaultConfiguration;
    	defaultConfigurationUrl = getConfigurationProperties().defaultConfigurationUrl;

    }
	
	@Override
	protected void onPause() {
		super.onPause();

		LocalBroadcastManager.getInstance(this).unregisterReceiver(mainAuthenticationReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(backgroundAuthenticationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(configurationUpdateReceiver);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
        handleIntent();
	}


    private void handleIntent() {
        setHomeIcon();
        configureShortcuts();


        Drawable backgroundImage = null;
        if (HomescreenBackground.getInstance(this).getImage() != null) {
            backgroundImage = new BitmapDrawable(getResources(), HomescreenBackground.getInstance(this).getImage());
        }

        ImageView background = (ImageView)findViewById(R.id.home_background);
        if (backgroundImage != null) {
            background.setImageDrawable(backgroundImage);
        }

        if (getIntent().getBooleanExtra(SHOW_LOGIN, false)) {
            getIntent().removeExtra(SHOW_LOGIN);
            login(false);
        }

        if (getIntent().getBooleanExtra(REQUEST_FINGERPRINT, false)) {
            getIntent().removeExtra(REQUEST_FINGERPRINT);
            login(true);
        }
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);

		mainAuthenticationReceiver = new MainAuthenticationReceiver();
		lbm.registerReceiver(mainAuthenticationReceiver, new IntentFilter(AuthenticateUserIntentService.ACTION_UPDATE_MAIN));
		
		backgroundAuthenticationReceiver = new BackgroundAuthenticationReceiver();
		lbm.registerReceiver(backgroundAuthenticationReceiver, new IntentFilter(AuthenticateUserIntentService.ACTION_BACKGROUND_AUTH));

        configurationUpdateReceiver = new ConfigurationUpdateReceiver();
        lbm.registerReceiver(configurationUpdateReceiver, new IntentFilter(ConfigurationUpdateService.ACTION_SUCCESS));

        if (getIntent().hasExtra(Extra.MODULE_ID)) {
            handleBeaconIntent(getIntent().getStringExtra(Extra.MODULE_ID));
            getIntent().removeExtra(Extra.MODULE_ID);
        }
    }

    private void handleBeaconIntent(String moduleId) {
        Log.v(TAG, "moduleId: " + moduleId);

        ContentResolver contentResolver = getContentResolver();
        String selection = EllucianContract.Modules.MODULES_ID + " = ?";

        Cursor modulesCursor = contentResolver.query(EllucianContract.Modules.CONTENT_URI,
                new String[]{BaseColumns._ID, EllucianContract.Modules.MODULE_TYPE, EllucianContract.Modules.MODULE_SUB_TYPE,
                        EllucianContract.Modules.MODULE_NAME, EllucianContract.Modules.MODULES_ID, EllucianContract.Modules.MODULE_SECURE},
                selection, new String[] {moduleId}, null );

        String type = "";
        String subType = "";
        String name = "";
        String secureString = "";

        if (modulesCursor != null && modulesCursor.moveToFirst()) {
            // no need to loop. will only find 1 module.
            int typeIndex = modulesCursor.getColumnIndex(EllucianContract.Modules.MODULE_TYPE);
            int subTypeIndex = modulesCursor.getColumnIndex(EllucianContract.Modules.MODULE_SUB_TYPE);
            int nameIndex = modulesCursor.getColumnIndex(EllucianContract.Modules.MODULE_NAME);
            int secureIndex = modulesCursor.getColumnIndex(EllucianContract.Modules.MODULE_SECURE);

            type = modulesCursor.getString(typeIndex);
            subType = modulesCursor.getString(subTypeIndex);
            name = modulesCursor.getString(nameIndex);
            secureString = modulesCursor.getString(secureIndex);
        }
        if (modulesCursor != null) {
            modulesCursor.close();
        }

        if (!TextUtils.isEmpty(moduleId)) {
            DrawerLayoutHelper.menuItemClickListener(this, moduleId, type, secureString, subType, name);
        }
    }


    private void login(boolean fingerprintLogin) {
        // standard
        if (getIntent().getParcelableExtra(QueuedIntentHolder.QUEUED_INTENT_HOLDER) != null) {
            QueuedIntentHolder qih = getIntent().getExtras().getParcelable(QueuedIntentHolder.QUEUED_INTENT_HOLDER);
            String moduleId = qih.moduleId;
            List<String> roles = null;
            if(moduleId != null) {
                roles = ModuleMenuAdapter.getModuleRoles(getContentResolver(), moduleId);
            }

            if (!getEllucianApp().isUserAuthenticated()) {
                Utils.showLoginDialog(this, qih.queuedIntent, roles);
            } else if (getEllucianApp().isFingerprintUpdateNeeded()) {
                Utils.showFingerprintDialog(this, qih.queuedIntent, roles);
            }
        } else {
            if (fingerprintLogin) {
                showFingerprintDialog();
            } else
                showLoginDialog();
        }
    }
	
	@Override
	public void onStart() {
		super.onStart();
		configure();
		sendView("Show Home Screen", "");
	}

    private void configure() {

		SharedPreferences preferences = getSharedPreferences(Utils.CONFIGURATION, MODE_PRIVATE);
		String configUrl = preferences.getString(Utils.CONFIGURATION_URL, null);

		if (configUrl == null && !useDefaultConfiguration) {
			showInstitutionSelector();
			finish();
		} else if (configUrl == null && useDefaultConfiguration) {
	        Intent intent = new Intent(this, ConfigurationLoadingActivity.class);
			intent.putExtra(Utils.CONFIGURATION_URL, defaultConfigurationUrl);
			startActivity(intent);
		}
	}

	private void showInstitutionSelector() {
		final Intent intentSetup = new Intent(MainActivity.this,
				SchoolSelectionActivity.class);
		startActivity(intentSetup);
	}
	
	private void showLoginDialog() {
        LoginDialogFragment loginFragment = LoginDialogFragment.newInstance(getResources().getConfiguration());
        loginFragment.show(getSupportFragmentManager(), LoginDialogFragment.LOGIN_DIALOG);

	}

    private void showFingerprintDialog() {
        FingerprintDialogFragment fingerprintDialogFragment = new FingerprintDialogFragment();
        fingerprintDialogFragment.show(getSupportFragmentManager(), FingerprintDialogFragment.FINGERPRINT_DIALOG);
    }

    public class MainAuthenticationReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent incomingIntent) {	
			String result = incomingIntent.getStringExtra(Extra.LOGIN_SUCCESS);
			if(!TextUtils.isEmpty(result) && result.equals(AuthenticateUserIntentService.ACTION_SUCCESS)) {
                configureShortcuts();
            }
		}		
	}
	
	public class BackgroundAuthenticationReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent incomingIntent) {
			String result = incomingIntent.getStringExtra(Extra.LOGIN_SUCCESS);
			if(!TextUtils.isEmpty(result) && result.equals(AuthenticateUserIntentService.ACTION_SUCCESS)) {
                configureShortcuts();
            }
		}
	}

    public class ConfigurationUpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent incomingIntent) {
            boolean refresh = incomingIntent.getBooleanExtra(ConfigurationUpdateService.REFRESH, false);

            // A successful configuration refresh happened. Recreate activity to reflects any changes.
            if (refresh) {
                recreate();
            }
        }
    }

    private void setHomeIcon() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_home_menu);
    }

    private void configureShortcuts() {
        ArrayList<ShortcutListFragment.ShortcutItem> shortcutItems = buildShortcutList();

        Log.d(TAG, "Shortcut list built with this number of items:" + shortcutItems.size());

        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        homeScreenFragment = (ShortcutListFragment) manager.findFragmentByTag(SHORTCUT_LIST_FRAGMENT);

        if (homeScreenFragment != null) {
            transaction.remove(homeScreenFragment);
        }

        if (shortcutItems.size() > 0) {
            homeScreenFragment = ShortcutListFragment.newInstance(shortcutItems);
            transaction.replace(R.id.home_screen_frame, homeScreenFragment, SHORTCUT_LIST_FRAGMENT);
        }

        transaction.commitAllowingStateLoss();

    }

    public ArrayList<ShortcutListFragment.ShortcutItem> buildShortcutList() {

        ArrayList<ShortcutListFragment.ShortcutItem> shortcutItems = new ArrayList<>();

        EllucianApplication ellucianApplication = (EllucianApplication) getApplicationContext();
        final ContentResolver contentResolver = getContentResolver();

        boolean allowMaps = Utils.allowMaps(this);
        String shortcutsSelection = "";
        List<String> shortcutsSelectionArgs = new ArrayList<>();
        List<String> customTypes = ellucianApplication.getModuleConfigTypeList();
        for (int i = 0; i < ModuleType.ALL.length; i++) {
            String type = ModuleType.ALL[i];

            if (type.equals(ModuleType.MAPS) && !allowMaps) {
                continue;
            }

            if (type.equals(ModuleType.CUSTOM)) {
                for (int n = 0; n < customTypes.size(); n++) {
                    String customType = customTypes.get(n);

                    shortcutsSelection += " OR ";
                    shortcutsSelection += "( " + EllucianContract.Modules.MODULE_TYPE + " = ?" + " AND " + EllucianContract.Modules.MODULE_SUB_TYPE + " = ? )";

                    shortcutsSelectionArgs.add(type);
                    shortcutsSelectionArgs.add(customType);
                }
            } else {
                if (shortcutsSelection.length() > 0) {
                    shortcutsSelection += " OR ";
                }
                shortcutsSelection += EllucianContract.Modules.MODULE_TYPE + " = ?";
                shortcutsSelectionArgs.add(type);
            }
        }

        if (shortcutsSelection.length() > 0) {
            shortcutsSelection = EllucianContract.Modules.MODULE_HOME_SCREEN_ORDER + " > 0 AND (" + shortcutsSelection + ")";
        } else {
            shortcutsSelection = EllucianContract.Modules.MODULE_HOME_SCREEN_ORDER + " > 0";
        }

        // Pull all the legal modules currently set in the Modules table of the database
        Cursor shortcutsCursor = contentResolver.query(EllucianContract.Modules.CONTENT_URI,
                new String[]{BaseColumns._ID, EllucianContract.Modules.MODULE_TYPE, EllucianContract.Modules.MODULE_SUB_TYPE,
                        EllucianContract.Modules.MODULE_NAME, EllucianContract.Modules.MODULES_ICON_URL,
                        EllucianContract.Modules.MODULES_ID, EllucianContract.Modules.MODULE_SECURE, EllucianContract.Modules.MODULE_SHOW_FOR_GUEST,
                        EllucianContract.Modules.MODULE_HOME_SCREEN_ORDER, EllucianContract.Modules.MODULE_DISPLAY_IN_MENU}, shortcutsSelection,
                shortcutsSelectionArgs.toArray(new String[shortcutsSelectionArgs.size()]),
                EllucianContract.Modules.DEFAULT_SORT);

        if (shortcutsCursor != null && shortcutsCursor.moveToFirst()) {
            do {
                int typeIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_TYPE);
                int subTypeIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_SUB_TYPE);
                int nameIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_NAME);
                int moduleIdIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULES_ID);
                int iconUrlIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULES_ICON_URL);
                int secureIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_SECURE);
                int showGuestIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_SHOW_FOR_GUEST);
                int homeScreenOrderIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_HOME_SCREEN_ORDER);
                int displayIndex = shortcutsCursor.getColumnIndex(EllucianContract.Modules.MODULE_DISPLAY_IN_MENU);


                String type = shortcutsCursor.getString(typeIndex);
                String subType = shortcutsCursor.getString(subTypeIndex);
                String name = shortcutsCursor.getString(nameIndex);
                String iconUrl = shortcutsCursor.getString(iconUrlIndex);
                String moduleId = shortcutsCursor.getString(moduleIdIndex);
                String secure = shortcutsCursor.getString(secureIndex);
                String display = shortcutsCursor.getString(displayIndex);
                if (display == null) { display = "true"; }
                int showGuestInt = shortcutsCursor.getInt(showGuestIndex);
                int homeScreenOrder = shortcutsCursor.getInt(homeScreenOrderIndex);
                boolean showGuest = showGuestInt == 1 ? true : false;

                List<String> moduleRoles = ModuleMenuAdapter.getModuleRoles(ellucianApplication.getContentResolver(), moduleId);
                boolean lock = ellucianApplication.isSignInNeeded() ? ModuleMenuAdapter.showLock(this, type, subType, secure, moduleRoles, moduleId) : false;

                if (ModuleMenuAdapter.doesModuleShowForUser(ellucianApplication, moduleId, showGuest) && display.equals("true")) {
                    ShortcutListFragment.ShortcutItem shortcut = new ShortcutListFragment.ShortcutItem(
                            name, moduleId, type, subType, secure, iconUrl, lock, homeScreenOrder);
                    shortcutItems.add(shortcut);
                }
            } while (shortcutsCursor.moveToNext());
        }
        if (shortcutsCursor != null) { shortcutsCursor.close(); }
        return shortcutItems;
    }
    
}
