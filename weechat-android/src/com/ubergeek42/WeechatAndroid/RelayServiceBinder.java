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
package com.ubergeek42.WeechatAndroid;

import android.os.Binder;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
/**
 * Provides functions that are available to clients of the relay service
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class RelayServiceBinder extends Binder {
	private RelayService relayService;
	
	public RelayServiceBinder(RelayService relayService) {
		this.relayService = relayService;
	}

	public void setHost(String host) { relayService.host = host; }
	public void setPort(String port) { relayService.port = port; }
	public void setPassword(String password) { relayService.pass = password; }
	
	public String getHost() { return relayService.host; }
	public String getPort() { return relayService.port; }
	public String getPassword() { return relayService.pass; }
	
	public boolean isConnected() {
		if (relayService.relayConnection == null) return false;
		return relayService.relayConnection.isConnected();
	}
	
	public void connect() {
		relayService.connect();
	}

	
	public void setRelayConnectionHandler(RelayConnectionHandler rch) {
		relayService.connectionHandler = rch;
	}
	
	
	/**
	 * Disconnect from the server and stop the background service
	 */
	public void shutdown() {
		relayService.shutdown();
	}

	/**
	 * Return a buffer object based on its full name
	 * @param bufferName - Full buffer name(e.g. irc.freenode.#weechat)
	 * @return a Buffer object for the given buffer
	 */
	public Buffer getBufferByName(String bufferName) {
		return relayService.bufferManager.findByName(bufferName);
	}

	/**
	 * Returns the BufferManager object for the current connection
	 * @return The BufferManager object
	 */
	public BufferManager getBufferManager() {
		return relayService.bufferManager;
	}

	/**
	 * Send a message to the server(expected to be formatted appropriately)
	 * @param string
	 */
	public void sendMessage(String string) {
		relayService.relayConnection.sendMsg(string);
	}

	public void resetNotifications() {
		relayService.resetNotification();
	}
	
	/**
	 * Subscribes to a buffer.  Gets the last MAXLINES of lines, and subscribes to nicklist changes
	 * @param bufferPointer
	 */
	public void subscribeBuffer(String bufferPointer) {
		Buffer buf = relayService.bufferManager.findByPointer(bufferPointer);
		// Get the last MAXLINES for each buffer(only if we don't already have at least MAXLINES)
		if (buf.getLines().size() < Buffer.MAXLINES)
			relayService.relayConnection.sendMsg("(listlines_reverse) hdata buffer:"+bufferPointer+"/own_lines/last_line(-" + Buffer.MAXLINES + ")/data date,displayed,prefix,message,highlight");
		// Get the nicklist for any buffers we have
		relayService.relayConnection.sendMsg("nicklist","nicklist",buf.getFullName());
		//relayService.relayConnection.sendMsg("sync "+buf.getFullName());
	}
	public void unsubscribeBuffer(String bufferPointer) {
		//Buffer buf = relayService.bufferManager.findByPointer(bufferPointer);
		//relayService.relayConnection.sendMsg("desync "+buf.getFullName()+" nicklist");
	}
}
