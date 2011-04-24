package com.ferg.awful.graphics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.github.droidfu.cachefu.AbstractCache;
import com.github.droidfu.cachefu.CacheHelper;

/**
 * Two-level write-through cache based on droid-fu's implementation.
 * 
 * Graphics are stored at the memory level in their object form (a Graphic), but are compressed
 * (normally as PNGs or GIFs) in the disk level cache. They are keyed by their original URLs.
 */
public class GraphicCache extends AbstractCache<String, Graphic> {

	public GraphicCache(int initialCapacity, long expirationInMinutes, int maxConcurrentThreads) {
        super("Graphic Cache", initialCapacity, expirationInMinutes, maxConcurrentThreads);
    }
	
    @Override
    public String getFileNameForKey(String imageUrl) {
        return CacheHelper.getFileNameFromUrl(imageUrl);
    }

    @Override
    protected Graphic readValueFromDisk(File file) throws IOException {
        BufferedInputStream istream = new BufferedInputStream(new FileInputStream(file));
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("Cannot read files larger than " + Integer.MAX_VALUE + " bytes");
        }

        int imageDataLength = (int) fileSize;

        byte[] imageData = new byte[imageDataLength];
        istream.read(imageData, 0, imageDataLength);
        istream.close();

        return GraphicFactory.decodeByteArray(imageData);
    }

    @Override
    protected void writeValueToDisk(File file, Graphic imageData) throws IOException {
        BufferedOutputStream ostream = new BufferedOutputStream(new FileOutputStream(file));

        GraphicFactory.writeToStream(imageData, ostream);

        ostream.close();
    }
}
