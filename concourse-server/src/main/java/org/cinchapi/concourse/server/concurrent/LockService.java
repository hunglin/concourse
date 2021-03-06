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
package org.cinchapi.concourse.server.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * A global service that provides ReadLock and WriteLock instances for a given
 * {@link Token}. The locks that are returned from this service can be used to
 * lock <em>notions of things</em> that aren't strictly defined in their own
 * right (i.e. a {@code key} in a {@code record})
 * <p>
 * <strong>WARNING</strong>: If the caller requests a lock for a given token,
 * but does not attempt to grab it immediately, then it is possible that
 * subsequent requests for locks identified by the same token will return
 * different instances. This is unlikely to happen in practice, but it is
 * recommended that lock grabs happen immediately after lock requests just to be
 * safe (e.g <code>LockService.getReadLock(key, record).lock()</code>).
 * </p>
 * 
 * @author jnelson
 */
public final class LockService {

    /**
     * Return the ReadLock that is identified by {@code objects}. Every caller
     * requesting a lock for {@code token} is guaranteed to get the same
     * instance if the lock is currently held by a reader of a writer.
     * 
     * @param objects
     * @return the ReadLock
     */
    public static ReadLock getReadLock(Object... objects) {
        return getReadLock(Token.wrap(objects));
    }

    /**
     * Return the ReadLock that is identified by {@code token}. Every caller
     * requesting a lock for {@code token} is guaranteed to get the same
     * instance if the lock is currently held by a reader of a writer.
     * 
     * @param token
     * @return the ReadLock
     */
    public static ReadLock getReadLock(Token token) {
        return CACHE.get(token).readLock();
    }

    /**
     * Return the WriteLock that is identified by {@code objects}. Every caller
     * requesting a lock for {@code token} is guaranteed to get the same
     * instance if the lock is currently held by a reader of a writer.
     * 
     * @param objects
     * @return the WriteLock
     */
    public static WriteLock getWriteLock(Object... objects) {
        return getWriteLock(Token.wrap(objects));
    }

    /**
     * Return the WriteLock that is identified by {@code token}. Every caller
     * requesting a lock for {@code token} is guaranteed to get the same
     * instance if the lock is currently held by a reader of a writer.
     * 
     * @param token
     * @return the WriteLock
     */
    public static WriteLock getWriteLock(Token token) {
        return CACHE.get(token).writeLock();

    }

    /**
     * This is a global cache that is responsible for returning the same lock
     * instance for a given token. This cache will periodically evict lock
     * instances that are not currently held by any readers or writers.
     */
    @SuppressWarnings("serial")
    private final static Map<Token, ReentrantReadWriteLock> CACHE = new HashMap<Token, ReentrantReadWriteLock>() {
        @Override
        public ReentrantReadWriteLock get(Object key) {
            if(!containsKey(key)) {
                final Token token = (Token) key;
                put(token, new TReentrantReadWriteLock(token));
            }
            return super.get(key);
        }
    };

    /**
     * A custom {@link ReentrantReadWriteLock} that is defined by a
     * {@link Token}.
     * 
     * @author jnelson
     */
    @SuppressWarnings("serial")
    private static final class TReentrantReadWriteLock extends
            ReentrantReadWriteLock {

        private final Token token;

        /**
         * Construct a new instance.
         * 
         * @param token
         */
        public TReentrantReadWriteLock(Token token) {
            this.token = token;
        }

        @Override
        public ReadLock readLock() {
            return new ReadLock(this) {

                @Override
                public void unlock() {
                    super.unlock();
                    if(!TReentrantReadWriteLock.this.isWriteLocked()
                            && TReentrantReadWriteLock.this.getReadLockCount() == 0) {
                        CACHE.remove(token);
                    }
                }

            };
        }

        @Override
        public WriteLock writeLock() {
            return new WriteLock(this) {

                @Override
                public void unlock() {
                    super.unlock();
                    if(!TReentrantReadWriteLock.this.isWriteLocked()
                            && TReentrantReadWriteLock.this.getReadLockCount() == 0) {
                        CACHE.remove(token);
                    }
                }

            };
        }

    }

    private LockService() {}

}
