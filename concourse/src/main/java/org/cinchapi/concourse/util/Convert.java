/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013-2014 Jeff Nelson, Cinchapi Software Collective
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cinchapi.concourse.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.cinchapi.concourse.Link;
import org.cinchapi.concourse.annotate.UtilityClass;
import org.cinchapi.concourse.thrift.TObject;
import org.cinchapi.concourse.thrift.Type;

/**
 * A collection of functions to convert objects. The public API defined in
 * {@link Concourse} uses certain objects for convenience that are not
 * recognized by Thrift, so it is necessary to convert back and forth between
 * different representations.
 * 
 * @author jnelson
 */
@UtilityClass
public final class Convert {

    /**
     * Return the Thrift Object that represents {@code object}.
     * 
     * @param object
     * @return the TObject
     */
    public static TObject javaToThrift(Object object) {
        ByteBuffer bytes;
        Type type = null;
        if(object instanceof Boolean) {
            bytes = ByteBuffer.allocate(1);
            bytes.put((boolean) object ? (byte) 1 : (byte) 0);
            type = Type.BOOLEAN;
        }
        else if(object instanceof Double) {
            bytes = ByteBuffer.allocate(8);
            bytes.putDouble((double) object);
            type = Type.DOUBLE;
        }
        else if(object instanceof Float) {
            bytes = ByteBuffer.allocate(4);
            bytes.putFloat((float) object);
            type = Type.FLOAT;
        }
        else if(object instanceof Link) {
            bytes = ByteBuffer.allocate(8);
            bytes.putLong(((Link) object).longValue());
            type = Type.LINK;
        }
        else if(object instanceof Long) {
            bytes = ByteBuffer.allocate(8);
            bytes.putLong((long) object);
            type = Type.LONG;
        }
        else if(object instanceof Integer) {
            bytes = ByteBuffer.allocate(4);
            bytes.putInt((int) object);
            type = Type.INTEGER;
        }
        else {
            bytes = ByteBuffer.wrap(object.toString().getBytes(
                    StandardCharsets.UTF_8));
            type = Type.STRING;
        }
        bytes.rewind();
        return new TObject(bytes, type);
    }

    /**
     * Return the Java Object that represents {@code object}.
     * 
     * @param object
     * @return the Object
     */
    public static Object thriftToJava(TObject object) {
        Object java = null;
        ByteBuffer buffer = object.bufferForData();
        switch (object.getType()) {
        case BOOLEAN:
            java = ByteBuffers.getBoolean(buffer);
            break;
        case DOUBLE:
            java = buffer.getDouble();
            break;
        case FLOAT:
            java = buffer.getFloat();
            break;
        case INTEGER:
            java = buffer.getInt();
            break;
        case LINK:
            java = Link.to(buffer.getLong());
            break;
        case LONG:
            java = buffer.getLong();
            break;
        default:
            java = ByteBuffers.getString(buffer);
            break;
        }
        buffer.rewind();
        return java;
    }

    private Convert() {/* Utility Class */}

}
