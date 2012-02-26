package com.ubergeek42.weechat.weechatrelay.protocol;

import java.util.ArrayList;

public class WHdata extends WObject {

	String key_list[] = null;
	String path_list[] = null;
	WType[] type_list = null;

	ArrayList<HdataEntry> items = new ArrayList<HdataEntry>();
	

	public void addItem(HdataEntry hde) {
		items.add(hde);
	}
	public int getCount() {
		return items.size();
	}
	public HdataEntry getItem(int index) {
		return items.get(index);
	}
	
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

	public void setKeys(String[] keys) {
		key_list = new String[keys.length];
		type_list = new WType[keys.length];
		for(int i=0;i<keys.length;i++) {
			String kt[] = keys[i].split(":");
			key_list[i] = kt[0];
			type_list[i] = WType.valueOf(kt[1].toUpperCase());
		}
	}

}
