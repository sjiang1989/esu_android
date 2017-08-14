/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ellucian.elluciango.R;

/**
 * Custom view that represents a course section instance, including its
 * title and time span that it occupies. Usually organized automatically by
 * {@link BlocksLayout} to match up against a {@link TimeRulerView} instance.
 */
public class BlockView extends LinearLayout {
    private long mStartTime;
    private long mEndTime;
    private int mColumn;

    public BlockView(Context context) {
    	super(context);
    }

    public BlockView(Context context, String courseLabel, String title, String location,
                     String time, long startTime, long endTime, int column) {
        super(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.course_block_layout, this, true);

        ((TextView) findViewById(R.id.course_label)).setText(courseLabel);
        ((TextView) findViewById(R.id.course_title)).setText(title);
        if (!TextUtils.isEmpty(location)) {
            ((TextView) findViewById(R.id.course_location)).setText(location);
        } else {
            findViewById(R.id.course_location).setVisibility(GONE);
        }
        if (!TextUtils.isEmpty(time)) {
            ((TextView) findViewById(R.id.course_time)).setText(time);
        } else {
            findViewById(R.id.course_time).setVisibility(GONE);
        }

        mStartTime = startTime;
        mEndTime = endTime;
        mColumn = column;
        Log.d("BlockView", "Column #:" + mColumn);

        setBackgroundResource(R.drawable.courses_block);
    }

    public long getStartTime() {
        return mStartTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public void setColumn(int column) {
        this.mColumn = column;
    }

    public int getColumn() {
        return mColumn;
    }
}
