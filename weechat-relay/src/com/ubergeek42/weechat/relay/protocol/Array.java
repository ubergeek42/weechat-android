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

import android.util.Log;

public class Array extends RelayObject {

	private final ArrayList<RelayObject> array = new ArrayList<RelayObject>();

	private final WType arrayType; // One of: Integer, String, Pointer, Buffer, Time
	private int arraySize;

	protected Array(WType arrayType, int size) {
		this.arrayType = arrayType;
		this.setArraySize(size);
	}

	protected void add(RelayObject value) {
		array.add(value);
	}

	public RelayObject get(int index) {
		return array.get(index);
	}

	public int length() {
		return arraySize;
	}
	public void setArraySize(int arraySize) {
		this.arraySize = arraySize;
	}

	public String[] asStringArray() {
		String[] ret = new String[length()];
		for (int i = 0; i < length(); i++) {
			ret[i] = array.get(i).asString();
		}
		return ret;
	}
	/**
	 * Debug toString
	 */
	@Override
	public String toString() {
		StringBuilder map = new StringBuilder();
		for(RelayObject value: array) {
			map.append(value);
			map.append(", ");
		}
		return map.toString();
	}


}
