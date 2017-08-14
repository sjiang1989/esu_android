/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ellucian.elluciango.R;

import java.util.ArrayList;

import static com.ellucian.mobile.android.registration.RegistrationActivity.CART_TAB_INDEX;


class RegistrationCartRecyclerAdapter extends RegistrationRecyclerAdapter {
    private static final String TAG = RegistrationCartRecyclerAdapter.class.getSimpleName();

    private final static int TYPE_SECTION_HEADER_AUTH_CODE_REQUIRED = 2;

    RegistrationCartRecyclerAdapter(Context context) {
        super(context, CART_TAB_INDEX);
    }

    @Override
    public int getItemViewType(int position) {
        for(int i = 0; i < this.headers.size(); i++) {
            ArrayList<? extends ItemInfoHolder> section = sections.get(i);
            int size = section.size() + 1;

            // check if position inside this section
            if (position == 0) {
                RegistrationHeaderHolder header = (RegistrationHeaderHolder) headers.get(i);
                if (header.authCodeRequired) {
                    return TYPE_SECTION_HEADER_AUTH_CODE_REQUIRED;
                } else {
                    return TYPE_SECTION_HEADER;
                }
            }

            if (position < size)
                return TYPE_SECTION_ITEM;

            // otherwise jump into next section
            position -= size;
        }
        return -1;
    }

    @Override
    public boolean isClickable(int position) {
        if (getItemViewType(position) == TYPE_SECTION_ITEM) {
            return true;
        } else {
            return false;
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view depending on the type
        if (viewType == TYPE_SECTION_ITEM) {
            return onCreateItemViewHolder(parent, viewType);
        } else {
            return onCreateHeaderViewHolder(parent, viewType);
        }

    }

    @Override
    public RecyclerView.ViewHolder onCreateHeaderViewHolder(final ViewGroup parent, int viewType) {
        View v;

        if (viewType == TYPE_SECTION_HEADER_AUTH_CODE_REQUIRED) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.registration_auth_required_cart_header, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_header, parent, false);

        }

        return new ItemViewHolder(this, v, null);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        if (getItemViewType(position) == TYPE_SECTION_ITEM) {
            checkViewForSelection(((ItemViewHolder)holder).itemView, position);
            onBindItemViewHolder(holder, position);
        } else {
            onBindHeaderViewHolder(holder, position);
        }
    }

}
