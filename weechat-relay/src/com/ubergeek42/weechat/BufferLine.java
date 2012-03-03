package com.ubergeek42.weechat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BufferLine {
	private static DateFormat df = new SimpleDateFormat("HH:mm:ss");
	
	private String message;
	private String prefix;
	private Date time;
	private boolean visible;

	// TODO: consider caching the "cleaned" values as well
	
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setTimestamp(Date time) {
		this.time = time;
	}
	public void setVisible(boolean displayed) {
		this.visible = displayed;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[" + df.format(time) + "] ");
		sb.append(cleanMessage(prefix));
		sb.append(" | ");
		sb.append(cleanMessage(message));
		return sb.toString();
	}
	
	public String getTimestampStr() {
		return df.format(time);
	}
	public String getMessage() {
		return cleanMessage(message);
	}

	public String getPrefix() {
		return cleanMessage(prefix);
	}
	
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

	

	

	
}
