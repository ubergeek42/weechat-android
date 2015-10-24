/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay;

import com.jcraft.jsch.Logger;
import org.slf4j.LoggerFactory;

public class JschLogger implements Logger {
    final private static org.slf4j.Logger logger = LoggerFactory.getLogger("Jsch");

    @Override
    public boolean isEnabled(int arg0) {
        return true;
    }

    @Override
    public void log(int level, String message) {
        switch (level) {
            case DEBUG: logger.debug(message); break;
            case INFO: logger.info(message); break;
            case WARN: logger.warn(message); break;
            case ERROR: 
            case FATAL: logger.error(message); break;
        }
    }
}
