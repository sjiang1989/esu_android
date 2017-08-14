/*
 * Copyright 2015 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.multimedia;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.app.DrawerLayoutHelper;
import com.ellucian.mobile.android.app.EllucianFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.util.Extra;

import java.io.IOException;

public class AudioFragment extends EllucianFragment implements MediaController.MediaPlayerControl,
		MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, DrawerLayoutHelper.DrawerListener  {

	private static final String TAG = AudioFragment.class.getSimpleName();
    private static final String CURRENT_POSITION = "current_position";
    private static final String WAS_PLAYING = "media_was_playing";

	private Activity activity;
	private View rootView;

	private MediaPlayer mediaPlayer;
	private MediaController mediaController;
	private final Handler handler = new Handler();

	private boolean contentExpanded;
	private int currentPosition;
	private boolean readyState;
    private boolean wasPlaying;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		activity = getActivity();
	}

	@Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		rootView = inflater.inflate(R.layout.fragment_audio, container, false);
		return rootView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Intent activityIntent = activity.getIntent();

		String imageUrl = activityIntent.getStringExtra(Extra.IMAGE_URL);
		String content = activityIntent.getStringExtra(Extra.CONTENT);

		AQuery aq = new AQuery(activity);
    	aq.id(R.id.audio_image).image(imageUrl);

    	ImageView imageView = (ImageView) rootView.findViewById(R.id.audio_image);
		imageView.setOnClickListener(new View.OnClickListener(){

			@Override
			public void onClick(View v) {
				triggerTouch();

			}
		});


    	final TextView contentView = (TextView) rootView.findViewById(R.id.audio_content);
    	if (!TextUtils.isEmpty(content)) {
			contentView.setMaxLines(2);
			contentView.setEllipsize(TextUtils.TruncateAt.END);
			contentView.setText(content);
			contentView.setOnClickListener(new View.OnClickListener(){

				@Override
				public void onClick(View v) {
					if (!contentExpanded) {
						contentView.setEllipsize(null);
						contentView.setMaxLines(Integer.MAX_VALUE);
						contentExpanded = true;
					} else {
						contentView.setEllipsize(TextUtils.TruncateAt.END);
						contentView.setMaxLines(2);
						contentExpanded = false;
					}
				}
			});

    	} else {
    		contentView.setVisibility(View.GONE);
    	}

		mediaController = new MediaController(activity);

		if ( savedInstanceState != null ) {
            if (savedInstanceState.containsKey(CURRENT_POSITION) &&
                    savedInstanceState.getInt(CURRENT_POSITION) > 0) {
                currentPosition = savedInstanceState.getInt(CURRENT_POSITION);
            }
            wasPlaying = savedInstanceState.getBoolean(WAS_PLAYING, false);
        }

		getEllucianActivity().getDrawerLayoutHelper().setDrawerListener(this);
	}

	@Override
	public void onStart() {
		super.onStart();

		sendView("Audio", getEllucianActivity().moduleName);

        readyState = false;

        // mediaPlayer needs to be recreated and reinitialized after activity being paused
        // or it will not be in a correct state and exceptions will be thrown
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(this);

        Intent activityIntent = activity.getIntent();

        String audioUrl = activityIntent.getStringExtra(Extra.AUDIO_URL);

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException", e);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException", e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }

    }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
        outState.putBoolean(WAS_PLAYING, mediaPlayer.isPlaying());
        outState.putInt(CURRENT_POSITION, mediaPlayer.getCurrentPosition());
	}

	@Override
	  public void onStop() {
	    super.onStop();

	    mediaController.hide();
	    mediaPlayer.stop();
	    mediaPlayer.release();
        mediaPlayer = null;
	  }



	private void triggerTouch() {
		try {
			if (!mediaController.isShowing()) {
				mediaController.show();
			} else {
				mediaController.hide();
			}
		} catch (Throwable t) {
			// avoid issues with Kindle
		}
	}

    @SuppressWarnings("unused")
	public void triggerTouch(View view) {
		triggerTouch();
	}


	/** MediaPlayer implemented methods*/

	@Override
	public void onPrepared(MediaPlayer mp) {
		readyState = true;

        mediaController.setMediaPlayer(this);
        mediaController.setAnchorView(rootView);

        handler.post(new Runnable() {
          public void run() {
            mediaController.setEnabled(true);
            // Only show controls if Menu drawer is not open
            if (!getEllucianActivity().getDrawerLayoutHelper().isDrawerOpen()) {
                mediaController.show(0);
            }

          }
        });
        if (wasPlaying) {
            start();
        }
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		mediaPlayer.stop();

	}

	/** MediaPlayerControl implemented methods*/

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return true;
	}

	@Override
	public boolean canSeekForward() {
		return true;
	}

	@Override
	public int getAudioSessionId() {
		return 0;
	}

	@Override
	public int getBufferPercentage() {
		return 0;
	}

	@Override
	public int getCurrentPosition() {
		if (mediaPlayer != null && readyState) {
			return mediaPlayer.getCurrentPosition();
		} else {
			return 0;
		}
	}

	@Override
	public int getDuration() {
		if (mediaPlayer != null && readyState) {
			return mediaPlayer.getDuration();
		} else {
			return 0;
		}
	}

	@Override
	public boolean isPlaying() {
		if (mediaPlayer != null && readyState) {
			return mediaPlayer.isPlaying();
		} else {
			return false;
		}
	}

	@Override
	public void pause() {
		if (mediaPlayer != null) {
			mediaPlayer.pause();
		}
	}

	@Override
	public void seekTo(int pos) {
		mediaPlayer.seekTo(pos);
	}

	@Override
	public void start() {
		sendEventToTracker1(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Play button pressed", null, getEllucianActivity().moduleName);
		mediaPlayer.start();
		mediaController.show();
	}

	/**
	 * Workaround for the Amazon Kindle throwing java.lang.AbstractMethodError: abstract method not implemented
	 */
    @SuppressWarnings("unused")
	public void onControllerHide() {
		Log.v(TAG, "onControllerHide()");
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
		// Do nothing on close
	}


}
