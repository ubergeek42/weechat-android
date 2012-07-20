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
package com.ubergeek42.weechat.relay;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.ubergeek42.weechat.Helper;
import com.ubergeek42.weechat.relay.protocol.Data;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Class to provide and manage a connection to a weechat relay server
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayConnection {

	public enum ConnectionType {
		STUNNEL, SSHTUNNEL, DEFAULT
	}

	private static Logger logger = LoggerFactory.getLogger(RelayConnection.class);
	
	private Socket sock = null;
	private String password = null;
	private String serverString = null;
	private InetAddress server = null;
	private int port;
	
	private OutputStream outstream = null;
	private InputStream instream = null;
	
	private HashMap<String,HashSet<RelayMessageHandler>> messageHandlers = new HashMap<String, HashSet<RelayMessageHandler>>();
	private ArrayList<RelayConnectionHandler> connectionHandlers = new ArrayList<RelayConnectionHandler>();
	
	private boolean connected = false;
	private boolean autoReconnect = false;
	
	private Thread currentConnection;
	
	/** Stunnel Settings */
	private String stunnelCert;
	private String stunnnelKeyPass;
	
	/** SSH Tunnel Settings */
	private Session sshSession;
	private String sshHost;
	private String sshUsername;
	private String sshPassword;
	private int sshPort = 22;
	private int sshLocalPort = 22231;
	
	/**
	 * Sets up a connection to a weechat relay server
	 * @param server - server to connect to(ip or hostname)
	 * @param port - port to connect on
	 * @param password - password for the relay server
	 */
	public RelayConnection(String server, String port, String password) {
		this.serverString = server;
		this.port = Integer.parseInt(port);
		this.password = password;
		
		currentConnection = createSocketConnection;
	}
	
	/**
	 * Sets the connection type(Currently supports Stunnel, SSH Tunnel, and normal socket connections)
	 * @param ct - The connection type(SSHTUNNEL, STUNNEL, or DEFAULT)
	 */
	public void setConnectionType(ConnectionType ct) {
		switch(ct) {
		case STUNNEL:
			logger.debug("Connection type set to: STUNNEL");
			currentConnection = createStunnelSocketConnection;
			break;
		case SSHTUNNEL:
			logger.debug("Connection type set to: SSH TUNNEL");
			currentConnection = createSSHTunnelSocketConnection;
			break;
		case DEFAULT:
		default:
			logger.debug("Connection type set to: DEFAULT");
			currentConnection = createSocketConnection;
		}
	}


	/**
	 * Sets whether to attempt reconnecting if disconnected
	 * @param autoreconnect - Whether to autoreconnect or not
	 */
	public void setAutoReconnect(boolean autoreconnect) {
		this.autoReconnect = autoreconnect;
	}

	
	/**
	 * @return The server we are connected to
	 */
	public String getServer() {
		return serverString;
	}
	
	/**
	 * Connects to the server.  On success isConnected() will return true.
	 * On failure, prints a stack trace...
	 * TODO: proper error handling(should throw an exception)
	 */
	public void connect() throws IOException {
		if (isConnected() || socketReader.isAlive()) return;
		
        server = InetAddress.getByName(serverString);

		currentConnection.start();
	}
	
	/**
	 * @return Whether we have a connection to the server
	 */
	public boolean isConnected() {
		return connected;
	}
	
	/**
	 * Disconnects from the server, and cleans up
	 */
	public void disconnect() {
		if (!connected) return;
		try {
			if (currentConnection.isAlive()) {
				currentConnection.interrupt();
			}
			
			if (connected) outstream.write("quit\n".getBytes());
			
			connected = false;
			if (instream!=null)  { instream.close();  instream=null; }
			if (outstream!=null) { outstream.close(); outstream=null; }
			if (sock!=null)      { sock.close();      sock=null; }
			
			if (socketReader.isAlive()) {
				// TODO: kill the thread if necessary
			}

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
	 * Register a connection handler to receive onConnected/onDisconnected events
	 * @param wrch - The connection handler
	 */
	public void setConnectionHandler(RelayConnectionHandler wrch) {
		connectionHandlers.add(wrch);
	}
	
	/**
	 * Does post connection setup(Sends initial commands/etc)
	 */
	private void postConnectionSetup() {
		connected = true;
		sendMsg(null, "init","password="+password+",compression=gzip");

		socketReader.start();
		
		// Call any registered connection handlers
		for (RelayConnectionHandler wrch : connectionHandlers) {
			wrch.onConnect();
		}
	}
	/**
	 * Connects to the server in a new thread, so we can interrupt it if we want to cancel the connection
	 */
	private Thread createSocketConnection = new Thread(new Runnable() {
		public void run() {
            // You only need to execute this code once
			try {
				sock = new Socket(server, port);
				outstream = sock.getOutputStream();
				instream = sock.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			postConnectionSetup();
			
			logger.trace("plain - createSocketConnection finished");
		}
	});
	
	/**
	 * Set the certificate to use when connecting to stunnel
	 * @param path - Path to the certificate
	 */
	public void setStunnelCert(String path) {
		stunnelCert = path;
	}
	/**
	 * Password to open the stunnel key
	 * @param pass
	 */
	public void setStunnelKey(String pass) {
		stunnnelKeyPass = pass;
	}
	/**
	 * Connects to the server(via stunnel) in a new thread, so we can interrupt it if we want to cancel the connection
	 */
	private Thread createStunnelSocketConnection = new Thread(new Runnable() {
		public void run() {
			//sock = new Socket(server, port);
            // You only need to execute this code once
            SSLContext context = null;
            KeyStore keyStore = null;
            TrustManagerFactory tmf = null;
            KeyStore keyStoreCA = null;
            KeyManagerFactory kmf = null;
			try {
				FileInputStream pkcs12in = new FileInputStream(new File(stunnelCert));
				
				context = SSLContext.getInstance("TLS");
                
				// Local client certificate and key and server certificate
				keyStore = KeyStore.getInstance("PKCS12");
				keyStore.load(pkcs12in, stunnnelKeyPass.toCharArray());
				
                // Build a TrustManager, that trusts only the server certificate
				keyStoreCA = KeyStore.getInstance("BKS");
				keyStoreCA.load(null, null);
				keyStoreCA.setCertificateEntry("Server", keyStore.getCertificate("Server"));
				tmf = TrustManagerFactory.getInstance("X509");
				tmf.init(keyStoreCA);
				
				// Build a KeyManager for Client auth
				kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(keyStore, null);
				context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				return;
			} catch (KeyStoreException e) {
				e.printStackTrace();
				return;
			} catch (CertificateException e) {
				e.printStackTrace();
				return;
			} catch (UnrecoverableKeyException e) {
				e.printStackTrace();
				return;
			} catch (KeyManagementException e) {
				e.printStackTrace();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

            SocketFactory socketFactory = context.getSocketFactory();
			try {
				sock = socketFactory.createSocket(server, port);
				outstream = sock.getOutputStream();
				instream = sock.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
				
			
			postConnectionSetup();
			logger.trace("Stunnel - createSocketConnection finished");
		}
	});
	
	/**
	 * Host to connect to via ssh
	 * @param host - Where to connect to
	 */
	public void setSSHHost(String host) {
		sshHost = host;
	}
	/**
	 * Username for ssh
	 * @param user - the user to connect as
	 */
	public void setSSHUsername(String user) {
		sshUsername = user;
	}
	/**
	 * Set the port to connect to with SSH
	 * @param port - the SSH port on the remote server
	 */
	public void setSSHPort(String port) {
		sshPort = Integer.parseInt(port);
	}
	/**
	 * Password for ssh
	 * @param pass
	 */
	public void setSSHPassword(String pass) {
		sshPassword = pass;
	}
	/**
	 * Connects to the server(via an ssh tunnel) in a new thread, so we can interrupt it if we want to cancel the connection
	 */
	private Thread createSSHTunnelSocketConnection = new Thread(new Runnable() {
		public void run() {
			// You only need to execute this code once
			try {
				JSch jsch = new JSch();
				sshSession = jsch.getSession(sshUsername, sshHost, sshPort);
				sshSession.setPassword(sshPassword);
				sshSession.setConfig("StrictHostKeyChecking", "no");
				sshSession.connect();
				sshSession.setPortForwardingL(sshLocalPort, serverString, port);
			} catch (JSchException e) {
				e.printStackTrace();
			}

			// Connect to the local SSH Tunnel
			try {
				sock = new Socket(InetAddress.getByName("127.0.0.1"), sshLocalPort);
				outstream = sock.getOutputStream();
				instream = sock.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			postConnectionSetup();
			logger.trace("SSH - createSocketConnection finished");
		}
	});
	
	/**
	 * Reads data from the socket, breaks it into messages, and dispatches the handlers
	 */
	private Thread socketReader = new Thread(new Runnable() {
		public void run() {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			while(sock!=null && !sock.isClosed()) {
				byte b[] = new byte[1024];
				try {
					int r = instream.read(b);
					if (r>0) {
						buffer.write(b,0,r);
					} else if (r==-1){ // Stream closed
						break;
					}
					
					while (buffer.size() >=4) {
						// Calculate length
						
						// TODO: wasteful...
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
						logger.trace("handleMessage took " + (System.currentTimeMillis()-start) + "ms(id: "+wm.getID()+")");
						
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
	 * Registers a handler to be called whenever a message is received
	 * @param id - The string ID to handle(e.g. "_nicklist" or "_buffer_opened")
	 * @param wmh - The object to receive the callback
	 */
	public void addHandler(String id, RelayMessageHandler wmh) {
		HashSet<RelayMessageHandler> currentHandlers = messageHandlers.get(id);
		if (currentHandlers == null)
			currentHandlers = new HashSet<RelayMessageHandler>();
		currentHandlers.add(wmh);
		messageHandlers.put(id, currentHandlers);
	}
	
	/**
	 * Signal any observers whenever we receive a message
	 * @param msg - Message we received
	 */
	private void handleMessage(RelayMessage msg) {
		String id = msg.getID();
		if (messageHandlers.containsKey(id)) {
			HashSet<RelayMessageHandler> handlers = messageHandlers.get(id);
			for (RelayMessageHandler rmh : handlers) { 
				if (msg.getObjects().length==0) {
					rmh.handleMessage(null, id);
				} else {
					for(RelayObject obj: msg.getObjects()) {
						rmh.handleMessage(obj, id);
					}
				}
			}
		} else {
			logger.debug("Unhandled message: " + id);
		}
	}
}
