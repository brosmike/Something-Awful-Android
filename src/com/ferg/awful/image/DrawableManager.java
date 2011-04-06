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
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.ferg.awful.AwfulApplication;
import com.ferg.awful.R;
import com.ferg.awful.network.NetworkUtils;
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
	private static final String TAG = "DrawableManager";
	
	private DrawableCacheHolder mCacheHolder;
	
	private Map<String, List<OnDrawableLoadedListener>> mUrlQueues;
	private final ReentrantLock mUrlQueuesLock;
	
	/**
	 * Will use the cache from the given delegate object to hold on
	 * to downloads.
	 */
	public DrawableManager(DrawableCacheHolder holder) {
		mCacheHolder = holder;
		mUrlQueues = Collections.synchronizedMap(new HashMap<String, List<OnDrawableLoadedListener>>());
		mUrlQueuesLock = new ReentrantLock();
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
        	bytes = NetworkUtils.fetch(urlString);
        	getCache().put(urlString, bytes);
        	Log.v(TAG, "downloaded:" + urlString);
        }
    	
    	return DrawableFactory.decodeByteArray(bytes);
    }
    
    
    /**
     * Retrieves the specified drawable, doing anything expensive in a new thread, and then calls
     * the appropriate callback method back on the calling thread.
     * 
     * This call may be canceled with the {@link #cancelDrawableFetch(String, OnDrawableLoadedListener)}
     * method.
     * 
     * If multiple fetchDrawableAsync calls are made concurrently for the same url, it will only
     * be downloaded once.
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
     *     on whether the requested drawable is cached).
     */
    public void fetchDrawableAsync(Context ctx, final String urlString, OnDrawableLoadedListener callback) {
    	if(getCache().containsKeyInMemory(urlString)) {
    		byte[] bytes = getCache().get(urlString);
    		Drawable d = DrawableFactory.decodeByteArray(bytes);
    		
    		Log.v(TAG, "cache hit:" + urlString);
    		callback.afterFetch(ctx, d);
    	} else {
    		// might take a while - assign a placeholder and work on an async task
    		callback.beforeFetch(ctx, urlString);
    		mUrlQueuesLock.lock();
    		try {
	    		if(mUrlQueues.containsKey(urlString)) {
	    			mUrlQueues.get(urlString).add(callback);
	    		} else {
	    			final List<OnDrawableLoadedListener> queue = Collections.synchronizedList(new LinkedList<OnDrawableLoadedListener>());
	    			queue.add(callback);
	    			mUrlQueues.put(urlString, queue);
	    			FetchDrawableTask worker = new FetchDrawableTask(ctx, new OnDrawableLoadedListener() {
	    				@Override
						public void beforeFetch(Context context, String urlString) { }
						@Override
						public void afterFetch(Context context, Drawable drawable) {
							for(OnDrawableLoadedListener subCallback : queue) {
								subCallback.afterFetch(context, drawable);
							}
							mUrlQueues.remove(urlString);
						}
						@Override
						public void onFetchError(Context context, Exception error) {
							for(OnDrawableLoadedListener subCallback : queue) {
								subCallback.onFetchError(context, error);
							}
							mUrlQueues.remove(urlString);
						}
	    			});
	    			worker.execute(urlString);
	    		}
    		} finally {
    			mUrlQueuesLock.unlock();
    		}
    	}
    }
    
    /**
     * 
     * @param urlString
     * @param callback
     * @return Whether a callback was cancelled
     */
    public boolean cancelDrawableFetch(String urlString, OnDrawableLoadedListener callback) {
    	boolean result = false;
    	mUrlQueuesLock.lock();
    	
    	try {
	    	if(mUrlQueues.containsKey(urlString)) {
		    	List<OnDrawableLoadedListener> callbacks = mUrlQueues.get(urlString);
		    	result = callbacks.remove(callback);
	    	}
    	} finally {
    		mUrlQueuesLock.unlock();    		
    	}
    	
    	return result;
    }
    
    private static final int TAG_KEY_URL = 1;
    private static final int TAG_KEY_STATUS = 2;
    private static final int TAG_KEY_CALLBACK = 3;
    
    private static enum FetchStatus {
    	LOADING,
    	SUCCESS,
    	ERROR
    }
    
    
    /**
     * Retrieves the specified drawable, doing anything expensive in a new thread, and then places
     * the drawable in the given ImageView on completion.
     * 
     * If an error occurs, the ImageView will be populated with the {@link R.drawable.missing_image}
     * resource.
     * 
     * If this method is called again on the same ImageView before the first image is loaded, only
     * the most recent call will have an effect.
     * 
     * If multiple fetchDrawableAsync calls are made concurrently for the same url, it will only
     * be downloaded once.
     *  
     * @param ctx
     *     The context from which the call originates. This is used to help ensure that any
     *     subthreads used by the implementation get cleaned up if the context dies before the
     *     fetch completes.
     * @param urlString
     *     The URL from which to fetch
     * @param resultView
     *     Where to put the resulting Drawable on completion. Note that this uses tags from the 
     *     ImageView to determine things like "does the view still want to show the image I loaded"
     */
    public void fetchDrawableAsync(Context ctx, final String urlString, final ImageView resultView) {
    	FetchStatus status = (FetchStatus) resultView.getTag(TAG_KEY_STATUS);
    	if(FetchStatus.LOADING.equals(status)) {
    		cancelDrawableFetch(urlString, (OnDrawableLoadedListener) resultView.getTag(TAG_KEY_CALLBACK));
    	}
    	
    	resultView.setTag(TAG_KEY_URL, urlString);
    	if(urlString == null) {
    		resultView.setTag(TAG_KEY_CALLBACK, null);
    		resultView.setTag(TAG_KEY_STATUS, null);
    		return;
    	}
    	
    	OnDrawableLoadedListener callback = new OnDrawableLoadedListener() {
    		@Override
			public void beforeFetch(Context context, String urlString) {
				resultView.setImageResource(R.drawable.loading_image);
			}
    		@Override
    		public void afterFetch(Context ctx, Drawable d) {
    			if(urlString.equals(resultView.getTag())) {
    				resultView.setTag(TAG_KEY_CALLBACK, null);
    				resultView.setTag(TAG_KEY_STATUS, FetchStatus.SUCCESS);
    				resultView.setImageDrawable(d);
    			}
    		}
    		@Override
        	public void onFetchError(Context ctx, Exception error) {
        		Log.e(TAG, "Error loading image at "+urlString, error);
        		if(urlString.equals(resultView.getTag())) {
        			resultView.setTag(TAG_KEY_CALLBACK, null);
        			resultView.setTag(TAG_KEY_STATUS, FetchStatus.ERROR);
        			resultView.setImageResource(R.drawable.missing_image);
        		}
        	}
    	};
    	
    	resultView.setTag(TAG_KEY_CALLBACK, callback);
    	resultView.setTag(TAG_KEY_STATUS, FetchStatus.LOADING);
    	
    	fetchDrawableAsync(ctx, urlString, callback);
    }
    
        
    /**
     * A simple callback for the asynchronous image fetch methods.
     */
    public interface OnDrawableLoadedListener {
    	/**
    	 * Gets called on the original thread right before any expensive handling happens.
    	 * 
    	 * This is good for, say, putting in a placeholder "loading" image.
    	 * 
    	 * This will NOT get called if no expensive processing needs to happen before
    	 * {@link #onDrawableLoaded} can be called (say, an in-memory cache hit)
    	 * 
    	 * @param context
    	 *     The currently live version of the context that called the method using the callback
    	 * @param urlString
    	 *     The URL for the drawable that's about to start loading
    	 */
    	public void beforeFetch(Context context, String urlString);
    	
    	/**
    	 * Gets called after a successful download.
    	 * 
    	 * @param context 
    	 *     The currently live version of the context that called the method using the callback
    	 * @param drawable
    	 *     The (possibly newly loaded, possibly cached) image
    	 */
    	public void afterFetch(Context context, Drawable drawable);
    	
    	/**
    	 * Gets called after an unsuccessful download.
    	 * 
    	 * @param context 
    	 *     The currently live version of the context that called the method using the callback
    	 * @param error
    	 *     The cause of the fetch failure
    	 */
    	public void onFetchError(Context context, Exception error);
    }

    private class FetchDrawableTask extends BetterAsyncTask<String, Void, Drawable> {
    	private HttpUriRequest mRequest;
    	private OnDrawableLoadedListener mCallback;
    	
		public FetchDrawableTask(Context context, OnDrawableLoadedListener callback) {
			super(context);
			mCallback = callback;
			mRequest = null;
			disableDialog();
		}
		
		@Override
		protected Drawable doCheckedInBackground(Context ctx, String... params) throws Exception {
			String urlString = params[0];
			byte[] bytes = null;
	    	
	    	if(getCache().containsKey(urlString)) {
	    		bytes = getCache().get(urlString);
	    		Log.v(TAG, "cache hit:" + urlString);
	        } else {
	        	mRequest = new HttpGet(urlString);
	        	try {
	        		bytes = NetworkUtils.fetch(mRequest);
	        	} catch(IOException e) {
	        		if(isCancelled()) {
	        			return null;
	        		} else {
	        			throw e;
	        		}
	        	}
	        	getCache().put(urlString, bytes);
	        	Log.v(TAG, "downloaded:" + urlString);
	        }
	    	
	    	if(isCancelled()) {
	    		return null;
	    	}
	    	
	    	return DrawableFactory.decodeByteArray(bytes);
		}

		@Override
		protected void after(Context context, Drawable result) {
			if(!isCancelled()) {
				mCallback.afterFetch(context, result);
			}
		}

		@Override
		protected void handleError(Context context, Exception error) {
			if(!isCancelled()) {
				mCallback.onFetchError(context, error);
			}
		}
		
		@Override
		protected void onCancelled() {
			if(mRequest != null) {
				mRequest.abort();
			}
		}
    	
    }
    
}
