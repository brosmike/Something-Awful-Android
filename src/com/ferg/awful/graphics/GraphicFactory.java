package com.ferg.awful.graphics;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import android.graphics.BitmapFactory;

public class GraphicFactory {
	
	/**
	 * Creates a new Graphic based on the given byte array. There are a wide variety of error
	 * conditions whereupon this method may throw a RuntimeException.
	 * 
	 * @param bytes
	 * 		The data to build the Graphic from
	 * @return
	 * 		The built Graphic. This generally won't be null; on an error condition, this method will
	 * 		instead throw a RuntimeException
	 */
	public static Graphic decodeByteArray(byte[] bytes) {
		if(bytes == null) {
			return null;
		} else if(isGif(bytes)) {
			return decodeGifByteArray(bytes);
		} else {
			return new BitmapGraphic(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
		}
	}
	
	/**
	 * Writes the graphic in question to the given output stream such that a later call to a decode
	 * function on the same data should succeed.
	 * 
	 * @param graphic The Graphic to encode
	 * @param outStream The stream to encode to
	 */
	public static void writeToStream(Graphic graphic, OutputStream outStream) {
		// Currently, all graphics can be identified by the stream they write
		// We don't need any special markers, so we can just write them directly.
		// This method is here in case that changes for some reason in the future.
		graphic.writeToStream(outStream);
	}
	
	private static boolean isGif(byte[] bytes) {
		if(bytes == null || bytes.length < 6) return false;
		
		String gifTag;
		try {
			gifTag = new String(bytes, 0, 3, "ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("ASCII encoding unsupported", e);
		}
		return gifTag.equals("GIF");
	}
	
	/**
	 * Note: Doesn't necessarily return an AnimatedGifGraphic. Might return a normal BitmapGraphic
	 * if the GIF is not in fact animated
	 */
	private static Graphic decodeGifByteArray(byte[] bytes) {
		GifDecoder decoder = new GifDecoder();
		int status = decoder.read(new ByteArrayInputStream(bytes));
		
		if(status != GifDecoder.STATUS_OK || decoder.getFrameCount() < 1) {
			return null;
		} else if(decoder.getFrameCount() == 1) {
			return new BitmapGraphic(decoder.getBitmap());
		} else {
			AnimatedGifGraphic g = new AnimatedGifGraphic(decoder.getFrameCount(), decoder.getLoopCount());
			for(int i=0;i<decoder.getFrameCount();i++) {
				g.addFrame(decoder.getFrame(i), decoder.getDelay(i));
			}
			return g;
		}
	}
}
