/*
 * Copyright 2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.adapter.SectionedItemHolderRecyclerAdapter;
import com.ellucian.mobile.android.client.courses.Instructor;
import com.ellucian.mobile.android.client.courses.MeetingPattern;
import com.ellucian.mobile.android.client.registration.Section;
import com.ellucian.mobile.android.util.CalendarUtils;
import com.ellucian.mobile.android.util.VersionSupportUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.ellucian.mobile.android.registration.RegistrationActivity.CART_TAB_INDEX;
import static com.ellucian.mobile.android.registration.RegistrationActivity.SEARCH_TAB_INDEX;


class RegistrationRecyclerAdapter extends SectionedItemHolderRecyclerAdapter {
    private static final String TAG = RegistrationRecyclerAdapter.class.getSimpleName();

    private SimpleDateFormat altTimeParserFormat;
    private SimpleDateFormat defaultTimeParserFormat;
    private DateFormat timeFormatter;
    private ArrayList<Integer> checkedPositions = new ArrayList<>();
    private final List<OnCheckBoxClickedListener> listenerList = new ArrayList<>();
    private int sectionsRequiringAuthCode = 0;
    private int tabIndex;

    RegistrationRecyclerAdapter(Context context, int tabIndex) {
        this(context, tabIndex, R.layout.list_header);
    }

    private RegistrationRecyclerAdapter(Context context, int tabIndex, int headerLayoutResId) {
        super(context, headerLayoutResId);
        this.tabIndex = tabIndex;
        this.defaultTimeParserFormat = new SimpleDateFormat("HH:mm'Z'", Locale.US);
        this.defaultTimeParserFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.altTimeParserFormat = new SimpleDateFormat("HH:mm", Locale.US);
        this.timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
    }

    interface OnCheckBoxClickedListener {
        void onCheckBoxClicked(CheckBox checkBox, boolean isChecked, int position);
    }

    void registerOnCheckBoxClickedListener(OnCheckBoxClickedListener value) {
        listenerList.add(value);
    }

    @Override
    public ItemViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
        View v;

        v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.registration_list_checkbox_row, parent, false);

        return new ItemViewHolder(this, v, onItemClickListener);
    }

    public void onBindHeaderViewHolder(RecyclerView.ViewHolder holder, int position) {
        RegistrationHeaderHolder headerHolder = (RegistrationHeaderHolder) getItem(position);
        TextView headerText = (TextView) ((ItemViewHolder)holder).itemView.findViewById(R.id.list_header_title);
        headerText.setText(headerHolder.headerText);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindItemViewHolder(RecyclerView.ViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        Object object = getItem(position);
        final Section section = (Section) object;
        View view = holder.itemView;

        // Reset views to visible for next view
        view.findViewById(R.id.instructor_credits_separator).setVisibility(View.VISIBLE);

        CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkbox);

        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBox checkBox = (CheckBox)v;
                boolean checked = checkBox.isChecked();
                if (checked) {
                    checkedPositions.add(Integer.valueOf(position));
                    section.setCheckboxSelected(true);
                } else {
                    checkedPositions.remove(Integer.valueOf(position));
                    section.setCheckboxSelected(false);
                }
                for (OnCheckBoxClickedListener listener : listenerList) {
                    listener.onCheckBoxClicked(checkBox, checked, position);
                }
            }
        });

        checkBox.setChecked(section.isCheckboxSelected());

        if (!TextUtils.isEmpty(section.courseName)) {
            String titleString = section.courseName;
            if (!TextUtils.isEmpty(section.courseSectionNumber)) {
                titleString = context.getString(R.string.default_course_section_format,
                        section.courseName,
                        section.courseSectionNumber);
            }
            TextView courseNameView = (TextView) view.findViewById(R.id.course_name);
            courseNameView.setText(titleString);
        }

        // For Search Tab Only
        if (tabIndex == SEARCH_TAB_INDEX) {
            String academicLevels = "";
            if (section.academicLevels != null && section.academicLevels.length > 0) {
                academicLevels = TextUtils.join(",", section.academicLevels);
            }

            String levelAndLocationText = "";
            TextView levelAndLocationView = (TextView) view.findViewById(R.id.academic_level_and_location);
            if (!TextUtils.isEmpty(academicLevels) && !TextUtils.isEmpty(section.location)) {
                levelAndLocationText = context.getString(R.string.default_two_string_separator_format,
                        academicLevels,
                        section.location);

            } else if (!TextUtils.isEmpty(academicLevels)) {
                levelAndLocationText = academicLevels;
            } else if (!TextUtils.isEmpty(section.location)) {
                levelAndLocationText = section.location;
            }

            levelAndLocationView.setText(levelAndLocationText);

            // Available/Capacity images & text
            if (section.available != null && section.capacity != null) {
                String capacityText = context.getString(R.string.remaining_capacity,
                        section.available,
                        section.capacity);
                Drawable meterImage = null;
                meterImage = RegistrationDetailFragment.getMeterImage(section, context);

                RelativeLayout seatsAvailView = (RelativeLayout) view.findViewById(R.id.seats_available_box);
                seatsAvailView.setVisibility(View.VISIBLE);
                if (meterImage != null) {
                    VersionSupportUtils.enableMirroredDrawable(meterImage);
                    ((ImageView) seatsAvailView.findViewById(R.id.seats_available_image)).setImageDrawable(meterImage);
                }
                ((TextView) seatsAvailView.findViewById(R.id.seats_available_text)).setText(capacityText);
            }

            // Authorization Code
            handleAuthorizationCode(section, view);
        }

        // For Cart Tab Only
        if (tabIndex == CART_TAB_INDEX) {
            handleAuthorizationCode(section, view);
        }

        if (!TextUtils.isEmpty(section.sectionTitle)) {
            TextView sectionTitleView = (TextView) view.findViewById(R.id.section_title);
            sectionTitleView.setText(section.sectionTitle);
        }

        TextView instructorView = (TextView) view.findViewById(R.id.instructor);
        if (section.instructors != null && section.instructors.length != 0) {
            String instructorNames = "";
            for (Instructor instructor : section.instructors) {

                if (!TextUtils.isEmpty(instructorNames)) {
                    instructorNames += " ; ";
                }

                if (!TextUtils.isEmpty(instructor.lastName)) {
                    String shortName = "";
                    if (!TextUtils.isEmpty(instructor.firstName)) {
                        shortName += context.getString(R.string.default_last_name_first_initial_format,
                                instructor.lastName,
                                instructor.firstName.charAt(0));
                    } else {
                        shortName = instructor.lastName;
                    }
                    instructorNames += shortName;
                } else if (!TextUtils.isEmpty(instructor.formattedName)) {
                    instructorNames += instructor.formattedName;
                }

            }

            instructorView.setText(instructorNames);
        } else {
            view.findViewById(R.id.instructor_credits_separator).setVisibility(View.GONE);
        }

        TextView creditsView = (TextView) view.findViewById(R.id.credits);
        String creditsString = "";

        if (section.selectedCredits != -1) {

            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        section.selectedCredits,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        section.selectedCredits,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = context.getString(R.string.registration_row_credits_format,
                        section.selectedCredits,
                        context.getString(R.string.registration_credits));
            }

        } else if (section.credits != 0) {

            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        section.credits,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        section.credits,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = context.getString(R.string.registration_row_credits_format,
                        section.credits,
                        context.getString(R.string.registration_credits));
            }

        } else if ((!TextUtils.isEmpty(section.variableCreditOperator) && section.variableCreditOperator.equals(Section.VARIABLE_OPERATOR_OR))
                || section.minimumCredits != 0) {

            if (section.maximumCredits != 0) {
                creditsString = context.getString(R.string.registration_row_credits_min_max_format,
                        section.minimumCredits,
                        section.maximumCredits,
                        context.getString(R.string.registration_credits));
            } else {
                creditsString = context.getString(R.string.registration_row_credits_format,
                        section.minimumCredits,
                        context.getString(R.string.registration_credits));
            }

        } else if (section.ceus != 0) {
            creditsString = context.getString(R.string.registration_row_credits_format,
                    section.ceus,
                    context.getString(R.string.registration_ceus));
        } else {
            // Only want to display zero in last possible case to avoid not showing the correct alternative
            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        0f,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = context.getString(R.string.registration_row_credits_with_type_format,
                        0f,
                        context.getString(R.string.registration_credits),
                        context.getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = context.getString(R.string.registration_row_credits_format,
                        0f,
                        context.getString(R.string.registration_credits));
            }
        }
        creditsView.setText(creditsString);

        TextView meetingsTypeView = (TextView) view.findViewById(R.id.meetings_and_type);
        if (section.meetingPatterns != null && section.meetingPatterns.length != 0) {

            String meetingsString = "";

            for (MeetingPattern pattern : section.meetingPatterns) {

                if (!TextUtils.isEmpty(meetingsString)) {
                    meetingsString += " ; ";
                }

                String daysString = "";
                if (pattern.daysOfWeek != null && pattern.daysOfWeek.length != 0) {

                    for (int dayNumber : pattern.daysOfWeek) {

                        if (!TextUtils.isEmpty(daysString)) {
                            daysString += ", ";
                        }
                        // Adding 1 to number to make the Calendar constants
                        daysString += CalendarUtils.getDayShortName(dayNumber);
                    }

                }

                Date startTimeDate = null;
                Date endTimeDate = null;
                String displayStartTime = "";
                String displayEndTime = "";

                try {
                    if (!TextUtils.isEmpty(pattern.sisStartTimeWTz) && pattern.sisStartTimeWTz.contains(" ")) {
                        String[] splitTimeAndZone = pattern.sisStartTimeWTz.split(" ");
                        String time = splitTimeAndZone[0];
                        String timeZone = splitTimeAndZone[1];
                        altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
                        startTimeDate = altTimeParserFormat.parse(time);
                    } else if (!TextUtils.isEmpty(pattern.startTime)) {
                        startTimeDate = defaultTimeParserFormat.parse(pattern.startTime);
                    }

                    if (!TextUtils.isEmpty(pattern.sisEndTimeWTz) && pattern.sisEndTimeWTz.contains(" ")) {
                        String[] splitTimeAndZone = pattern.sisEndTimeWTz.split(" ");
                        String time = splitTimeAndZone[0];
                        String timeZone = splitTimeAndZone[1];
                        altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
                        endTimeDate = altTimeParserFormat.parse(time);
                    } else if (!TextUtils.isEmpty(pattern.endTime)) {
                        endTimeDate = defaultTimeParserFormat.parse(pattern.endTime);
                    }

                    if (startTimeDate != null) {
                        displayStartTime = timeFormatter.format(startTimeDate);
                    }
                    if (endTimeDate != null) {
                        displayEndTime = timeFormatter.format(endTimeDate);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "ParseException: ", e);
                }

                if (!TextUtils.isEmpty(displayStartTime)) {

                    if (!TextUtils.isEmpty(pattern.instructionalMethodCode)) {
                        meetingsString += context.getString(R.string.default_meeting_days_times_and_type_format,
                                daysString,
                                displayStartTime,
                                displayEndTime,
                                pattern.instructionalMethodCode);
                    } else {
                        meetingsString += context.getString(R.string.default_meeting_days_and_times_format,
                                daysString,
                                displayStartTime,
                                displayEndTime);
                    }

                } else {
                    meetingsString += daysString;
                }

            }

            if (!TextUtils.isEmpty(meetingsString)) {
                meetingsTypeView.setText(meetingsString);
            }
        } else {
            meetingsTypeView.setVisibility(View.GONE);
        }

    }

    ArrayList<Integer> getCheckedPositions() {
        return checkedPositions;
    }

    void setCheckedPositions(ArrayList<Integer> checkedPositions) {
        this.checkedPositions = checkedPositions;
    }

    void clearCheckedPositions() {
        for (Integer position : checkedPositions) {
            Section section = (Section) getItem(position);
            section.setCheckboxSelected(false);
        }
        checkedPositions = new ArrayList<>();
    }

    void uncheckCheckbox(int position) {
        checkedPositions.remove(Integer.valueOf(position));
        Section section = (Section) getItem(position);
        section.setCheckboxSelected(false);
        notifyDataSetChanged();
    }

    void updateCheckedPositions() {
        ArrayList<Integer> updatedCheckedPositions = new ArrayList<>();
        for (int i=0; i < getItemCount(); i++) {
            Object o = getItem(i);
            if (o instanceof Section) {
                Section section = (Section) o;
                if (section.isCheckboxSelected()) {
                    updatedCheckedPositions.add(i);
                }
            }
        }
        setCheckedPositions(updatedCheckedPositions);
    }

    int getSectionsRequiringAuthCode() {
        return sectionsRequiringAuthCode;
    }

    void setSectionsRequiringAuthCode(int sectionsRequiringAuthCode) {
        this.sectionsRequiringAuthCode = sectionsRequiringAuthCode;
    }

    private void handleAuthorizationCode(Section section, View view) {
        if (section.authorizationCodeRequired) {
            LinearLayout ll = (LinearLayout) view.findViewById(R.id.registration_row_layout);
            ll.setBackgroundColor(VersionSupportUtils.getColorHelper(view, R.color.status_important_text_light_color));
        }
    }

}
