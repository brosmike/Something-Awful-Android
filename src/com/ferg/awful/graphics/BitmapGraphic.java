package com.ferg.awful.graphics;

import java.io.OutputStream;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class BitmapGraphic implements Graphic {
	private Bitmap mBitmap;
	
	public BitmapGraphic(Bitmap backingBitmap) {
		mBitmap = backingBitmap;
	}
	
	public Drawable toDrawable(Resources res) {
		return new BitmapDrawable(res, mBitmap);
	}
	
	public void writeToStream(OutputStream outStream) {
		mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
	}
}
