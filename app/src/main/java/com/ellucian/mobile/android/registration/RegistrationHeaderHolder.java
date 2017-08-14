/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import com.ellucian.mobile.android.adapter.EllucianRecyclerAdapter;

class RegistrationHeaderHolder implements EllucianRecyclerAdapter.ItemInfoHolder {
    String headerText;
    boolean authCodeRequired = false;

    RegistrationHeaderHolder(String headerText) {
        this(headerText, false);
    }

    RegistrationHeaderHolder(String headerText, boolean authCodeRequired) {
        this.headerText = headerText;
        this.authCodeRequired = authCodeRequired;
    }

    @Override
    public String getDefaultText() {
        return headerText;
    }
}
