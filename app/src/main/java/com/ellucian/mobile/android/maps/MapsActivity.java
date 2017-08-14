/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.maps;

import android.Manifest;
import android.app.Dialog;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.services.MapsIntentService;
import com.ellucian.mobile.android.maps.LayersDialogFragment.LayersDialogFragmentListener;
import com.ellucian.mobile.android.provider.EllucianContract.MapsBuildings;
import com.ellucian.mobile.android.provider.EllucianContract.MapsCampuses;
import com.ellucian.mobile.android.provider.EllucianContract.Modules;
import com.ellucian.mobile.android.provider.EllucianDatabase.Tables;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.PermissionUtil;
import com.ellucian.mobile.android.util.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MapsActivity extends EllucianActivity implements
        LayersDialogFragmentListener, OnInfoWindowClickListener,
        LocationListener,
        LoaderManager.LoaderCallbacks<Cursor>,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = MapsActivity.class.getSimpleName();
    private static final String SELECTED_CAMPUS = "selected_campus";
    private static final String SELECTED_CAMPUS_NAME = "selected_campus_name";
    private static final String CAMPUS_CENTER = "campus_center";
    private static final String BUILDING_LOCATIONS = "building_locations";
    private static final long LOCATION_REQUEST_INTERVAL = 5 * Utils.ONE_SECOND;

    // LOCATION is optional - user can deny and still use maps.
    private static final int LOCATION_REQUEST_ID = 1;
    private static String[] LOCATION_PERMISSIONS =
            {Manifest.permission.ACCESS_FINE_LOCATION};

    private GoogleMap map;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest locationRequest;

    private Cursor campusCursor;
    private final HashMap<Marker, Building> markers = new HashMap<>();
    private int selectedCampus = -1; // -1 until user makes selection or location is enabled.
    private boolean recreated;
    private String selectedCampusName;
    private Location lastKnownLocation;
    private Location campusCenter;
    private ArrayList<Building> buildings = new ArrayList<>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.hasPlayServicesAvailable(this)) {
            finish();
        }

        buildGoogleApiClient();

        setContentView(R.layout.activity_maps);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        getLoaderManager();

        if (savedInstanceState == null) {
            Intent serviceIntent = new Intent(this, MapsIntentService.class);
            serviceIntent.putExtra(Extra.MODULE_ID, moduleId);
            serviceIntent.putExtra(Extra.MAPS_CAMPUSES_URL, getIntent().getStringExtra(Extra.MAPS_CAMPUSES_URL));
            startService(serviceIntent);

            mapFragment.setRetainInstance(true);
        } else {
            recreated = true;
            selectedCampus = savedInstanceState.getInt(SELECTED_CAMPUS, -1);

            if (savedInstanceState.containsKey(SELECTED_CAMPUS_NAME)) {
                selectedCampusName = savedInstanceState.getString(SELECTED_CAMPUS_NAME);
                setTitle(selectedCampusName);
            }

            if (savedInstanceState.containsKey(CAMPUS_CENTER)) {
                campusCenter = savedInstanceState.getParcelable(CAMPUS_CENTER);
            }

            if (savedInstanceState.containsKey(BUILDING_LOCATIONS)) {
                buildings = (ArrayList<Building>) savedInstanceState.getSerializable(BUILDING_LOCATIONS);
            }
        }

        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnInfoWindowClickListener(this);
        map.setOnMyLocationButtonClickListener(this);
        if (buildings.size() > 0) {
            createMarkersFromBuildings();
        }
        handleIntent();
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (!recreated)
                requestLocationPermission();
        } else {
            map.setMyLocationEnabled(true);
            if (lastKnownLocation != null && (selectedCampus == -1)) {
                selectClosestCampus();
            }
        }
    }

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    private synchronized void buildGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        locationRequest = new LocationRequest();
        locationRequest.setInterval(LOCATION_REQUEST_INTERVAL);
        locationRequest.setFastestInterval(LOCATION_REQUEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    @Override
    public boolean onMyLocationButtonClick() {
        if (Utils.isLocationEnabled(this)) {
            showMyLocation();
        } else {
            Toast.makeText(this, R.string.maps_enable_location_services, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * (Android M+ only)
     * Request location permission. No explanation to user is needed.
     */
    private void requestLocationPermission() {
        SettingsUtils.userWasAskedForPermission(this, Utils.PERMISSIONS_ASKED_FOR_LOCATION);
        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, LOCATION_REQUEST_ID);
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == LOCATION_REQUEST_ID) {
            if (PermissionUtil.verifyPermissions(grantResults)) {
                // All required permissions have been granted.
                getUsersLocation();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent();
    }

    private void handleIntent() {
        map.setInfoWindowAdapter(new CustomInfoWindowAdapter(getLayoutInflater()));
        getLoaderManager().initLoader(0, null, this);
    }

    private void showMyLocation() {
        Log.i(TAG, "showMyLocation");
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, "Geolocate user", null, moduleName);
        if (lastKnownLocation != null) {
            LatLng myLatlng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            LatLng campusLatLng = new LatLng(campusCenter.getLatitude(), campusCenter.getLongitude());
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(myLatlng);
            builder.include(campusLatLng);
            LatLngBounds bounds = builder.build();
            int padding = 100; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            map.animateCamera(cu);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected()) {
            requestLocationUpdates();
        }

        if (map != null) {
            map.setIndoorEnabled(true);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
        
        if (map != null) {
            map.setIndoorEnabled(false);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        lastKnownLocation = location;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_maps, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(
                R.id.maps_action_search).getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));
        searchView.setOnSearchClickListener(new SearchView.OnClickListener() {

            @Override
            public void onClick(View v) {
                MapsActivity.this.sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_SEARCH, "Search", null, moduleName);
            }

        });
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (campusCursor == null) {
            menu.removeItem(R.id.maps_campus);
        } else if (campusCursor.getCount() < 2) {
            selectedCampus = 0;
            menu.removeItem(R.id.maps_campus);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.maps_campus:
                createSelectCampusDialog().show();
                return true;
            case R.id.maps_layers:
                sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, "Change map view", null, moduleName);
                showLayersDialog();
                return true;
            case R.id.maps_legal:
                startActivity(new Intent(this, LegalNoticesActivity.class));
                return true;
            case R.id.maps_buildings:
                sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Tap building icon", null, moduleName);
                Intent intent = new Intent(this, BuildingListActivity.class);
                intent.putExtra(Extra.MODULE_ID, moduleId);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showLayersDialog() {
        int layer = 0;
        switch (map.getMapType()) {
            case GoogleMap.MAP_TYPE_NORMAL:
                layer = MAP_TYPE_NORMAL;
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                layer = MAP_TYPE_SATELLITE;
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                layer = MAP_TYPE_TERRAIN;
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                layer = MAP_TYPE_HYBRID;
                break;

        }
        LayersDialogFragment layersDialog = LayersDialogFragment
                .newInstance(layer);
        layersDialog.setLayersDialogFragmentListener(this);
        layersDialog.show(getFragmentManager(), null);

    }

    @Override
    public void setLayer(int layer) {
        switch (layer) {
            case MAP_TYPE_NORMAL:
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
            case MAP_TYPE_SATELLITE:
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                break;
            case MAP_TYPE_TERRAIN:
                map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                break;
            case MAP_TYPE_HYBRID:
                map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;
        }
    }

    private void moveToCampus(int itemPosition) {
        Cursor cursor = campusCursor;
        cursor.moveToPosition(itemPosition);
        String campusName = cursor.getString(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_NAME));
        double centerLat = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_CENTER_LATITUDE));
        double centerLng = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_CENTER_LONGITUDE));
        double nwLat = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_NORTHWEST_LATITUDE));
        double nwLng = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_NORTHWEST_LONGITUDE));
        double seLat = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_SOUTHEAST_LATITUDE));
        double seLng = cursor.getDouble(cursor
                .getColumnIndex(MapsCampuses.CAMPUS_SOUTHEAST_LONGITUDE));

        campusCenter = new Location("CampusCenter");
        campusCenter.setLatitude(centerLat);
        campusCenter.setLongitude(centerLng);
        map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(centerLat,
                centerLng)));
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(new LatLng(nwLat, nwLng))
                .include(new LatLng(seLat, seLng)).build();
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));

        Bundle arguments = new Bundle();
        arguments.putString("campusName", campusName);
        getLoaderManager().restartLoader(1, arguments, this);
        selectedCampusName = campusName;
        setTitle(selectedCampusName);
    }

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id) {
		case 0:
			return new CursorLoader(this, MapsCampuses.CONTENT_URI, null, Tables.MAPS_CAMPUSES + "." + Modules.MODULES_ID + " = ? ",
					new String[] { this.moduleId }, MapsCampuses.DEFAULT_SORT);
		case 1:
			return new CursorLoader(this, MapsCampuses.buildBuildingsUri(args
					.getString("campusName")), null, null, null,
					MapsBuildings.DEFAULT_SORT);
		default:
			return null;
		}
	}

    private void selectClosestCampus() {
        Log.i(TAG, "selectClosestCampus");
        float distance = Float.MAX_VALUE;
        Cursor cursor = campusCursor;

        if (cursor != null) {
            int campusesFound = cursor.getCount();
            Log.d(TAG, "Number of Campuses found: " + campusesFound);
            if (campusesFound > 1) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);
                    double centerLat = cursor.getDouble(cursor
                            .getColumnIndex(MapsCampuses.CAMPUS_CENTER_LATITUDE));
                    double centerLng = cursor.getDouble(cursor
                            .getColumnIndex(MapsCampuses.CAMPUS_CENTER_LONGITUDE));
                    campusCenter = new Location("CampusCenter");
                    campusCenter.setLatitude(centerLat);
                    campusCenter.setLongitude(centerLng);
                    float campusDistance = lastKnownLocation.distanceTo(campusCenter);
                    if (campusDistance < distance) {
                        selectedCampus = i;
                        distance = campusDistance;
                    }
                }
                moveToCampus(selectedCampus);
            } else if (campusesFound == 1) {
                moveToCampus(0);
            }
        }

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        int id = loader.getId();
        switch (id) {
            case 0:
                campusCursor = data;
                if (data.getCount() > 0) {
                    Log.d(TAG, "Cursor updated. Found this many campuses" + campusCursor.getCount());
                    invalidateOptionsMenu();
                    // If campusCenter is null, the user hasn't been zoomed to any campus yet.
                    if (campusCenter == null) {
                        // Zoom to the closest campus if we have user's location.
                        // Otherwise, pick the first from the list
                        if (lastKnownLocation == null) {
                            moveToCampus(0);
                        } else {
                            selectClosestCampus();
                        }

                    }
                }
                break;
            case 1:
                if (data.moveToFirst()) {
                    buildings = new ArrayList<>();
                    do {

                        String buildingName = data.getString(data
                                .getColumnIndex(MapsBuildings.BUILDING_NAME));
                        String campusName = data.getString(data
                                .getColumnIndex(MapsCampuses.CAMPUS_NAME));
                        String category = data.getString(data
                                .getColumnIndex(MapsBuildings.BUILDING_CATEGORIES));//Categories.MAPS_BUILDINGS_CATEGORY_NAMEMAPS_BUILDINGS_CATEGORY_NAME));
                        String description = data
                                .getString(data
                                        .getColumnIndex(MapsBuildings.BUILDING_DESCRIPTION));
                        String imageUri = data.getString(data
                                .getColumnIndex(MapsBuildings.BUILDING_IMAGE_URL));
                        String label = data.getString(data
                                .getColumnIndex(MapsBuildings.BUILDING_ADDRESS));
                        double buildingLat = data.getDouble(data
                                .getColumnIndex(MapsBuildings.BUILDING_LATITUDE));
                        double buildingLon = data.getDouble(data
                                .getColumnIndex(MapsBuildings.BUILDING_LONGITUDE));
                        String additionalServices = data
                                .getString(data
                                        .getColumnIndex(MapsBuildings.BUILDING_ADDITIONAL_SERVICES));
                        Building building = new Building();
                        building.name = buildingName;
                        building.campusName = campusName;
                        building.type = category;
                        building.description = description;
                        building.imageUrl = imageUri;
                        building.address = label;
                        building.latitude = buildingLat;
                        building.longitude = buildingLon;
                        building.additionalServices = additionalServices;
                        buildings.add(building);
                    } while (data.moveToNext());
                    createMarkersFromBuildings();
                }
                break;
        }

    }

    private void createMarkersFromBuildings() {
        Set<Marker> keys = new HashSet<>(markers.keySet());
        for (Marker marker : keys) {
            marker.remove();
            markers.remove(marker);
        }

        map.clear();
        for (Building building : buildings) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(new LatLng(building.latitude, building.longitude))
                    .title(building.name).snippet(building.type));

            markers.put(marker, building);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(CAMPUS_CENTER, campusCenter);
        savedInstanceState.putInt(SELECTED_CAMPUS, selectedCampus);
        savedInstanceState.putString(SELECTED_CAMPUS_NAME, selectedCampusName);
        savedInstanceState.putSerializable(BUILDING_LOCATIONS, buildings);
    }

    @Override
    public void onInfoWindowClick(Marker arg0) {
        sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Select Map Pin", null, moduleName);
        Building building = markers.get(arg0);
        Intent intent = MapUtils.buildBuildingDetailIntent(this, building.name,
                building.type, building.address, building.description,
                building.phone, building.email, building.imageUrl,
                building.latitude, building.longitude, null, null, building.campusName, building.additionalServices, building.showName, true);
        startActivity(intent);
    }

    @Override
    public void startActivity(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            intent.putExtra(Extra.MODULE_ID, moduleId);
        }
        super.startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        sendView("Map of campus", moduleName);
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        getUsersLocation();
    }

    private void getUsersLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!recreated)
                requestLocationPermission();
        } else {
            requestLocationUpdates();
            lastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            enableMyLocation();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "Connection suspended. Re-connect");
        mGoogleApiClient.connect();
    }

    private void requestLocationUpdates() {
        Log.i(TAG, "requestLocationUpdates: ");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        }
    }

    public Dialog createSelectCampusDialog() {
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Tap campus selector", null, moduleName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        int listItemSelected = selectedCampus;
        if (selectedCampus == -1) {
            listItemSelected = 0;
        }

        builder.setTitle(R.string.maps_campus_selection_title)
                .setSingleChoiceItems(campusCursor, listItemSelected, MapsCampuses.CAMPUS_NAME,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, "Select campus", null, moduleName);
                                selectedCampus = which;
                                moveToCampus(which);
                                dialog.dismiss();
                            }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) { }
                });

        return builder.create();
    }

}