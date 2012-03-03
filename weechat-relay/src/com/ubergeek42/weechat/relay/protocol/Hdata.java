package com.ubergeek42.weechat.relay.protocol;

import java.util.ArrayList;
/**
 * Hdata Object from Weechat; basically a list of assocative arrays
 * See the following URL for more information:
 *  http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_hdata
 *  http://www.weechat.org/files/doc/devel/weechat_plugin_api.en.html#hdata
 *  
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class Hdata extends RelayObject {

	// XXX: Protected since they are accessed from WData directly...
	protected String key_list[] = null;
	protected String path_list[] = null;
	protected WType[] type_list = null;

	private ArrayList<HdataEntry> items = new ArrayList<HdataEntry>();
	
	protected void addItem(HdataEntry hde) {
		items.add(hde);
	}
	
	protected void setKeys(String[] keys) {
		key_list = new String[keys.length];
		type_list = new WType[keys.length];
		for(int i=0;i<keys.length;i++) {
			String kt[] = keys[i].split(":");
			key_list[i] = kt[0];
			type_list[i] = WType.valueOf(kt[1].toUpperCase());
		}
	}
	
	/**
	 * @return The number of items in the hdata object
	 */
	public int getCount() {
		return items.size();
	}
	
	/**
	 * Gets one element from the hdata object
	 * @param index - The index of the object to get
	 * @return An HdataEntry object of the item
	 */
	public HdataEntry getItem(int index) {
		return items.get(index);
	}
	
	/**
	 * Debug toString
	 */
	public String toString() {
		String s = "[WHdata]\n  path=";
		for(String p: path_list) {
			s += p+"/";
		}
		s+="\n";
		for(HdataEntry hde: items) {
			s += hde.toString(2)+"\n";	
		}
		return s;
	}
}
