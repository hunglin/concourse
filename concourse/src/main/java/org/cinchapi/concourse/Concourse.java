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
package org.cinchapi.concourse;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.cinchapi.concourse.annotate.CompoundOperation;
import org.cinchapi.concourse.config.ConcourseConfiguration;
import org.cinchapi.concourse.security.ClientSecurity;
import org.cinchapi.concourse.thrift.AccessToken;
import org.cinchapi.concourse.thrift.ConcourseService;
import org.cinchapi.concourse.thrift.Operator;
import org.cinchapi.concourse.thrift.TObject;
import org.cinchapi.concourse.thrift.TransactionToken;
import org.cinchapi.concourse.time.Time;
import org.cinchapi.concourse.util.Convert;
import org.cinchapi.concourse.util.TLinkedTableMap;
import org.cinchapi.concourse.util.Transformers;
import org.cinchapi.concourse.util.TLinkedHashMap;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * <p>
 * Concourse is a schemaless and distributed version control database with
 * optimistic availability, serializable transactions and full-text search.
 * Concourse provides a more intuitive approach to data management that is easy
 * to deploy, access and scale with minimal tuning while also maintaining the
 * referential integrity and ACID characteristics of traditional database
 * systems.
 * </p>
 * <h2>Data Model</h2>
 * <p>
 * The Concourse data model is lightweight and flexible which enables it to
 * support any kind of data at very large scales. Concourse trades unnecessary
 * structural notions of schemas, tables and indexes for a more natural modeling
 * of data based solely on the following concepts:
 * </p>
 * <p>
 * <ul>
 * <li><strong>Record</strong> &mdash; A logical grouping of data about a single
 * person, place, or thing (i.e. an object). Each {@code record} is a collection
 * of key/value pairs that are together identified by a unique primary key.
 * <li><strong>Key</strong> &mdash; An attribute that maps to a set of
 * <em>one or more</em> distinct {@code values}. A {@code record} can have many
 * different {@code keys}, and the {@code keys} in one {@code record} do not
 * affect those in another {@code record}.
 * <li><strong>Value</strong> &mdash; A dynamically typed quantity that is
 * mapped from a {@code key} in a {@code record}.
 * </ul>
 * </p>
 * <h4>Data Types</h4>
 * <p>
 * Concourse natively stores most of the Java primitives: boolean, double,
 * float, integer, long, and string (UTF-8). Otherwise, the value of the
 * {@link #toString()} method for the Object is stored.
 * </p>
 * <h4>Links</h4>
 * <p>
 * Concourse supports linking a {@code key} in one {@code record} to another
 * {@code record}. Links are one-directional, but it is possible to add two
 * links that are the inverse of each other to simulate bi-directionality (i.e.
 * link "friend" in Record 1 to Record 2 and link "friend" in Record 2 to Record
 * 1).
 * </p>
 * <h2>Transactions</h2>
 * <p>
 * By default, Concourse conducts every operation in {@code autocommit} mode
 * where every change is immediately written. Concourse also supports the
 * ability to stage a group of operations in transactions that are atomic,
 * consistent, isolated, and durable using the {@link #stage()},
 * {@link #commit()} and {@link #abort()} methods.
 * 
 * </p>
 * 
 * @author jnelson
 */
public abstract class Concourse {

    /**
     * Create a new Client connection using the details provided in
     * {@code concourse_client.prefs}. If the prefs file does not exist or does
     * not contain connection information, then the default connection details
     * ({@code admin@localhost:1717}) will be used.
     * 
     * @return the database handler
     */
    public static Concourse connect() {
        return new Client();
    }

    /**
     * Create a new Client connection for {@code username}@{@code host}:
     * {@code port} using {@code password}.
     * 
     * @param host
     * @param port
     * @param username
     * @param password
     * @return the database handler
     */
    public static Concourse connect(String host, int port, String username,
            String password) {
        return new Client(host, port, username, password);
    }

    /**
     * Discard any changes that are currently staged for commit.
     * <p>
     * After this function returns, Concourse will return to {@code autocommit}
     * mode and all subsequent changes will be committed immediately.
     * </p>
     */
    public abstract void abort();

    /**
     * Add {@code key} as {@code value} in each of the {@code records} if it is
     * not already contained.
     * 
     * @param key
     * @param value
     * @param records
     * @return a mapping from each record to a boolean indicating if
     *         {@code value} is added
     */
    @CompoundOperation
    public abstract Map<Long, Boolean> add(String key, Object value,
            Collection<Long> records);

    /**
     * Add {@code key} as {@code value} to {@code record} if it is not already
     * contained.
     * 
     * @param key
     * @param value
     * @param record
     * @return {@code true} if {@code value} is added
     */
    public abstract <T> boolean add(String key, T value, long record);

    /**
     * Audit {@code record} and return a log of revisions.
     * 
     * @param record
     * @return a mapping from timestamp to a description of a revision
     */
    public abstract Map<Timestamp, String> audit(long record);

    /**
     * Audit {@code key} in {@code record} and return a log of revisions.
     * 
     * @param key
     * @param record
     * @return a mapping from timestamp to a description of a revision
     */
    public abstract Map<Timestamp, String> audit(String key, long record);

    /**
     * Clear each of the {@code keys} in each of the {@code records} by removing
     * every value for each key in each record.
     * 
     * @param keys
     * @param records
     */
    @CompoundOperation
    public abstract void clear(Collection<String> keys, Collection<Long> records);

    /**
     * Clear each of the {@code keys} in {@code record} by removing every value
     * for each key.
     * 
     * @param keys
     * @param record
     */
    @CompoundOperation
    public abstract void clear(Collection<String> keys, long record);

    /**
     * Clear {@code key} in each of the {@code records} by removing every value
     * for {@code key} in each record.
     * 
     * @param key
     * @param records
     */
    @CompoundOperation
    public abstract void clear(String key, Collection<Long> records);

    /**
     * Atomically clear {@code key} in {@code record} by removing each contained
     * value.
     * 
     * @param record
     */
    public abstract void clear(String key, long record);

    /**
     * Attempt to permanently commit all the currently staged changes. This
     * function returns {@code true} if and only if all the changes can be
     * successfully applied. Otherwise, this function returns {@code false} and
     * all the changes are aborted.
     * <p>
     * After this function returns, Concourse will return to {@code autocommit}
     * mode and all subsequent changes will be written immediately.
     * </p>
     * 
     * @return {@code true} if all staged changes are successfully committed
     */
    public abstract boolean commit();

    /**
     * Create a new Record and return its Primary Key.
     * 
     * @return the Primary Key of the new Record
     */
    public abstract long create();

    /**
     * Describe each of the {@code records} and return a mapping from each
     * record to the keys that currently have at least one value.
     * 
     * @param records
     * @return the populated keys in each record
     */
    @CompoundOperation
    public abstract Map<Long, Set<String>> describe(Collection<Long> records);

    /**
     * Describe each of the {@code records} at {@code timestamp} and return a
     * mapping from each record to the keys that had at least one value.
     * 
     * @param records
     * @param timestamp
     * @return the populated keys in each record at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<Long, Set<String>> describe(Collection<Long> records,
            Timestamp timestamp);

    /**
     * Describe {@code record} and return the keys that currently have at least
     * one value.
     * 
     * @param record
     * @return the populated keys in {@code record}
     */
    public abstract Set<String> describe(long record);

    /**
     * Describe {@code record} at {@code timestamp} and return the keys that had
     * at least one value.
     * 
     * @param record
     * @param timestamp
     * @return the populated keys in {@code record} at {@code timestamp}
     */
    public abstract Set<String> describe(long record, Timestamp timestamp);

    /**
     * Close the Client connection.
     */
    public abstract void exit();

    /**
     * Fetch each of the {@code keys} from each of the {@code records} and
     * return a mapping from each record to a mapping from each key to the
     * contained values.
     * 
     * @param keys
     * @param records
     * @return the contained values for each of the {@code keys} in each of the
     *         {@code records}
     */
    @CompoundOperation
    public abstract Map<Long, Map<String, Set<Object>>> fetch(
            Collection<String> keys, Collection<Long> records);

    /**
     * Fetch each of the {@code keys} from each of the {@code records} at
     * {@code timestamp} and return a mapping from each record to a mapping from
     * each key to the contained values.
     * 
     * @param keys
     * @param records
     * @param timestamp
     * @return the contained values for each of the {@code keys} in each
     *         of the {@code records} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<Long, Map<String, Set<Object>>> fetch(
            Collection<String> keys, Collection<Long> records,
            Timestamp timestamp);

    /**
     * Fetch each of the {@code keys} from {@code record} and return a mapping
     * from each key to the contained values.
     * 
     * @param keys
     * @param record
     * @return the contained values for each of the {@code keys} in
     *         {@code record}
     */
    @CompoundOperation
    public abstract Map<String, Set<Object>> fetch(Collection<String> keys,
            long record);

    /**
     * Fetch each of the {@code keys} from {@code record} at {@code timestamp}
     * and return a mapping from each key to the contained values.
     * 
     * @param keys
     * @param record
     * @param timestamp
     * @return the contained values for each of the {@code keys} in
     *         {@code record} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<String, Set<Object>> fetch(Collection<String> keys,
            long record, Timestamp timestamp);

    /**
     * Fetch {@code key} from each of the {@code records} and return a mapping
     * from each record to contained values.
     * 
     * @param key
     * @param records
     * @return the contained values for {@code key} in each {@code record}
     */
    @CompoundOperation
    public abstract Map<Long, Set<Object>> fetch(String key,
            Collection<Long> records);

    /**
     * Fetch {@code key} from} each of the {@code records} at {@code timestamp}
     * and return a mapping from each record to the contained values.
     * 
     * @param key
     * @param records
     * @param timestamp
     * @return the contained values for {@code key} in each of the
     *         {@code records} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<Long, Set<Object>> fetch(String key,
            Collection<Long> records, Timestamp timestamp);

    /**
     * Fetch {@code key} from {@code record} and return all the contained
     * values.
     * 
     * @param key
     * @param record
     * @return the contained values
     */
    public abstract Set<Object> fetch(String key, long record);

    /**
     * Fetch {@code key} from {@code record} at {@code timestamp} and return the
     * set of values that were mapped.
     * 
     * @param key
     * @param record
     * @param timestamp
     * @return the contained values
     */
    public abstract Set<Object> fetch(String key, long record,
            Timestamp timestamp);

    /**
     * Find {@code key} {@code operator} {@code value} and return the set of
     * records that satisfy the criteria. This is analogous to the SELECT action
     * in SQL.
     * 
     * @param key
     * @param operator
     * @param value
     * @return the records that match the criteria
     */
    public abstract Set<Long> find(String key, Operator operator, Object value);

    /**
     * Find {@code key} {@code operator} {@code value} and {@code value2} and
     * return the set of records that satisfy the criteria. This is analogous to
     * the SELECT action in SQL.
     * 
     * @param key
     * @param operator
     * @param value
     * @param value2
     * @return the records that match the criteria
     */
    public abstract Set<Long> find(String key, Operator operator, Object value,
            Object value2);

    /**
     * Find {@code key} {@code operator} {@code value} and {@code value2} at
     * {@code timestamp} and return the set of records that satisfy the
     * criteria. This is analogous to the SELECT action in SQL.
     * 
     * @param key
     * @param operator
     * @param value
     * @param value2
     * @param timestamp
     * @return the records that match the criteria
     */
    public abstract Set<Long> find(String key, Operator operator, Object value,
            Object value2, Timestamp timestamp);

    /**
     * Find {@code key} {@code operator} {@code value} at {@code timestamp} and
     * return the set of records that satisfy the criteria. This is analogous to
     * the SELECT action in SQL.
     * 
     * @param key
     * @param operator
     * @param value
     * @return the records that match the criteria
     */
    public abstract Set<Long> find(String key, Operator operator, Object value,
            Timestamp timestamp);

    /**
     * Get each of the {@code keys} from each of the {@code records} and return
     * a mapping from each record to a mapping of each key to the first
     * contained value.
     * 
     * @param keys
     * @param records
     * @return the first contained value for each of the {@code keys} in each of
     *         the {@code records}
     */
    @CompoundOperation
    public abstract Map<Long, Map<String, Object>> get(Collection<String> keys,
            Collection<Long> records);

    /**
     * Get each of the {@code keys} from each of the {@code records} at
     * {@code timestamp} and return a mapping from each record to a mapping of
     * each key to the first contained value.
     * 
     * @param keys
     * @param records
     * @param timestamp
     * @return the first contained value for each of the {@code keys} in each of
     *         the {@code records} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<Long, Map<String, Object>> get(Collection<String> keys,
            Collection<Long> records, Timestamp timestamp);

    /**
     * Get each of the {@code keys} from {@code record} and return a mapping
     * from each key to the first contained value.
     * 
     * @param keys
     * @param record
     * @return the first contained value for each of the {@code keys} in
     *         {@code record}
     */
    @CompoundOperation
    public abstract Map<String, Object> get(Collection<String> keys, long record);

    /**
     * Get each of the {@code keys} from {@code record} at {@code timestamp} and
     * return a mapping from each key to the first contained value.
     * 
     * @param keys
     * @param record
     * @param timestamp
     * @return the first contained value for each of the {@code keys} in
     *         {@code record} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<String, Object> get(Collection<String> keys,
            long record, Timestamp timestamp);

    /**
     * Get {@code key} from each of the {@code records} and return a mapping
     * from each record to the first contained value.
     * 
     * @param key
     * @param records
     * @return the first contained value for {@code key} in each of the
     *         {@code records}
     */
    @CompoundOperation
    public abstract Map<Long, Object> get(String key, Collection<Long> records);

    /**
     * Get {@code key} from each of the {@code records} at {@code timestamp} and
     * return a mapping from each record to the first contained value.
     * 
     * @param key
     * @param records
     * @param timestamp
     * @return the first contained value for {@code key} in each of the
     *         {@code records} at {@code timestamp}
     */
    @CompoundOperation
    public abstract Map<Long, Object> get(String key, Collection<Long> records,
            Timestamp timestamp);

    /**
     * Get {@code key} from {@code record} and return the first contained value
     * or {@code null} if there is none. Compared to
     * {@link #fetch(String, long)}, this method is suited for cases when the
     * caller is certain that {@code key} in {@code record} maps to a single
     * value of type {@code T}.
     * 
     * @param key
     * @param record
     * @return the first contained value
     */
    public abstract <T> T get(String key, long record);

    /**
     * Get {@code key} from {@code record} at {@code timestamp} and return the
     * first contained value or {@code null} if there was none. Compared to
     * {@link #fetch(String, long, long)}, this method is suited for cases when
     * the caller is certain that {@code key} in {@code record} mapped to a
     * single value of type {@code T} at {@code timestamp}.
     * 
     * @param key
     * @param record
     * @param timestamp
     * @return the first contained value
     */
    public abstract <T> T get(String key, long record, Timestamp timestamp);

    /**
     * Return the version of the server to which this client is currently
     * connected.
     * 
     * @return the server version
     */
    public abstract String getServerVersion();

    /**
     * Link {@code key} in {@code source} to each of the {@code destinations}.
     * 
     * @param key
     * @param source
     * @param destinations
     * @return a mapping from each destination to a boolean indicating if the
     *         link was added
     */
    public abstract Map<Long, Boolean> link(String key, long source,
            Collection<Long> destinations);

    /**
     * Link {@code key} in {@code source} to {@code destination}.
     * 
     * @param key
     * @param source
     * @param destination
     * @return {@code true} if the link is added
     */
    public abstract boolean link(String key, long source, long destination);

    /**
     * Ping each of the {@code records}.
     * 
     * @param records
     * @return a mapping from each record to a boolean indicating if the record
     *         currently has at least one populated key
     */
    @CompoundOperation
    public abstract Map<Long, Boolean> ping(Collection<Long> records);

    /**
     * Ping {@code record}.
     * 
     * @param record
     * @return {@code true} if {@code record} currently has at least one
     *         populated key
     */
    public abstract boolean ping(long record);

    /**
     * Remove {@code key} as {@code value} in each of the {@code records} if it
     * is contained.
     * 
     * @param key
     * @param value
     * @param records
     * @return a mapping from each record to a boolean indicating if
     *         {@code value} is removed
     */
    @CompoundOperation
    public abstract Map<Long, Boolean> remove(String key, Object value,
            Collection<Long> records);

    /**
     * Remove {@code key} as {@code value} to {@code record} if it is contained.
     * 
     * @param key
     * @param value
     * @param record
     * @return {@code true} if {@code value} is removed
     */
    public abstract <T> boolean remove(String key, T value, long record);

    /**
     * Revert each of the {@code keys} in each of the {@code records} to
     * {@code timestamp} by creating new revisions that the relevant changes
     * that have occurred since {@code timestamp}.
     * 
     * @param keys
     * @param records
     * @param timestamp
     */
    @CompoundOperation
    public abstract void revert(Collection<String> keys,
            Collection<Long> records, Timestamp timestamp);

    /**
     * Revert each of the {@code keys} in {@code record} to {@code timestamp} by
     * creating new revisions that the relevant changes
     * that have occurred since {@code timestamp}.
     * 
     * @param keys
     * @param record
     * @param timestamp
     */
    @CompoundOperation
    public abstract void revert(Collection<String> keys, long record,
            Timestamp timestamp);

    /**
     * Revert {@code key} in each of the {@code records} to {@code timestamp} by
     * creating new revisions that the relevant changes that have occurred
     * since {@code timestamp}.
     * 
     * @param key
     * @param records
     * @param timestamp
     */
    @CompoundOperation
    public abstract void revert(String key, Collection<Long> records,
            Timestamp timestamp);

    /**
     * Atomically revert {@code key} in {@code record} to {@code timestamp} by
     * creating new revisions that undo the relevant changes that have
     * occurred since {@code timestamp}.
     * 
     * @param key
     * @param record
     * @param timestamp
     */
    public abstract void revert(String key, long record, Timestamp timestamp);

    /**
     * Search {@code key} for {@code query} and return the set of records that
     * match.
     * 
     * @param key
     * @param query
     * @return the records that match the query
     */
    public abstract Set<Long> search(String key, String query);

    /**
     * Set {@code key} as {@code value} in each of the {@code records}.
     * 
     * @param key
     * @param value
     * @param records
     */
    @CompoundOperation
    public abstract void set(String key, Object value, Collection<Long> records);

    /**
     * Atomically set {@code key} as {@code value} in {@code record}. This is a
     * convenience method that clears the values for {@code key} and adds
     * {@code value}.
     * 
     * @param key
     * @param value
     * @param record
     */
    public abstract <T> void set(String key, T value, long record);

    /**
     * Turn on {@code staging} mode so that all subsequent changes are
     * collected in a staging area before possibly being committed. Staged
     * operations are guaranteed to be reliable, all or nothing
     * units of work that allow correct recovery from failures and provide
     * isolation between clients so that Concourse is always in a consistent
     * state (e.g. a transaction).
     * <p>
     * After this method returns, all subsequent operations will be done in
     * {@code staging} mode until either {@link #abort()} or {@link #commit()}
     * is invoked.
     * </p>
     */
    public abstract void stage();

    /**
     * Remove link from {@code key} in {@code source} to {@code destination}.
     * 
     * @param key
     * @param source
     * @param destination
     * @return {@code true} if the link is removed
     */
    public abstract boolean unlink(String key, long source, long destination);

    /**
     * Verify {@code key} equals {@code value} in {@code record} and return
     * {@code true} if {@code value} is currently mapped from {@code key} in
     * {@code record}.
     * 
     * @param key
     * @param value
     * @param record
     * @return {@code true} if {@code key} equals {@code value} in
     *         {@code record}
     */
    public abstract boolean verify(String key, Object value, long record);

    /**
     * Verify {@code key} equaled {@code value} in {@code record} at
     * {@code timestamp} and return {@code true} if {@code value} was mapped
     * from {@code key} in {@code record}.
     * 
     * @param key
     * @param value
     * @param record
     * @param timestamp
     * @return {@code true} if {@code key} equaled {@code value} in
     *         {@code record} at {@code timestamp}
     */
    public abstract boolean verify(String key, Object value, long record,
            Timestamp timestamp);

    /**
     * Atomically verify {@code key} equals {@code expected} in {@code record}
     * and swap with {@code replacement}.
     * 
     * @param key
     * @param expected
     * @param record
     * @param replacement
     * @return {@code true} if the swap is successful
     */
    public abstract boolean verifyAndSwap(String key, Object expected,
            long record, Object replacement);

    /**
     * The implementation of the {@link Concourse} interface that establishes a
     * connection with the remote server and handles communication. This class
     * is a more user friendly wrapper around a Thrift
     * {@link ConcourseService.Client}.
     * 
     * @author jnelson
     */
    private final static class Client extends Concourse {

        // NOTE: The configuration variables are static because we want to
        // guarantee that they are set before the client connection is
        // constructed. Even though these variables are static, it is still the
        // case that any changes to the configuration will be picked up
        // immediately for new client connections.
        private static String SERVER_HOST;
        private static int SERVER_PORT;
        private static String USERNAME;
        private static String PASSWORD;
        static {
            ConcourseConfiguration config;
            try {
                config = ConcourseConfiguration
                        .loadConfig("concourse_client.prefs");
            }
            catch (Exception e) {
                config = null;
            }
            SERVER_HOST = "localhost";
            SERVER_PORT = 1717;
            USERNAME = "admin";
            PASSWORD = "admin";
            if(config != null) {
                SERVER_HOST = config.getString("host", SERVER_HOST);
                SERVER_PORT = config.getInt("port", SERVER_PORT);
                USERNAME = config.getString("username", USERNAME);
                PASSWORD = config.getString("password", PASSWORD);
            }
        }

        /**
         * Represents a request to respond to a query using the current state as
         * opposed to the history.
         */
        private static Timestamp now = Timestamp.fromMicros(0);

        /**
         * An encrypted copy of the username passed to the constructor.
         */
        private final ByteBuffer username;

        /**
         * An encrypted copy of the password passed to the constructor.
         */
        private final ByteBuffer password;

        /**
         * The host of the connection.
         */
        private final String host;

        /**
         * The port of the connection.
         */
        private final int port;

        /**
         * The Thrift client that actually handles all RPC communication.
         */
        private final ConcourseService.Client client;

        /**
         * The client keeps a copy of its {@link AccessToken} and passes it to
         * the
         * server for each remote procedure call. The client will
         * re-authenticate
         * when necessary using the username/password read from the prefs file.
         */
        private AccessToken creds = null;

        /**
         * Whenever the client starts a Transaction, it keeps a
         * {@link TransactionToken} so that the server can stage the changes in
         * the
         * appropriate place.
         */
        private TransactionToken transaction = null;

        /**
         * Create a new Client connection to the Concourse server specified in
         * {@code concourse.prefs} and return a handler to facilitate database
         * interaction.
         */
        public Client() {
            this(SERVER_HOST, SERVER_PORT, USERNAME, PASSWORD);
        }

        /**
         * Create a new Client connection to a Concourse server and return a
         * handler to facilitate database interaction.
         * 
         * @param host
         * @param port
         * @param username
         * @param password
         */
        public Client(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = ClientSecurity.encrypt(username);
            this.password = ClientSecurity.encrypt(password);
            final TTransport transport = new TSocket(host, port);
            try {
                transport.open();
                TProtocol protocol = new TBinaryProtocol(transport);
                client = new ConcourseService.Client(protocol);
                authenticate();
                Runtime.getRuntime().addShutdownHook(new Thread("shutdown") {

                    @Override
                    public void run() {
                        if(transaction != null && transport.isOpen()) {
                            abort();
                        }
                    }

                });
            }
            catch (TTransportException e) {
                throw new RuntimeException(
                        "Could not connect to the Concourse Server at " + host
                                + ":" + port);
            }
        }

        @Override
        public void abort() {
            execute(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    if(transaction != null) {
                        final TransactionToken token = transaction;
                        transaction = null;
                        client.abort(creds, token);
                    }
                    return null;
                }

            });
        }

        @Override
        public Map<Long, Boolean> add(String key, Object value,
                Collection<Long> records) {
            Map<Long, Boolean> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Result");
            for (long record : records) {
                result.put(record, add(key, value, record));
            }
            return result;
        }

        @Override
        public <T> boolean add(final String key, final T value,
                final long record) {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    return client.add(key, Convert.javaToThrift(value), record,
                            creds, transaction);
                }

            });
        }

        @Override
        public Map<Timestamp, String> audit(final long record) {
            return execute(new Callable<Map<Timestamp, String>>() {

                @Override
                public Map<Timestamp, String> call() throws Exception {
                    Map<Long, String> audit = client.audit(record, null, creds,
                            transaction);
                    return ((TLinkedHashMap<Timestamp, String>) Transformers
                            .transformMap(audit,
                                    new Function<Long, Timestamp>() {

                                        @Override
                                        public Timestamp apply(Long input) {
                                            return Timestamp.fromMicros(input);
                                        }

                                    })).setKeyName("DateTime").setValueName(
                            "Revision");
                }

            });
        }

        @Override
        public Map<Timestamp, String> audit(final String key, final long record) {
            return execute(new Callable<Map<Timestamp, String>>() {

                @Override
                public Map<Timestamp, String> call() throws Exception {
                    Map<Long, String> audit = client.audit(record, key, creds,
                            transaction);
                    return ((TLinkedHashMap<Timestamp, String>) Transformers
                            .transformMap(audit,
                                    new Function<Long, Timestamp>() {

                                        @Override
                                        public Timestamp apply(Long input) {
                                            return Timestamp.fromMicros(input);
                                        }

                                    })).setKeyName("DateTime").setValueName(
                            "Revision");
                }

            });
        }

        @Override
        public void clear(Collection<String> keys, Collection<Long> records) {
            for (long record : records) {
                for (String key : keys) {
                    clear(key, record);
                }
            }
        }

        @Override
        public void clear(Collection<String> keys, long record) {
            for (String key : keys) {
                clear(key, record);
            }
        }

        @Override
        public void clear(String key, Collection<Long> records) {
            for (long record : records) {
                clear(key, record);
            }
        }

        @Override
        public void clear(final String key, final long record) {
            execute(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    client.clear(key, record, creds, transaction);
                    return null;
                }

            });
        }

        @Override
        public boolean commit() {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    final TransactionToken token = transaction;
                    transaction = null;
                    return client.commit(creds, token);
                }

            });
        }

        @Override
        public long create() {
            return Time.now(); // TODO get a primary key using a plugin
        }

        @Override
        public Map<Long, Set<String>> describe(Collection<Long> records) {
            Map<Long, Set<String>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Keys");
            for (long record : records) {
                result.put(record, describe(record));
            }
            return result;
        }

        @Override
        public Map<Long, Set<String>> describe(Collection<Long> records,
                Timestamp timestamp) {
            Map<Long, Set<String>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Keys");
            for (long record : records) {
                result.put(record, describe(record, timestamp));
            }
            return result;
        }

        @Override
        public Set<String> describe(long record) {
            return describe(record, now);
        }

        @Override
        public Set<String> describe(final long record, final Timestamp timestamp) {
            return execute(new Callable<Set<String>>() {

                @Override
                public Set<String> call() throws Exception {
                    return client.describe(record, timestamp.getMicros(),
                            creds, transaction);
                }

            });
        }

        @Override
        public void exit() {
            client.getInputProtocol().getTransport().close();
            client.getOutputProtocol().getTransport().close();
        }

        @Override
        public Map<Long, Map<String, Set<Object>>> fetch(
                Collection<String> keys, Collection<Long> records) {
            TLinkedTableMap<Long, String, Set<Object>> result = TLinkedTableMap
                    .<Long, String, Set<Object>> newTLinkedTableMap("Record");
            for (long record : records) {
                for (String key : keys) {
                    result.put(record, key, fetch(key, record));
                }
            }
            return result;
        }

        @Override
        public Map<Long, Map<String, Set<Object>>> fetch(
                Collection<String> keys, Collection<Long> records,
                Timestamp timestamp) {
            TLinkedTableMap<Long, String, Set<Object>> result = TLinkedTableMap
                    .<Long, String, Set<Object>> newTLinkedTableMap("Record");
            for (long record : records) {
                for (String key : keys) {
                    result.put(record, key, fetch(key, record, timestamp));
                }
            }
            return result;
        }

        @Override
        public Map<String, Set<Object>> fetch(Collection<String> keys,
                long record) {
            Map<String, Set<Object>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Key", "Values");
            for (String key : keys) {
                result.put(key, fetch(key, record));
            }
            return result;
        }

        @Override
        public Map<String, Set<Object>> fetch(Collection<String> keys,
                long record, Timestamp timestamp) {
            Map<String, Set<Object>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Key", "Values");
            for (String key : keys) {
                result.put(key, fetch(key, record, timestamp));
            }
            return result;
        }

        @Override
        public Map<Long, Set<Object>> fetch(String key, Collection<Long> records) {
            Map<Long, Set<Object>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", key);
            for (long record : records) {
                result.put(record, fetch(key, record));
            }
            return result;
        }

        @Override
        public Map<Long, Set<Object>> fetch(String key,
                Collection<Long> records, Timestamp timestamp) {
            Map<Long, Set<Object>> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", key);
            for (long record : records) {
                result.put(record, fetch(key, record, timestamp));
            }
            return result;
        }

        @Override
        public Set<Object> fetch(String key, long record) {
            return fetch(key, record, now);
        }

        @Override
        public Set<Object> fetch(final String key, final long record,
                final Timestamp timestamp) {
            return execute(new Callable<Set<Object>>() {

                @Override
                public Set<Object> call() throws Exception {
                    Set<TObject> values = client.fetch(key, record,
                            timestamp.getMicros(), creds, transaction);
                    return Transformers.transformSet(values,
                            new Function<TObject, Object>() {

                                @Override
                                public Object apply(TObject input) {
                                    return Convert.thriftToJava(input);
                                }

                            });
                }

            });
        }

        @Override
        public Set<Long> find(String key, Operator operator, Object value) {
            return find(key, operator, value, now);
        }

        @Override
        public Set<Long> find(String key, Operator operator, Object value,
                Object value2) {
            return find(key, operator, value, value2, now);
        }

        @Override
        public Set<Long> find(final String key, final Operator operator,
                final Object value, final Object value2,
                final Timestamp timestamp) {
            return execute(new Callable<Set<Long>>() {

                @Override
                public Set<Long> call() throws Exception {
                    return client.find(key, operator, Lists.transform(
                            Lists.newArrayList(value, value2),
                            new Function<Object, TObject>() {

                                @Override
                                public TObject apply(Object input) {
                                    return Convert.javaToThrift(input);
                                }

                            }), timestamp.getMicros(), creds, transaction);
                }

            });
        }

        @Override
        public Set<Long> find(final String key, final Operator operator,
                final Object value, final Timestamp timestamp) {
            return execute(new Callable<Set<Long>>() {

                @Override
                public Set<Long> call() throws Exception {
                    return client.find(key, operator, Lists.transform(
                            Lists.newArrayList(value),
                            new Function<Object, TObject>() {

                                @Override
                                public TObject apply(Object input) {
                                    return Convert.javaToThrift(input);
                                }

                            }), timestamp.getMicros(), creds, transaction);
                }

            });
        }

        @Override
        public Map<Long, Map<String, Object>> get(Collection<String> keys,
                Collection<Long> records) {
            TLinkedTableMap<Long, String, Object> result = TLinkedTableMap
                    .<Long, String, Object> newTLinkedTableMap("Record");
            for (long record : records) {
                for (String key : keys) {
                    result.put(record, key, get(key, record));
                }
            }
            return result;
        }

        @Override
        public Map<Long, Map<String, Object>> get(Collection<String> keys,
                Collection<Long> records, Timestamp timestamp) {
            TLinkedTableMap<Long, String, Object> result = TLinkedTableMap
                    .<Long, String, Object> newTLinkedTableMap("Record");
            for (long record : records) {
                for (String key : keys) {
                    result.put(record, key, get(key, record, timestamp));
                }
            }
            return result;
        }

        @Override
        public Map<String, Object> get(Collection<String> keys, long record) {
            Map<String, Object> result = TLinkedHashMap.newTLinkedHashMap(
                    "Key", "Value");
            for (String key : keys) {
                result.put(key, get(key, record));
            }
            return result;
        }

        @Override
        public Map<String, Object> get(Collection<String> keys, long record,
                Timestamp timestamp) {
            Map<String, Object> result = TLinkedHashMap.newTLinkedHashMap(
                    "Key", "Value");
            for (String key : keys) {
                result.put(key, get(key, record, timestamp));
            }
            return result;
        }

        @Override
        public Map<Long, Object> get(String key, Collection<Long> records) {
            Map<Long, Object> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", key);
            for (long record : records) {
                result.put(record, get(key, record));
            }
            return result;
        }

        @Override
        public Map<Long, Object> get(String key, Collection<Long> records,
                Timestamp timestamp) {
            Map<Long, Object> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", key);
            for (long record : records) {
                result.put(record, get(key, record, timestamp));
            }
            return result;
        }

        @Override
        @Nullable
        public <T> T get(String key, long record) {
            return get(key, record, now);
        }

        @SuppressWarnings("unchecked")
        @Override
        @Nullable
        public <T> T get(String key, long record, Timestamp timestamp) {
            Set<Object> values = fetch(key, record, timestamp);
            if(!values.isEmpty()) {
                return (T) values.iterator().next();
            }
            return null;
        }

        @Override
        public String getServerVersion() {
            return execute(new Callable<String>() {

                @Override
                public String call() throws Exception {
                    return client.getServerVersion();
                }

            });
        }

        @Override
        public Map<Long, Boolean> link(String key, long source,
                Collection<Long> destinations) {
            Map<Long, Boolean> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Result");
            for (long destination : destinations) {
                result.put(destination, link(key, source, destination));
            }
            return result;
        }

        @Override
        public boolean link(String key, long source, long destination) {
            return add(key, Link.to(destination), source);
        }

        @Override
        public Map<Long, Boolean> ping(Collection<Long> records) {
            Map<Long, Boolean> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Result");
            for (long record : records) {
                result.put(record, ping(record));
            }
            return result;
        }

        @Override
        public boolean ping(final long record) {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    return client.ping(record, creds, transaction);
                }

            });
        }

        @Override
        public Map<Long, Boolean> remove(String key, Object value,
                Collection<Long> records) {
            Map<Long, Boolean> result = TLinkedHashMap.newTLinkedHashMap(
                    "Record", "Result");
            for (long record : records) {
                result.put(record, remove(key, value, record));
            }
            return result;
        }

        @Override
        public <T> boolean remove(final String key, final T value,
                final long record) {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    return client.remove(key, Convert.javaToThrift(value),
                            record, creds, transaction);
                }

            });
        }

        @Override
        public void revert(Collection<String> keys, Collection<Long> records,
                Timestamp timestamp) {
            for (long record : records) {
                for (String key : keys) {
                    revert(key, record, timestamp);
                }
            }
        }

        @Override
        public void revert(Collection<String> keys, long record,
                Timestamp timestamp) {
            for (String key : keys) {
                revert(key, record, timestamp);
            }

        }

        @Override
        public void revert(String key, Collection<Long> records,
                Timestamp timestamp) {
            for (long record : records) {
                revert(key, record, timestamp);
            }

        }

        @Override
        public void revert(final String key, final long record,
                final Timestamp timestamp) {
            execute(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    client.revert(key, record, timestamp.getMicros(), creds,
                            transaction);
                    return null;
                }

            });

        }

        @Override
        public Set<Long> search(final String key, final String query) {
            return execute(new Callable<Set<Long>>() {

                @Override
                public Set<Long> call() throws Exception {
                    return client.search(key, query, creds, transaction);
                }

            });
        }

        @Override
        public void set(String key, Object value, Collection<Long> records) {
            for (long record : records) {
                set(key, value, record);
            }
        }

        @Override
        public <T> void set(final String key, final T value, final long record) {
            execute(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    client.set0(key, Convert.javaToThrift(value), record,
                            creds, transaction);
                    return null;
                }

            });
        }

        @Override
        public void stage() {
            execute(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    transaction = client.stage(creds);
                    return null;
                }

            });
        }

        @Override
        public String toString() {
            return "Connected to " + host + ":" + port + " as "
                    + new String(ClientSecurity.decrypt(username).array());
        }

        @Override
        public boolean unlink(String key, long source, long destination) {
            return remove(key, Link.to(destination), source);
        }

        @Override
        public boolean verify(String key, Object value, long record) {
            return verify(key, value, record, now);
        }

        @Override
        public boolean verify(final String key, final Object value,
                final long record, final Timestamp timestamp) {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    return client.verify(key, Convert.javaToThrift(value),
                            record, timestamp.getMicros(), creds, transaction);
                }

            });
        }

        @Override
        public boolean verifyAndSwap(final String key, final Object expected,
                final long record, final Object replacement) {
            return execute(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    return client.verifyAndSwap(key,
                            Convert.javaToThrift(expected), record,
                            Convert.javaToThrift(replacement), creds,
                            transaction);
                }

            });
        }

        /**
         * Authenticate the {@link #username} and {@link #password} and populate
         * {@link #creds} with the appropriate AccessToken.
         */
        private void authenticate() {
            try {
                creds = client.login(ClientSecurity.decrypt(username),
                        ClientSecurity.decrypt(password));
            }
            catch (TException e) {
                throw Throwables.propagate(e);
            }
        }

        /**
         * Execute the task defined in {@code callable}. This method contains
         * retry logic to handle cases when {@code creds} expires and must be
         * updated.
         * 
         * @param callable
         * @return the task result
         */
        private <T> T execute(Callable<T> callable) {
            try {
                return callable.call();
            }
            catch (SecurityException e) {
                authenticate();
                return execute(callable);
            }
            catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

    }

}
