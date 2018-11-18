package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.ubergeek42.weechat.relay.JschLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Pattern;

public class SSHConnection implements IConnection {

    final private static Logger logger = LoggerFactory.getLogger("SSHConnection");


    final private Session sshSession;
    final private String sshPassword;

    final private String hostname;
    final private int port;

    final private Socket socket = new Socket();
    final private Socket forwardingSocket = new Socket();

    static {
        JSch.setConfig("PreferredAuthentications", "password,publickey");
        JSch.setLogger(new JschLogger());
    }

    public SSHConnection(String hostname, int port, String sshHostname, int sshPort, String sshUsername,
                         String sshPassword, byte[] sshKey, byte[] sshKnownHosts) throws JSchException {
        this.hostname = hostname;
        this.port = port;
        this.sshPassword = sshPassword;

        JSch jsch = new JSch();
        jsch.setKnownHosts(new ByteArrayInputStream(sshKnownHosts));

        boolean useKeyFile = sshKey != null && sshKey.length > 0;
        if (useKeyFile) jsch.addIdentity("key", sshKey, null, sshPassword.getBytes());

        sshSession = jsch.getSession(sshUsername, sshHostname, sshPort);
        sshSession.setSocketFactory(new SingleUseSocketFactory());
        if (!useKeyFile) sshSession.setUserInfo(new WeechatUserInfo());
    }

    @Override public Streams connect() throws IOException, JSchException {
        sshSession.connect();
        int localPort = sshSession.setPortForwardingL(0, hostname, port);
        forwardingSocket.connect(new InetSocketAddress("127.0.0.1", localPort));
        return new Streams(forwardingSocket.getInputStream(), forwardingSocket.getOutputStream());
    }

    @Override public void disconnect() throws IOException {
        logger.trace("disconnect()");
        sshSession.disconnect();
        Utils.closeAll(socket, forwardingSocket);
        logger.trace("disconnect()ed");
    }

    // this class is preferred than sshSession.setPassword()
    // as it provides a better password prompt matching on some systems
    final private static Pattern PASSWORD_PROMPT = Pattern.compile("(?i)password[^:]*:");

    private class WeechatUserInfo implements UserInfo, UIKeyboardInteractive {
        public String getPassphrase() {return null;}
        public String getPassword() {return sshPassword;}
        public boolean promptPassphrase(String message) {return false;}
        public boolean promptPassword(String message) {return true;}
        public boolean promptYesNo(String message) {return false;}
        public void	showMessage(String message) {}
        public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
            return (prompt.length == 1 && !echo[0] && PASSWORD_PROMPT.matcher(prompt[0]).find()) ?
                    new String[]{sshPassword} : null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class SingleUseSocketFactory implements SocketFactory {
        @Override public Socket createSocket(String host, int port) {
            try {
                socket.connect(new InetSocketAddress(host, port));
            } catch (IOException e) {
                // JSch doesn't expose the cause of exceptions raised by createSocket.
                // throw a RuntimeException so we know if we were interrupted or if
                // there was some other connection failure.
                throw new RuntimeException(e);
            }
            return socket;
        }

        @Override public InputStream getInputStream(Socket socket) throws IOException {
            return socket.getInputStream();
        }

        @Override public OutputStream getOutputStream(Socket socket) throws IOException {
            return socket.getOutputStream();
        }
    }
}
