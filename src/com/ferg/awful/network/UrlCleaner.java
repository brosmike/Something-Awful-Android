package com.ferg.awful.network;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.CharsetEncoder;

import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.TagNodeVisitor;
import org.htmlcleaner.Utils;

import android.net.Uri;
import android.util.Log;

/**
 * Interface to traverse an HtmlCleaner TagNode, converting all relative links to absolute ones
 * and fixing illegal characters (" " -> "%20" and the like)
 *
 */
public class UrlCleaner implements TagNodeVisitor {
	private static final String TAG = "UrlCleaner";
	
	private static final String[] urlAttrs = {
		"src",
		"href"
	};
	
	private String mBaseUrl;
	
	public UrlCleaner(String baseUrl) {
		mBaseUrl = baseUrl;
	}
	
	@Override
	public boolean visit(TagNode node, HtmlNode htmlNode) {
		if (htmlNode instanceof TagNode) {
            TagNode tag = (TagNode) htmlNode;
            for(String urlAttr : urlAttrs) {
            	if(tag.hasAttribute(urlAttr)) {
            		String url = tag.getAttributeByName(urlAttr);
            		url = Utils.fullUrl(mBaseUrl, url);
            		url = url.replace(" ", "%20");
            		// TODO: There's others, but proper URL encoding verification
            		// is apparently a huge pain and not standardized. :effort:
            		tag.setAttribute(urlAttr, url);
            	}
            }
        }
        // tells visitor to continue traversing the DOM tree
        return true;
	}

}
