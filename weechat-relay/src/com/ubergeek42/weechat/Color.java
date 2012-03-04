package com.ubergeek42.weechat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Color {
	private static Logger logger = LoggerFactory.getLogger(Color.class);
	// Default weechat colors...00-16
	private static final String weechatColors[] = new String[]{
		"#D3D3D3",	// Grey
		"#000000",	// Black
		"#545454",	// Dark Gray
		"#DC143C",	// Dark Red
		"#FF0000",	// Light Red
		"#006400",	// Dark Green
		"#90EE90",	// Light Green
		"#A52A2A",	// Brown
		"#FFFF00",	// Yellow
		"#00008B",	// Dark Blue
		"#ADD8E6",	// Light Blue
		"#8B008B",	// Dark Magenta
		"#FF00FF",	// Light Magenta
		"#008B8B",	// Dark Cyan
		"#00FFFF",	// Cyan
		"#D3D3D3",	// Gray
		"#FFFFFF"	// White
	};
	private static final String weechatOptions[] = new String[] {
		"#000000", //# 0"default"
		"#000000", //# 1"chat",
		"#999999", //# 2"chat_time", 
		"#000000", //# 3"chat_time_delimiters", 
		"#FF6633", //# 4"chat_prefix_error", 
		"#990099", //# 5"chat_prefix_network", 
		"#000000", //# 6"chat_prefix_action", 
		"#00CC00", //# 7"chat_prefix_join", 
		"#CC0000", //# 8"chat_prefix_quit", 
		"#CC00FF", //# 9"chat_prefix_more", 
		"#330099", //# 10"chat_prefix_suffix", 
		"#000000", //# 11"chat_buffer", 
		"#000000", //# 12"chat_server", 
		"#000000", //# 13"chat_channel", 
		"#000000", //# 14"chat_nick", 
		"*#000000", //# 15"chat_nick_self", 
		"#000000", //# 16"chat_nick_other", 
		"#000000", //# 17 (nick1 -- obsolete)"", 
		"#000000", //# 18 (nick2 -- obsolete)"", 
		"#000000", //# 19 (nick3 -- obsolete)"", 
		"#000000", //# 20 (nick4 -- obsolete)"", 
		"#000000", //# 21 (nick5 -- obsolete)"", 
		"#000000", //# 22 (nick6 -- obsolete)"", 
		"#000000", //# 23 (nick7 -- obsolete)"", 
		"#000000", //# 24 (nick8 -- obsolete)"", 
		"#000000", //# 25 (nick9 -- obsolete)"", 
		"#000000", //# 26 (nick10 -- obsolete)"", 
		"#666666", //# 27"chat_host", 
		"#9999FF", //# 28"chat_delimiters", 
		"#3399CC", //# 29"chat_highlight", 
		"#000000", //# 30"chat_read_marker", 
		"#000000", //# 31"chat_text_found", 
		"#000000", //# 32"chat_value", 
		"#000000", //# 33"chat_prefix_buffer", 
		"#000000", //# 34"chat_tags", 
		"#000000", //# 35"chat_inactive_window", 
		"#000000", //# 36"chat_inactive_buffer", 
		"#000000"  //# 37"chat_prefix_buffer_inactive_buffer"
	};
	
	private static String extendedColors[] = new String[256];
	static {
		for(int i=0;i<256;i++) {
			extendedColors[i] = String.format("#%02x%02x%02x",i,i,i);
		}
	}
	
	private static final String FG_DEFAULT = weechatColors[0];
	private static final String BG_DEFAULT = weechatColors[1];
	
	
	private String msg;
	private int index;
	
	
	// Current state
	boolean bold = false;
	boolean reverse = false;
	boolean italic = false;
	boolean underline = false;
	String fgColor = weechatColors[0];
	String bgColor = weechatColors[1];
	
	public Color(String message) {
		this.msg = message;
		this.index = 0;
	}
	
	private char getChar() {
		if (index>=msg.length()) {
			return ' ';
		}
		return msg.charAt(index++);
	}
	private char peekChar() {
		if (index>=msg.length()) {
			return ' ';
		}
		return msg.charAt(index);
	}
	
	private String getWeechatOptions() {
		char c1 = getChar();
		char c2 = getChar();
		int color = Integer.parseInt("" + c1 + c2);
		
		return weechatOptions[color];
	}
	
	// Returns a string for a given weechat standard color
	private String getWeechatColor() {
		char c1 = getChar();
		char c2 = getChar();
		int color = Integer.parseInt("" + c1 + c2);
		
		return weechatColors[color];
	}
	// Returns a string for a given extended color
	private String getExtendedColor() {
		char c1 = getChar();
		char c2 = getChar();
		char c3 = getChar();
		char c4 = getChar();
		char c5 = getChar();
		int color = Integer.parseInt("" + c1 + c2 + c3 + c4 + c5);
		
		return extendedColors[color];
	}
	
	private String getColor() {
		if (peekChar()=='@') {
			getChar(); // consume the @

			consumeAttributes();
			return getExtendedColor();
		} else { // standard color is 2 digits
			consumeAttributes();
			return getWeechatColor();
		}
	}
	
	private void consumeAttributes() {
		// Consume attributes
		char c = peekChar();
		while (c == '*' || c=='!' || c=='/' || c=='_' || c=='|') {
			getChar(); // consume the item
			setAttribute(c);
			c = peekChar(); // peek at the next character
		}
	}
	
	private void setAttribute(char c) {
		switch(c) {
			case '*': // Bold
				bold=true;break;
			case '!': // Reverse(Unsupported)
				reverse=true;break;
			case '/': // Italics
				italic=true;break;
			case '_': // Underline
				underline=true;break;
			case '|': // Keep attributes
				break;
		}
	}
	private void removeAttribute(char c) {
		switch(c) {
			case '*': // Bold
				bold=false;break;
			case '!': // Reverse(Unsupported)
				reverse=false;break;
			case '/': // Italics
				italic=false;break;
			case '_': // Underline
				underline=false;break;
			case '|': // Keep attributes
				break;
		}
	}

	private void getFormatString() {
		fgColor = getColor();
		if (peekChar() == ',') {
			getChar();
			bgColor = getColor();
		}
	}
	private String getHTMLTag() {
		return getHTMLTag(true);
	}
	private String getHTMLTag(boolean closeTag) {
		String ret;
		/*
		String attribs = "";
		attribs += String.format("color:%s;", fgColor);
		attribs += String.format("background-color:%s;", bgColor);
		if (italic) attribs += "font-style:italic;";
		if (bold) attribs += "font-weight:bold;";
		
		if (closeTag)
			ret = String.format("</span><span style=\"%s\">", attribs);
		else
			ret = String.format("<span style=\"%s\">", attribs);
		return ret;
		*/
		if (closeTag)
			ret = String.format("</font><font color=\"%s\">", fgColor);
		else
			ret = String.format("<font color=\"%s\">", fgColor);
		return ret;
	}

	public String toHTML() {
		if(msg==null) return msg;
		StringBuffer html = new StringBuffer(getHTMLTag(false));

		char c;
		
		while(index < msg.length()) {
			if (peekChar()==0x1C) {
				getChar();
				// reset attributes and color(doesn't consume anything else)
				fgColor = FG_DEFAULT;
				bgColor = BG_DEFAULT;
				bold = false;
				reverse = false;
				italic = false;
				underline = false;
				
				html.append(getHTMLTag());
				continue;
			} else if (peekChar()==0x1A) { // set attribute
				getChar();
				c = getChar();
				setAttribute(c);
				
				html.append(getHTMLTag());
				continue;
			} else if (peekChar()==0x1B) { // Remove attribute
				getChar();
				c = getChar();
				removeAttribute(c);
				
				html.append(getHTMLTag());
				continue;
			} else if (peekChar()==0x19) {
				getChar();
				
				if(peekChar()==0x1C) {// reset color
					getChar();
					fgColor = FG_DEFAULT;
					bgColor = BG_DEFAULT;
					html.append(getHTMLTag());
					continue; 
				}
				
				if (peekChar()=='b') { // Only for bar items; ignore
					getChar();
					c = getChar(); // consume an additional character
					continue;
				}
				
				if (peekChar()=='F') { // Set foreground color+attributes
					getChar();
					getFormatString();
					html.append(getHTMLTag());
				} else if (peekChar()=='B') { // Set background color +attributes
					getChar();
					getFormatString();
					html.append(getHTMLTag());
				} else if (peekChar()=='*') {
					getChar();
					getFormatString();
					html.append(getHTMLTag());
				} else {
					if (peekChar()=='@') {
						getChar();
						fgColor = getExtendedColor();
						// should map to ncurses pair, which doesn't exist
					} else {
						fgColor = getWeechatOptions();
					}
					html.append(getHTMLTag());
				}
			} else {
				// Not formatting or anything, so append it to the string
				html.append(getChar());
			}
		}
		html.append("</font>");
		//logger.debug("HTML for string: " + html.toString());
		return html.toString() ;
	}
	
	

	

	public String stripColors() {
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
