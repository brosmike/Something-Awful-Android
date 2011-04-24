package com.ferg.awful.graphics;


/**
 * Since droid-fu caches don't support changing certain attributes after creation, we need to be
 * able to get a cache that might regenerate on us. We can do that by holding on to one of these
 * instead of a direct reference to the cache in question.
 * 
 * Notably, AwfulApplication implements this.
 */
public interface GraphicCacheHolder {
	public GraphicCache getGraphicCache();
}
