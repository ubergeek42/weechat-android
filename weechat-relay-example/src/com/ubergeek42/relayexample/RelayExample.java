package com.ubergeek42.relayexample;

import com.ubergeek42.weechat.ChatBufferObserver;
import com.ubergeek42.weechat.ChatBuffers;
import com.ubergeek42.weechat.WeechatBuffer;
import com.ubergeek42.weechat.weechatrelay.WRelayConnection;
import com.ubergeek42.weechat.weechatrelay.WRelayConnectionHandler;

public class RelayExample implements ChatBufferObserver, WRelayConnectionHandler {
	static ChatBuffers cb = new ChatBuffers();
	private WRelayConnection wr;
	public static void main(String[] args) {
		new RelayExample().demo();
	}

	private void demo() {
		String server = "10.0.0.1";
		String port = "8001";
		String password = "testpassword";
		
		System.out.format("Attempting connection to %s:%s with password %s\n", server, port, password);
		wr = new WRelayConnection(server, port, password);
		wr.setConnectionHandler(this);
		wr.tryConnect();
	}

	@Override
	public void onConnect() {
		// Hook a handler for testing the infolist functionality
		wr.addHandler("infolist-test", new InfolistMessageHandler());
		wr.sendMsg("infolist-test","infolist", "buffer");
		
		// Hook a handler for testing the "info" functionality
		wr.addHandler("info-test", new InfoMessageHandler());
		wr.sendMsg("info-test", "info", "version");
		
		// Hook a handler for testing hdata functionality
		cb.onChanged(this);
		wr.addHandler("listbuffers", cb);
		wr.sendMsg("listbuffers","hdata","buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables");
		// Please view the source for ChatBuffers to see how this was handled
		// ChatBuffers also handles a bunch of other special event messages(such as _buffer_opened, or _buffer_closed)
		
		// Sleep a bit to get our messages, then quit
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Cleaning up");
		wr.disconnect();
	}

	@Override
	public void onDisconnect() {
		System.out.println("Disconnected...");
	}
	
	@Override
	public void onBuffersChanged() {
		System.out.println("[Buffer list]");
		for (int i=0;i<cb.getNumBuffers(); i++) {
			WeechatBuffer wb = cb.getBuffer(i);
			// Just print some simple information about the buffer
			System.out.println("  " + wb.getShortName() + " " + wb.getFullName());
		}
	}
}
