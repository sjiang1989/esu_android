/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.maps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.maps.LayersDialogFragment.LayersDialogFragmentListener;
import com.ellucian.mobile.android.settings.SettingsUtils;
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
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsSingleLocationActivity extends EllucianActivity
		implements LayersDialogFragmentListener, LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = MapsSingleLocationActivity.class.getSimpleName();
    private static final long LOCATION_REQUEST_INTERVAL = 5 * Utils.ONE_SECOND;

    // LOCATION is optional - user can deny and still use maps.
    private static final int LOCATION_REQUEST_ID = 1;
    private static String[] LOCATION_PERMISSIONS =
            {Manifest.permission.ACCESS_FINE_LOCATION};

    private GoogleMap map;
    private Marker marker;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest locationRequest;
    private Location lastKnownLocation;
    private boolean recreated;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.hasPlayServicesAvailable(this)) {
            finish();
        }

        buildGoogleApiClient();

        setContentView(R.layout.activity_maps_single_location);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        if (savedInstanceState == null) {
            mapFragment.setRetainInstance(true);
        } else {
            recreated = true;
        }

        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnMyLocationButtonClickListener(this);
        handleIntent();
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

    private void handleIntent() {

        Intent intent = getIntent();
        final String title = intent.getStringExtra("title");
        LatLng location = new LatLng(intent.getDoubleExtra("latitude", 0),
                intent.getDoubleExtra("longitude", 0));
        setTitle(title);

        map.setInfoWindowAdapter(new CustomInfoWindowAdapter(getLayoutInflater()));
        marker = map.addMarker(new MarkerOptions().position(location).title(title));

        if (!recreated) {
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(location, 17);
            map.moveCamera(cu);
        }
        marker.showInfoWindow();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_maps_single_location, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.maps_layers:
			sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, "Change map view", null, moduleName);
			showLayersDialog();
			return true;
		case R.id.maps_legal:
			startActivity(new Intent(this, LegalNoticesActivity.class));
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

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (!recreated)
                requestLocationPermission();
        } else {
            map.setMyLocationEnabled(true);
        }
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

    @Override
    public void onLocationChanged(Location location) {
        lastKnownLocation = location;
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        getUsersLocation();
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

    @Override
    public boolean onMyLocationButtonClick() {
        if (Utils.isLocationEnabled(this)) {
            showMyLocation();
        } else {
            Toast.makeText(this, R.string.maps_enable_location_services, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void showMyLocation() {
        Log.i(TAG, "showMyLocation");
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_INVOKE_NATIVE, "Geolocate user", null, moduleName);
        if (lastKnownLocation != null) {
            LatLng myLatlng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(myLatlng);
            builder.include(marker.getPosition());
            LatLngBounds bounds = builder.build();
            int padding = 100; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            map.animateCamera(cu);
        }
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

}