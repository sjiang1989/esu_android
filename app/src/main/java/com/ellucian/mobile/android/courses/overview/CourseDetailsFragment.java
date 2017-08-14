/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.courses.overview;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.EllucianFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.provider.EllucianContract.CourseCourses;
import com.ellucian.mobile.android.provider.EllucianContract.CourseInstructors;
import com.ellucian.mobile.android.provider.EllucianContract.CoursePatterns;
import com.ellucian.mobile.android.util.CalendarUtils;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.Utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class CourseDetailsFragment extends EllucianFragment implements
		LoaderManager.LoaderCallbacks<Cursor> {
	
	private static final String TAG = CourseDetailsFragment.class.getSimpleName();
	private Activity activity;
	private String courseId;
	private View rootView;
	private TextView titleTextView;
	private TextView datesTextView;
	private TextView descriptionTextView;
    private int primaryColor;

	private final int PATTERNS_LOADER = 1;
	private final int INSTRUCTORS_LOADER = 2;
	private final int COURSE_LOADER = 3;

    private String sectionStartDate = "";
    private String sectionEndDate = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		activity = getActivity();

        primaryColor = Utils.getPrimaryColor(activity);

		courseId = getActivity().getIntent().getStringExtra(Extra.COURSES_COURSE_ID);

		LoaderManager manager = getLoaderManager();
		manager.initLoader(INSTRUCTORS_LOADER, null, this);
		manager.initLoader(COURSE_LOADER, null, this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		rootView = inflater.inflate(R.layout.fragment_course_details,
				container, false);
		titleTextView = (TextView) rootView.findViewById(R.id.course_details_title);
		datesTextView = (TextView) rootView.findViewById(R.id.course_details_dates);
		descriptionTextView = (TextView) rootView.findViewById(R.id.course_details_course_description);
		return rootView;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		if (id == PATTERNS_LOADER) {
			loader = new CursorLoader(getActivity(), CoursePatterns.CONTENT_URI, null,
					CourseCourses.COURSE_ID + " = ?", new String[] { courseId }, null);
		} else if (id == INSTRUCTORS_LOADER) {
			loader = new CursorLoader(getActivity(), CourseInstructors.CONTENT_URI, null,
					CourseCourses.COURSE_ID + " = ?", new String[] { courseId }, null);
		} else if (id == COURSE_LOADER) {
			Uri courseUrl = CourseCourses.buildCourseUri(courseId);
			loader = new CursorLoader(getActivity(), courseUrl, null, null,
					null, null);
		}
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) {
			return;
		}

		if (loader.getId() == PATTERNS_LOADER) {
			onPatternsQueryComplete(cursor);
		} else if (loader.getId() == INSTRUCTORS_LOADER) {
			onInstructorsQueryComplete(cursor);
		} else if (loader.getId() == COURSE_LOADER) {
			onCourseQueryComplete(cursor);
            getLoaderManager().initLoader(PATTERNS_LOADER, null, this);
        } else {
			cursor.close();
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}
	
	private void onPatternsQueryComplete(Cursor cursor) {
		final LinearLayout meetingLayout = (LinearLayout) rootView.findViewById(R.id.course_details_meetings_layout);
		meetingLayout.removeAllViews();
		final LayoutInflater inflater = getActivity().getLayoutInflater();
		
		final SimpleDateFormat defaultTimeParserFormat = new SimpleDateFormat("HH:mm'Z'", Locale.US); //for dates	
		defaultTimeParserFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		final SimpleDateFormat altTimeParserFormat = new SimpleDateFormat("HH:mm", Locale.US); //for dates	
		final DateFormat timeFormatter = android.text.format.DateFormat.getTimeFormat(getActivity());
		
		if (cursor.moveToFirst()) {
            int count = 0;
            rootView.findViewById(R.id.course_details_no_meetings).setVisibility(View.GONE);
			do {
                count++;
				final String daysOfWeek = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_DAYS));
                final String startDate = cursor.getString(cursor
                        .getColumnIndex(CoursePatterns.PATTERN_START_DATE));
                final String endDate = cursor.getString(cursor
                        .getColumnIndex(CoursePatterns.PATTERN_END_DATE));
				final String startTime = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_START_TIME));
				final String endTime = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_END_TIME));
				final String location = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_LOCATION));
				final String room = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_ROOM));
				final String buildingId = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_BUILDING_ID));
				final String instructionalMethod = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_INSTRUCTIONAL_METHOD));
				final String campusId = cursor.getString(cursor
						.getColumnIndex(CoursePatterns.PATTERN_CAMPUS_ID));
                final String campusName = cursor.getString(cursor
                        .getColumnIndex(CoursePatterns.PATTERN_CAMPUS_NAME));

				// Converting dates to correct format for display
				Date startTimeDate = null;
				Date endTimeDate = null;
				String displayStartTime = "";
				String displayEndTime = "";

				try {
					if (!TextUtils.isEmpty(startTime) && startTime.contains(" ")) {
						String[] splitTimeAndZone = startTime.split(" ");
						String time = splitTimeAndZone[0];
						String timeZone = splitTimeAndZone[1];
						altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
						startTimeDate = altTimeParserFormat.parse(time);
					} else {
						startTimeDate = defaultTimeParserFormat.parse(startTime);
					}
					
					if (!TextUtils.isEmpty(endTime) && endTime.contains(" ")) {
						String[] splitTimeAndZone = endTime.split(" ");
						String time = splitTimeAndZone[0];
						String timeZone = splitTimeAndZone[1];
						altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
						endTimeDate = altTimeParserFormat.parse(time);
					} else {
						endTimeDate = defaultTimeParserFormat.parse(endTime);
					}
										
					
					if (startTimeDate != null) {
						displayStartTime = timeFormatter.format(startTimeDate);
					}	
					if (endTimeDate != null) {
						displayEndTime = timeFormatter.format(endTimeDate);
					}
				} catch (ParseException e) {
					e.printStackTrace();
				}
				
				// Getting correct days to show
				String[] days = daysOfWeek.split(",");
				String displayDaysOfWeek = "";
                try {
                    for (String day : days) {
                        int dayNumber = Integer.parseInt(day);
                        if (!TextUtils.isEmpty(displayDaysOfWeek)) {
                            displayDaysOfWeek += ", ";
                        }
                        // Adding 1 to number to make the Calendar constants
                        displayDaysOfWeek += CalendarUtils.getDayShortName(dayNumber);
                    }
                    displayDaysOfWeek += ": ";
                } catch (Exception e) {
                    Log.e("CourseDetailsFragment", e.getMessage());
                }
				
                DateFormat localFormat = android.text.format.DateFormat.getDateFormat(getActivity());
                DateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                String meetingStartDate = "";
                String meetingEndDate = "";
                if (startDate != null) {
                    try {
                        Date start = dateFormat.parse(startDate);
                        meetingStartDate = localFormat.format(start);
                    } catch (ParseException e) {
                        Log.e(TAG, "onPatternsQueryComplete: errorMessage:" + e.getMessage());
                    }
                }
                if (endDate != null) {
                    try {
                        Date end = dateFormat.parse(endDate);
                        meetingEndDate = localFormat.format(end);
                    } catch (ParseException e) {
                        Log.e(TAG, "onPatternsQueryComplete: errorMessage:" + e.getMessage());
                    }
                }

                // If this meeting has a different date range than the section,
                // we want to display it.
                String displayDateRange;
                if (TextUtils.equals(meetingStartDate, sectionStartDate)
                        && TextUtils.equals(meetingEndDate, sectionEndDate)) {
                    // The same.
                    displayDateRange = "";
                } else {
                    if (TextUtils.equals(meetingStartDate, meetingEndDate)) {
                        displayDateRange = meetingStartDate;
                    } else {
                        displayDateRange = getString(R.string.date_to_date_format,
                                meetingStartDate,
                                meetingEndDate);
                    }
                }

				final LinearLayout meetingRow = (LinearLayout)inflater.inflate(
						R.layout.course_details_meeting_row, meetingLayout,
						false);

                final ImageView dividerLine = (ImageView) meetingRow.findViewById(R.id.divider_line);
                if (count > 1) dividerLine.setVisibility(View.VISIBLE);

				final TextView daysView = (TextView) meetingRow
						.findViewById(R.id.course_details_meeting_row_days);
				daysView.setText(displayDaysOfWeek);

                String displayDateTimeType;

				// Date & Time
                if (!TextUtils.isEmpty(displayStartTime)) {
					String displayTimeRange = getString(R.string.time_to_time_format,
										displayStartTime, 
										displayEndTime);
                    displayDateTimeType = getString(R.string.course_details_date_time,
                            displayDateRange,
                            displayTimeRange);
				} else {
                    displayDateTimeType = displayDateRange;
                }

				if (!TextUtils.isEmpty(instructionalMethod)) {
                    displayDateTimeType = getString(R.string.course_details_date_time_type,
                            displayDateTimeType,
                            instructionalMethod);
				}

                final TextView dateTimeView = (TextView) meetingRow
                        .findViewById(R.id.course_details_meeting_date_time_type);
                dateTimeView.setText(displayDateTimeType);

                final RelativeLayout locationView = (RelativeLayout) meetingRow.
                        findViewById(R.id.course_details_meeting_row_location);
				final TextView locationTextView = (TextView) meetingRow
						.findViewById(R.id.course_details_meeting_row_location_txt);
				String locationString = "";
				if (!TextUtils.isEmpty(location)) {
									
					if (!TextUtils.isEmpty(room)) {
				
						locationString = getString(R.string.default_building_and_room_format,
												location,
												room);
					} else {
						locationString = location;
					}
				} 
				
				
				if (!TextUtils.isEmpty(locationString)) {
					locationTextView.setText(locationString);
					// Show underline of text
                    locationTextView.setTextColor(primaryColor);
					locationTextView.setPaintFlags(locationTextView.getPaintFlags() |   Paint.UNDERLINE_TEXT_FLAG);
                    ((ImageView)meetingRow.findViewById(R.id.course_details_meeting_row_location_image))
                            .setColorFilter(primaryColor);
                } else {
					locationView.setVisibility(View.GONE);
				}

                if (buildingId != null && location != null) {
                    meetingRow.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_LIST_SELECT, "Map Detail", null, getEllucianActivity().moduleName);
                            ((CourseOverviewActivity) activity).openBuildingDetail(buildingId, location);
                        }
                    });
                }

                TextView campusText = (TextView) meetingRow
                        .findViewById(R.id.course_details_meeting_row_campus);
                if (!TextUtils.isEmpty(campusName)) {
                    campusText.setText(campusName);
                } else if (!TextUtils.isEmpty(campusId)) {
                    campusText.setText(campusId);
                } else {
                    campusText.setVisibility(View.GONE);
                }
				
				/*
				//TODO l10n days of the week and the labels
				if(daysOfWeek.contains("Sunday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day0)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				} if(daysOfWeek.contains("Monday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day1)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				} if(daysOfWeek.contains("Tuesday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day2)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO)
				} if(daysOfWeek.contains("Wednesday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day3)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				} if(daysOfWeek.contains("Thursday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day4)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				} if(daysOfWeek.contains("Friday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day5)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				} if(daysOfWeek.contains("Saturday")) {
					((TextView) meetingRow
					.findViewById(R.id.courses_detail_meeting_pattern_row_day6)).setBackgroundColor(getResources().getColor(R.color.header_color));//TODO
				}
				*/

				meetingLayout.addView(meetingRow);
			} while (cursor.moveToNext());
		}
	}

	private void onInstructorsQueryComplete(Cursor cursor) {
		
		final LinearLayout facultyLayout = (LinearLayout) rootView
				.findViewById(R.id.course_details_faculty_layout);
		facultyLayout.removeAllViews();
		final LayoutInflater inflater = getActivity().getLayoutInflater();
		
		if (cursor.moveToFirst()) {
			rootView.findViewById(R.id.course_details_faculty_title).setVisibility(View.VISIBLE);
			do {
                rootView.findViewById(R.id.course_details_no_faculty).setVisibility(View.GONE);
				final String instructorName = cursor.getString(cursor
						.getColumnIndex(CourseInstructors.INSTRUCTOR_FORMATTED_NAME));

				final View instructorView = inflater.inflate(
						R.layout.course_details_faculty_row, facultyLayout,
						false);
				final TextView instructorNameView = (TextView) instructorView
						.findViewById(R.id.course_details_instructor_name);
				instructorNameView.setText(instructorName);
                instructorNameView.setTextColor(primaryColor);
				// Show underline of text
				instructorNameView.setPaintFlags(instructorNameView.getPaintFlags() |   Paint.UNDERLINE_TEXT_FLAG);

				facultyLayout.addView(instructorView);
			} while (cursor.moveToNext());
		}
		
	}
	
    private void onCourseQueryComplete(Cursor cursor) {
        if (!cursor.moveToFirst()) {
            return;
        }
        
        titleTextView.setText( cursor.getString(cursor.getColumnIndex(CourseCourses.COURSE_TITLE)));
        descriptionTextView.setText( cursor.getString(cursor.getColumnIndex(CourseCourses.COURSE_DESCRIPTION)));
        String startDate = cursor.getString( cursor.getColumnIndex(CourseCourses.COURSE_FIRST_MEETING_DATE));
        String endDate = cursor.getString( cursor.getColumnIndex(CourseCourses.COURSE_LAST_MEETING_DATE));
        DateFormat fromDatabase = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            Date start = fromDatabase.parse(startDate);
            Date end = fromDatabase.parse(endDate);
            DateFormat localFormat = android.text.format.DateFormat.getDateFormat(getActivity());
            sectionStartDate = localFormat.format(start);
            sectionEndDate = localFormat.format(end);
        } catch (Exception e) {
            Log.e(TAG, "onCourseQueryComplete: errorMessage:" + e.getMessage());
        }

        datesTextView.setText(getString(R.string.date_to_date_format,
                sectionStartDate,
                sectionEndDate));

    }

	@Override
	public void onStart() {
		super.onStart();
		sendView("Course Overview", getEllucianActivity().moduleName);
	}
    
    
}
