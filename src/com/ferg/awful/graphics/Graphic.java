package com.ferg.awful.graphics;

import java.io.OutputStream;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

/**
 * This essentially has the same job as a {@link Bitmap}, except that it also allows for Animated
 * GIF support.
 * 
 * Generally, direct interaction with this object should be kept to a minimum. Much like Bitmap
 * and BitmapFactory, most work on these objects should go through a GraphicFactory method.
 */
public interface Graphic {
	
	/**
	 * Returns a Drawable representation of this graphic. The resulting Drawable will be backed by
	 * this object and any changes to this object will be reflected in the Drawable; however, this
	 * object will not maintain any strong reference to the resulting Drawable, so keeping the
	 * Graphic around won't leak the Drawable.
	 * 
	 * @param res The application Resources upon which to base the density of the resulting image
	 */
	public Drawable toDrawable(Resources res);
	
	/**
	 * Writes the contents of this Graphic to the given output stream. Note that this method should
	 * generally NOT be called directly; instead use the equivalent method of GraphicFactory, which
	 * knows how to write Graphics to streams such that it can decode them back into the correct
	 * implementing subtype.
	 *  
	 * @param outStream The stream into which to encode the Graphic
	 */
	public void writeToStream(OutputStream outStream);
}
