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

/**
 * An Info item, basically a name and value pair
 * See the following URL for more information:
 *  http://www.weechat.org/files/doc/devel/weechat_relay_protocol.en.html#object_info
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 *
 */
public class Info extends RelayObject {
	private String value;
	private String name;

	protected Info(String name, String value) {
		this.name = name;
		this.value = value;
	}
	
	/**
	 * @return The name of the Info item
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return The value of the Info item
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * Debug toString
	 */
	public String toString() {
		return "[WInfo]:\n  " + name + " -> " + value;
	}
}
