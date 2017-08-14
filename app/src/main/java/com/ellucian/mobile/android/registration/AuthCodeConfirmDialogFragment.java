/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianDialogFragment;
import com.ellucian.mobile.android.client.registration.Section;

public class AuthCodeConfirmDialogFragment extends EllucianDialogFragment {
	
	private RegistrationActivity registrationActivity;
	
	public static AuthCodeConfirmDialogFragment newInstance(Section section, int position) {
		AuthCodeConfirmDialogFragment fragment = new AuthCodeConfirmDialogFragment();
		Bundle args = new Bundle();
		args.putParcelable(RegistrationActivity.SECTION, section);
		args.putInt("position", position);
		fragment.setArguments(args);
		
		return fragment;
	}
	
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			registrationActivity = (RegistrationActivity) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("Attached Activity must of type: RegistrationActivity");
        }
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

    @NonNull
    @Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle args = getArguments();
		final Section section = args.getParcelable(RegistrationActivity.SECTION);
		final int position = args.getInt("position");

		AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());

		View layout = registrationActivity.getLayoutInflater().inflate(R.layout.decimal_number_input_dialog_layout, null);
		final TextView title = (TextView) layout.findViewById(R.id.title);
		final EditText input = (EditText) layout.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.registration_auth_code);
		final Button okButton = (Button) layout.findViewById(R.id.ok_button);
		final Button cancelButton = (Button) layout.findViewById(R.id.cancel_button);

		title.setText(getText(R.string.registration_auth_code_dialog_title));

		okButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String value = input.getText().toString().trim();

				Toast emptyToast = Toast.makeText(registrationActivity,
						R.string.dialog_field_empty, Toast.LENGTH_SHORT);
				emptyToast.setGravity(Gravity.CENTER, 0, 0);

				if (TextUtils.isEmpty(value)) {
					emptyToast.show();
				} else {
                    registrationActivity.onAuthCodeConfirmOkClicked(section.termId, section.sectionId, value);
                    AuthCodeConfirmDialogFragment.this.dismiss();
				}
			}
		});

		cancelButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
                registrationActivity.onAuthCodeConfirmCancelClicked(position);
				AuthCodeConfirmDialogFragment.this.dismiss();
			}
		});

		builder.setView(layout);

		Dialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);

		return dialog;
	}

	@Override
	public void onDestroyView() {
		// Trick to keep dialog open on rotate
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

}

