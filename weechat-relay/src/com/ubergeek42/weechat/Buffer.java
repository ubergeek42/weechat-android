package com.ubergeek42.weechat;

import java.util.ArrayList;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Representation of a buffer from weechat
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class Buffer {
	public static final int MAXLINES = 200;
	private static Logger logger = LoggerFactory.getLogger(Buffer.class);

	
	Object messagelock = new Object();
	Object nicklock = new Object();
	
	private int bufferNumber;
	private String pointer;
	private String fullName;
	private String shortName;
	private String title;
	private boolean hasNicklist;
	private int type;

	private int numUnread=0;
	private int numHighlights=0;
	
	private ArrayList<BufferObserver> observers = new ArrayList<BufferObserver>();
	private LinkedList<BufferLine> lines = new LinkedList<BufferLine>();
	private ArrayList<NickItem> nicks = new ArrayList<NickItem>();
	private Hashtable local_vars;
	
	public void addLine(BufferLine m) {
		addLineNoNotify(m);
		notifyObservers();
		numUnread++;
	}
	public void addLineNoNotify(BufferLine m) {
		synchronized(messagelock) {
			lines.addLast(m);
			if (lines.size() > MAXLINES)
				lines.removeFirst();
		}
	}
	public void addLineFirstNoNotify(BufferLine m) {
		synchronized(messagelock) {
			lines.addFirst(m);
			if (lines.size() > MAXLINES)
				lines.removeLast();
		}
	}
	
	// Notify anyone who cares
	public void notifyObservers() {
		for(BufferObserver o: observers)
			o.onLineAdded();
	}
	public void addObserver(BufferObserver ob) {
		observers.add(ob);
	}
	public void removeObserver(BufferObserver ob) {
		observers.remove(ob);
	}
	
	public void setNumber(int i)              { bufferNumber = i; }
	public void setPointer(String s)          { this.pointer = s; }
	public void setFullName(String s)         { this.fullName = s; }
	public void setShortName(String s)        { this.shortName = s; }
	public void setTitle(String s)            { this.title = s; }
	public void setNicklistVisible(boolean b) { this.hasNicklist = b; }
	public void setType(int i)                { this.type = i; }
	public void setLocals(Hashtable ht)       { this.local_vars = ht; }
	
	public String getPointer()                { return pointer; }
	public String getFullName()               { return this.fullName; }
	public String getTitle()                  { return this.title; }
	public String getShortName()              { return this.shortName; }
	public RelayObject getLocalVar(String key){ return this.local_vars.get(key); }

	public void resetHighlight() {numHighlights = 0;}
	public void resetUnread()    {numUnread = 0;}
	public void addHighlight()   {numHighlights++;}
	
	public int getHighlights() { return numHighlights; }
	public int getUnread() { return numUnread;}
	
	public LinkedList<BufferLine> getLines() {
		// Give them a copy, so we don't get concurrent modification exceptions
		LinkedList<BufferLine> ret = new LinkedList<BufferLine>();
		synchronized(messagelock) {
			for(BufferLine m: lines) {
				ret.add(m);
			}
		}
		return ret;
	}
	
	public String getLinesHTML() {
		// TODO: think about thread synchronization
		StringBuffer sb = new StringBuffer();
		for (BufferLine m: lines) {
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
	public void clearNicklist() {
		synchronized(nicklock) {
			nicks.clear();
		}
	}
	public void destroy() {
		for(BufferObserver o: observers)
			o.onBufferClosed();
	}
}
