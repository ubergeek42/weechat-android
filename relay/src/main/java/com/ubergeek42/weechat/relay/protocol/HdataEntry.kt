/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ubergeek42.weechat.relay.protocol

/**
 * An Entry in an Hdata object. This is basically an associative array from String to WObject
 * 
 * @author ubergeek42<kj></kj>@ubergeek42.com>
 */
class HdataEntry : RelayObject() {
    private val pointers = ArrayList<String?>()
    private val data = HashMap<String?, RelayObject?>()

    fun addPointer(pointer: String?) {
        pointers.add(pointer)
    }

    fun addObject(key: String?, value: RelayObject?) {
        data.put(key, value)
    }

    /**
     * Debug print method
     * 
     * @param indent
     * - Number of spaces to indent the output
     * @return The string...
     */
    fun toString(indent: Int): String {
        var `is` = ""
        for (i in 0..<indent) {
            `is` += " "
        }

        var ret = String.format("%s[HdataEntry]\n", `is`)
        var pointerString = ""
        for (p in pointers) {
            pointerString += p + ", "
        }
        ret += String.format("%s  Pointers: %s\n", `is`, pointerString)

        for (k in data.keys) {
            ret += String.format("%s  %s=%s\n", `is`, k, data.get(k))
        }

        return ret
    }

    /**
     * Gets an element from the Hdata Entry
     * 
     * @param key
     * - The element to retrieve
     * @return The desired object(or null if not found)
     */
    fun getItem(key: String?): RelayObject? {
        return data.get(key)
    }

    val pointer: String?
        /**
         * @return The pointer to the object at the end of the hdata path
         */
        get() = pointers[pointers.size - 1]

    val pointerLong: Long
        get() {
            try {
                return java.lang.Long.parseUnsignedLong(this.pointer!!.substring(2), 16)
            } catch (e: Exception) {
                return -1
            }
        }

    /**
     * Returns a pointer from the hdata path
     * 
     * @param index
     * - Which element in the path to get the pointer for
     * @return The pointer to the chosen element(as a String
     */
    fun getPointer(index: Int): String? {
        return pointers.get(index)
    }

    fun getPointerLong(index: Int): Long {
        try {
            return java.lang.Long.parseUnsignedLong(getPointer(index)!!.substring(2), 16)
        } catch (e: Exception) {
            return -1
        }
    }

    fun getInt(id: String) = getItem(id)?.asInt()
    fun getChar(id: String) = getItem(id)?.asChar()
    fun getString(id: String) = getItem(id)?.asString()
    fun getHashtable(id: String) = getItem(id) as Hashtable // FIXME?

    fun getPointerLong(id: String) = getItem(id)?.asPointerLong()
    fun getArray(id: String) = getItem(id)?.asArray()

    fun getStringOrNull(id: String) = getItem(id)?.asString()
    fun getIntOrNull(id: String) = getItem(id)?.asInt()
    fun getByteOrNull(id: String) = getItem(id)?.asByte()

    fun getStringArrayOrNull(id: String): kotlin.Array<String>? = getItem(id)?.let {
        return@let if (it.type == RelayObject.WType.ARR) {
            it.asArray().asStringArray()
        } else {
            null
        }
    }
}
