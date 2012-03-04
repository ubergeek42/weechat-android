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
	private boolean visible;
	// TODO: consider caching the color "cleaned" values as well
	
	/**
	 * Set the prefix for the line
	 * @param prefix - The prefix for the line
	 */
	public void setPrefix(String prefix) {
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
	 * Set the content for the line
	 * @param message - The content for the line
	 */
	public void setMessage(String message) {
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
		return DATEFORMAT.format(time);
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
		if(msg==null) return msg;
		StringBuffer cleaned = new StringBuffer();
		try {
		for(int i=0;i<msg.length();) {
			char c = msg.charAt(i++);
			
			if (c==0x1C) {
				// reset color(doesn't consume anything else)
				continue;
			} else if (c==0x1A || c==0x1B) {
				c = msg.charAt(i++);
				continue;
			} else if (c==0x19) {
				c = msg.charAt(i++);
				if(c==0x1C) continue; // reset color
				
				// Special code(related to bar things)
				if (c=='b') {
					c = msg.charAt(i++); // consume an additional character
					continue;
				}
				
				if (c=='F' || c=='B' || c=='*') {
					c = msg.charAt(i++);
				}
				
				
				// Extended color is 5 digits
				if (c=='@') {
					c=msg.charAt(i++);
					// Consume attributes
					while(c == '*' || c=='!' || c=='/' || c=='_' || c=='|') {
						c=msg.charAt(i++);
					}
					i+=5;
				} else { // standard color is 2 digits
					// consume attributes
					while(c == '*' || c=='!' || c=='/' || c=='_' || c=='|') {
						c=msg.charAt(i++);
					}
					i++;
				}
				c=msg.charAt(i++);
				
				if (c == ',') {
					// Extended color is 5 digits
					if (c=='@') {
						c=msg.charAt(i++);
						// Consume attributes
						while(c == '*' || c=='!' || c=='/' || c=='_' || c=='|') {
							c=msg.charAt(i++);
						}
						i+=5;
					} else { // standard color is 2 digits
						// consume attributes
						while(c == '*' || c=='!' || c=='/' || c=='_' || c=='|') {
							c=msg.charAt(i++);
						}
						i++;
					}
					c=msg.charAt(i++);
				}
				// TODO: probably a bug here is two 0x19's come right after one another
			}
			
			cleaned.append(c);
		}
		}catch(StringIndexOutOfBoundsException e) {
			// Ignored
		}
		return cleaned.toString();
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
