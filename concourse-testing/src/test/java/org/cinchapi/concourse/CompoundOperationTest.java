/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Jeff Nelson, Cinchapi Software Collective
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.cinchapi.concourse.testing.Variables;
import org.cinchapi.concourse.time.Time;
import org.cinchapi.concourse.time.Timestamp;
import org.cinchapi.concourse.util.TestData;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Unit tests for all the compound api operations in {@link Concourse}.
 * 
 * @author jnelson
 */
public class CompoundOperationTest extends ConcourseIntegrationTest {
    
    @Test
    public void testPingMultiRecords(){
        Set<Long> records = getRecords();
        for(Boolean bool : client.ping(records).values()){
            Assert.assertFalse(bool);
        }
        populateKeyInRecords(TestData.getString(), records);
        for(Boolean bool : client.ping(records).values()){
            Assert.assertTrue(bool);
        }
    }

    @Test
    public void testAddMultiRecords() {
        Set<Long> records = Variables.register("records", getRecords());
        String key = Variables.register("key", TestData.getString());
        Object value = Variables.register("value", TestData.getObject());
        client.add(key, value, records);
        for (long record : records) {
            Assert.assertTrue(client.verify(key, value, record));
        }
    }

    @Test
    public void testClearMultiKeysMultiRecords() {
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        populateKeysInRecords(keys, records);
        client.clear(keys, records);
        for (String key : keys) {
            for (long record : records) {
                Assert.assertTrue(client.fetch(key, record).isEmpty());
            }
        }
    }

    @Test
    public void testClearMultiKeysSingleRecord() {
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        populateKeysInRecord(keys, record);
        client.clear(keys, record);
        for (String key : keys) {
            Assert.assertTrue(client.fetch(key, record).isEmpty());
        }
    }

    @Test
    public void testClearSingleKeyMultiRecords() {
        Set<Long> records = getRecords();
        String key = TestData.getString();
        populateKeyInRecords(key, records);
        client.clear(key, records);
        for (long record : records) {
            Assert.assertTrue(client.fetch(key, record).isEmpty());
        }
    }
    
    @Test
    public void testDescribeMultiRecords() {
        Set<Long> records = getRecords();
        Set<String> keys = getKeys();
        populateKeysInRecords(keys, records);
        Map<Long, Set<String>> result = client.describe(records);
        for (long record : result.keySet()) {
            Assert.assertEquals(keys, result.get(record));
        }
    }
    
    @Test
    public void testDescribeMultiRecordsWithTime() {
        Set<Long> records = getRecords();
        Set<String> keys = getKeys();
        populateKeysInRecords(keys, records);
        Timestamp timestamp = Timestamp.now();
        Set<String> newKeys = Sets.difference(getKeys(), keys);
        populateKeysInRecords(newKeys, records);
        Map<Long, Set<String>> result = client.describe(records, timestamp);
        for (long record : records) {
            Assert.assertEquals(keys, result.get(record));
        }
    }

    @Test
    public void testFetchMulitKeysMulitRecords() {
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecords(keys,
                records);
        Assert.assertEquals(data, client.fetch(keys, records));
    }
    
    @Test
    public void testRevertMultiKeysMultiRecords(){
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecords(keys,
                records);
        Timestamp timestamp = Timestamp.now();
        populateKeysInRecords(keys, records);
        client.revert(keys, records, timestamp);
        Assert.assertEquals(data, client.fetch(keys, records, timestamp));
    }

    @Test
    public void testFetchMulitKeysMultiRecordsWithTime() {
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecords(keys,
                records);
        Timestamp timestamp = Timestamp.now();
        populateKeysInRecords(keys, records);
        Assert.assertEquals(data, client.fetch(keys, records, timestamp));
    }

    @Test
    public void testFetchMultiKeysSingleRecord() {
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        Assert.assertEquals(populateKeysInRecord(keys, record).get(record),
                client.fetch(keys, record));
    }

    @Test
    public void testFetchMultiKeysSingleRecordWithTime() {
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecord(keys,
                record);
        Timestamp timestamp = Timestamp.now();
        populateKeysInRecord(keys, record);
        Assert.assertEquals(data.get(record),
                client.fetch(keys, record, timestamp));
    }
    
    @Test
    public void testRevertMultiKeysSingleRecord(){
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecord(keys,
                record);
        Timestamp timestamp = Timestamp.now();
        populateKeysInRecord(keys, record);
        client.revert(keys, record, timestamp);
        Assert.assertEquals(data.get(record),
                client.fetch(keys, record, timestamp));
    }

    @Test
    public void testFetchSingleKeyMultiRecords() {
        String key = TestData.getString();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeyInRecords(key,
                records);
        Map<Long, Set<Object>> result = client.fetch(key, records);
        for (long record : records) {
            Assert.assertEquals(data.get(record).get(key), result.get(record));
        }
    }

    @Test
    public void testFetchSingleKeyMultiRecordsWithTime() {
        String key = TestData.getString();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeyInRecords(key,
                records);
        Timestamp timestamp = Timestamp.now();
        populateKeyInRecords(key, records);
        Map<Long, Set<Object>> result = client.fetch(key, records, timestamp);
        for (long record : records) {
            Assert.assertEquals(data.get(record).get(key), result.get(record));
        }
    }
    
    @Test
    public void testRevertSingleKeyMultiRecords(){
        String key = TestData.getString();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeyInRecords(key,
                records);
        Timestamp timestamp = Timestamp.now();
        populateKeyInRecords(key, records);
        client.revert(key, records, timestamp);
        Map<Long, Set<Object>> result = client.fetch(key, records, timestamp);
        for (long record : records) {
            Assert.assertEquals(data.get(record).get(key), result.get(record));
        }
    }

    @Test
    public void testGetMultiKeysMultiRecords() {
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecords(keys,
                records);
        Map<Long, Map<String, Object>> result = client.get(keys, records);
        for (long record : records) {
            for (String key : keys) {
                Assert.assertEquals(
                        data.get(record).get(key).iterator().next(), result
                                .get(record).get(key));
            }
        }
    }
    
    @Test
    public void testGetMultiKeysMultiRecordsWithTime() {
        Set<String> keys = getKeys();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecords(keys,
                records);
        Timestamp timestamp = Timestamp.now();
        for (long record : records) {
            for (String key : keys) {
                client.remove(key, data.get(record).get(key).iterator().next(),
                        record);
            }
        }
        Map<Long, Map<String, Object>> result = client.get(keys, records,
                timestamp);
        for (long record : records) {
            for (String key : keys) {
                Assert.assertEquals(
                        data.get(record).get(key).iterator().next(), result
                                .get(record).get(key));
            }
        }
    }

    @Test
    public void testGetMultiKeysSingleRecord() {
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecord(keys,
                record);
        Map<String, Object> result = client.get(keys, record);
        for (String key : keys) {
            Assert.assertEquals(data.get(record).get(key).iterator().next(),
                    result.get(key));
        }

    }

    @Test
    public void testGetMultiKeysSingleRecordWithTime() {
        Set<String> keys = getKeys();
        long record = TestData.getLong();
        Map<Long, Map<String, Set<Object>>> data = populateKeysInRecord(keys,
                record);
        Timestamp timestamp = Timestamp.now();
        for (String key : keys) {
            client.remove(key, data.get(record).get(key).iterator().next(),
                    record);
        }
        Map<String, Object> result = client.get(keys, record, timestamp);
        for (String key : keys) {
            Assert.assertEquals(data.get(record).get(key).iterator().next(),
                    result.get(key));
        }
    }

    @Test
    public void testGetSingleKeyMultiRecords(){
        String key = TestData.getString();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeyInRecords(key,
                records);
        Map<Long, Object> result = client.get(key, records);
        for (long record : records) {
            Assert.assertEquals(data.get(record).get(key).iterator().next(),
                    result.get(record));
        }
    }

    @Test
    public void testGetSingleKeyMultiRecordsWithTime(){
        String key = TestData.getString();
        Set<Long> records = getRecords();
        Map<Long, Map<String, Set<Object>>> data = populateKeyInRecords(key,
                records);
        Timestamp timestamp = Timestamp.now();
        for (long record : records) {
            client.remove(key, data.get(record).get(key).iterator().next(),
                    record);
        }
        Map<Long, Object> result = client.get(key, records, timestamp);
        for (long record : records) {
            Assert.assertEquals(data.get(record).get(key).iterator().next(),
                    result.get(record));
        }
    }

    @Test
    public void testRemoveMultiRecords(){
        Set<Long> records = Variables.register("records", getRecords());
        String key = Variables.register("key", TestData.getString());
        Object value = Variables.register("value", TestData.getObject());
        client.add(key, value, records);
        client.remove(key, value, records);
        for (long record : records) {
            Assert.assertFalse(client.verify(key, value, record));
        }
    }
    
    @Test
    public void testSetMultiRecords(){
        Set<Long> records = Variables.register("records", getRecords());
        String key = Variables.register("key", TestData.getString());
        Object value = Variables.register("value", TestData.getObject());
        client.add(key, value, records);
        Object newValue = null;
        while(newValue == null || value.equals(newValue)){
            newValue = Variables.register("newValue", TestData.getObject());
        }
        client.set(key, newValue, records);
        for (long record : records) {
            Assert.assertTrue(client.verify(key, newValue, record));
        }
    }

    /**
     * Return a random list of keys.
     * 
     * @return the keys
     */
    private Set<String> getKeys() {
        Set<String> strings = Sets.newHashSet();
        for (int i = 0; i < TestData.getScaleCount(); i++) {
            String string = null;
            while (string == null || strings.contains(string)) {
                string = TestData.getString();
            }
            strings.add(string);
        }
        return strings;
    }

    /**
     * Return a random list of longs
     * 
     * @return the longs
     */
    private Set<Long> getRecords() {
        Set<Long> longs = Sets.newHashSet();
        for (long i = 0; i < TestData.getScaleCount(); i++) {
            longs.add(i);
        }
        return longs;
    }

    /**
     * Populate key in each of the {@code records} with random values.
     * 
     * @param key
     * @param records
     */
    private Map<Long, Map<String, Set<Object>>> populateKeyInRecords(
            String key, Collection<Long> records) {
        return populateKeysInRecords(Lists.newArrayList(key), records);
    }

    /**
     * Populate each of the {@code keys} in {@code record} with random values.
     * 
     * @param keys
     * @param record
     */
    private Map<Long, Map<String, Set<Object>>> populateKeysInRecord(
            Collection<String> keys, long record) {
        return populateKeysInRecords(keys, Lists.newArrayList(record));
    }

    /**
     * Populate each of the {@code keys} in each of the {@code records} with
     * random values.
     * 
     * @param keys
     * @param records
     */
    private Map<Long, Map<String, Set<Object>>> populateKeysInRecords(
            Collection<String> keys, Collection<Long> records) {
        Map<Long, Map<String, Set<Object>>> data = Maps.newLinkedHashMap();
        for (long record : records) {
            Map<String, Set<Object>> subdata = Maps.newLinkedHashMap();
            data.put(record, subdata);
            for (String key : keys) {
                Set<Object> values = Sets.newLinkedHashSet();
                subdata.put(key, values);
                int numValues = (TestData.getScaleCount() % 5) + 1;
                for (int i = 0; i < numValues; i++) {
                    Object value = null;
                    while (value == null || values.contains(value)) {
                        value = TestData.getObject();
                    }
                    client.add(key, value, record);
                    values.add(value);
                }
            }
        }
        return Variables.register("data" + Time.now(), data); // append
                                                              // timestamp in
                                                              // case this
                                                              // method is
                                                              // called multiple
                                                              // times in a test
    }
}