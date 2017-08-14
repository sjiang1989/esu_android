/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
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

public class RegistrationSearchResultsRecyclerFragment extends EllucianDefaultRecyclerFragment {
    private static final String TAG = RegistrationSearchResultsRecyclerFragment.class.getSimpleName();

	private RegistrationActivity activity;
	private Button addToCartButton;
	private boolean newSearch;
    private static final String CHECKED_POSITIONS = "checked_positions";

    public RegistrationSearchResultsRecyclerFragment() {
	}
	
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		this.activity = (RegistrationActivity) getActivity();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		rootView = inflater.inflate(R.layout.fragment_registration_search_results_list, container, false);
		addToCartButton = (Button) rootView.findViewById(R.id.add_to_cart);

		addToCartButton.setBackgroundColor(Utils.getPrimaryColor(activity));
		addToCartButton.setTextColor(Utils.getHeaderTextColor(activity));

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
		
		if (newSearch) {
			final int mCurCheckPosition = 1;
			detailBundle = null;
			Handler handler = new Handler();
	   		handler.post(new Runnable(){

				@Override
				public void run() {
					
					int positionToClick = 0;
					if (dualPane) {
						positionToClick = mCurCheckPosition;
					}
					// Reset and auto-select first on list if not empty
					// On non-dual-pane layouts we force click the header instead which
					// will scroll to the correct place and clear the selected
					if (((RegistrationRecyclerAdapter)adapter).getItemCountWithoutHeaders() > 0) {
                        recyclerView.setSelectedIndex(positionToClick);
//						listView.performItemClick(null, positionToClick,
//								listView.getAdapter().getItemId(positionToClick));
		    		}
					// After force click scroll list to top to show header
                    recyclerView.smoothScrollToPosition(0);
//					listView.smoothScrollToPosition(0);
//					listView.setSelection(0);
							
				}
	   			
	   		});
	   		newSearch = false;
		}
		
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
    public Bundle buildDetailBundle(Object... objects) {
        Bundle bundle = new Bundle();

        bundle.putString(Extra.MODULE_NAME, getEllucianActivity().moduleName);

        Section section = (Section)objects[0];
        bundle.putParcelable(RegistrationActivity.SECTION, section);
        bundle.putString(RegistrationDetailFragment.REQUESTING_LIST_FRAGMENT,
                this.getClass().getSimpleName());
        bundle.putString(RegistrationDetailFragment.REGISTRATION_MODULE_ID,
                ((EllucianActivity)getActivity()).moduleId);

        return bundle;
    }

	@Override
	public void onStart() {
		super.onStart();
		sendView("Registration search results list", getEllucianActivity().moduleName);
	}
	
	
	@Override
	public void onResume() {
		super.onResume();

        if (adapter != null) {
            ArrayList<Integer> checkedPositions = ((RegistrationRecyclerAdapter)adapter).getCheckedPositions();
            updateAddToCartButton(checkedPositions);
        } else {
            updateAddToCartButton(null);
        }

	}

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntegerArrayList(CHECKED_POSITIONS, ((RegistrationRecyclerAdapter)adapter).getCheckedPositions());
    }
		
	public void updateAddToCartButton(ArrayList<Integer> checkedList) {
        if (checkedList == null || checkedList.isEmpty()) {
            addToCartButton.setVisibility(View.GONE);
        } else {
            int numberShown = checkedList.size();
            if (numberShown > 0) {
                addToCartButton.setText(getString(R.string.label_with_count_format,
                        getString(R.string.registration_add_to_cart),
                        numberShown));
                if (!addToCartButton.isShown()) {
                    addToCartButton.setVisibility(View.VISIBLE);
                }
            } else {
                addToCartButton.setVisibility(View.GONE);
            }
        }

	}
	
	protected void setAddToCartButtonEnabled(boolean enabled) {
		addToCartButton.setEnabled(enabled);
	}
	
	public void setNewSearch(boolean value) {
		newSearch = value;
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
