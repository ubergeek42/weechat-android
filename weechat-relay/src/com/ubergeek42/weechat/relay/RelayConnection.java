package com.ubergeek42.weechat.relay;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.Helper;
import com.ubergeek42.weechat.relay.protocol.Data;

/**
 * Class to provide and manage a connection to a weechat relay server
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayConnection {

	private static Logger logger = LoggerFactory.getLogger(RelayConnection.class);
	
	private Socket sock = null;
	private String password = null;
	private InetAddress server = null;
	private int port;
	
	private OutputStream outstream = null;
	private InputStream instream = null;
	
	private HashMap<String,RelayMessageHandler> messageHandlers = new HashMap<String, RelayMessageHandler>();
	private ArrayList<RelayConnectionHandler> connectionHandlers = new ArrayList<RelayConnectionHandler>();
	
	private boolean connected = false;
	
	/**
	 * Sets up a connection to a weechat relay server
	 * @param server - server to connect to(ip or hostname)
	 * @param port - port to connect on
	 * @param password - password for the relay server
	 */
	public RelayConnection(String server, String port, String password) {
		try {
			this.server = InetAddress.getByName(server);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		this.port = Integer.parseInt(port);
		this.password = password;
	}
	
	/**
	 * @return The server we are connected to
	 */
	public String getServer() {
		return server.getHostName();
	}
	
	/**
	 * @return Whether we have a connection to the server
	 */
	public boolean isConnected() {
		return connected;
	}
	
	/**
	 * Registers a handler to be called whenever a message is received
	 * @param id - The string ID to handle(e.g. "_nicklist" or "_buffer_opened")
	 * @param wmh - The object to receive the callback
	 */
	public void addHandler(String id, RelayMessageHandler wmh) {
		messageHandlers.put(id, wmh);
	}
	/**
	 * Connects to the server.  On success isConnected() will return true.
	 * On failure, prints a stack trace...
	 * TODO: proper error handling(should throw an exception)
	 */
	public void tryConnect() {
		createSocketConnection.start();
	}
	/**
	 * Register a connection handler to receive onConnected/onDisconnected events
	 * @param wrch - The connection handler
	 */
	public void setConnectionHandler(RelayConnectionHandler wrch) {
		connectionHandlers.add(wrch);
	}
	
	/**
	 * Disconnects from the server, and cleans up
	 */
	public void disconnect() {
		if (!connected) return;
		try {
			logger.trace("Interrupting create connectiong thread");
			if (createSocketConnection.isAlive()) {
				createSocketConnection.interrupt();
			}
			
			logger.trace("Sending quit message");
			if (connected) outstream.write("quit\n".getBytes());
			
			logger.trace("Closing streams");
			connected = false;
			if (instream!=null)  { instream.close();  instream=null; }
			if (outstream!=null) { outstream.close(); outstream=null; }
			if (sock!=null)      { sock.close();      sock=null; }
			
			if (socketReader.isAlive()) {
				// TODO: kill the thread if necessary
			}

			logger.trace("Calling any registered connection handlers");
			// Call any registered disconnect handlers
			for (RelayConnectionHandler wrch : connectionHandlers) {
				wrch.onDisconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Sends the specified message to the server
	 * @param msg - The message to send
	 */
	public void sendMsg(String msg) {
		if (!connected) return;
		msg = msg+"\n";
		try {
			outstream.write(msg.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Sends a message to the server
	 * @param id - id of the message
	 * @param command - command to send
	 * @param arguments - arguments for the command
	 */
	public void sendMsg(String id, String command, String arguments) {
		String msg;
		if (id==null)
			msg = String.format("%s %s", command, arguments);
		else 
			msg = String.format("(%s) %s %s",id,command, arguments);
		sendMsg(msg);
	}
	
	/**
	 * Connects to the server in a new thread, so we can interrupt it if we want to cancel the connection
	 */
	private Thread createSocketConnection = new Thread(new Runnable() {
		public void run() {
			logger.trace("createSocketConnection thread started");
			try {
				sock = new Socket(server, port);
				outstream = sock.getOutputStream();
				instream = sock.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			connected = true;
			sendMsg(null, "init","password="+password+",compression=gzip");
			
			socketReader.start();
			
			// Call any registered connection handlers
			for (RelayConnectionHandler wrch : connectionHandlers) {
				wrch.onConnect();
			}
			logger.trace("createSocketConnection finished");
		}
	});
	
	/**
	 * Reads data from the socket, breaks it into messages, and dispatches the handlers
	 */
	private Thread socketReader = new Thread(new Runnable() {
		public void run() {
			logger.trace("socketReader thread started");
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			while(sock!=null && !sock.isClosed()) {
				byte b[] = new byte[256];
				try {
					logger.trace("Reading from stream");
					int r = instream.read(b);
					logger.trace("Read " + r + " bytes from the stream"); 
					if (r>0) {
						buffer.write(b,0,r);
					} else if (r==-1){ // Stream closed
						break;
					}
					
					while (buffer.size() >=4) {
						// Calculate length
						
						int length = new Data(buffer.toByteArray()).getUnsignedInt();
						
						// Still have more message to read
						if (buffer.size() < length) break;
						
						logger.trace("socketReader got message, size: " + length);
						// We have a full message, so let's do something with it
						byte[] bdata = buffer.toByteArray();
						byte[] msgdata = Helper.copyOfRange(bdata, 0, length);
						byte[] remainder = Helper.copyOfRange(bdata, length, bdata.length);
						RelayMessage wm = new RelayMessage(msgdata);
						
						long start = System.currentTimeMillis();
						handleMessage(wm);
						logger.debug("handleMessage took " + (System.currentTimeMillis()-start) + "ms");
						
						// Reset the buffer, and put back any additional data
						buffer.reset();
						buffer.write(remainder);
					}
					
				} catch (IOException e) {
					if (sock!=null && !sock.isClosed()) {
						e.printStackTrace();
					} else {
						// Socket closed..no big deal
					}
				}
			}
			connected = false;
			sock = null;
			instream = null;
			outstream = null;
			// Call any registered disconnect handlers
			for (RelayConnectionHandler wrch : connectionHandlers) {
				wrch.onDisconnect();
			}
		}
	});

	/**
	 * Signal any observers whenever we receive a message
	 * @param msg - Message we received
	 */
	private void handleMessage(RelayMessage msg) {
		String id = msg.getID();
		if (messageHandlers.containsKey(id)) {
			RelayMessageHandler wmh = messageHandlers.get(id);
			wmh.handleMessage(msg, id);
		} else {
			logger.debug("Unhandled message: " + id);
		}
	}
	
	

}
