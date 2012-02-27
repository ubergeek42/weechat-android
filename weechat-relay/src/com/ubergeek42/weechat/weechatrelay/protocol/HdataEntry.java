package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.ArrayList;
import java.util.HashMap;
/**
 * An Entry in an Hdata object.  This is basically an associative array from String to WObject
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class HdataEntry extends WObject {
	private ArrayList<String> pointers = new ArrayList<String>();
	private HashMap<String, WObject> data = new HashMap<String, WObject>();
	
	protected void addPointer(String pointer) {
		pointers.add(pointer);
	}

	protected void addObject(String key, WObject value) {
		data.put(key, value);
	}

	/**
	 * Debug print method
	 * @param indent - Number of spaces to indent the output
	 * @return The string...
	 */
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
	/**
	 * Gets an element from the Hdata Entry
	 * @param key - The element to retrieve
	 * @return The desired object(or null if not found)
	 */
	public WObject getItem(String key) {
		return data.get(key);
	}

	/**
	 * @return The pointer to the object at the end of the hdata path
	 */
	public String getPointer() {
		return pointers.get(pointers.size()-1);
	}
	/**
	 * Returns a pointer from the hdata path
	 * @param index - Which element in the path to get the pointer for
	 * @return The pointer to the chosen element(as a String
	 */
	public String getPointer(int index) {
		return pointers.get(index);
	}
}
