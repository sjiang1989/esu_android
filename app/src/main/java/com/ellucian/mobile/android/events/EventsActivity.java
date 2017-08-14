/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.events;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.CategoryDialogFragment;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.EllucianDefaultListFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.services.EventsIntentService;
import com.ellucian.mobile.android.provider.EllucianContract.Events;
import com.ellucian.mobile.android.provider.EllucianContract.EventsCategories;
import com.ellucian.mobile.android.provider.EllucianContract.Modules;
import com.ellucian.mobile.android.provider.EllucianDatabase;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PreferencesUtils;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.util.VersionSupportUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class EventsActivity extends EllucianActivity implements LoaderManager.LoaderCallbacks<Cursor>,
	CategoryDialogFragment.CategoryDialogListener {
	
	private final Activity activity = this;
    private SimpleCursorAdapter adapter;
	private EllucianDefaultListFragment mainFragment;
	private DialogFragment dialogFragment;
	private String[] allCategories;
	private String[] filteredCategories;
	private String query;
	private boolean resetListPosition;
    private EventsIntentServiceReceiver eventsIntentServiceReceiver;
    private boolean showSpinner = true;
    private static final String[] eventsColumns = new String[] {
		Events._ID,
		Events.EVENTS_TITLE, 
		Events.EVENTS_START, 
		Events.EVENTS_LOCATION, 
		Events.EVENTS_CATEGORIES,
		Events.EVENTS_DESCRIPTION,
		Events.EVENTS_CONTACT,
		Events.EVENTS_EMAIL,
		Events.EVENTS_END,
		Events.EVENTS_ALL_DAY
	};
	
	// TODO - fix this when events gets fixed on the mobile server
	private final SimpleDateFormat eventsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
	
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	setContentView(R.layout.activity_default_dual_pane);
        
        this.setTitle(moduleName);
    	
    	adapter = new SimpleCursorAdapter(
				this,
				R.layout.events_row, 
				null, 
				new String [] { Events.EVENTS_TITLE, Events.EVENTS_START, Events.EVENTS_LOCATION, Events.EVENTS_CATEGORIES, Events.EVENTS_DESCRIPTION },
				new int[] { R.id.events_title, R.id.events_start_time, R.id.events_location, R.id.events_category, R.id.event_summary},
				0);
    	
    	
    	FragmentManager manager = getSupportFragmentManager();
		FragmentTransaction transaction = manager.beginTransaction();
		mainFragment =  (EllucianDefaultListFragment) manager.findFragmentByTag("EventsListFragment");
		
        registerEventsServiceReceiver();
        if (mainFragment == null) {
			mainFragment = EllucianDefaultListFragment.newInstance(this, EventsListFragment.class.getName(), null);
			
			mainFragment.setListAdapter(adapter);
			transaction.add(R.id.frame_main, mainFragment, "EventsListFragment");
		} else {
			mainFragment.setListAdapter(adapter);
			transaction.attach(mainFragment);
		}
		
		ViewBinder viewBinder = new EventsViewBinder();
		if (viewBinder != null) {
			mainFragment.setViewBinder(viewBinder);
		}
		
		transaction.commit();
		
		if (savedInstanceState != null) {
			query = savedInstanceState.getString("query");
		}
    	
    	handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
    	// This case is when a user is already in a module that has singleTop set and 
    	// they select the same module type in the navigation menu
    	if ( !Intent.ACTION_SEARCH.equals(intent.getAction())  && 
    			!moduleId.equals(intent.getStringExtra(Extra.MODULE_ID)) ) {
    		Intent restartingIntent = new Intent(this, EventsActivity.class);
    		restartingIntent.putExtras(intent.getExtras());
    		restartingIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
    		startActivity(restartingIntent);
    		this.finish();
    	}
    	handleIntent(intent);
    }
    
    private void handleIntent(Intent intent) {
    	resetListPosition = false;
  
        String categoriesString = PreferencesUtils.getStringFromPreferences(this, CATEGORY_DIALOG, moduleId + "_" + FILTERED_CATEGORIES, "");
        if (!TextUtils.isEmpty(categoriesString)) {
        	filteredCategories = categoriesString.split(",");
        } else {
        	filteredCategories = null;
        }
        
        Bundle arguments = null;
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // handles a search query
            query = intent.getStringExtra(SearchManager.QUERY);
            arguments = new Bundle();
            arguments.putString("query", query);
            
            resetListPosition = true;
        } else if (!TextUtils.isEmpty(query)){
        	arguments = new Bundle();
            arguments.putString("query", query);
        }
        
        getSupportLoaderManager().restartLoader(0, arguments, this);
        getSupportLoaderManager().restartLoader(1, null, this);
        
        Intent serviceIntent = new Intent(this, EventsIntentService.class);
        serviceIntent.putExtra(Extra.MODULE_ID, moduleId);
        serviceIntent.putExtra(Extra.REQUEST_URL, requestUrl);
        startService(serviceIntent);
        if (showSpinner) {
            Utils.showProgressIndicator(this);
        }
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
        unregisterEventsServiceReceiver();
    	if (filteredCategories != null) {
	    	StringBuilder categoriesString = new StringBuilder();
	        for (int i = 0; i < filteredCategories.length; i++) {
	        	if (!TextUtils.isEmpty(categoriesString)) {
	        		categoriesString.append(",");
	        	}
	        	categoriesString.append(filteredCategories[i]);
	        }
	        PreferencesUtils.addStringToPreferences(this, CATEGORY_DIALOG, moduleId + "_" + FILTERED_CATEGORIES, categoriesString.toString());
    	} else {
	        PreferencesUtils.removeValuesFromPreferences(this, CATEGORY_DIALOG, moduleId + "_" + FILTERED_CATEGORIES);
    	}
    	
    	if (dialogFragment != null) {
    		dialogFragment.dismiss();
    		dialogFragment = null;
    	}

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerEventsServiceReceiver();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	if (!TextUtils.isEmpty(query)) {
    		outState.putString("query", query);
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.events, menu);
        
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView =
                 (SearchView) menu.findItem(R.id.events_action_search).getActionView();
        searchView.setSearchableInfo(
                 searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnCloseListener(new OnCloseListener() {

			@Override
			public boolean onClose() {
	        	getSupportLoaderManager().restartLoader(0, null, EventsActivity.this);
	        	query = null;
	        	resetListPosition = true;
				return false;
			}
           
        });
        searchView.setOnSearchClickListener(new SearchView.OnClickListener() {

            @Override
            public void onClick(View v) {
                EventsActivity.this.sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_SEARCH, "Search", null, moduleName);
            }

        });
        if (!TextUtils.isEmpty(query)) {
        	searchView.setQuery(query, false);
        }
		return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.events_menu_filter:
    		sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_LIST_SELECT, "Select filter", null, moduleName);
    		dialogFragment = new CategoryDialogFragment();
    	    dialogFragment.show(getSupportFragmentManager(), moduleId + "_" + CATEGORY_DIALOG);
    		return true;
    	case R.id.events_action_search:
    		onSearchRequested();
    		return true;
    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }
    
    
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		String[] projection;
		String selection;
		String[] selectionArgs;
			
		switch (id) {
		case 0:	
			Uri uri = Events.CONTENT_URI;
			if(args != null && args.containsKey("query")) {
				uri = Events.buildSearchUri(args.getString("query"));
			}
			Log.d("EventsActivity.onCreateLoader", "Creating loader for: " + id + " with URI: " + uri);
			// Because of the JOIN on tables you have to specify the right table column.
			selection = EllucianDatabase.Tables.EVENTS + "." + Modules.MODULES_ID + " = ?";

			if (filteredCategories != null && filteredCategories.length > 0) {				
				selectionArgs = new String[filteredCategories.length + 1];
				selectionArgs[0] = moduleId;
				System.arraycopy(filteredCategories, 0, selectionArgs, 1, filteredCategories.length);
			} else {
				selectionArgs = new String[] {moduleId};
			}
			
			for (int i = 1; i < selectionArgs.length; i++) {
				selection += " AND " + EventsCategories.EVENTS_CATEGORY_NAME + " != ?"; 
			}

			return new CursorLoader(this, uri, eventsColumns, selection, selectionArgs, Events.DEFAULT_SORT);
		case 1:
			Log.d("EventsActivity.onCreateLoader", "Creating loader for: " + id + " with URI: " + EventsCategories.CONTENT_URI);
			selection = Modules.MODULES_ID + " = ?";
			projection = new String[] {EventsCategories.EVENTS_CATEGORY_NAME };
			selectionArgs = new String[] {moduleId};
			return new CursorLoader(this, EventsCategories.CONTENT_URI, projection, selection, selectionArgs, EventsCategories.DEFAULT_SORT);
		default:
			return null;
		}

	}


	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		int id = loader.getId();
		switch (id) {
		case 0:
			Log.d("EventsActivity.onLoadFinished", "Finished loading cursor.  Swapping cursor in adapter containing " + data.getCount());
			adapter.swapCursor(data);
			createNotifyHandler(mainFragment);
            if (data.getCount() == 0) {
                showSpinner = true;
            } else {
                showSpinner = false;
                Utils.hideProgressIndicator(this);
            }
			break;
		case 1:
			Log.d("EventsActivity.onLoadFinished", "Finished loading cursor.  Updating categories");
			
			ArrayList<String> categoriesList = new ArrayList<>();
			while (data.moveToNext()) {
	        	int columnIndex = data.getColumnIndex(EventsCategories.EVENTS_CATEGORY_NAME);
	        	categoriesList.add(data.getString(columnIndex));
	        }
			allCategories = categoriesList.toArray(new String[categoriesList.size()]);
			break;
		}

	}


	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		int id = loader.getId();
		switch (id) {
		case 0:
			Log.d("EventsActivity.onLoaderReset", "Resetting loader");
			adapter.swapCursor(null);
			break;
		}
	}
	
	/** Make sure to call this method in the LoaderManager.LoaderCallback.onLoadFinished method if  
   	 *  it is overridden in the subclass 
   	 */
	private void createNotifyHandler(final EllucianDefaultListFragment fragment) {
   		Handler handler = new Handler(Looper.getMainLooper());
   		handler.post(new Runnable(){

			@Override
			public void run() {
				fragment.setInitialCursorPosition(resetListPosition);
				
			}
   			
   		});
   	}
	
	private String getDefaultDateFormattedString(Date date) {
		String dateString;
		DateFormat dateFormatter = android.text.format.DateFormat.getDateFormat(this.getBaseContext());
		dateString = dateFormatter.format(date);
		return dateString;
	}
	
	private String getDefaultTimeFormattedString(Date date) {
		String timeString;
		DateFormat timeFormatter = android.text.format.DateFormat.getTimeFormat(this.getBaseContext());
		timeString = timeFormatter.format(date);
		return timeString;
	}
	
	public String getEventDateFormattedString(Date start, Date end, boolean allDay) {
		String output;

		if (allDay) {
			output = getString(R.string.date_all_day_event_format,
						getDefaultDateFormattedString(start));
		} else if (end != null) {
			output = getString(R.string.date_time_to_time_format,
						getDefaultDateFormattedString(start),
						getDefaultTimeFormattedString(start),
						getDefaultTimeFormattedString(end));
		} else {
			output = getString(R.string.date_time_format,
						getDefaultDateFormattedString(start),
						getDefaultTimeFormattedString(start));
		}
		return output;
	}
	
	// TODO - fix this when events gets fixed on the mobile server
//	public String fromEventDate(Date date) {
//		String formattedDate = null;
//		if (date != null) {
//			formattedDate = eventsFormat.format(date);
//		}
//		return formattedDate;
//	}
	
	// TODO - fix this when events gets fixed on the mobile server
	public Date toEventDate(String formattedDate) {
		Date date = null;
		if (formattedDate != null) {
			try {
				date = eventsFormat.parse(formattedDate);
			} catch (ParseException e) {
				Log.e("EllucianDatabase.toDate",
						"Unable to convert " + formattedDate + " to a date.\n"
								+ e.getLocalizedMessage());
			}
		}
		return date;
	}
	
	private class EventsViewBinder implements SimpleCursorAdapter.ViewBinder {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int index) {

			if(index == cursor.getColumnIndex(Events.EVENTS_START)) {
				int allDayColumn = cursor.getColumnIndex(Events.EVENTS_ALL_DAY);
				int allDayFlag = cursor.getInt(allDayColumn);
				
				// TODO - fix this when events gets fixed on the mobile server
				String startDateString = cursor.getString(index);
				int endColumn = cursor.getColumnIndex(Events.EVENTS_END);
				String endDateString = cursor.getString(endColumn);

				Date startDate = toEventDate(startDateString);
				Date endDate = toEventDate(endDateString);
				
				String output;
				
				if (allDayFlag == 0) {
					output = getEventDateFormattedString(startDate, endDate, false);
				} else {
					output = getEventDateFormattedString(startDate, null, true);
				}
				
				((TextView) view).setText(output);
				return true;
			} else if (index == cursor.getColumnIndex(Events.EVENTS_CATEGORIES)) {
				// There is a bug in android that cuts off the side of a italic text in TextView.
				// Adding a space to end of string is a quick fix.
				String category = cursor.getString(index);
				category += " ";
				((TextView) view).setText(category);
				return true;
            } else if (index == cursor.getColumnIndex(Events.EVENTS_DESCRIPTION)) {
                // Convert String to HTML
                String description = cursor.getString(index);
                if (!TextUtils.isEmpty(description )) {
                    // Strip out breaks so we show more content in the list
                    description =  description.replaceAll("<br />", "")
                            .replaceAll("<br>", "")
                            .replaceAll("<br/>", "")
                            .replaceAll("</br>", "");
                    Spanned asHtml = VersionSupportUtils.fromHtml(description);
                    ((TextView) view).setText(asHtml);
                } else {
                    ((TextView) view).setText(null);
                }
                return true;
			} else {
				return false;
			}
			
		}
	}
	
	@Override
	public String[] getAllCategories() {		
		return allCategories;
	}

	@Override
	public String[] getFilteredCategories() {
		return filteredCategories;
	}

	@Override
	public void updateFilteredCategories(String[] filteredCategories) {
		this.filteredCategories = filteredCategories;
		resetListPosition = true;
		getSupportLoaderManager().restartLoader(0, null, this);
	}

    private void registerEventsServiceReceiver() {
        if(eventsIntentServiceReceiver == null) {
            Log.d("EventsActivity.RegisterEventsServiceReceiver", "Registering new service receiver");
            eventsIntentServiceReceiver = new EventsIntentServiceReceiver();
            IntentFilter filter = new IntentFilter(EventsIntentService.ACTION_FINISHED);
            LocalBroadcastManager.getInstance(this).registerReceiver(eventsIntentServiceReceiver, filter);
        }
    }

    private void unregisterEventsServiceReceiver() {
        if(eventsIntentServiceReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(eventsIntentServiceReceiver);
        }
    }

    /**
     * Broadcast receiver which receives notification when the EventsIntentService
     * is finished performing an update of the data from the web services to the
     * local database.  Upon successful completion, this receiver will reload
     * the events data from the local database.
     *
     */
    private class EventsIntentServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Utils.hideProgressIndicator(activity);
            boolean updated = intent.getBooleanExtra(EventsIntentService.PARAM_OUT_DATABASE_UPDATED, false);
            Log.d("EventsIntentServiceReceiver", "onReceive: database updated = " + updated);
            if (updated) {
                Log.d("EventsIntentServiceReceiver.onReceive", "All events retrieved and database updated");
            }
        }

    }

}
