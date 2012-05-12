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

	private HashMap<String,RelayObject> hashtable = new HashMap<String,RelayObject>();
	
	private WType keyType; // One of: Integer, String, Pointer, Buffer, Time
	private WType valueType; // Same possible types as for keys
	
	protected Hashtable(WType keyType, WType valueType) {
		this.keyType = keyType;
		this.valueType = valueType;
	}

	protected void put(RelayObject key, RelayObject value) {
		hashtable.put(key.toString(), value);
	}
	
	public RelayObject get(String key) {
		return hashtable.get(key);
	}
	
	/**
	 * Debug toString
	 */
	@Override
	public String toString() {
		StringBuilder map = new StringBuilder();
		for(String key: hashtable.keySet()) {
			RelayObject value = hashtable.get(key);
			map.append(key);
			map.append(" -> ");
			map.append(value);
			map.append(", ");
		}
		return map.toString();
	}
}
