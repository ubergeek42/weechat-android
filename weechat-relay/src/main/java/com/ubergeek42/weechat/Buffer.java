/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.weechat;

import java.util.ArrayList;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Representation of a buffer from weechat
 * 
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
    // Notify level for buffer, default = 2 == highlight and msg
    private int notify = 2;

    private int numUnread = 0;
    private int numHighlights = 0;
    private ArrayList<BufferObserver> observers = new ArrayList<BufferObserver>();
    private LinkedList<BufferLine> lines = new LinkedList<BufferLine>();
    private ArrayList<NickItem> nicks = new ArrayList<NickItem>();
    private ArrayList<String> snicks = new ArrayList<String>();
    private Hashtable local_vars;

    public void addLine(BufferLine m) {
        addLineNoNotify(m);
        numUnread++;
        notifyObservers();

        // this is a line that comes with a nickname (at all times?)
        // change snicks accordingly, placing last used nickname first
        String nick = "";
        for (String tag : m.getTags())
            if (tag.startsWith("nick_")) {
                nick = tag.substring(5);
                break;
            }
        if (!nick.equals("")) {
            snicks.remove(nick);
            snicks.add(0, nick);
        }
    }

    // Add Line to the Buffer, but don't increase the unread count. Examples for such lines are joins/quits
    public void addLineNoUnread(BufferLine m) {
        addLineNoNotify(m);
        notifyObservers();
    }

    public void addLineNoNotify(BufferLine m) {
        synchronized (messagelock) {
            lines.addLast(m);
            if (lines.size() > MAXLINES) {
                lines.removeFirst();
            }
        }
    }

    public void addLineFirstNoNotify(BufferLine m) {
        synchronized (messagelock) {
            lines.addFirst(m);
            if (lines.size() > MAXLINES) {
                lines.removeLast();
            }
        }
    }

    public void clearLines() {
    	synchronized(messagelock) {
    		lines.clear();
    		numUnread = 0;
    		numHighlights = 0;
    	}
    }
    
    // Notify anyone who cares
    public void notifyObservers() {
        for (BufferObserver o : observers) {
            o.onLineAdded();
        }
    }

    public void addObserver(BufferObserver ob) {
        observers.add(ob);
    }

    public void removeObserver(BufferObserver ob) {
        observers.remove(ob);
    }

    public void setNumber(int i) {
        this.bufferNumber = i;
    }

    public void setPointer(String s) {
        this.pointer = s;
    }

    public void setFullName(String s) {
        this.fullName = s;
    }

    public void setShortName(String s) {
        this.shortName = s;
    }

    public void setTitle(String s) {
        this.title = s;
    }

    public void setNicklistVisible(boolean b) {
    }

    public void setType(int i) {
    }

    public void setLocals(Hashtable ht) {
        this.local_vars = ht;
    }

    public void setNotifyLevel(int i) {
        this.notify = i;
    }

    public int getNumber() {
        return bufferNumber;
    }

    public String getPointer() {
        return pointer;
    }

    public String getFullName() {
        return this.fullName;
    }

    public String getTitle() {
        return this.title;
    }

    public String getShortName() {
        return this.shortName;
    }

    public int getNotifyLevel() {
        return notify;
    }

    public RelayObject getLocalVar(String key) {
        if (this.local_vars == null) {
            return null;
        }
        return this.local_vars.get(key);
    }

    public void resetHighlight() {
        numHighlights = 0;
    }

    public void resetUnread() {
        numUnread = 0;
    }

    public void addHighlight() {
        numHighlights++;
    }

    public void addHighlights(int highlights) {
        numHighlights += highlights;
    }

    public void addUnread() {
        numUnread++;
    }

    public void addUnreads(int unreads) {
        numUnread += unreads;
    }

    public int getHighlights() {
        return numHighlights;
    }

    public int getUnread() {
        return numUnread;
    }

    public LinkedList<BufferLine> getLines() {
        // Give them a copy, so we don't get concurrent modification exceptions
        LinkedList<BufferLine> ret = new LinkedList<BufferLine>();
        synchronized (messagelock) {
            for (BufferLine m : lines) {
                ret.add(m);
            }
        }
        return ret;
    }

    public String getLinesHTML() {
        // TODO: think about thread synchronization
        StringBuffer sb = new StringBuffer();
        for (BufferLine m : lines) {
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
        synchronized (nicklock) {
            nicks.add(ni);
            snicks.add(ni.toString());
        }
        for (BufferObserver o : observers) {
            o.onNicklistChanged();
        }
    }
    
    public void removeNick(NickItem ni) {
    	synchronized (nicklock) {
    		nicks.remove(ni);
            snicks.remove(ni.toString());
    	}
    	for (BufferObserver o : observers) {
    		o.onNicklistChanged();
    	}
    }
    public void updateNick(NickItem ni) {
    	synchronized (nicklock) {
    		nicks.remove(ni);
    		nicks.add(ni);
    	}
    	for (BufferObserver o : observers) {
    		o.onNicklistChanged();
    	}
    }

    // return ArrayList containing strings with nicknames
    // it is supposed to be in last spoke-places first order
    public ArrayList<String> getNicks() {
        return snicks;
    }

    public int getNumNicks() {
        int ret = 0;
        synchronized (nicklock) {
            ret = nicks.size();
        }
        return ret;
    }

    public void clearNicklist() {
        synchronized (nicklock) {
            nicks.clear();
            snicks.clear();
        }
    }

    public void destroy() {
        for (BufferObserver o : observers) {
            o.onBufferClosed();
        }
    }

    public boolean hasLine(String linePointer) {
        for (BufferLine line : lines) {
            if (line.getPointer().equals(linePointer)) {
                return true;
            }
        }
        return false;
    }

}
