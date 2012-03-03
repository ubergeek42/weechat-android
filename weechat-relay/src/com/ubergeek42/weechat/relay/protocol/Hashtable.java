package com.ubergeek42.weechat.relay.protocol;

import java.util.HashMap;

/**
 * Hashtable implementation from Weechat
 * See the following URL(s) for more information:
 *   http://www.weechat.org/files/doc/devel/weechat_plugin_api.en.html#hashtables
 *   http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_hashtable
 *   
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class Hashtable extends RelayObject {

	private HashMap<RelayObject,RelayObject> hashtable = new HashMap<RelayObject,RelayObject>();
	
	private WType keyType;
	private WType valueType;
	
	protected Hashtable(WType keyType, WType valueType) {
		this.keyType = keyType;
		this.valueType = valueType;
	}

	protected void put(RelayObject key, RelayObject value) {
		hashtable.put(key, value);
	}
	
	/**
	 * Debug toString
	 */
	@Override
	public String toString() {
		StringBuilder map = new StringBuilder();
		for(RelayObject key: hashtable.keySet()) {
			RelayObject value = hashtable.get(key);
			map.append(key);
			map.append(" -> ");
			map.append(value);
			map.append(", ");
		}
		return map.toString();
	}
}
