package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.ArrayList;
import java.util.HashMap;

public class WInfolist extends WObject {

	private String name;
	private ArrayList<HashMap<String,WObject>> items = new ArrayList<HashMap<String,WObject>>();
	
	public WInfolist(String name) {
		this.name = name;
		this.type = WType.INL;
	}

	public void addItem(HashMap<String, WObject> variables) {
		items.add(variables);
	}

	
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
