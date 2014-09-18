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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * A line/message from a buffer.
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public class BufferLine {
    final private static Logger logger = LoggerFactory.getLogger("BufferLine");
    final private static boolean DEBUG = true;

    private String message;
    private String prefix;
    private Date date;

    private boolean visible;
    private boolean highlighted;
    private boolean unread;
    private String pointer;

    private String[] tags;

    public BufferLine(String pointer, Date date, String prefix, String message,
               boolean displayed, boolean highlighted, String[] tags) {
        this.pointer = pointer;
        this.date = date;
        this.prefix = prefix;
        this.message = (message == null) ? "" : message;
        this.visible = displayed;
        this.highlighted = highlighted;
        this.tags = tags;

        unread = findOutIfUnread();
    }

    // TODO: consider caching the color "cleaned" values as well

    public Date getTimestamp() {
        return date;
    }

    public String getMessage() {
        return message;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Return the pointer that represents this line */
    public String getPointer() {
        return pointer;
    }

    public String[] getTags() {
        return tags;
    }

    /** should line be treated as an unread message */
    public boolean isUnread() {
        return unread;
    }

    private boolean findOutIfUnread() {
        if (highlighted) return true;
        if (!visible) return false;

        // there's no tags, probably it's an old version of weechat, so we err
        // on the safe side and treat it as unread
        if (tags == null) return true;

        // Every "message" to user should have one or more of these tags
        // notify_message, notify_highlight or notify_message
        if (tags.length == 0) return false;
        final List list = Arrays.asList(tags);
        return list.contains("notify_message") || list.contains("notify_highlight")
                || list.contains("notify_private");
    }
}
