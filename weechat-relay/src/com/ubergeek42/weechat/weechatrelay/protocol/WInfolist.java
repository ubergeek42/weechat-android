package com.ubergeek42.weechat.weechatrelay.protocol;

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
public class WInfolist extends WObject {

	private String name;
	private ArrayList<HashMap<String,WObject>> items = new ArrayList<HashMap<String,WObject>>();
	
	protected WInfolist(String name) {
		this.name = name;
		this.type = WType.INL;
	}

	protected void addItem(HashMap<String, WObject> variables) {
		items.add(variables);
	}

	/**
	 * Debug toString message
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(name + ":\n");
		for(HashMap<String,WObject> item: items) {
			for(String name: item.keySet()) {
				sb.append(String.format("  %s->%s, ",name,item.get(name)));
			}
			sb.append("\n\n");
		}
		return sb.toString();
	}
}
