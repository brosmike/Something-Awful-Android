package com.ferg.awful.graphics;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class AnimatedGifGraphic implements Graphic {
	private static final class GifFrame {
		public Bitmap bitmap;
		public int delay; // in milliseconds
		
		public GifFrame(Bitmap aBitmap, int aDelayInMillis) {
			bitmap = aBitmap;
			delay = aDelayInMillis;
		}
	}
	
	private List<GifFrame> mFrames;
	private int mLoopCount;
	
	public AnimatedGifGraphic() {
		mFrames = new ArrayList<GifFrame>();
		mLoopCount = 0;
	}
	
	public AnimatedGifGraphic(int frameCount, int loopCount) {
		mFrames = new ArrayList<GifFrame>(frameCount);
		mLoopCount = loopCount;
	}
	
	public void setLoopCount(int loopCount) {
		mLoopCount = loopCount;
	}
	
	public void addFrame(Bitmap bitmap, int delayInMillis) {
		mFrames.add(new GifFrame(bitmap, delayInMillis));
	}
	
	
	
	public Drawable toDrawable(Resources res) {
		AnimationDrawable d = new AnimationDrawable();
		
		Bitmap firstFrame = mFrames.get(0).bitmap;
		d.setBounds(0, 0, firstFrame.getWidth(), firstFrame.getHeight());
		d.setOneShot(mLoopCount == 1);
		for(GifFrame frame : mFrames) {
			d.addFrame(new BitmapDrawable(frame.bitmap), frame.delay);
		}
		
		d.start();
		return d;
	}
	
	
	public void writeToStream(OutputStream outStream) {
		GifEncoder e = new GifEncoder();
		e.start(outStream);
		for(GifFrame frame : mFrames) {
			e.setDelay(frame.delay);
			e.addFrame(frame.bitmap);
		}
		e.finish();
	}
}
