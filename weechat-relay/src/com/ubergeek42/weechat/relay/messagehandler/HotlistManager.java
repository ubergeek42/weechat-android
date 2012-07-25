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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.Array;
import com.ubergeek42.weechat.relay.protocol.Hdata;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.Infolist;
import com.ubergeek42.weechat.relay.protocol.RelayObject;
import com.ubergeek42.weechat.relay.protocol.RelayObject.WType;

/**
 * Manages a list of buffers present in weechat
 * @author thveem <xt@bash.no>
 *
 */
public class HotlistManager implements RelayMessageHandler {
	private static Logger logger = LoggerFactory.getLogger(HotlistManager.class);
	
	ArrayList<HotlistItem> hotlist = new ArrayList<HotlistItem>();
	private HotlistManagerObserver onChangeObserver;
	private BufferManager bufferManager;


	public void setBufferManager(BufferManager bfm) {
		this.bufferManager = bfm;
	}

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
	/**
	 * Remove hotlist item from hotlist. Called when switching to the buffer
	 * to read the lines
	 * @param fullBufferName
	 */
	public void removeHotlistItem(String fullBufferName) {
		//HotlistItem hli = null;
	    for (HotlistItem hli : hotlist) {
	    	if(hli.getFullName().equals(fullBufferName)) {
	    		hotlist.remove(hli);
	    		hotlistChanged();
	    		return;
	    	}
	    }
	}

	@Override
	public void handleMessage(RelayObject obj, String id) {

		if (id.equals("_buffer_line_added")){ // New line added...what is it?
			Hdata hdata = (Hdata) obj;
			outer: for(int i=0;i<hdata.getCount(); i++) {
				HdataEntry hde = hdata.getItem(i);
				// TODO: check last item of path is line_data

				// Is line displayed or hidden by filters, etc?
				boolean displayed = (hde.getItem("displayed").asChar()==0x01);
				if(!displayed) {
					continue;
				}

				String bPointer = hde.getItem("buffer").asPointer();
				Buffer b = bufferManager.findByPointer(bPointer);
				if(b==null) {
					// No buffer associated with this line
					continue;
				}

				// TODO Check for buffer type
				// Ignore core / server, etc

				// Try to get the array tags (added in 0.3.9-dev: 2012-07-23)
				RelayObject tags = hde.getItem("tags_array");
				if(tags!=null  && tags.getType() == WType.ARR) {
					logger.debug("tags_array:"+ tags.toString());
					Array tagsArray = tags.asArray();
					int tagCount = tagsArray.length();
					if(tagCount == 0) {
						// All important messages have tags
						logger.debug("Found no tags in buffer:"+b.getFullName()+",skipping line.");
						continue;
					}
					for(int ai=0;ai<tagCount;ai++) {
						String tag = tagsArray.get(ai).asString();
						if(tag.equals("irc_smart_filter")||
						   tag.equals("irc_mode")||
						   tag.equals("irc_quit")||
						   tag.equals("irc_join")||
						   tag.equals("notify_none")
						   ) {
							logger.debug("Found tag:"+tag+",skipping line.");
							continue outer;
						}
					}

				}


				HotlistItem hli = new HotlistItem(hde, b);
				boolean found = false;
			    for (HotlistItem oldhli : hotlist) {
			    	//FIXME implement comparator ?
			    	if(oldhli.getFullName().equals(hli.getFullName())) {
			    		oldhli.count_00 += hli.count_00;
			    		oldhli.count_01 += hli.count_01;
			    		oldhli.count_02 += hli.count_02;
			    		oldhli.count_03 += hli.count_03;
			    		found=true;
			    		break;
			    	}
			    }
			    // Only add to hotlist if there are actual messages
			    if (!found && (hli.getHighlights() > 0 || hli.getUnread() > 0)) {
					hotlist.add(hli);
				}
			}
		}else {

			Infolist infolist = (Infolist)obj;
			hotlist.clear();

			for(int i=0;i<infolist.size(); i++) {
				System.out.format("  Item %d\n",i);
				HashMap<String,RelayObject> item = infolist.getItem(i);

				HotlistItem hli = new HotlistItem(item);

				// Sync the initial counts to the buffers too
				Buffer b = bufferManager.findByPointer(hli.buffer);
				if (b != null) {
					b.addHighlights(hli.getHighlights());
					b.addUnreads(hli.getUnread());
				}

				// Only add messages and highlights to hotlist
				// TODO: this could be a preference
				if(hli.count_01 > 0 || hli.count_02 > 0) {
					// We got count, check and see if we already have buffer in hotlist
				    hotlist.add(hli);
					if (b != null) {
						// FIXME this could probably only be signaled once
						bufferManager.buffersChanged();
					}
				}
				logger.debug("Added hotlistitem " + hli);


			}

		}
		// Sort the hotlist, highlights first, then unread
		Collections.sort(hotlist, new Comparator<HotlistItem>() {
	        @Override public int compare(HotlistItem b1, HotlistItem b2) {
	        	int b1Highlights = b1.getHighlights();
	        	int b2Highlights = b2.getHighlights();
	        	if(b2Highlights > 0 || b1Highlights > 0) {
	        		return b2Highlights - b1Highlights;
	        	}
	            return b1.buffer_number - b2.buffer_number;
	        }
		});

		// FIXME We probably changed, but this could be more intelligent
		hotlistChanged();
	}
}
