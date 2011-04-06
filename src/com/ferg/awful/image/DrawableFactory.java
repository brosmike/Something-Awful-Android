package com.ferg.awful.image;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class DrawableFactory {
	
	public static Drawable decodeByteArray(byte[] bytes) {
		if(bytes == null) {
			return null;
		} else if(isGif(bytes)) {
			return decodeGifByteArray(bytes);
		} else {
			return Drawable.createFromStream(new ByteArrayInputStream(bytes), "src");
		}
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
	
	private static Drawable decodeGifByteArray(byte[] bytes) {
		GifDecoder decoder = new GifDecoder();
		int status = decoder.read(new ByteArrayInputStream(bytes));
		
		if(status != GifDecoder.STATUS_OK || decoder.getFrameCount() < 1) {
			return null;
		} else if(decoder.getFrameCount() == 1) {
			return new BitmapDrawable(decoder.getBitmap());
		} else {
			AnimationDrawable d = new AnimationDrawable();
			Bitmap firstFrame = decoder.getBitmap();
			d.setBounds(0, 0, firstFrame.getWidth(), firstFrame.getHeight());
			d.setOneShot(decoder.getLoopCount() == 1);
			
			for(int frame=0; frame < decoder.getFrameCount(); frame++) {
				d.addFrame(
						new BitmapDrawable(decoder.getFrame(frame)),
						decoder.getDelay(frame));
			}
			
			d.start();
			return d;
		}
	}
}
