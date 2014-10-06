package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.ubergeek42.weechat.relay.JschLogger;

import java.net.Socket;
import java.util.regex.Pattern;

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
                    sshSession.setUserInfo(new WeechatUserInfo());
                sshSession.setConfig("StrictHostKeyChecking", "no");
                sshSession.connect();
                sshSession.setPortForwardingL(sshLocalPort, server, port);
            } catch (Exception e) {
                e.printStackTrace();
                notifyHandlersOfError(e);
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
                notifyHandlersOfError(e);
            }
        }
    });
    //TODO: override disconnect/other methods to make sure the tunnel is closed/cleaned up nicely

    private class WeechatUserInfo implements UserInfo, UIKeyboardInteractive {
        public String getPassphrase() { return null; }
        public String getPassword() { return sshPassword; }
        public boolean promptPassphrase(String message) { return false; }
        public boolean promptPassword(String message) { return true; }
        public boolean promptYesNo(String message) { return false; }
        public void	showMessage(String message) { }
        public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
            String[] response = null;

            Pattern passwordPrompt = Pattern.compile("(?i)password[^:]*:");
            if (prompt.length==1 && !echo[0] && passwordPrompt.matcher(prompt[0]).find()) {
                response = new String[1];
                response[0] = sshPassword;
            }

            return response;
        }
    }
}
