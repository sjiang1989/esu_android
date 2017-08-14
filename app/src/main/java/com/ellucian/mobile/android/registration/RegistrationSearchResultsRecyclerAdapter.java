/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ellucian.elluciango.R;

import static com.ellucian.mobile.android.registration.RegistrationActivity.SEARCH_TAB_INDEX;


class RegistrationSearchResultsRecyclerAdapter extends RegistrationRecyclerAdapter {
    private static final String TAG = RegistrationSearchResultsRecyclerAdapter.class.getSimpleName();

    RegistrationSearchResultsRecyclerAdapter(Context context) {
        super(context, SEARCH_TAB_INDEX);
    }

    @Override
    public RecyclerView.ViewHolder onCreateHeaderViewHolder(final ViewGroup parent, int viewType) {
        View v;

        if (this.getSectionsRequiringAuthCode() > 0) {
            Log.d(TAG, "onCreateHeaderViewHolder: Using the 'auth code required' header");
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.registration_auth_required_search_header, parent, false);
        } else {
            Log.d(TAG, "onCreateHeaderViewHolder: Use the standard header");
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_header, parent, false);

        }

        return new ItemViewHolder(this, v, null);
    }

}
