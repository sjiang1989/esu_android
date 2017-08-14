// Copyright 2014-2016 Ellucian Company L.P and its affiliates.

package com.ellucian.mobile.android.registration;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianDialogFragment;

public class RegisterConfirmDialogFragment extends EllucianDialogFragment {

	private RegistrationActivity registrationActivity;
	
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			registrationActivity = (RegistrationActivity) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("Attached Activity must of type: RegistrationActivity");
        }
	}
	
	@NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
		
		//builder.setTitle(R.string.registration_dialog_title)
		builder.setMessage(R.string.registration_dialog_register_message)
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                	dialog.cancel(); 

                }
            })
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                	registrationActivity.onRegisterConfirmOkClicked();
                }
            });

		return builder.create();
	}

}
