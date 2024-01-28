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
package com.ubergeek42.weechat.relay.protocol;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * An Entry in an Hdata object. This is basically an associative array from String to WObject
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public class HdataEntry extends RelayObject {
    private final ArrayList<String> pointers = new ArrayList<String>();
    private final HashMap<String, RelayObject> data = new HashMap<String, RelayObject>();

    protected void addPointer(String pointer) {
        pointers.add(pointer);
    }

    protected void addObject(String key, RelayObject value) {
        data.put(key, value);
    }

    /**
     * Debug print method
     * 
     * @param indent
     *            - Number of spaces to indent the output
     * @return The string...
     */
    public String toString(int indent) {
        String is = "";
        for (int i = 0; i < indent; i++) {
            is += " ";
        }

        String ret = String.format("%s[HdataEntry]\n", is);
        String pointerString = "";
        for (String p : pointers) {
            pointerString += p + ", ";
        }
        ret += String.format("%s  Pointers: %s\n", is, pointerString);

        for (String k : data.keySet()) {
            ret += String.format("%s  %s=%s\n", is, k, data.get(k));
        }

        return ret;
    }

    /**
     * Gets an element from the Hdata Entry
     * 
     * @param key
     *            - The element to retrieve
     * @return The desired object(or null if not found)
     */
    public RelayObject getItem(String key) {
        return data.get(key);
    }

    /**
     * @return The pointer to the object at the end of the hdata path
     */
    public String getPointer() {
        return pointers.get(pointers.size() - 1);
    }

    public long getPointerLong() {
        try {
            return Long.parseUnsignedLong(getPointer().substring(2), 16);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns a pointer from the hdata path
     * 
     * @param index
     *            - Which element in the path to get the pointer for
     * @return The pointer to the chosen element(as a String
     */
    public String getPointer(int index) {
        return pointers.get(index);
    }

    public long getPointerLong(int index) {
        try {
            return Long.parseUnsignedLong(getPointer(index).substring(2), 16);
        } catch (Exception e) {
            return -1;
        }
    }
}
