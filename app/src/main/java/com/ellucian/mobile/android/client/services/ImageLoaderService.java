/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.client.services;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.ImageView;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxStatus;
import com.androidquery.callback.BitmapAjaxCallback;
import com.ellucian.mobile.android.util.Extra;

import java.util.List;

public class ImageLoaderService extends IntentService {
	private static final String TAG = ImageLoaderService.class.getSimpleName();
	public static final String ACTION_FINISHED = "com.ellucian.mobile.android.client.services.ImageLoaderService.action.updated";
	private boolean broadcastWhenDone;
    private List<String> imageUrlList;
    private int imageCounter = 0;
    private int imagesToDownload = 0;

    public ImageLoaderService() {
		super("ImageLoaderService");
	}
	@Override
	protected void onHandleIntent(Intent intent) {
		imageUrlList = intent.getStringArrayListExtra(Extra.IMAGE_URL_LIST);
		broadcastWhenDone = intent.getBooleanExtra(Extra.SEND_BROADCAST, false);
        downloadImages();
	}
	
	private void sendBroadcast() {
		Log.d(TAG, "Images download sending broadcast");
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction(ACTION_FINISHED);
		LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(ImageLoaderService.this);
		broadcastManager.sendBroadcast(broadcastIntent);
	}
	
    private void downloadImages() {
        AQuery aq = new AQuery(ImageLoaderService.this);
        Log.v("broadcastWhenDone", ""+broadcastWhenDone);
        for (String imageUrl : imageUrlList) {
            Bitmap bit = aq.getCachedImage(imageUrl);
            if (bit == null) {
                // If cached imaged does not exist add to number to be downloaded and
                // start asynchronous download of that image
                Log.d(TAG, "Image could not be found in cache, starting download of: \n" + imageUrl);
                imagesToDownload++;
                ImageView iv = new ImageView(ImageLoaderService.this);
                // If marked for broadcast each image will add an counter in their callbacks
                if (broadcastWhenDone) {
                    try {
                        aq.id(iv).image(imageUrl, false, true, 0, 0,
                                new BitmapAjaxCallback() {

                                    @Override
                                    public void callback(String url,
                                            ImageView view, Bitmap bitmap,
                                            AjaxStatus status) {
                                        view.setImageBitmap(bitmap);
                                        imageCounter++;
                                        Log.d(TAG, "Image downloaded "
                                                + imageCounter + " " + url);
                                    }
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "downloadImages() failed. Exception: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    aq.id(iv).image(imageUrl, false, true);
                }
            }
        }

        Log.d("OnHandleIntent", "Number of images to download: " + imagesToDownload);

        if (broadcastWhenDone) {
            int checks = 0;
            // Keep checking to see if all the image callbacks have been fired
            while (imageCounter < imagesToDownload) {
                try {
                    Thread.sleep(1000);
                    if (checks >= 30) {
                        Log.d("TAG", "Waited 30 seconds for images to download... continuing");
                        break;
                    }
                    checks++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            sendBroadcast();
        }
	}
}
