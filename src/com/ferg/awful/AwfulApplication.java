package com.ferg.awful;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ferg.awful.graphics.GraphicCache;
import com.ferg.awful.graphics.GraphicCacheHolder;
import com.github.droidfu.DroidFuApplication;
import com.github.droidfu.cachefu.AbstractCache;

/**
 * Allows application-wide access to the global image cache
 * 
 * @author brosmike
 */
public class AwfulApplication extends DroidFuApplication implements GraphicCacheHolder {
	private static String TAG="AwfulApplication";
	
	private GraphicCache mGraphicCache;
	
	public synchronized GraphicCache getGraphicCache() {
		if(mGraphicCache == null) {
			regenerateGraphicCache();
		}
		return mGraphicCache;
	}
	
	/**
	 * Regenerates the cache object according to stored user preferences. Does NOT clear the disk
	 * cache, but DOES clear the memory cache.
	 */
	public void regenerateGraphicCache() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int capacity = prefs.getInt("cache_capacity", 50);
		long expirationInMinutes = prefs.getLong("cache_expiration_in_days", 30) * (24*60);
		String storageType = prefs.getString("cache_storage_type", "sdcard");

		mGraphicCache = new GraphicCache(capacity, expirationInMinutes, 2);
		
		if("sdcard".equals(storageType)) {
			mGraphicCache.enableDiskCache(this, AbstractCache.DISK_CACHE_SDCARD);
		} else if("internal".equals(storageType)) {
			mGraphicCache.enableDiskCache(this, AbstractCache.DISK_CACHE_INTERNAL);
		}
	}
}
