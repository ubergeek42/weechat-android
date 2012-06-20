/*******************************************************************************
 * Copyright 2012 Keith Johnson
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
package com.ubergeek42.weechat.relay.protocol;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A Info-List object from Weechat
 * See the following URL(s) for more information:
 *  http://www.weechat.org/files/doc/devel/weechat_plugin_api.en.html#infolists
 *  http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_infolist
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class Infolist extends RelayObject {

	private String name;
	private ArrayList<HashMap<String,RelayObject>> items = new ArrayList<HashMap<String,RelayObject>>();
	
	protected Infolist(String name) {
		this.name = name;
		this.type = WType.INL;
	}

	protected void addItem(HashMap<String, RelayObject> variables) {
		items.add(variables);
	}

	/**
	 * @return The name of this infolist
	 */
	public String getName() {
		return this.name;
	}
	/**
	 * Returns a hashmap for the specified item
	 * @param index - The item to retrieve
	 * @return A HashMap<String,WObject> representing it
	 */
	public HashMap<String,RelayObject> getItem(int index) {
		return items.get(index);
	}
	/**
	 * @return The number of items in the infolist
	 */
	public int size() {
		return items.size();
	}
	
	/**
	 * Debug toString message
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(name + ":\n");
		for(HashMap<String,RelayObject> item: items) {
			for(String name: item.keySet()) {
				sb.append(String.format("  %s->%s, ",name,item.get(name)));
			}
			sb.append("\n\n");
		}
		return sb.toString();
	}
}
