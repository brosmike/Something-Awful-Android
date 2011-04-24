package com.ferg.awful;

import android.os.Bundle;

import com.ferg.awful.graphics.GraphicCacheHolder;
import com.ferg.awful.graphics.GraphicCache;
import com.github.droidfu.activities.BetterDefaultActivity;

/**
 * Convenience class to avoid having to call a configurator's lifecycle methods everywhere. This
 * class should avoid implementing things directly; the ActivityConfigurator does that job.
 * 
 * Most Activities in this awful app should extend this guy; that will provide things like locking
 * orientation according to user preference.
 * 
 * This class also provides a few helper methods for grabbing preferences and the like.
 */
public class AwfulActivity extends BetterDefaultActivity implements GraphicCacheHolder {
	private ActivityConfigurator mConf;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mConf = new ActivityConfigurator(this);
		mConf.onCreate();
		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		mConf.onStart();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mConf.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mConf.onPause();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		mConf.onStop();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mConf.onDestroy();
	}

	@Override
	public GraphicCache getGraphicCache() {
		return ((AwfulApplication) getApplication()).getGraphicCache();
	}
		
}
