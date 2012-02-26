package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.HashMap;

public class WHashtable extends WObject {

	HashMap<WObject,WObject> hashtable = new HashMap<WObject,WObject>();
	
	WType keyType;
	WType valueType;
	
	public WHashtable(WType keyType, WType valueType) {
		this.keyType = keyType;
		this.valueType = valueType;
	}

	public void put(WObject key, WObject value) {
		hashtable.put(key, value);
	}
	
	@Override
	public String toString() {
		StringBuilder map = new StringBuilder();
		for(WObject key: hashtable.keySet()) {
			WObject value = hashtable.get(key);
			map.append(key);
			map.append(" -> ");
			map.append(value);
			map.append(", ");
		}
		return map.toString();
	}
}
