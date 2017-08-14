// Copyright 2014-2016 Ellucian Company L.P and its affiliates.

package com.ellucian.mobile.android.client.registration;

import android.os.Parcel;
import android.os.Parcelable;

import com.ellucian.mobile.android.adapter.EllucianRecyclerAdapter;
import com.ellucian.mobile.android.client.courses.Instructor;
import com.ellucian.mobile.android.client.courses.MeetingPattern;

public class Section implements Parcelable, EllucianRecyclerAdapter.ItemInfoHolder  {
	public static final String GRADING_TYPE_AUDIT = "Audit";
	public static final String GRADING_TYPE_GRADED = "Graded";
	public static final String GRADING_TYPE_PASS_FAIL = "PassFail";
	public static final String VARIABLE_OPERATOR_TO = "TO";
	public static final String VARIABLE_OPERATOR_OR = "OR";
	public static final String VARIABLE_OPERATOR_INC = "INC";
	public static final String CLASSIFICATION_PLANNED = "planned";
	public static final String CLASSIFICATION_REGISTERED = "registered";

    public String planId;
	public String termId;
    public String termName;
	public String sectionId;
	private String courseId;
	public String sectionTitle;
	public String courseName;
	public String courseDescription;
	public String courseSectionNumber;
	public String firstMeetingDate;
	public String lastMeetingDate;
	public float credits;
	public float ceus;
	private String status;
	public String gradingType;
	public Instructor[] instructors;
	public MeetingPattern[] meetingPatterns;
	public String classification;
	public float minimumCredits;
	public float maximumCredits;
	public float variableCreditIncrement;
	public String variableCreditOperator;
	private boolean allowPassNoPass;
	private boolean allowAudit;
	private boolean onlyPassNoPass;
	public float selectedCredits = -1;
	public String location;
	public String[] academicLevels;
    public Integer capacity;
    public Integer available;
    public boolean authorizationCodeRequired;
    public String authorizationCodePresented;
    private boolean checkboxSelected;

	public Section() {
	}
	
    @Override
    public String getDefaultText() {
        return sectionTitle;
    }

	public Section(Parcel in) { 
		readFromParcel(in);
	}
	
	private void readFromParcel(Parcel in) {
        planId = in.readString();
		termId = in.readString();
        termName = in.readString();
		sectionId = in.readString();
		courseId = in.readString();
		sectionTitle = in.readString();
		courseName = in.readString();
		courseDescription = in.readString();
		courseSectionNumber = in.readString();
		firstMeetingDate = in.readString();
		lastMeetingDate = in .readString();
		credits = in.readFloat();
		ceus = in.readFloat();
		status = in.readString();
		gradingType = in.readString();
		instructors = in.createTypedArray(Instructor.CREATOR);
		meetingPatterns = in.createTypedArray(MeetingPattern.CREATOR);
		classification = in.readString();
		minimumCredits = in.readFloat();
		maximumCredits = in.readFloat();
		variableCreditIncrement = in.readFloat();
		variableCreditOperator = in.readString();
		allowPassNoPass = in.readInt() == 1 ? true : false;
		allowAudit = in.readInt() == 1 ? true : false;
		onlyPassNoPass = in.readInt() == 1 ? true : false;
		selectedCredits = in.readFloat();
		location = in.readString();
		academicLevels = in.createStringArray();
        capacity = (Integer) in.readSerializable();
        available = (Integer) in.readSerializable();
        authorizationCodeRequired = in.readInt() == 1 ? true : false;
        authorizationCodePresented = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(planId);
        dest.writeString(termId);
        dest.writeString(termName);
		dest.writeString(sectionId);
		dest.writeString(courseId);
		dest.writeString(sectionTitle);
		dest.writeString(courseName);
		dest.writeString(courseDescription);
		dest.writeString(courseSectionNumber);
		dest.writeString(firstMeetingDate);
		dest.writeString(lastMeetingDate);
		dest.writeFloat(credits);
		dest.writeFloat(ceus);
		dest.writeString(status);
		dest.writeString(gradingType);
		dest.writeTypedArray(instructors, flags);
		dest.writeTypedArray(meetingPatterns, flags);
		dest.writeString(classification);
		dest.writeFloat(minimumCredits);
		dest.writeFloat(maximumCredits);
		dest.writeFloat(variableCreditIncrement);
		dest.writeString(variableCreditOperator);
		dest.writeInt(allowPassNoPass ? 1 : 0);
		dest.writeInt(allowAudit ? 1 : 0);
		dest.writeInt(onlyPassNoPass ? 1 : 0);
		dest.writeFloat(selectedCredits);
		dest.writeString(location);
		dest.writeStringArray(academicLevels);
        dest.writeSerializable(capacity);
        dest.writeSerializable(available);
        dest.writeInt(authorizationCodeRequired ? 1 : 0);
        dest.writeString(authorizationCodePresented);
		
	}
	
	public static final Parcelable.Creator<Section> CREATOR = new Parcelable.Creator<Section>() { 
		public Section createFromParcel(Parcel in) { 
			return new Section(in); 
		}   
		
		public Section[] newArray(int size) { 
			return new Section[size]; 
		}
	};

    public boolean isCheckboxSelected() {
        return checkboxSelected;
    }

    public void setCheckboxSelected(boolean checkboxSelected) {
        this.checkboxSelected = checkboxSelected;
    }

}

