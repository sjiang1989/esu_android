/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.courses.overview;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.text.TextUtils;

import com.ellucian.mobile.android.client.ContentProviderOperationBuilder;
import com.ellucian.mobile.android.client.courses.CoursesResponse;
import com.ellucian.mobile.android.client.courses.Instructor;
import com.ellucian.mobile.android.client.courses.MeetingPattern;
import com.ellucian.mobile.android.client.courses.Section;
import com.ellucian.mobile.android.client.courses.Term;
import com.ellucian.mobile.android.provider.EllucianContract.CourseCourses;
import com.ellucian.mobile.android.provider.EllucianContract.CourseInstructors;
import com.ellucian.mobile.android.provider.EllucianContract.CoursePatterns;
import com.ellucian.mobile.android.provider.EllucianContract.CourseTerms;

import java.util.ArrayList;

public class CourseDetailsBuilder extends ContentProviderOperationBuilder<CoursesResponse> {
	
	public CourseDetailsBuilder(Context context) {
		super(context);
	}
	
	@Override
	public ArrayList<ContentProviderOperation> buildOperations(CoursesResponse response) {
		final ArrayList<ContentProviderOperation> batch = new ArrayList<>();
		
		if (response.terms != null && response.terms.length > 0) {
			Term term = response.terms[0];
			if (term.sections != null && term.sections.length > 0) {
				Section section = term.sections[0];
				String courseIdSelection = CourseCourses.COURSE_ID + " = ?";
				String[] courseIdArgs = new String[] { section.sectionId }; 
				
				// delete specific course in courses tables
				batch.add(ContentProviderOperation
						  .newDelete(CourseCourses.CONTENT_URI)
						  .withSelection(courseIdSelection, courseIdArgs)
						  .build());
				batch.add(ContentProviderOperation
						  .newDelete(CourseInstructors.CONTENT_URI)
						  .withSelection(courseIdSelection, courseIdArgs)
						  .build());
				batch.add(ContentProviderOperation
						  .newDelete(CoursePatterns.CONTENT_URI)
						  .withSelection(courseIdSelection, courseIdArgs)
						  .build());
				
				// add detailed course information
				
				batch.add(ContentProviderOperation
						.newInsert(CourseCourses.CONTENT_URI)
						.withValue(CourseCourses.COURSE_ID, section.sectionId)
						.withValue(CourseTerms.TERM_ID, term.id)
						.withValue(CourseCourses.COURSE_NAME, section.courseName)
						.withValue(CourseCourses.COURSE_TITLE, section.sectionTitle)
						.withValue(CourseCourses.COURSE_SECTION_NUMBER, section.courseSectionNumber)
						.withValue(CourseCourses.COURSE_DESCRIPTION, section.courseDescription)
						.withValue(CourseCourses.COURSE_IS_INSTRUCTOR, section.isInstructor ? 1 : 0)
						.withValue(CourseCourses.COURSE_LEARNING_PROVIDER, section.learningProvider)
						.withValue(CourseCourses.COURSE_LEARNING_PROVIDER_SITE_ID, section.learningProviderSiteId)
                        .withValue(CourseCourses.COURSE_FIRST_MEETING_DATE, section.firstMeetingDate)
                        .withValue(CourseCourses.COURSE_LAST_MEETING_DATE, section.lastMeetingDate)
						.build());
				
				for (Instructor instructor : section.instructors) {
					batch.add(ContentProviderOperation
							.newInsert(CourseInstructors.CONTENT_URI)
							.withValue(CourseCourses.COURSE_ID, section.sectionId)
							.withValue(CourseInstructors.INSTRUCTOR_ID, instructor.instructorId)
							.withValue(CourseInstructors.INSTRUCTOR_FIRST_NAME, instructor.firstName)
							.withValue(CourseInstructors.INSTRUCTOR_MIDDLE_NAME, instructor.middleInitial)
							.withValue(CourseInstructors.INSTRUCTOR_LAST_NAME, instructor.lastName)
							.withValue(CourseInstructors.INSTRUCTOR_FORMATTED_NAME, instructor.formattedName)
							.withValue(CourseInstructors.INSTRUCTOR_PRIMARY, instructor.primary ? 1 : 0)
							.build());	
				}	
				
				
				for (MeetingPattern pattern : section.meetingPatterns) {
					// Storing int[] as comma delimited string
					String daysString = "";
					for (int dayNumber : pattern.daysOfWeek) {
						daysString += "" + dayNumber + ",";
					}		
					
					String startTime = !TextUtils.isEmpty(pattern.sisStartTimeWTz) ? 
							pattern.sisStartTimeWTz : pattern.startTime;
					String endTime = !TextUtils.isEmpty(pattern.sisEndTimeWTz) ? 
							pattern.sisEndTimeWTz : pattern.endTime;
					
					batch.add(ContentProviderOperation
							.newInsert(CoursePatterns.CONTENT_URI)
							.withValue(CourseCourses.COURSE_ID, section.sectionId)
							.withValue(CoursePatterns.PATTERN_DAYS, daysString)
                            .withValue(CoursePatterns.PATTERN_START_DATE, pattern.startDate)
                            .withValue(CoursePatterns.PATTERN_END_DATE, pattern.endDate)
							.withValue(CoursePatterns.PATTERN_START_TIME, startTime)
							.withValue(CoursePatterns.PATTERN_END_TIME, endTime)
							.withValue(CoursePatterns.PATTERN_ROOM, pattern.room)
							.withValue(CoursePatterns.PATTERN_LOCATION, pattern.building)
							.withValue(CoursePatterns.PATTERN_BUILDING_ID, pattern.buildingId)
							.withValue(CoursePatterns.PATTERN_CAMPUS_ID, pattern.campusId)
                            .withValue(CoursePatterns.PATTERN_CAMPUS_NAME, pattern.campus)
                            .withValue(CoursePatterns.PATTERN_INSTRUCTIONAL_METHOD, pattern.instructionalMethodCode)
							.build());
				}
			}
		}

		return batch;
		
	}
}
