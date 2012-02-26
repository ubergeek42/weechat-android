package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.ArrayList;
import java.util.HashMap;

public class HdataEntry extends WObject {
	private ArrayList<String> pointers = new ArrayList<String>();
	private HashMap<String, WObject> data = new HashMap<String, WObject>();
	
	public void addPointer(String pointer) {
		pointers.add(pointer);
	}

	public void addObject(String key, WObject value) {
		data.put(key, value);
	}

	public String toString(int indent) {
		String is="";
		for(int i=0;i<indent;i++) is+=" ";
		
		String ret = String.format("%s[HdataEntry]\n",is);
		String pointerString = "";
		for(String p: pointers) {pointerString += p + ", ";}
		ret += String.format("%s  Pointers: %s\n", is, pointerString);
		
		for(String k: data.keySet()) {
			ret += String.format("%s  %s=%s\n", is, k, data.get(k));
		}
		
		return ret;
	}
	
	public WObject getItem(String key) {
		return data.get(key);
	}

	public String getPointer() {
		return pointers.get(pointers.size()-1);
	}
	public String getPointer(int index) {
		return pointers.get(index);
	}
}
