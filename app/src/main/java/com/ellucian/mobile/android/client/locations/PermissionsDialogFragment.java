/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.locations;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.EllucianDialogFragment;
import com.ellucian.mobile.android.settings.SettingsUtils;
import com.ellucian.mobile.android.util.Utils;

import java.util.Calendar;

public class PermissionsDialogFragment extends EllucianDialogFragment {

    private static final int LOCATION_REQUEST_ID = 1;
    private static String[] LOCATION_PERMISSIONS = {Manifest.permission.ACCESS_COARSE_LOCATION};
    private static final int BLUETOOTH_REQUEST_ID = 2;

    public static PermissionsDialogFragment newInstance(int type) {
        PermissionsDialogFragment frag = new PermissionsDialogFragment();
        Bundle args = new Bundle();
        args.putInt("type", type);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int type = getArguments().getInt("type");

        if (type == EllucianActivity.LOCATION_ALERT_DIALOG) {
            return locationsDialog();
        } else {
            return bluetoothDialog();
        }
    }

    private Dialog locationsDialog() {
        Dialog locationsDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.location_permissions_title)
                .setMessage(R.string.location_permissions_message)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dismiss();
                                SettingsUtils.userWasAskedForPermission(getContext(), Utils.PERMISSIONS_ASKED_FOR_LOCATION);
                                requestPermissions(LOCATION_PERMISSIONS, LOCATION_REQUEST_ID);
                            }
                        }
                )
                .setNeutralButton(R.string.location_notification_action_mute_for_today,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dismiss();
                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                                editor.putString(Utils.LOCATIONS_NOTIF_REMIND, String.valueOf(getStartOfDay()));
                                editor.apply();
                            }
                        }
                )
                .setNegativeButton(R.string.location_notification_action_mute,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dismiss();
                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                                editor.putString(Utils.LOCATIONS_NOTIF_REMIND, "false");
                                editor.apply();
                            }
                        }
                )
                .create();
        locationsDialog.setCanceledOnTouchOutside(false);
        return locationsDialog;
    }

    private Dialog bluetoothDialog() {
        Dialog bluetoothDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.bluetooth_permissions_title)
                .setMessage(R.string.bluetooth_permissions_message)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dismiss();
                                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(enableIntent, BLUETOOTH_REQUEST_ID);
                            }
                        }
                )
                .setNeutralButton(R.string.location_notification_action_mute_for_today,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dismiss();
                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                                editor.putString(Utils.BLUETOOTH_NOTIF_REMIND, String.valueOf(getStartOfDay()));
                                editor.apply();
                            }
                        }
                )
                .setNegativeButton(R.string.location_notification_action_mute,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dismiss();
                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = defaultSharedPrefs.edit();
                                editor.putString(Utils.BLUETOOTH_NOTIF_REMIND, "false");
                                editor.apply();
                            }
                        }
                )
                .create();
        bluetoothDialog.setCanceledOnTouchOutside(false);
        return bluetoothDialog;
    }

    private long getStartOfDay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
