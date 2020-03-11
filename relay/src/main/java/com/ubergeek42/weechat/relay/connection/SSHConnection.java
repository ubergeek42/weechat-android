package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.ubergeek42.weechat.relay.JschLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.regex.Pattern;

import static com.ubergeek42.weechat.relay.connection.RelayConnection.CONNECTION_TIMEOUT;

public class SSHConnection implements IConnection {
    final private String sshPassword;
    final private String hostname;
    final private int port;

    final private Session sshSession;

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
        if (!useKeyFile) sshSession.setUserInfo(new WeechatUserInfo());
    }

    @Override public Streams connect() throws IOException, JSchException {
        sshSession.connect(CONNECTION_TIMEOUT);
        int localPort = sshSession.setPortForwardingL(0, hostname, port);
        Socket forwardingSocket = new Socket("127.0.0.1", localPort);
        return new Streams(forwardingSocket.getInputStream(), forwardingSocket.getOutputStream());
    }

    @Override public void disconnect() {
        sshSession.disconnect();
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
}
