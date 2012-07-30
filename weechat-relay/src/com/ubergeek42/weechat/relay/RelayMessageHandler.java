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

import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * An interface for receiving message callbacks from WRelayConnection
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public interface RelayMessageHandler {
    /**
     * Called each time a message is received
     * 
     * @param obj
     *            - A relay protocol object from the message
     * @param id
     *            - The id that triggered this callback
     */
    public abstract void handleMessage(RelayObject obj, String id);
}
