package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.ubergeek42.weechat.relay.JschLogger;

import java.net.Socket;

public class SSHConnection extends AbstractConnection {
    /** SSH Tunnel Settings */
    private Session sshSession;
    private String sshHost;
    private String sshUsername;
    private String sshKeyFilePath = null;
    private String sshPassword;
    private int sshPort = 22;
    private int sshLocalPort = 22231;


    public SSHConnection(String server, int port) {
        this.server = server;
        this.port = port;
        this.connector = sshConnector;
    }

    /**
     * Host to connect to via ssh
     *
     * @param host - Where to connect to
     */
    public void setSSHHost(String host) {
        sshHost = host;
    }

    /**
     * Username for ssh
     *
     * @param user - the user to connect as
     */
    public void setSSHUsername(String user) {
        sshUsername = user;
    }

    /**
     * Set the port to connect to with SSH
     *
     * @param port - the SSH port on the remote server
     */
    public void setSSHPort(String port) {
        sshPort = Integer.parseInt(port);
    }

    /**
     * Password for ssh(either for the user or for the keyfile)
     *
     * @param pass
     */
    public void setSSHPassword(String pass) {
        sshPassword = pass;
    }

    /**
     * Path to an ssh private key to use
     * @param keyfile
     */
    public void setSSHKeyFile(String keyfile) {
        sshKeyFilePath = keyfile;
    }

    /**
     * Connects to the server(via an ssh tunnel) in a new thread, so we can interrupt it if we want
     * to cancel the connection
     */

    private Thread sshConnector = new Thread(new Runnable() {
        @Override
        public void run() {
            // You only need to execute this code once
            try {
                JSch.setLogger(new JschLogger());
                JSch jsch = new JSch();
                System.out.println("[KeyAuth] " + sshKeyFilePath + " - " + sshPassword);
                if (sshKeyFilePath != null && sshKeyFilePath.length()>0) {
                    jsch.addIdentity(sshKeyFilePath, sshPassword);
                }
                System.out.println("[identities] " + jsch.getIdentityNames());

                sshSession = jsch.getSession(sshUsername, sshHost, sshPort);

                if (sshKeyFilePath == null || sshKeyFilePath.length()==0)
                    sshSession.setPassword(sshPassword);
                sshSession.setConfig("StrictHostKeyChecking", "no");
                sshSession.connect();
                sshSession.setPortForwardingL(sshLocalPort, server, port);
            } catch (Exception e) {
                e.printStackTrace();
                notifyHandlers(STATE.ERROR);
                return;
            }

            // Connect to the local SSH Tunnel
            try {
                sock = new Socket("127.0.0.1", sshLocalPort);
                out_stream = sock.getOutputStream();
                in_stream = sock.getInputStream();
                connected = true;
                notifyHandlers(STATE.CONNECTED);
            } catch (Exception e) {
                e.printStackTrace();
                notifyHandlers(STATE.ERROR);
            }
        }
    });
    //TODO: override disconnect/other methods to make sure the tunnel is closed/cleaned up nicely
}
