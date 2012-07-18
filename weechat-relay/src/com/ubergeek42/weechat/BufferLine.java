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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A line/message from a buffer.
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class BufferLine {
	/**
	 * The default representation for the timestamp when a line is rendered
	 */
	public final DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm:ss");
	
	private String message;
	private String prefix;
	private Date time;
	
	
	private String messageHTML = null;
	private String prefixHTML = null;
	private String timeStr = null;
	
	private boolean visible;
	private boolean highlight;
	private String pointer;
	
	// TODO: consider caching the color "cleaned" values as well
	
	/**
	 * Set the prefix for the line
	 * @param prefix - The prefix for the line
	 */
	public void setPrefix(String prefix) {
		if (prefix == null)
			prefix = "";
		this.prefix = prefix;
	}
	/**
	 * Get the prefix for the line, and strip out any color sequences
	 * @return The stripped prefix for the line
	 */
	public String getPrefix() {
		return cleanMessage(prefix);
	}
	/**
	 * Get the prefix for the line formatted with html(and colors) 
	 * @return an HTML formatted prefix
	 */
	public String getPrefixHTML() {
		Color c = new Color();
		if (this.prefixHTML==null) {
			c.setText(prefix,true);
			this.prefixHTML = c.toHTML();
		}
		return this.prefixHTML;
	}

	/**
	 * Set the content for the line
	 * @param message - The content for the line
	 */
	public void setMessage(String message) {
		if (message==null)
			message = "";
		this.message = message;
	}
	/**
	 * Get the message for the line, and strip out any color sequences
	 * @return The stripped message for the line
	 */
	public String getMessage() {
		return cleanMessage(message);
	}
	/**
	 * Get the message formatted with html(and colors)
	 * @return an HTML formatted message
	 */
	public String getMessageHTML() {
		Color c = new Color();
		if (this.messageHTML==null) {
			c.setText(message, true);
			this.messageHTML = c.toHTML();
		}
		return this.messageHTML;
	}

	/**
	 * Set the timestamp associated with the line
	 * @param time - The line's timestamp as a Java Date object
	 */
	public void setTimestamp(Date time) {
		this.time = time;
	}
	/**
	 * Get the timestamp associated with the line as a Java Date object
	 */
	public Date getTimestamp() {
		return this.time;
	}
	/**
	 * Get the timestamp for the line formatted as a string according to DATEFORMAT
	 * @return A string representation of the timestamp
	 */
	public String getTimestampStr() {
		if (this.timeStr == null)
			this.timeStr = DATEFORMAT.format(time);
		return this.timeStr;
	}
	
	/**
	 * Set whether this line should be visible or not
	 * @param displayed - Whether to show the line or not
	 */
	public void setVisible(boolean displayed) {
		this.visible = displayed;
	}
	/**
	 * Get whether the line should be visible or not
	 * @return Whether the line is visible or not
	 */
	public boolean getVisible() {
		return this.visible;
	}

	/**
	 * Strips out all of the weechat specific color codes from a string
	 * @param msg - The message to be cleaned of color codes
	 * @return The message without any color codes
	 */
	private String cleanMessage(String msg) {
		return Color.stripColors(msg);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[" + DATEFORMAT.format(time) + "] ");
		sb.append(cleanMessage(prefix));
		sb.append(" | ");
		sb.append(cleanMessage(message));
		return sb.toString();
	}
	/**
	 * Set whether the line was a 'highlight' and should be noticed by the user
	 * @param highlight - Whether the line is highlighted or not
	 */
	public void setHighlight(boolean highlight) {
		this.highlight = highlight;
	}
	/**
	 * Returns whether a line is a 'highlight' or not
	 * @return Whether the line is a 'highlight' or not
	 */
	public boolean getHighlight() {
		return highlight;
	}
	/**
	 * A pointer to the hdata object for the line
	 * @param pointer
	 */
	public void setPointer(String pointer) {
		this.pointer = pointer;
	}
	/**
	 * Return the pointer that represents this line
	 */
	public String getPointer() {
		return this.pointer;
	}
}
