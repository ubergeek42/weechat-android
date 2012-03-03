package com.ubergeek42.weechat;

import java.util.ArrayList;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.weechatrelay.WRelayConnection;

public class WeechatBuffer {
	public static final int MAXLINES = 50;
	private static Logger logger = LoggerFactory.getLogger(WeechatBuffer.class);

	
	Object messagelock = new Object();
	Object nicklock = new Object();
	
	private int bufferNumber;
	private String pointer;
	private String fullName;
	private String shortName;
	private String title;
	private boolean hasNicklist;
	private int type;

	private ArrayList<WBufferObserver> observers = new ArrayList<WBufferObserver>();
	private LinkedList<ChatMessage> lines = new LinkedList<ChatMessage>();
	private ArrayList<NickItem> nicks = new ArrayList<NickItem>();
	
	public void addLine(ChatMessage m) {
		addLineNoNotify(m);
		notifyObservers();
	}
	public void addLineNoNotify(ChatMessage m) {
		synchronized(messagelock) {
			lines.addLast(m);
			if (lines.size() > MAXLINES)
				lines.removeFirst();
		}
	}
	
	// Notify anyone who cares
	public void notifyObservers() {
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
		synchronized(messagelock) {
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
	public void addNick(NickItem ni) {
		synchronized(nicklock) {
			nicks.add(ni);
		}
	}
	public String[] getNicks() {
		int i = 0;
		String ret[] = new String[0];
		synchronized(nicklock) {
			ret = new String[nicks.size()];
			for(NickItem ni: nicks) {
				ret[i] = ni.toString();
				logger.debug(ret[i]);
				i++;
			}
		}
		return ret;
	}
	public int getNumNicks() {
		int ret=0;
		synchronized(nicklock) {
			ret = nicks.size();
		}
		return ret;
	}
}
