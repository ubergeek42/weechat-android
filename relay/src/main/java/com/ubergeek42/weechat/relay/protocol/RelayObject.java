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

import java.util.Arrays;
import java.util.Date;

/**
 * An object contained in a Weechat Relay Message
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayObject {

    public enum WType {
        CHR, INT, LON, STR, BUF, PTR, TIM, HTB, HDA, INF, INL, ARR, UNKNOWN
    }

    private char charValue;
    private int intValue;
    private long longValue;
    private String strValue;
    private byte[] baValue;
    private Array arrayValue;

    protected WType type = WType.UNKNOWN;

    protected RelayObject() {
        // Does nothing
    }

    protected RelayObject(char c) {
        charValue = c;
        type = WType.CHR;
    }

    protected RelayObject(int i) {
        intValue = i;
        type = WType.INT;
    }

    protected RelayObject(long l) {
        longValue = l;
        type = WType.LON;
    }

    protected RelayObject(String s) {
        strValue = s;
        type = WType.STR;
    }

    protected RelayObject(byte[] b) {
        baValue = b;
        type = WType.BUF;
    }

    public RelayObject(Array array) {
        arrayValue = array;
        type = WType.ARR;
    }

    protected void setType(WType t) {
        type = t;
    }

    /**
     * Throws an exception if the object type doesn't match the argument
     * 
     * @param t
     *            - the wype we expect/want from this Object
     */
    private void checkType(WType t) {
        if (type != t) {
            throw new RuntimeException("Cannont convert from " + type + " to " + t);
        }
    }

    public WType getType() {
        return type;
    }

    /**
     * @return The char representation of an object
     */
    public char asChar() {
        checkType(WType.CHR);
        return charValue;
    }

    // same but signed
    public byte asByte() {
        return (byte) asChar();
    }

    /**
     * @return The unsigned integer representation of the object
     */
    public int asInt() {
        checkType(WType.INT);
        return intValue;
    }

    /**
     * @return The long integer representation of the object TODO: see if this needs to be changed
     *         to a BigInteger
     */
    public long asLong() {
        checkType(WType.LON);
        return longValue;
    }

    /**
     * @return The string representation of the object
     */
    public String asString() {
        checkType(WType.STR);
        return strValue;
    }

    /**
     * @return A byte array representation of the object
     */
    public byte[] asBytes() {
        checkType(WType.BUF);
        return baValue;
    }

    /**
     * @return An array representation of the object
     */
    public Array asArray() {
        checkType(WType.ARR);
        return arrayValue;
    }

    /**
     * @return A string representing a pointer(e.g. 0xDEADBEEF)
     */
    public String asPointer() {
        checkType(WType.PTR);
        return strValue;
    }


    public long asPointerLong() {
        try {
            return Long.parseUnsignedLong(asPointer().substring(2), 16);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * @return A Date representation of the object
     */
    public Date asTime() {
        checkType(WType.TIM);
        return new Date(longValue * 1000);
    }

    /**
     * Debug string representation of the object
     */
    @Override
    public String toString() {
        String value = "Unknown";
        switch (type) {
        case CHR:
            value = String.format("0x%02x", (int) asChar());
            break;
        case INT:
            value = "" + asInt();
            break;
        case LON:
            value = "" + asLong();
            break;
        case STR:
            value = asString();
            break;
        case TIM:
            value = "" + asTime();
            break;
        case PTR:
            value = "" + asPointer();
            break;
        case BUF:
            value = "" + Arrays.toString(asBytes());
            break; // Need a better printer for a byte buffer
        case ARR:
            value = "" + asArray();
            break;
        }
        // return String.format("%s -> %s", type, value);
        return String.format("%s", value);
    }

}
