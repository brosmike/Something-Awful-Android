package com.ferg.awful.image;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.github.droidfu.cachefu.AbstractCache;
import com.github.droidfu.cachefu.CacheHelper;

/**
 * Two-level write-through cache based on droid-fu's implementation that just maps URLs to files
 * containing raw bytes.
 */
public class RawByteCache extends AbstractCache<String, byte[]> {

	public RawByteCache(int initialCapacity, long expirationInMinutes, int maxConcurrentThreads) {
        super("RawByteCache", initialCapacity, expirationInMinutes, maxConcurrentThreads);
    }

    @Override
    public String getFileNameForKey(String imageUrl) {
        return CacheHelper.getFileNameFromUrl(imageUrl);
    }

    @Override
    protected byte[] readValueFromDisk(File file) throws IOException {
        BufferedInputStream istream = new BufferedInputStream(new FileInputStream(file));
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("Cannot read files larger than " + Integer.MAX_VALUE + " bytes");
        }

        int imageDataLength = (int) fileSize;

        byte[] imageData = new byte[imageDataLength];
        istream.read(imageData, 0, imageDataLength);
        istream.close();

        return imageData;
    }

    @Override
    protected void writeValueToDisk(File file, byte[] imageData) throws IOException {
        BufferedOutputStream ostream = new BufferedOutputStream(new FileOutputStream(file));

        ostream.write(imageData);

        ostream.close();
    }
}
