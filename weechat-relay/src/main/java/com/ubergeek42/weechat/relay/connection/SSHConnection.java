/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.ubergeek42.weechat.relay.JschLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.util.regex.Pattern;

public class SSHConnection extends AbstractConnection {

    protected static Logger logger = LoggerFactory.getLogger("SSHConnection");

    private Session sshSession;
    private Socket sock;

    private String sshPassword;
    private String server;
    private int port;

    public SSHConnection(String server, int port, String sshHost, int sshPort, String sshUsername,
                         String sshPassword, byte[] sshKey, byte[] sshKnownHosts) throws JSchException {
        this.server = server;
        this.port = port;
        this.sshPassword = sshPassword;

        JSch.setLogger(new JschLogger());
        JSch jsch = new JSch();
        jsch.setKnownHosts(new ByteArrayInputStream(sshKnownHosts));
        jsch.setConfig("PreferredAuthentications", "password,publickey");
        boolean useKeyFile = sshKey != null && sshKey.length > 0;
        if (useKeyFile) jsch.addIdentity("key", sshKey, null, sshPassword.getBytes());
        sshSession = jsch.getSession(sshUsername, sshHost, sshPort);
        sshSession.setSocketFactory(new SocketChannelFactory());
        if (!useKeyFile) sshSession.setUserInfo(new WeechatUserInfo());
    }

    @Override protected void doConnect() throws Exception {
        try {
            sshSession.connect();
        } catch (RuntimeException e) {
            throw e.getCause() instanceof ClosedByInterruptException ?
                    (ClosedByInterruptException) e.getCause() : e;
        }

        int localPort = sshSession.setPortForwardingL(0, server, port);
        sock = new Socket("127.0.0.1", localPort);
        out = sock.getOutputStream();
        in = sock.getInputStream();
    }

    @Override public void doDisconnect() {
        super.doDisconnect();
        sshSession.disconnect();
        try {sock.close();} catch (IOException | NullPointerException ignored) {}
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
