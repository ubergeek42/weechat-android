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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.InflaterInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ubergeek42.weechat.relay.protocol.Data;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Represents a message from the Weechat Relay Server
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayMessage {

    private static Logger logger = LoggerFactory.getLogger(RelayMessage.class);

    private ArrayList<RelayObject> objects = new ArrayList<RelayObject>();
    private boolean compressed = false;
    private int length = 0;
    private String id = null;

    protected RelayMessage(byte[] data) {
        Data wd = new Data(data); // Load the data into our consumer

        // Get total message length
        length = wd.getUnsignedInt();

        // Determine compression ratio
        int c = wd.getByte();
        if (c == 0x00) {
            compressed = false;
        } else if (c == 0x01) {
            compressed = true;
            try {
                // System.out.println("[WMessage.constructor] Decompressing data");
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(
                        wd.getByteArray()));
                byte b[] = new byte[256];
                while (is.available() == 1) {
                    int r = is.read(b);
                    if (r < 0) {
                        break;
                    }
                    bout.write(b, 0, r);
                }
                data = bout.toByteArray();
                wd = new Data(data);
                // System.out.format("[WMessage.constructor] Data size: %d/%d\n", length,
                // data.length+5);// 5 is how much we've already read
            } catch (IOException e) {
                System.err.println("[WMessage.constructor] Failed to decompress data stream");
                e.printStackTrace();
            }

        } else {
            throw new RuntimeException("[WMessage.constructor] unknown compression type: "
                    + String.format("%02X", c));
        }

        // Optional data element
        id = wd.getString();

        // One or more objects at this point
        while (wd.empty() == false) {
            objects.add(wd.getObject());
        }
    }

    /**
     * Debug message for a WMessage
     */
    @Override
    public String toString() {
        String msg = String.format(
                "[WMessage.tostring]\n  Length: %d\n  Compressed: %s\n  ID: %s\n", length, ""
                        + compressed, id);
        for (RelayObject obj : objects) {
            msg += obj.toString() + "\n";
        }
        return msg;
    }

    /**
     * @return The ID associated with the message
     */
    public String getID() {
        return this.id;
    }

    /**
     * @return The set of objects in the message
     */
    public RelayObject[] getObjects() {
        RelayObject[] ret = new RelayObject[objects.size()];
        ret = objects.toArray(ret);
        return ret;
    }
}
