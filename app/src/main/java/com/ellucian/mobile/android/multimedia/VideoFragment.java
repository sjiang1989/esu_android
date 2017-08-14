/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.multimedia;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.DrawerLayoutHelper;
import com.ellucian.mobile.android.app.EllucianFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.util.CustomToast;
import com.ellucian.mobile.android.util.Extra;

public class VideoFragment extends EllucianFragment implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, DrawerLayoutHelper.DrawerListener   {

    private static final String TAG = VideoFragment.class.getSimpleName();
    private static final String CURRENT_POSITION = "current_position";
    private static final String WAS_PLAYING = "media_was_playing";

    private View rootView;
    private VideoView videoView;
	private MediaController mediaController;
	private CustomToast loadingMessage;
	private int currentPosition;
    private boolean wasPlaying;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_video, container, false);
        return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

        videoView = (VideoView) rootView.findViewById(R.id.video);

        mediaController = new MediaController(getActivity());

        videoView.setOnCompletionListener(this);
        videoView.setOnPreparedListener(this);
        videoView.setMediaController(mediaController);

        Intent activityIntent = getActivity().getIntent();

        String description = activityIntent.getStringExtra(Extra.CONTENT);

        TextView descriptionView = (TextView) rootView.findViewById(R.id.description);
        if (descriptionView != null) {
            if (!TextUtils.isEmpty(description)) {
                descriptionView.setText(description);
            } else {
                descriptionView.setVisibility(View.GONE);
            }
        }

        String videoUrl = activityIntent.getStringExtra(Extra.VIDEO_URL);

        Uri videoUri = Uri.parse(videoUrl);
        videoView.setVideoURI(videoUri);

        if ( savedInstanceState != null ) {
            if (savedInstanceState.containsKey(CURRENT_POSITION) &&
                    savedInstanceState.getInt(CURRENT_POSITION) > 0) {
                currentPosition = savedInstanceState.getInt(CURRENT_POSITION);
            }
            wasPlaying = savedInstanceState.getBoolean(WAS_PLAYING, false);
        }

		loadingMessage = new CustomToast(getActivity(), getString(R.string.loading_message));
		loadingMessage.setDuration(30);
		loadingMessage.setGravity(Gravity.CENTER, 0, 0);
		loadingMessage.show();
		
		getEllucianActivity().getDrawerLayoutHelper().setDrawerListener(this);
	}

    @Override
    public void onStart() {
        super.onStart();
        sendView("Video", getEllucianActivity().moduleName);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // If Menu drawer open close in landscape mode
            if (getEllucianActivity().getDrawerLayoutHelper().isDrawerOpen()) {
                getEllucianActivity().getDrawerLayoutHelper().closeDrawer();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(WAS_PLAYING, videoView.isPlaying());
        outState.putInt(CURRENT_POSITION, videoView.getCurrentPosition());
    }

    @Override
    public void onStop() {
        super.onStop();

        // makes sure toast closed
        if (loadingMessage != null) {
            loadingMessage.cancel();
        }

        mediaController.hide();
        videoView.stopPlayback();
    }

	/** MediaPlayer methods */

	@Override
	public void onCompletion(MediaPlayer arg0) {
		sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Play button pressed", null, getEllucianActivity().moduleName);
		
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		loadingMessage.cancel();
		
        if (!mediaController.isShowing()) {
            mediaController.show(0);
        }

        videoView.seekTo(currentPosition);
        if (wasPlaying) {
            Log.d(TAG, "onPrepared: restart video at " + currentPosition);
            videoView.start();
        }
	}
	
	/** DrawerLayoutHelper.DrawerListener implemented methods */
	@Override
	public void onDrawerOpened() {
		if (mediaController.isShowing()) {
			mediaController.hide();
		}	
	}
	
	@Override
	public void onDrawerClosed() {
		// do nothing on close		
	}	
}
