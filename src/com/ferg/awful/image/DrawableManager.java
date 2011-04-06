package com.ferg.awful.image;

/*
 Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.    
*/
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.ferg.awful.AwfulApplication;
import com.ferg.awful.R;
import com.github.droidfu.cachefu.AbstractCache;
import com.github.droidfu.concurrent.BetterAsyncTask;

/**
 * Responsible for retrieval and caching of images.
 * 
 * Activities which need to work with remote images can instantiate their own personal instance
 * and use that for their lifetimes; the manager is capable of dealing with the application cache
 * rebuilding itself and the like, and the cache is shared between different instances. 
 */
public class DrawableManager {
	private static final int MAX_IMAGE_SIZE = 20971520; // 20mb
	
	private static final String TAG = "DrawableManager";
	
	private DrawableCacheHolder mCacheHolder;
	
	/**
	 * Will use the cache from the given delegate object to hold on
	 * to downloads.
	 */
	public DrawableManager(DrawableCacheHolder holder) {
		mCacheHolder = holder;
    }
	
	/**
	 * This convenience method uses the cache from the Context's application, which
	 * better be an AwfulApplication.
	 */
	public DrawableManager(Activity awfulContext) {
		this((AwfulApplication) awfulContext.getApplication());
	}

	private AbstractCache<String, byte[]> getCache() {
		return mCacheHolder.getDrawableCache();
	}
	
	/**
	 * Retrieves the drawable from the given URL, using a cached value if it exists.
	 * 
	 * Note that this may perform a fetch on the calling thread! This method should not be used on
	 * a UI thread for this reason. Instead, use 
	 * {@link fetchDrawableAsync(Context, String, OnDrawableLoadedListener)} or
	 * {@link fetchDrawableAsync(Context, String, ImageView)}
	 * 
	 */
    public Drawable fetchDrawable(String urlString) throws MalformedURLException, IOException {
    	byte[] bytes = null;
    	
    	if(getCache().containsKey(urlString)) {
    		bytes = getCache().get(urlString);
    		Log.v(TAG, "cache hit:" + urlString);
        } else {
        	bytes = fetch(urlString);
        	getCache().put(urlString, bytes);
        	Log.v(TAG, "downloaded:" + urlString);
        }
    	
    	return DrawableFactory.decodeByteArray(bytes);
    }
    
    /**
     * Retrieves the specified drawable, doing anything expensive in a new thread, and then runs the
     * callback in the original thread.
     *  
     * @param ctx
     *     The context from which the call originates. This is used to help ensure that any
     *     subthreads used by the implementation get cleaned up if the context dies before the
     *     fetch completes.
     * @param urlString
     *     The URL from which to fetch
     * @param callback
     *     Gets called in the obvious way once the fetch is completed. This will always be called
     *     on the original calling thread, but it may or may not be called synchronously (depending
     *     on whether the requested drawable is cached)
     */
    public void fetchDrawableAsync(Context ctx, String urlString, OnDrawableLoadedListener callback) {
    	if(getCache().containsKeyInMemory(urlString)) {
    		byte[] bytes = getCache().get(urlString);
    		Drawable d = DrawableFactory.decodeByteArray(bytes);
    		
    		Log.v(TAG, "cache hit:" + urlString);
    		callback.onDrawableLoaded(ctx, d);
    	} else {
    		// might take a while - work on an async task
    		FetchDrawableTask worker = new FetchDrawableTask(ctx, callback);
    		worker.execute(urlString);
    	}
    }
    
    /**
     * Retrieves the specified drawable, doing anything expensive in a new thread, and then places
     * the drawable in the given ImageView on completion. If an error occurs, the ImageView will
     * be populated with the {@link R.drawable.missing_image} resource.
     *  
     * @param ctx
     *     The context from which the call originates. This is used to help ensure that any
     *     subthreads used by the implementation get cleaned up if the context dies before the
     *     fetch completes.
     * @param urlString
     *     The URL from which to fetch
     * @param resultView
     *     Where to put the resulting Drawable on completion
     */
    public void fetchDrawableAsync(Context ctx, final String urlString, final ImageView resultView) {
    	fetchDrawableAsync(ctx, urlString, new OnDrawableLoadedListener() {
    		public void onDrawableLoaded(Context ctx, Drawable d) {
    			resultView.setImageDrawable(d);
    		}
        	public void onDrawableLoadError(Context ctx, Exception error) {
        		Log.e(TAG, "Error loading image at "+urlString, error);
        		resultView.setImageResource(R.drawable.missing_image);
        	}
    	});
    }
    
    /**
     * A simple callback for the asynchronous image fetch methods.
     */
    public interface OnDrawableLoadedListener {
    	/**
    	 * Gets called after a successful download.
    	 * 
    	 * @param context 
    	 *     The currently live version of the context that called the method using the callback
    	 * @param drawable
    	 *     The (possibly newly loaded, possibly cached) image
    	 */
    	public void onDrawableLoaded(Context context, Drawable drawable);
    	
    	/**
    	 * Gets called after an unsuccessful download.
    	 * 
    	 * @param context 
    	 *     The currently live version of the context that called the method using the callback
    	 * @param error
    	 *     The cause of the fetch failure
    	 */
    	public void onDrawableLoadError(Context context, Exception error);
    }

    private class FetchDrawableTask extends BetterAsyncTask<String, Void, Drawable> {
    	private OnDrawableLoadedListener mCallback;
    	
		public FetchDrawableTask(Context context, OnDrawableLoadedListener callback) {
			super(context);
			mCallback = callback;
		}
		
		@Override
		protected Drawable doCheckedInBackground(Context ctx, String... params) throws Exception {
			return fetchDrawable(params[0]);
		}

		@Override
		protected void after(Context context, Drawable result) {
			mCallback.onDrawableLoaded(context, result);
		}

		@Override
		protected void handleError(Context context, Exception error) {
			mCallback.onDrawableLoadError(context, error);
		}
    	
    }
    

    /**
     * Fetches on-thread. Don't call from UI threads.
     * 
     * Has all the usual points of failure for an HTTP fetch, plus it enforces the
     * {@link #MAX_IMAGE_SIZE} constraint (as an IOException).
     */
    private byte[] fetch(String urlString) throws MalformedURLException, IOException {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpGet request = new HttpGet(urlString);
        HttpResponse response = httpClient.execute(request);
        long contentLength = response.getEntity().getContentLength();
        if(contentLength > MAX_IMAGE_SIZE) {
        	throw new IOException("Response length ("+contentLength+") exceeds maximum image size ("+MAX_IMAGE_SIZE+")");
        }
        byte[] bytes = new byte[(int) contentLength];
        InputStream responseStream = response.getEntity().getContent();
        int status = responseStream.read(bytes, 0, (int) contentLength);
        if(status != -1) {
        	throw new IOException("Response length exceeded reported content length ("+contentLength+")");
        }
        return bytes; 
    }

}
