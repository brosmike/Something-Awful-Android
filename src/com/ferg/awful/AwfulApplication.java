package com.ferg.awful;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.ferg.awful.image.DrawableCacheHolder;
import com.ferg.awful.image.RawByteCache;
import com.ferg.awful.thumbnail.ThumbnailBus;
import com.ferg.awful.thumbnail.ThumbnailMessage;
import com.github.droidfu.DroidFuApplication;
import com.github.droidfu.cachefu.AbstractCache;

/**
 * Allows application-wide access to the global image cache
 * 
 * @author brosmike
 */
public class AwfulApplication extends DroidFuApplication implements DrawableCacheHolder {
	private static String TAG="AwfulApplication";
	
	private RawByteCache mDrawableCache;
	
	public synchronized AbstractCache<String, byte[]> getDrawableCache() {
		if(mDrawableCache == null) {
			regenerateDrawableCache();
		}
		return mDrawableCache;
	}
	
	/**
	 * Regenerates the cache object according to stored user preferences. Does NOT clear the disk
	 * cache, but DOES clear the memory cache.
	 */
	public void regenerateDrawableCache() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int capacity = prefs.getInt("cache_capacity", 50);
		long expirationInMinutes = prefs.getLong("cache_expiration_in_days", 30) * (24*60);
		String storageType = prefs.getString("cache_storage_type", "sdcard");

		mDrawableCache = new RawByteCache(capacity, expirationInMinutes, 2);
		
		if("sdcard".equals(storageType)) {
			mDrawableCache.enableDiskCache(this, AbstractCache.DISK_CACHE_SDCARD);
		} else if("internal".equals(storageType)) {
			mDrawableCache.enableDiskCache(this, AbstractCache.DISK_CACHE_INTERNAL);
		}
	}
	
	private ThumbnailBus mImageBus=new ThumbnailBus();
	private SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> mImageCache=
							new SimpleWebImageCache<ThumbnailBus, ThumbnailMessage>(null, null, 101, mImageBus);
	
	
	ThumbnailBus getImageBus() {
		return(mImageBus);
	}
	
	SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> getImageCache() {
		return(mImageCache);
	}
}
