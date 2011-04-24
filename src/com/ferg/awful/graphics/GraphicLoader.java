package com.ferg.awful.graphics;

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
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import com.ferg.awful.R;
import com.ferg.awful.network.NetworkUtils;
import com.github.droidfu.concurrent.BetterAsyncTask;

/**
 * Responsible for retrieval and caching of images.
 * 
 * Activities which need to work with remote images can instantiate their own personal instance
 * and use that for their lifetimes; the manager is capable of dealing with the application cache
 * rebuilding itself and the like, and the cache is shared between different instances. 
 */
public class GraphicLoader {
	/**
	 * We want to limit this number because trying to do too many at once can lead to OOM errors as
	 * memory fills up on big images before we can purge them and replace them with manageably sized
	 * versions.
	 */
	private static final int MAX_CONCURRENT_DOWNLOADS = 4;
	
	private static final String TAG = "GraphicLoader";
	
	private GraphicCacheHolder mCacheHolder;
	
	// These objects SHOULD NOT BE ACCESSED without holding mUrlQueuesLock
	private int mActiveUrlDownloads;
	private Queue<String> mUrlsAwaitingDownload;
	private Map<String, FetchGraphicTask> mUrlTasks;
	private Map<String, List<OnGraphicLoadedListener>> mUrlQueues;
	private final ReentrantLock mUrlQueuesLock;
	
	/**
	 * Will use the cache from the given delegate object to hold on
	 * to downloads.
	 */
	public GraphicLoader(GraphicCacheHolder holder) {
		mCacheHolder = holder;
		mActiveUrlDownloads = 0;
		mUrlsAwaitingDownload = new LinkedList<String>();
		mUrlQueues = new HashMap<String, List<OnGraphicLoadedListener>>();
		mUrlTasks = new HashMap<String, FetchGraphicTask>();
		mUrlQueuesLock = new ReentrantLock();
    }
	
	private GraphicCache getCache() {
		return mCacheHolder.getGraphicCache();
	}
	
	/**
     * Retrieves the specified drawable, doing anything expensive in a new thread, and then calls
     * the appropriate callback method back on the calling thread.
     * 
     * This call may be canceled with the {@link #cancelDrawableFetch(String, OnGraphicLoadedListener)}
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
    public void fetchDrawableAsync(Context ctx, final String urlString, OnGraphicLoadedListener callback) {
    	if(getCache().containsKeyInMemory(urlString)) {
    		Graphic g = getCache().get(urlString);
    		
    		Log.v(TAG, "cache hit:" + urlString);
    		callback.afterFetch(ctx, g);
    	} else {
    		// might take a while - assign a placeholder and work on an async task
    		callback.beforeFetch(ctx, urlString);
    		
    		FetchGraphicTask worker = null; 
    		
    		mUrlQueuesLock.lock();
    		try {
	    		if(mUrlQueues.containsKey(urlString)) {
	    			mUrlQueues.get(urlString).add(callback);
	    		} else {
	    			final List<OnGraphicLoadedListener> queue = new LinkedList<OnGraphicLoadedListener>();
	    			queue.add(callback);
	    			mUrlQueues.put(urlString, queue);
	    			
	    			worker = new FetchGraphicTask(ctx, mCacheHolder, urlString, new OnLoadDequeuer(this, urlString));
	    			mUrlTasks.put(urlString, worker);
	    			mUrlsAwaitingDownload.add(urlString);
	    		}
    		} finally {
    			mUrlQueuesLock.unlock();
    		}
    		
    		if(worker != null) {
    			pollForTask();
    		}
    	}
    }
    
    private void pollForTask() {
    	mUrlQueuesLock.lock();
    	while(mActiveUrlDownloads < MAX_CONCURRENT_DOWNLOADS && !mUrlsAwaitingDownload.isEmpty()) {
			String nextUrl = mUrlsAwaitingDownload.poll();
			mActiveUrlDownloads++;
			mUrlTasks.get(nextUrl).execute();
		}
		mUrlQueuesLock.unlock();
    }
    
    /**
     * 
     * @param urlString
     * @param callback
     * @return Whether a callback was cancelled
     */
    public boolean cancelDrawableFetch(String urlString, OnGraphicLoadedListener callback) {
    	boolean result = false;
    	mUrlQueuesLock.lock();
    	
    	try {
	    	if(mUrlQueues.containsKey(urlString)) {
		    	List<OnGraphicLoadedListener> callbacks = mUrlQueues.get(urlString);
		    	result = callbacks.remove(callback);
		    	if(result && callbacks.isEmpty()) {
		    		mUrlQueues.remove(urlString);
		    		mUrlsAwaitingDownload.remove(urlString);
		    		FetchGraphicTask task = mUrlTasks.get(urlString);
		    		if(task == null) {
		    			Log.e(TAG, "Queue for URL "+urlString+" had no associated task!");
		    		} else {
		    			task.abort();
		    			mUrlTasks.remove(urlString);
		    		}
		    	}
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
    		cancelDrawableFetch(urlString, (OnGraphicLoadedListener) resultView.getTag(TAG_KEY_CALLBACK));
    	}
    	
    	resultView.setTag(TAG_KEY_URL, urlString);
    	if(urlString == null) {
    		resultView.setTag(TAG_KEY_CALLBACK, null);
    		resultView.setTag(TAG_KEY_STATUS, null);
    		return;
    	}
    	
    	OnGraphicLoadedListener callback = new ImageViewPopulator(resultView, urlString);
    	
    	resultView.setTag(TAG_KEY_CALLBACK, callback);
    	resultView.setTag(TAG_KEY_STATUS, FetchStatus.LOADING);
    	
    	fetchDrawableAsync(ctx, urlString, callback);
    }
    
    private static class ImageViewPopulator implements OnGraphicLoadedListener {
    	private WeakReference<ImageView> mView;
    	private String mUrlString;
    	
    	public ImageViewPopulator(ImageView view, String url) {
			mView = new WeakReference<ImageView>(view);
			mUrlString = url;
		}
		
    	@Override
		public void beforeFetch(Context context, String urlString) {
    		ImageView resultView = mView.get();
    		if(resultView == null) return;
    		
			resultView.setImageResource(R.drawable.loading_image);
		}
		@Override
		public void afterFetch(Context ctx, Graphic g) {
			ImageView resultView = mView.get();
    		if(resultView == null) return;
    		
			if(mUrlString.equals(resultView.getTag())) {
				resultView.setTag(TAG_KEY_CALLBACK, null);
				resultView.setTag(TAG_KEY_STATUS, FetchStatus.SUCCESS);
				resultView.setImageDrawable(g.toDrawable(ctx.getResources()));
			}
		}
		@Override
    	public void onFetchError(Context ctx, Exception error) {
			ImageView resultView = mView.get();
    		if(resultView == null) return;
    		
    		Log.e(TAG, "Error loading image at "+mUrlString, error);
    		if(mUrlString.equals(resultView.getTag())) {
    			resultView.setTag(TAG_KEY_CALLBACK, null);
    			resultView.setTag(TAG_KEY_STATUS, FetchStatus.ERROR);
    			resultView.setImageResource(R.drawable.missing_image);
    		}
        	}
    }
        
    /**
     * A simple callback for the asynchronous image fetch methods.
     */
    public interface OnGraphicLoadedListener {
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
    	public void afterFetch(Context context, Graphic drawable);
    	
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

    
    private static class FetchGraphicTask extends BetterAsyncTask<Void, Void, Graphic> {
    	private HttpUriRequest mRequest;
    	private OnGraphicLoadedListener mCallback;
    	private String mUrlString;
    	
		public FetchGraphicTask(Context context, GraphicCacheHolder cacheHolder, String urlString, OnGraphicLoadedListener callback) {
			super(context);
			mCallback = callback;
			mUrlString = urlString;
			mRequest = null;
			disableDialog();
		}
		
		@Override
		protected Graphic doCheckedInBackground(Context ctx, Void... unused) throws Exception {
			GraphicCacheHolder holder = (GraphicCacheHolder) ctx;
			GraphicCache cache = holder.getGraphicCache();
				
			Graphic g = cache.get(mUrlString);
	    	
	    	if(g != null) {
	    		Log.v(TAG, "cache hit:" + mUrlString);
	        } else {
	        	mRequest = new HttpGet(mUrlString);
	        	try {
	        		byte[] bytes = NetworkUtils.fetch(mRequest);
	        		g = GraphicFactory.decodeByteArray(bytes);
	        		
	        		//Store the original in case we want it later
	        		cache.put(mUrlString, g);
	        		
	        		//Reduce the size to what the user actually wants
	        		//TODO
	        		
	        		//Store the resized version too
	        		
	        	} catch(IOException e) {
	        		if(isCancelled()) {
	        			return null;
	        		} else {
	        			throw e;
	        		}
	        	}
	        	Log.v(TAG, "downloaded:" + mUrlString);
	        }
	    	
	    	if(isCancelled()) {
	    		return null;
	    	}
	    	
	    	return g;
		}

		/**
		 * Use this instead of {@link #cancel(boolean)} - it knows how to stop the running
		 * http request
		 */
		public boolean abort() {
			if(!isCancelled() && mRequest != null) {
				// There's no race condition here because mRequest only ever gets set (once),
				// never reset back to null
				mRequest.abort();
			}
			return cancel(true);
		}
		
		@Override
		protected void after(Context context, Graphic result) {
			mCallback.afterFetch(context, result);
		}

		@Override
		protected void handleError(Context context, Exception error) {
			mCallback.onFetchError(context, error);
		}
    }
    
    private void onUrlFinished(String url) {
		mUrlQueuesLock.lock();
		mActiveUrlDownloads--;
		mUrlQueuesLock.unlock();
		pollForTask();
	}
	
    private static class OnLoadDequeuer implements OnGraphicLoadedListener {
    	private WeakReference<GraphicLoader> mGraphicLoader;
    	private String mUrlString;
    	
    	public OnLoadDequeuer(GraphicLoader aGraphicLoader, String aUrlString) {
    		mGraphicLoader = new WeakReference<GraphicLoader>(aGraphicLoader);
    		mUrlString = aUrlString;
    	}
    	
    	/** May return null */
    	private List<OnGraphicLoadedListener> popListenerQueue(GraphicLoader loader) {
			loader.mUrlQueuesLock.lock();
			
			List<OnGraphicLoadedListener> queue = loader.mUrlQueues.get(mUrlString);
			loader.mUrlQueues.remove(mUrlString);
			loader.mUrlTasks.remove(mUrlString);
			
			loader.mUrlQueuesLock.unlock();
			return queue;
    	}
    	
		@Override
		public void beforeFetch(Context context, String urlString) { }
		
		@Override
		public void afterFetch(Context context, Graphic graphic) {
			GraphicLoader loader = mGraphicLoader.get();
			if(loader == null) return;
			
			List<OnGraphicLoadedListener> queue = popListenerQueue(loader);
			
			if(queue != null) {
				for(OnGraphicLoadedListener subCallback : queue) {
					subCallback.afterFetch(context, graphic);
				}
			}
			
			loader.onUrlFinished(mUrlString);
		}
		
		@Override
		public void onFetchError(Context context, Exception error) {
			GraphicLoader loader = mGraphicLoader.get();
			if(loader == null) return;
			
			List<OnGraphicLoadedListener> queue = popListenerQueue(loader);
			
			if(queue != null) {
				for(OnGraphicLoadedListener subCallback : queue) {
					subCallback.onFetchError(context, error);
				}
			}
			
			loader.onUrlFinished(mUrlString);
		}
	}
    
}
