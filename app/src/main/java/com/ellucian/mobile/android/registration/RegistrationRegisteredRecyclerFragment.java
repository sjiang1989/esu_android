/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */
package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
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

public class RegistrationRegisteredRecyclerFragment extends EllucianDefaultRecyclerFragment {
    private static final String TAG = RegistrationRegisteredRecyclerFragment.class.getSimpleName();
	
	private RegistrationActivity activity;
    private Button dropButton;
    private static final String CHECKED_POSITIONS = "checked_positions";

    public RegistrationRegisteredRecyclerFragment() {
    }

    @Override
	public void onAttach(Context context) {
		super.onAttach(context);
		this.activity = (RegistrationActivity) getActivity();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_registration_registered_list, container, false);
		dropButton = (Button) rootView.findViewById(R.id.drop);

        dropButton.setBackgroundColor(Utils.getPrimaryColor(activity));
		dropButton.setTextColor(Utils.getHeaderTextColor(activity));

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
            if (savedInstanceState.containsKey(CHECKED_POSITIONS)) {
                Log.d(TAG, "Found saved checked course sections");
                ((RegistrationRecyclerAdapter)adapter).setCheckedPositions(
                        savedInstanceState.getIntegerArrayList(CHECKED_POSITIONS));
            }
        }

    }

    @Override
	public void onStart() {
		super.onStart();
		sendView("Registration Registered Sections list", getEllucianActivity().moduleName);
	}
	
	@Override
	public void onResume() {
		super.onResume();
        ArrayList<Integer> checkedPositions = ((RegistrationRecyclerAdapter)adapter).getCheckedPositions();
        updateDropButton(checkedPositions);
	}

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntegerArrayList(CHECKED_POSITIONS, ((RegistrationRecyclerAdapter)adapter).getCheckedPositions());
    }

	public void updateDropButton(ArrayList<Integer> checkedList) {
        if (checkedList == null || checkedList.isEmpty()) {
            dropButton.setVisibility(View.GONE);
        } else {
            int numberShown = checkedList.size();
            if (numberShown > 0) {
                dropButton.setText(getString(R.string.label_with_count_format,
                        getString(R.string.registration_drop),
                        numberShown));
                if (!dropButton.isShown()) {
                    dropButton.setVisibility(View.VISIBLE);
                }
            } else {
                dropButton.setVisibility(View.GONE);
            }
        }
	}

	@Override
	public Bundle buildDetailBundle(Object... objects) {
		Bundle bundle = new Bundle();
		
		bundle.putString(Extra.MODULE_NAME, getEllucianActivity().moduleName);

		Section section = (Section)objects[0];
		bundle.putParcelable(RegistrationActivity.SECTION, section);
        bundle.putString(RegistrationDetailFragment.REGISTRATION_MODULE_ID,
                ((EllucianActivity) getActivity()).moduleId);

        return bundle;
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
