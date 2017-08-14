/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.EllucianDefaultDetailActivity;
import com.ellucian.mobile.android.app.EllucianDefaultDetailFragment;
import com.ellucian.mobile.android.app.EllucianDefaultRecyclerFragment;
import com.ellucian.mobile.android.app.EllucianRecyclerView;
import com.ellucian.mobile.android.client.registration.Section;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.view.SimpleDividerItemDecoration;

import java.util.ArrayList;

public class RegistrationCartRecyclerFragment extends EllucianDefaultRecyclerFragment {
	public static final String TAG = RegistrationCartRecyclerFragment.class.getSimpleName();

	private RegistrationActivity activity;
	private Button registerButton;
	private View eligibilityErrorView;
	private boolean showEligibilityError;
	private String errorMessages;
    private View authRequiredHeader;
    private static final String CHECKED_POSITIONS = "checked_positions";

    public RegistrationCartRecyclerFragment() {
	}
	
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		this.activity = (RegistrationActivity) getActivity();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_registration_cart_list, container, false);
        registerButton = (Button) rootView.findViewById(R.id.register);
        eligibilityErrorView = rootView.findViewById(R.id.eligibility_error_message_view);
        authRequiredHeader = rootView.findViewById(R.id.registration_auth_req_top_header);

        registerButton.setBackgroundColor(Utils.getPrimaryColor(activity));
        registerButton.setTextColor(Utils.getHeaderTextColor(activity));

        recyclerView = (EllucianRecyclerView) rootView.findViewById(R.id.recycler_view);
        // use a linear layout manager
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(activity));
        recyclerView.setAdapter(adapter);

        if (adapter.getItemCount() == 0) {
            TextView emptyView = (TextView) rootView.findViewById(android.R.id.empty);
            emptyView.setVisibility(View.VISIBLE);
        }
		return rootView;
	}

    @Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState != null) {
			showEligibilityError = savedInstanceState.getBoolean("showEligibilityError");
			errorMessages = savedInstanceState.getString("errorMessages");

            if (savedInstanceState.containsKey(CHECKED_POSITIONS)) {
                Log.d(TAG, "Found saved checked course sections");
                ((RegistrationRecyclerAdapter)adapter).setCheckedPositions(
                        savedInstanceState.getIntegerArrayList(CHECKED_POSITIONS));
            }
		}

		showEligibilityErrorView(showEligibilityError);

        if (((RegistrationRecyclerAdapter)adapter).getSectionsRequiringAuthCode() > 0) {
            showAuthRequiredHeader();
        }

	}


    @Override
    public Bundle buildDetailBundle(Object... objects) {
        Bundle bundle = new Bundle();

        bundle.putString(Extra.MODULE_NAME, getEllucianActivity().moduleName);

        Section section = (Section)objects[0];
        bundle.putParcelable(RegistrationActivity.SECTION, section);
        bundle.putString(RegistrationDetailFragment.REGISTRATION_MODULE_ID,
                ((EllucianActivity)getActivity()).moduleId);

        return bundle;
    }

	@Override
	public void showDetails(int index) {

        String simpleName = RegistrationCartRecyclerFragment.class.getSimpleName();

        if (dualPane) {
			//We can display everything in-place with fragments

            detailBundle.putString(RegistrationDetailFragment.REQUESTING_LIST_FRAGMENT, simpleName);
            EllucianDefaultDetailFragment details = getDetailFragment(detailBundle, index);

			// Execute a transaction, replacing any existing fragment
			// with this one inside the frame.
			FragmentTransaction ft = getFragmentManager().beginTransaction();
			ft.replace(R.id.frame_extra, details);

			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
			ft.commit();

		} else {
			// Otherwise we need to launch a new activity to display
			// the dialog fragment with selected text.

			Intent intent = new Intent();
			intent.setClass(getActivity(), getDetailActivityClass());
            intent.putExtras(activity.getIntent().getExtras());
			intent.putExtras(detailBundle);
			intent.putExtra("index", index);
			// startActivityForResult for RegistrationDetailActivity to handle remove requests
			intent.putExtra(RegistrationDetailFragment.REQUESTING_LIST_FRAGMENT, simpleName);
			getActivity().startActivityForResult(intent, RegistrationActivity.REGISTRATION_DETAIL_REQUEST_CODE);

		}
	}

	@Override
	public void onStart() {
		super.onStart();
		sendView("Registration Cart list", getEllucianActivity().moduleName);
	}
	
	
	@Override
	public void onResume() {
		super.onResume();
        ArrayList<Integer> checkedPositions = ((RegistrationRecyclerAdapter)adapter).getCheckedPositions();
        updateRegisterButton(checkedPositions);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("showEligibilityError", showEligibilityError);
		outState.putString("errorMessages", errorMessages);
        outState.putIntegerArrayList(CHECKED_POSITIONS, ((RegistrationRecyclerAdapter)adapter).getCheckedPositions());
    }
	
	public void updateRegisterButton(ArrayList<Integer> checkedList) {
        if (checkedList == null || checkedList.isEmpty()) {
            registerButton.setVisibility(View.GONE);
        } else {
            int numberShown = checkedList.size();
            if (numberShown > 0) {
                registerButton.setText(getString(R.string.label_with_count_format,
                        getString(R.string.registration_register),
                        numberShown));
                if (!registerButton.isShown()) {
                    registerButton.setVisibility(View.VISIBLE);
                }
            } else {
                registerButton.setVisibility(View.GONE);
            }
        }
	}

    public void showAuthRequiredHeader() {
        if (!authRequiredHeader.isShown()) {
            authRequiredHeader.setVisibility(View.VISIBLE);
        }
    }

	protected void setRegisterButtonEnabled(boolean enabled) {
		registerButton.setEnabled(enabled);
	}
	
	private void showEligibilityErrorView(boolean show) {
		if (show) {
			if (!TextUtils.isEmpty(errorMessages)) {
				TextView messagesView = (TextView) rootView.findViewById(R.id.messages);
				messagesView.setMovementMethod(ScrollingMovementMethod.getInstance());
				messagesView.setText(errorMessages);
			}

			eligibilityErrorView.setVisibility(View.VISIBLE);
		} else {
			eligibilityErrorView.setVisibility(View.GONE);
		}
	}

    public void setShowEligibilityError(boolean showError, String message) {
        showEligibilityError = showError;
        this.errorMessages = message;
    }

    @Override
    public Class<? extends EllucianDefaultDetailFragment> getDetailFragmentClass() {
        return RegistrationDetailFragment.class;
    }

    @Override
    public Class<? extends EllucianDefaultDetailActivity> getDetailActivityClass() {
        return RegistrationDetailActivity.class;
    }

}

