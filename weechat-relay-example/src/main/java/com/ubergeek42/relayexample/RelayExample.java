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
package com.ubergeek42.relayexample;

import java.io.IOException;
import java.util.LinkedList;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;
import com.ubergeek42.weechat.relay.messagehandler.LineHandler;
import com.ubergeek42.weechat.relay.messagehandler.NicklistHandler;

public class RelayExample implements BufferManagerObserver, RelayConnectionHandler {
	static BufferManager bufferManager = new BufferManager();
	private RelayConnection relay;
	public static void main(String[] args) throws IOException {
		new RelayExample().demo();
	}

	private void demo() throws IOException {
		String server = "127.0.0.1";
		String port = "8001";
		String password = "testpassword";
		
		System.out.format("Attempting connection to %s:%s with password %s\n", server, port, password);
		relay = new RelayConnection(server, port, password);
		relay.setConnectionHandler(this);
		relay.connect();
	}

	@Override
	public void onConnect() {
		relay.addHandler("test", new TestMessageHandler());
		relay.sendMsg("test","test","");
		
		/*
		// Hook a handler for testing hdata functionality
		bufferManager.onChanged(this);
		relay.addHandler("listbuffers", bufferManager);
		relay.sendMsg("listbuffers","hdata","buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables");
		// Please view the source for BufferManager to see how this was handled
		// BufferManager also handles a bunch of other special event messages(such as _buffer_opened, or _buffer_closed)
		
		// Hook for testing nicklists
		NicklistHandler nickHandler = new NicklistHandler(bufferManager);
		relay.addHandler("_nicklist", nickHandler);
		relay.addHandler("nicklist", nickHandler);
		relay.sendMsg("nicklist", "nicklist", "irc.freenode.#weechat");
		
		
		// Hook a handler for testing the infolist functionality
		relay.addHandler("infolist-test", new InfolistMessageHandler());
		relay.sendMsg("infolist-test","infolist", "buffer");
		
		// Hook a handler for testing the "info" functionality
		relay.addHandler("info-test", new InfoMessageHandler());
		relay.sendMsg("info-test", "info", "version");
		
		// Hook new lines that are received
		LineHandler msgHandler = new LineHandler(bufferManager);
		relay.addHandler("_buffer_line_added", msgHandler);
		// Request a list of last 5 lines from all buffers
		relay.addHandler("listlines_reverse", msgHandler);
		relay.sendMsg("listlines_reverse","hdata", "buffer:gui_buffers(*)/own_lines/last_line(-5)/data date,displayed,prefix,message");
		
		
		
		// Prints the messages for the first buffer
		Buffer b = bufferManager.getBuffer(0);
		LinkedList<BufferLine> lines = b.getLines();
		for(BufferLine bl: lines) {
			System.out.println(bl.toString());
		}
		//*/
		
		
		
		// Sleep a bit to get our messages, then quit
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
				
		System.out.println("Cleaning up");
		relay.disconnect();
		
		
	}

	@Override
	public void onDisconnect() {
		System.out.println("Disconnected...");
	}

    @Override
    public void onError(String err, Object o) {
        System.out.println(String.format("Error: %s", err));
    }

    @Override
	public void onBuffersChanged() {
		System.out.println("[Buffer list]");
		for (int i=0;i<bufferManager.getNumBuffers(); i++) {
			Buffer wb = bufferManager.getBuffer(i);
			// Just print some simple information about the buffer
			System.out.println("  " + wb.getShortName() + " " + wb.getFullName());
		}
	}
}
