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
	public static final DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm:ss");
	
	private String message;
	private String prefix;
	private Date time;
	
	
	private String messageHTML = null;
	private String prefixHTML = null;
	private String timeStr = null;
	
	private boolean visible;
	
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
			c.setText(prefix);
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
			c.setText(message);
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
}
