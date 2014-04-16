
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.Transaction;

/**
 * Represents a 64-bit counter value that can be adjusted concurrently by multiple transactions without locking.
 * Supported by {@link org.jsimpledb.kv.KVDatabase}s whose {@linkplain org.jsimpledb.kv.KVDatabase#createTransaction transactions}
 * implement the {@link org.jsimpledb.kv.CountingKVStore} interface.
 *
 * <p>
 * To define a field of type {@link Counter}, annotate the field's getter method as a normal field using
 * {@link org.jsimpledb.annotation.JField &#64;JField}. No setter method should be defined.
 * </p>
 *
 * <p>
 * If you access a {@link Counter} field while running on a non-supporting database,
 * an {@link UnsupportedOperationException} will be thrown.
 * </p>
 *
 * <p>
 * During version change notification, counter fields appear as plain {@code long} values.
 * </p>
 */
public class Counter {

    private final Transaction tx;
    private final ObjId id;
    private final int storageId;

    Counter(Transaction tx, ObjId id, int storageId) {
        if (tx == null)
            throw new IllegalArgumentException("null tx");
        if (id == null)
            throw new IllegalArgumentException("null id");
        this.tx = tx;
        this.id = id;
        this.storageId = storageId;
    }

    /**
     * Read the counter's current value. Invoking this method will typically disable the lock-free
     * behavior of {@link #adjust adjust()} in the current transaction.
     *
     * @return current value of the counter
     * @throws org.jsimpledb.kv.StaleTransactionException if the transaction from which this instance
     *  was read is no longer usable
     * @throws DeletedObjectException if the object from which this instance was read no longer exists
     */
    public long get() {
        return this.tx.readCounterField(this.id, this.storageId);
    }

    /**
     * Set the counter's value. Invoking this method will typically disable the lock-free
     * behavior of {@link #adjust adjust()} in the current transaction.
     *
     * @param value new value for the counter
     * @throws org.jsimpledb.kv.StaleTransactionException if the transaction from which this instance
     *  was read is no longer usable
     * @throws DeletedObjectException if the object from which this instance was read no longer exists
     */
    public void set(long value) {
        this.tx.writeCounterField(this.id, this.storageId, value);
    }

    /**
     * Adjust the counter's value by the specified amount.
     *
     * @param offset amount to add to counter
     * @throws org.jsimpledb.kv.StaleTransactionException if the transaction from which this instance
     *  was read is no longer usable
     * @throws DeletedObjectException if the object from which this instance was read no longer exists
     */
    public void adjust(long offset) {
        this.tx.adjustCounterField(this.id, this.storageId, offset);
    }
}
