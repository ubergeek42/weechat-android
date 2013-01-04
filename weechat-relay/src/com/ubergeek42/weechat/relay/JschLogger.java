package com.ubergeek42.weechat.relay;

import com.jcraft.jsch.Logger;

public class JschLogger implements Logger {

	@Override
	public boolean isEnabled(int arg0) {
		return true;
	}

	@Override
	public void log(int level, String message) {
		System.out.println("[Level "+level+"] " + message);
	}

}
