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
package org.cinchapi.concourse.server.storage.db;

import static org.cinchapi.concourse.server.GlobalState.STOPWORDS;

import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.annotation.concurrent.ThreadSafe;

import org.cinchapi.concourse.annotate.DoNotInvoke;
import org.cinchapi.concourse.annotate.PackagePrivate;
import org.cinchapi.concourse.server.concurrent.ConcourseExecutors;
import org.cinchapi.concourse.server.model.Position;
import org.cinchapi.concourse.server.model.PrimaryKey;
import org.cinchapi.concourse.server.model.Text;
import org.cinchapi.concourse.server.model.Value;
import org.cinchapi.concourse.server.storage.Action;
import org.cinchapi.concourse.thrift.Type;
import org.cinchapi.concourse.util.TStrings;

import com.beust.jcommander.internal.Sets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A Block that stores SearchRevision data to be used in a SearchRecord.
 * <p>
 * Text is indexed in a block such that that a value matches a query if it
 * contains a sequence of terms where each term or a substring of that term
 * matches the term in the same relative position of the query (i.e. if the
 * query is for 'fo ar' then value 'foo bar' will match, etc).
 * </p>
 * <p>
 * </p>
 * 
 * @author jnelson
 */
@ThreadSafe
@PackagePrivate
final class SearchBlock extends Block<Text, Text, Position> {

    /**
     * DO NOT CALL!!
     * 
     * @param id
     * @param directory
     * @param diskLoad
     */
    @PackagePrivate
    @DoNotInvoke
    SearchBlock(String id, String directory, boolean diskLoad) {
        super(id, directory, diskLoad);
    }

    /**
     * DO NOT CALL. Use {@link #insert(Text, Value, PrimaryKey)} instead.
     */
    @Override
    @DoNotInvoke
    public final SearchRevision insert(Text locator, Text key, Position value,
            long version, Action type) {
        throw new UnsupportedOperationException();
    }

    /**
     * Insert a revision for {@code key} as {@code value} in {@code record} at
     * {@code version}
     * 
     * @param key
     * @param value
     * @param record
     * @param version
     * @param type
     */
    /*
     * (non-Javadoc)
     * This method is synchronized because it spawns threads to asynchronously
     * call doInsert(), which invokes a super class method that grabs the
     * write lock Therefore, we can't grab the write lock here because
     * that would create a deadlock.
     */
    public final synchronized void insert(Text key, Value value,
            PrimaryKey record, long version, Action type) {
        Preconditions.checkState(mutable,
                "Cannot modify a block that is not mutable");
        if(value.getType() == Type.STRING) {
            String string = value.getObject().toString().toLowerCase(); // CON-10
            String[] toks = string
                    .split(TStrings.REGEX_GROUP_OF_ONE_OR_MORE_WHITESPACE_CHARS);
            ExecutorService executor = ConcourseExecutors
                    .newCachedThreadPool("SearchBlock");
            int pos = 0;
            for (String tok : toks) {
                executor.submit(getRunnable(key, tok, pos, record, version,
                        type));
                pos++;
            }
            executor.shutdown();
            while (!executor.isTerminated()) {
                continue; // block until all tasks have completed
            }
        }
    }

    @Override
    protected SearchRevision makeRevision(Text locator, Text key,
            Position value, long version, Action type) {
        return Revision
                .createSearchRevision(locator, key, value, version, type);
    }

    @Override
    protected Class<SearchRevision> xRevisionClass() {
        return SearchRevision.class;
    }

    /**
     * Call super.{@link #insert(Text, Text, Position, long)}
     * 
     * @param locator
     * @param key
     * @param value
     * @param version
     * @param type
     */
    private final void doInsert(Text locator, Text key, Position value,
            long version, Action type) {
        super.insert(locator, key, value, version, type);
    }

    /**
     * Return a Runnable that will insert a revision for {@code term} at
     * {@code position} for {@code key} in {@code record} at {@code version}.
     * 
     * @param key
     * @param term
     * @param position
     * @param record
     * @param version
     * @param type
     * @return the index Runnable
     */
    private Runnable getRunnable(final Text key, final String term,
            final int position, final PrimaryKey record, final long version,
            final Action type) {
        return new Runnable() {

            // The set of substrings that have been indexed from {@code term} at
            // {@code position} for {@code key} in {@code record} at {@code
            // version}. This is used to ensure that we do not add duplicate
            // indexes (i.e. 'abrakadabra')
            private Set<String> indexed = Sets.newHashSet();

            @Override
            public void run() {
                if(STOPWORDS.contains(term)) {
                    return;
                }
                for (int i = 0; i < term.length(); i++) {
                    for (int j = i + 1; j < term.length() + 1; j++) {
                        String substring = term.substring(i, j).trim();
                        if(!Strings.isNullOrEmpty(substring)
                                && !STOPWORDS.contains(substring)
                                && !indexed.contains(substring)) {
                            doInsert(key, Text.wrap(term.substring(i, j)),
                                    Position.wrap(record, position), version,
                                    type);
                            indexed.add(substring);
                        }
                    }
                }
                indexed = null; // make eligible for immediate GC
            }
        };
    }

}
