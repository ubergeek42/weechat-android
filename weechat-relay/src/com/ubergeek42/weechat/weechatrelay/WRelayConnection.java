package com.ubergeek42.weechat.weechatrelay;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

import com.ubergeek42.weechat.Helper;
import com.ubergeek42.weechat.weechatrelay.protocol.WData;

/**
 * Class to provide and manage a connection to a weechat relay server
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class WRelayConnection {

	private Socket sock = null;
	private String password = null;
	private InetAddress server = null;
	private int port;
	
	private OutputStream outstream = null;
	private InputStream instream = null;
	
	private HashMap<String,WMessageHandler> handlers = new HashMap<String, WMessageHandler>();
	
	private boolean connected = false;
	
	/**
	 * Sets up a connection to a weechat relay server
	 * @param server - server to connect to(ip or hostname)
	 * @param port - port to connect on
	 * @param password - password for the relay server
	 */
	public WRelayConnection(String server, String port, String password) {
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
	public void addHandler(String id, WMessageHandler wmh) {
		handlers.put(id, wmh);
	}
	/**
	 * Connects to the server.  On success isConnected() will return true.
	 * On failure, prints a stack trace...
	 * TODO: proper error handling(should throw an exception)
	 */
	public void connect() {
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
	}
	/**
	 * Disconnects from the server, and cleans up
	 */
	public void disconnect() {
		try {
			// Send quit message
			if (connected) outstream.write("quit\n".getBytes());
			
			connected = false;
			if (instream!=null)  { instream.close();  instream=null; }
			if (outstream!=null) { outstream.close(); outstream=null; }
			if (sock!=null)      { sock.close();      sock=null; }
			
			if (socketReader.isAlive()) {
				// TODO: kill the thread if necessary
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
	 * Reads data from the socket, breaks it into messages, and dispatches the handlers
	 */
	private Thread socketReader = new Thread(new Runnable() {
		public void run() {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			while(!sock.isClosed()) {
				byte b[] = new byte[256];
				try {
					int r = instream.read(b);
					if (r>0)
						buffer.write(b,0,r);
					
					while (buffer.size() >=4) {
						// Calculate length
						
						int length = new WData(buffer.toByteArray()).getUnsignedInt();
						
						// Still have more message to read
						if (buffer.size() < length) break;
						
						// We have a full message, so let's do something with it
						byte[] bdata = buffer.toByteArray();
						byte[] msgdata = Helper.copyOfRange(bdata, 0, length);
						byte[] remainder = Helper.copyOfRange(bdata, length, bdata.length);
						WMessage wm = new WMessage(msgdata);
						
						handleMessage(wm);
						
						// Reset the buffer, and put back any additional data
						buffer.reset();
						buffer.write(remainder);
					}
					
				} catch (IOException e) {
					if (!sock.isClosed()) {
						e.printStackTrace();
						connected = false;
					} else {
						// Socket closed..no big deal
						connected = false;
					}
				}
			}
		}
	});

	/**
	 * Signal any observers whenever we receive a message
	 * @param msg - Message we received
	 */
	private void handleMessage(WMessage msg) {
		String id = msg.getID();
		if (handlers.containsKey(id)) {
			WMessageHandler wmh = handlers.get(id);
			wmh.handleMessage(msg, id);
		}
	}
	
	

}
