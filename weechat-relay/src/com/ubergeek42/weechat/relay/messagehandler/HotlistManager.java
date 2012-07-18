/*******************************************************************************
 * Copyright 2012 Tor Hveem
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.weechat.relay.messagehandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import android.util.Log;

import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Infolist;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Manages a list of buffers present in weechat
 * @author thveem <xt@bash.no>
 *
 */
public class HotlistManager implements RelayMessageHandler {
	private static final String TAG = "HotlistManager";

	
	ArrayList<HotlistItem> hotlist = new ArrayList<HotlistItem>();
	private HotlistManagerObserver onChangeObserver;
	

	/**
	 * Get the hotlist
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<HotlistItem> getHotlist() {
		return (ArrayList<HotlistItem>) hotlist.clone();
	}
	
	public int getSize() {
		return hotlist.size();
	}

	/**
	 * Register a single observer to be notified when the list of buffers changes
	 * @param ho - The observer to receive notifications
	 */
	public void onChanged(HotlistManagerObserver ho) {
		this.onChangeObserver = ho;
	}
	/**
	 * Can be called to inform clients that the hotlist have changed in some way
	 */
	public void hotlistChanged() {
		if (onChangeObserver != null) {
			onChangeObserver.onHotlistChanged();
		}
	}
	
	@Override
	public void handleMessage(RelayObject obj, String id) {
		
		/*Log.d(TAG, "Got id " + id);
		Log.d(TAG, "Got obj " + obj);*/
		
		Infolist infolist = (Infolist)obj;
		
		hotlist.clear();
		
		for(int i=0;i<infolist.size(); i++) {
			System.out.format("  Item %d\n",i);
			HashMap<String,RelayObject> item = infolist.getItem(i);
			
			HotlistItem hli = new HotlistItem(item);
			// Only add messages and highlights to hotlist
			// TODO: this could be a preference
			if(hli.count_01 > 0 || hli.count_02 > 0) {
			    hotlist.add(hli);
			}
				
			Log.d(TAG, "Added hotlistitem " + hli);
		}
		
		// Sort the hotlist
		Collections.sort(hotlist, new Comparator<HotlistItem>() {
	        @Override public int compare(HotlistItem b1, HotlistItem b2) {
	        	
	        	
	        	
	        	int b1Highlights = b1.getHighlights();
	        	int b2Highlights = b2.getHighlights();
	        	if(b2Highlights > 0 || b1Highlights > 0) {
	        		return b2Highlights - b1Highlights;
	        	}
	            return b2.getUnread() - b1.getUnread();
	        }
	        
	    });

		hotlistChanged();
	}
	

	

}
