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

/**
 * Color class takes care of parsing WeeChat's own color codes in strings to diplay attributes (bold,underline) and
 * colors on screen. WeeChat's color codes get mapped to HTML color codes wrapped in a <code>font</code>-tag.

 * This class can also help with stripping attributes and colors from the String.
 *
 * See WeeChat dev document for more information: <a href="http://www.weechat.org/files/doc/devel/weechat_dev.en.html#color_codes_in_strings">here</a>
 */
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
		"#FFFFFF", //# 0"default"
		"#FFFFFF", //# 1"chat",
		"#999999", //# 2"chat_time", 
		"#FFFFFF", //# 3"chat_time_delimiters", 
		"#FF6633", //# 4"chat_prefix_error", 
		"#990099", //# 5"chat_prefix_network", 
		"#FFFFFF", //# 6"chat_prefix_action", 
		"#00CC00", //# 7"chat_prefix_join", 
		"#CC0000", //# 8"chat_prefix_quit", 
		"#CC00FF", //# 9"chat_prefix_more", 
		"#330099", //# 10"chat_prefix_suffix", 
		"#FFFFFF", //# 11"chat_buffer", 
		"#FFFFFF", //# 12"chat_server", 
		"#FFFFFF", //# 13"chat_channel", 
		"#FFFFFF", //# 14"chat_nick", 
		"*#FFFFFF", //# 15"chat_nick_self", 
		"#FFFFFF", //# 16"chat_nick_other", 
		"#FFFFFF", //# 17 (nick1 -- obsolete)"", 
		"#FFFFFF", //# 18 (nick2 -- obsolete)"", 
		"#FFFFFF", //# 19 (nick3 -- obsolete)"", 
		"#FFFFFF", //# 20 (nick4 -- obsolete)"", 
		"#FFFFFF", //# 21 (nick5 -- obsolete)"", 
		"#FFFFFF", //# 22 (nick6 -- obsolete)"", 
		"#FFFFFF", //# 23 (nick7 -- obsolete)"", 
		"#FFFFFF", //# 24 (nick8 -- obsolete)"", 
		"#FFFFFF", //# 25 (nick9 -- obsolete)"", 
		"#FFFFFF", //# 26 (nick10 -- obsolete)"", 
		"#666666", //# 27"chat_host", 
		"#9999FF", //# 28"chat_delimiters", 
		"#3399CC", //# 29"chat_highlight", 
		"#FFFFFF", //# 30"chat_read_marker", 
		"#FFFFFF", //# 31"chat_text_found", 
		"#FFFFFF", //# 32"chat_value", 
		"#FFFFFF", //# 33"chat_prefix_buffer", 
		"#FFFFFF", //# 34"chat_tags", 
		"#FFFFFF", //# 35"chat_inactive_window", 
		"#FFFFFF", //# 36"chat_inactive_buffer", 
		"#FFFFFF"  //# 37"chat_prefix_buffer_inactive_buffer"
	};
	
	private static String extendedColors[] = new String[256];
	static {
		// 16 basic terminal colors(from: http://docs.oracle.com/cd/E19728-01/820-2550/term_em_colormaps.html)
		extendedColors[ 0] = "#000000"; // Black
		extendedColors[ 1] = "#FF0000"; // Light Red
		extendedColors[ 2] = "#00FF00"; // Light Green
		extendedColors[ 3] = "#FFFF00"; // Yellow
		extendedColors[ 4] = "#0000FF"; // Light blue
		extendedColors[ 5] = "#FF00FF"; // Light magenta
		extendedColors[ 6] = "#00FFFF"; // Light cyan
		extendedColors[ 7] = "#FFFFFF"; // High White
		extendedColors[ 8] = "#808080"; // Gray
		extendedColors[ 9] = "#800000"; // Red
		extendedColors[10] = "#008000"; // Green
		extendedColors[11] = "#808000"; // Brown
		extendedColors[12] = "#000080"; // Blue
		extendedColors[13] = "#800080"; // Magenta
		extendedColors[14] = "#008080"; // Cyan
		extendedColors[15] = "#C0C0C0"; // White
		
		// Extended terminal colors, from colortest.vim: http://www.vim.org/scripts/script.php?script_id=1349
		int base[] = new int[]{0, 95, 135, 175, 215, 255};
		for(int i=16;i<232;i++) {
			int j=i-16;
			extendedColors[i] = String.format("#%02x%02x%02x",base[(j/36)%6], base[(j/6)%6], base[j%6]);
		}
		for(int i=232; i<256; i++) {
			extendedColors[i] = String.format("#%02x%02x%02x",8+i*10,8+i*10,8+i*10);
		}
	}
	
	private static final String FG_DEFAULT = weechatColors[0];
	private static final String BG_DEFAULT = weechatColors[1];
	
	private static Color singleton = new Color();
	
	private String msg;
	private int index;
	
	// Current state
	boolean bold = false;
	boolean reverse = false;
	boolean italic = false;
	boolean underline = false;
	String fgColor = weechatColors[0];
	String bgColor = weechatColors[1];
	
	public Color() {
		this.msg = "";
		this.index = 0;
	}

	public void setText(String message, boolean encode_html) {
		if (encode_html) {
			this.msg = encodeHTML(message);
		} else {
			this.msg = message;
		}
		this.index = 0;
	}
	
	private void resetState() {
		bold = false;
		reverse = false;
		italic = false;
		underline = false;
		fgColor = FG_DEFAULT;
		bgColor = BG_DEFAULT;
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

	public static String stripColors(String msg) {
		if(msg==null) return msg;
		singleton.resetState();
		singleton.setText(msg, false);
		String ret = singleton.parseColors(false);
		return ret.toString();
	}
	
	public String toHTML() {
		return parseColors(true);
	}
	
	public String parseColors(boolean insert_html) {
		if(msg==null) return msg;
		StringBuffer parsedMsg;
		if (insert_html) {
			parsedMsg = new StringBuffer(getHTMLTag(false));
		} else {
			parsedMsg = new StringBuffer();
		}

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
				
				if (insert_html) { parsedMsg.append(getHTMLTag()); }
				continue;
			} else if (peekChar()==0x1A) { // set attribute
				getChar();
				c = getChar();
				setAttribute(c);
				
				if (insert_html) { parsedMsg.append(getHTMLTag()); }
				continue;
			} else if (peekChar()==0x1B) { // Remove attribute
				getChar();
				c = getChar();
				removeAttribute(c);
				
				if (insert_html) { parsedMsg.append(getHTMLTag()); }
				continue;
			} else if (peekChar()==0x19) {
				getChar();
				
				if(peekChar()==0x1C) {// reset color
					getChar();
					fgColor = FG_DEFAULT;
					bgColor = BG_DEFAULT;
					if (insert_html) { parsedMsg.append(getHTMLTag()); }
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
					if (insert_html) { parsedMsg.append(getHTMLTag()); }
				} else if (peekChar()=='B') { // Set background color +attributes
					getChar();
					getFormatString();
					if (insert_html) { parsedMsg.append(getHTMLTag()); }
				} else if (peekChar()=='*') {
					getChar();
					getFormatString();
					if (insert_html) { parsedMsg.append(getHTMLTag()); }
				} else {
					if (peekChar()=='@') {
						getChar();
						fgColor = getExtendedColor();
						// should map to ncurses pair, which doesn't exist
					} else {
						fgColor = getWeechatOptions();
					}
					if (insert_html) { parsedMsg.append(getHTMLTag()); }
				}
			} else if (peekChar()==0x0F) { // warning: see unit test testStrip0x0FwithWeeChatStyle() in ColorTest (weechat-relay)
                getChar();
                fgColor = FG_DEFAULT;
                bgColor = BG_DEFAULT;
                reverse = false;
                italic = false;
                underline = false;
                if (insert_html) { parsedMsg.append(getHTMLTag()); }
                continue;
            } else {
				// Not formatting or anything, so append it to the string
				parsedMsg.append(getChar());
			}
		}
		if (insert_html) { parsedMsg.append("</font>"); }
		//logger.debug("HTML for string: " + html.toString());
		return parsedMsg.toString() ;
	}
	
	
	
	// Strips colors encoded with the standard irc method
	public static String stripIRCColors(String msg) {
		
		if(msg==null) return msg;
		StringBuffer cleaned = new StringBuffer();
		try {
		for(int i=0;i<msg.length();) {
			char c = msg.charAt(i++);

			if (c==0x02 || c==0x0F || c==0x11 || c==0x12 || c==0x16 || c==0x1D || c==0x1F) { // Bold, Color Reset, Fixed,  Reverse, Reverse2, Italic, Underline
				// do nothing with them
				continue;
			} else if (c==0x03) { // color follows this
				// 1 or 2 digit color
				if (Character.isDigit(msg.charAt(i))) {
					c = msg.charAt(i++);
					if (Character.isDigit(msg.charAt(i))) {
						c = msg.charAt(i++);
					}
				}
				// comma, then 1 or 2 digits
				if (msg.charAt(i) == ',' && Character.isDigit(msg.charAt(++i))) {
					c = msg.charAt(i++);
					if (Character.isDigit(msg.charAt(i))) {
						c = msg.charAt(i++);
					}
				}
				continue;
			}

			cleaned.append(c);
		}
		}catch(StringIndexOutOfBoundsException e) {
			// Ignored
		}
		return cleaned.toString();
	}

    /**
     * Strips colors encoded with the standard IRC methods AND weechats methods.
     *
     * @param message Message to gets it colors and attributes stripped from
     * @return Message Stripped message
     */
    public static String stripAllColorsAndAttributes(String message) {
        return Color.stripColors(Color.stripIRCColors(message));
    }
	
	/**
	 * Encode a string as HTML(Escaping the various special characters that are valid html)
	 * Slightly modified to escape ampersand as well...
	 * Taken from:
	 * 	http://stackoverflow.com/a/8838023
	 *  http://forums.thedailywtf.com/forums/p/2806/72054.aspx#72054
	 * @param s - String to escape
	 * @return A string safe to use in an HTML document
	 */
	private static String encodeHTML(String s)
	{
	    StringBuffer out = new StringBuffer();
	    for(int i=0; i<s.length(); i++)
	    {
	        char c = s.charAt(i);
	        if (c=='&') {
	        	out.append("&amp;");
	        } else if (c==' ' && (i-1>0 && s.charAt(i-1)==' ')) {
	        	out.append("&nbsp;");
	        } else if(c > 127 || c=='"' || c=='<' || c=='>') {
	           out.append("&#"+(int)c+";");
	        }
	        else
	        {
	            out.append(c);
	        }
	    }
	    return out.toString();
	}
}
