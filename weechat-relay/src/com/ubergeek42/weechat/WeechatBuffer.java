package com.ubergeek42.weechat;

import java.util.ArrayList;
import java.util.LinkedList;

public class WeechatBuffer {
	private static final int MAXLINES = 100;
	
	Object lock = new Object();
	
	private int bufferNumber;
	private String pointer;
	private String fullName;
	private String shortName;
	private String title;
	private boolean hasNicklist;
	private int type;

	ArrayList<WBufferObserver> observers = new ArrayList<WBufferObserver>();
	private LinkedList<ChatMessage> lines = new LinkedList<ChatMessage>();
	private Nicklist nicks = new Nicklist();
	
	public void addLine(ChatMessage m) {
		synchronized(lock) {
			lines.addLast(m);
			if (lines.size() > MAXLINES)
				lines.removeFirst();
		}
		// Notify anyone who cares
		for(WBufferObserver o: observers)
			o.onLineAdded();
	}

	
	public void addObserver(WBufferObserver ob) {
		observers.add(ob);
	}
	public void removeObserver(WBufferObserver ob) {
		observers.remove(ob);
	}
	
	public void setNumber(int i)              { bufferNumber = i; }
	public void setPointer(String s)          { this.pointer = s; }
	public void setFullName(String s)         { this.fullName = s; }
	public void setShortName(String s)        { this.shortName = s; }
	public void setTitle(String s)            { this.title = s; }
	public void setNicklistVisible(boolean b) { this.hasNicklist = b; }
	public void setType(int i)                { this.type = i; }
	
	public String getPointer()                { return pointer; }
	public String getFullName()               { return this.fullName; }
	public String getTitle()                  { return this.title; }
	public String getShortName()              { return this.shortName; }

	public LinkedList<ChatMessage> getLines() {
		// Give them a copy, so we don't get concurrent modification exceptions
		LinkedList<ChatMessage> ret = new LinkedList<ChatMessage>();
		synchronized(lock) {
			for(ChatMessage m: lines) {
				ret.add(m);
			}
		}
		return ret;
	}
	
	public String getLinesHTML() {
		// TODO: think about thread synchronization
		StringBuffer sb = new StringBuffer();
		for (ChatMessage m: lines) {
			sb.append("<tr><td class=\"timestamp\">");
			sb.append(m.getTimestampStr());
			sb.append("</td><td>");
			sb.append(m.getPrefix());
			sb.append("</td><td>");
			sb.append(m.getMessage());
			sb.append("</td></tr>\n");
		}
		return sb.toString();
	}
}
