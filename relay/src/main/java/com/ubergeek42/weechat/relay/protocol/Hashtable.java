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

import java.util.HashMap;

/**
 * Hashtable implementation from Weechat See the following URL(s) for more information:
 * http://www.weechat.org/files/doc/devel/weechat_plugin_api.en.html#hashtables
 * http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_hashtable
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class Hashtable extends RelayObject {

    private HashMap<String, RelayObject> hashtable = new HashMap<String, RelayObject>();

    protected Hashtable(WType keyType, WType valueType) {
    }

    protected void put(RelayObject key, RelayObject value) {
        hashtable.put(key.toString(), value);
    }

    public RelayObject get(String key) {
        return hashtable.get(key);
    }

    /**
     * Debug toString
     */
    @Override
    public String toString() {
        StringBuilder map = new StringBuilder();
        for (String key : hashtable.keySet()) {
            RelayObject value = hashtable.get(key);
            map.append(key);
            map.append(" -> ");
            map.append(value);
            map.append(", ");
        }
        return map.toString();
    }
}
