/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client;

import android.content.ContentProviderOperation;
import android.content.Context;

import java.util.ArrayList;

public abstract class ContentProviderOperationBuilder<E> {
    protected final Context context;
	protected ContentProviderOperationBuilder(Context context) {
		this.context = context;
	}
	
	public abstract ArrayList<ContentProviderOperation> buildOperations(E model);
	
}
