
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.ArrayList;
import java.util.HashSet;

import org.jsimpledb.JTransaction;
import org.jsimpledb.change.Change;
import org.jsimpledb.change.ChangeAdapter;
import org.jsimpledb.change.FieldChange;
import org.jsimpledb.change.ObjectCreate;
import org.jsimpledb.change.ObjectDelete;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.SnapshotTransaction;
import org.jsimpledb.core.Transaction;
import org.slf4j.LoggerFactory;

/**
 * Represents a summary of objects that have been added, deleted, or changed during a JSimpleDB transaction.
 *
 * <p>
 * Instances collect information from provided {@link Change} objects, and accumulate summary information about
 * which objects were {@linkplain #getCreated created}, {@linkplain #getDeleted deleted}, and {@linkplain #getChanged changed}.
 * Objects are identified only by their {@link ObjId}'s; for space efficiency, no other information beyond these three sets of
 * {@link ObjId}'s is kept.
 * </p>
 *
 * <p>
 * This class also includes static methods allowing callers to implicitly associate a {@link ChangeSummary} instance with an
 * open {@link Transaction}, feed it {@link Change} events during that {@link Transaction}, and notify any registered
 * {@link Listener ChangeSummary.Listener}s of the changes upon successful completion of the {@link Transaction}.
 * </p>
 *
 * <p>
 * Usage example:
 * <pre>
 *  // One-time initialization
 *
 *  {@link #addChangeSummaryListener ChangeSummary.addChangeSummaryListener}(new {@link Listener ChangeSummary.Listener}() {
 *      &#64;Override
 *      public void notifyChanges(ChangeSummary changeSummary) {
 *          System.out.println("A committed transaction has affected these objects:");
 *          System.out.println("  Created objects: " + changeSummary.getCreated());
 *          System.out.println("  Deleted objects: " + changeSummary.getDeleted());
 *          System.out.println("  Changed objects: " + changeSummary.getChanged());
 *      }
 *  });
 *
 *  // In your model classes (as needed)
 *
 *  &#64;OnCreate
 *  private void onCreate() {
 *      {@link #recordChange(Change) ChangeSummary.recordChange}(new ObjectCreate&lt;Foo&gt;(this));
 *  }
 *
 *  &#64;OnDelete
 *  private void onDelete() {
 *      {@link #recordChange(Change) ChangeSummary.recordChange}(new ObjectDelete&lt;Foo&gt;(this));
 *  }
 *
 *  &#64;OnChange
 *  private void onChange(Change&lt;?&gt; change) {
 *      {@link #recordChange(Change) ChangeSummary.recordChange}(change);
 *  }
 * </pre>
 * </p>
 *
 * <p>
 * Note that objects can appear in any or all of the three sets. For example, and object that is created and then initialized
 * will appear in both the {@linkplain #getCreated created} and {@linkplain #getChanged changed} sets.
 * </p>
 *
 * <p>
 * Instances are not thread safe.
 * </p>
 */
public class ChangeSummary {

    private static final HashSet<Listener> LISTENERS = new HashSet<>();
    private static final LoadingCache<Transaction, Callback> CALLBACKS
      = CacheBuilder.newBuilder().weakKeys().build(new CacheLoader<Transaction, Callback>() {
        public Callback load(Transaction tx) {
            final Callback callback = new Callback();
            tx.addCallback(callback);
            return callback;
        }
    });
    private static final ChangeClassifier CHANGE_CLASSIFIER = new ChangeClassifier();

    private final ObjIdSet created = new ObjIdSet();
    private final ObjIdSet deleted = new ObjIdSet();
    private final ObjIdSet changed = new ObjIdSet();

// Instance methods

    /**
     * Get the {@link ObjId}s of all objects for which a {@link ObjectCreate} event was seen.
     *
     * <p>
     * The caller should not modify the returned {@link ObjIdSet} because it is shared among multiple
     * {@link Listener ChangeSummary.Listener}s.
     * </p>
     */
    public ObjIdSet getCreated() {
        return this.created;
    }

    /**
     * Get the {@link ObjId}s of all objects for which a {@link ObjectDelete} event was seen.
     *
     * <p>
     * The caller should not modify the returned {@link ObjIdSet} because it is shared among multiple
     * {@link Listener ChangeSummary.Listener}s.
     * </p>
     */
    public ObjIdSet getDeleted() {
        return this.deleted;
    }

    /**
     * Get the {@link ObjId}s of all objects for which a {@link FieldChange} event was seen.
     *
     * <p>
     * The caller should not modify the returned {@link ObjIdSet} because it is shared among multiple
     * {@link Listener ChangeSummary.Listener}s.
     * </p>
     */
    public ObjIdSet getChanged() {
        return this.changed;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[created="
          + this.created + "deleted=" + this.deleted + "changed=" + this.changed + "]";
    }

// Static methods

    /**
     * Register a {@link Change} originating from the {@link JTransaction} associated with the current thread.
     *
     * @param change the change that has occurred
     * @throws IllegalArgumentException if {@code change} is null
     * @throws IllegalStateException if there is no {@link JTransaction} associated with the current thread
     * @throws StaleTransactionException if the {@link JTransaction} associated with the current thread is no longer usable
     */
    public static void recordChange(Change<?> change) {
        if (change == null)
            throw new IllegalArgumentException("null change");
        ChangeSummary.recordChange(JTransaction.getCurrent().getTransaction(),
          change.getJObject().getObjId(), change.visit(CHANGE_CLASSIFIER));
    }

    /**
     * Register a change originating from the {@link JTransaction} associated with the current thread.
     *
     * <p>
     * Equivalent to:
     * <blockquote><pre>
     *  ChangeSummary.recordChange(JTransaction.getCurrent().getTransaction(), id, changeType);
     * </pre></blockquote>
     * </p>
     *
     * @param id the ID of the object that changed
     * @param changeType the type of change that has occurred
     * @throws IllegalArgumentException if {@code id} or {@code changeType} is null
     * @throws IllegalStateException if there is no {@link JTransaction} associated with the current thread
     * @throws StaleTransactionException if the {@link JTransaction} associated with the current thread is no longer usable
     */
    public static void recordChange(ObjId id, ChangeType changeType) {
        ChangeSummary.recordChange(JTransaction.getCurrent().getTransaction(), id, changeType);
    }

    /**
     * Register a change to the given object originating from the given {@link Transaction}.
     *
     * @param tx the {@link Transaction} in which the change occurred
     * @param id the ID of the object that changed
     * @param changeType the type of change that has occurred
     * @throws IllegalArgumentException if {@code tx} is a {@link SnapshotTransaction}
     * @throws StaleTransactionException if {@code tx} is no longer usable
     * @throws IllegalArgumentException if any parameter is null
     */
    public static void recordChange(Transaction tx, ObjId id, ChangeType changeType) {
        if (tx == null)
            throw new IllegalArgumentException("null tx");
        if (tx instanceof SnapshotTransaction)
            throw new IllegalArgumentException("transaction is a SnapshotTransaction");
        if (id == null)
            throw new IllegalArgumentException("null id");
        if (changeType == null)
            throw new IllegalArgumentException("null changeType");
        final Callback callback = CALLBACKS.getUnchecked(tx);
        final ChangeSummary changeSummary = callback.getChangeSummary();
        synchronized (changeSummary) {
            changeType.apply(changeSummary, id);
        }
    }

    /**
     * Add a listener to be notified with a {@link ChangeSummary} after every successful transaction
     * in which one or more {@link Change}s were reported via {@link #recordChange(Transaction, ObjId, ChangeType) recordChange()}.
     *
     * @throws IllegalArgumentException if {@code listener} is null
     */
    public static void addChangeSummaryListener(Listener listener) {
        if (listener == null)
            throw new IllegalArgumentException("null listener");
        synchronized (LISTENERS) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Remove a listener previously added via {@link #addChangeSummaryListener addChangeSummaryListener()}.
     */
    public static void removeChangeSummaryListener(Listener listener) {
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }

// ChangeType

    /**
     * Type of changes recognized by a {@link ChangeSummary}.
     *
     * @see ChangeSummary#recordChange(Transaction, ObjId, ChangeType) ChangeSummary.recordChange()
     */
    public enum ChangeType {
        /**
         * The object was created.
         */
        CREATED {
            @Override
            void apply(ChangeSummary changeSummary, ObjId id) {
                changeSummary.getCreated().add(id);
            }
        },

        /**
         * The object was deleted.
         */
        DELETED {
            @Override
            void apply(ChangeSummary changeSummary, ObjId id) {
                changeSummary.getDeleted().add(id);
            }
        },

        /**
         * Some field in the object was changed.
         */
        CHANGED {
            @Override
            void apply(ChangeSummary changeSummary, ObjId id) {
                changeSummary.getChanged().add(id);
            }
        };

        abstract void apply(ChangeSummary changeSummary, ObjId id);
    }

// ChangeClassifier

    /**
     * Classifies a {@link Change} object into one of the defined {@link ChangeType}s:
     * {@link ChangeType#CREATED}, {@link ChangeType#DELETED}, or {@link ChangeType#CHANGED}.
     *
     * @see ChangeSummary#recordChange(Transaction, ObjId, ChangeType) ChangeSummary.recordChange()
     */
    public static class ChangeClassifier extends ChangeAdapter<ChangeType> {

        @Override
        public <T> ChangeType caseObjectCreate(ObjectCreate<T> change) {
            return ChangeType.CREATED;
        }

        @Override
        public <T> ChangeType caseObjectDelete(ObjectDelete<T> change) {
            return ChangeType.DELETED;
        }

        @Override
        protected <T> ChangeType caseFieldChange(FieldChange<T> change) {
            return ChangeType.CHANGED;
        }
    }

// Listener

    /**
     * Listener interface for receiving summaries of objects that have been added,
     * deleted, or changed during JSimpleDB transactions.
     *
     * @see ChangeSummary
     */
    public interface Listener {

        /**
         * Receive notification that a transaction has successfully committed with the given summarized changes.
         *
         * @param changeSummary change summary
         */
        void notifyChanges(ChangeSummary changeSummary);
    }

// Callback

    private static class Callback extends Transaction.CallbackAdapter {

        private final ChangeSummary summary = new ChangeSummary();

        public ChangeSummary getChangeSummary() {
            return this.summary;
        }

        @Override
        public void afterCommit() {
            final ArrayList<Listener> listeners;
            synchronized (LISTENERS) {
                listeners = new ArrayList<>(LISTENERS);
            }
            for (Listener listener : listeners) {
                try {
                    listener.notifyChanges(this.summary);
                } catch (Throwable e) {
                    LoggerFactory.getLogger(this.getClass()).error("exception from listener " + listener, e);
                }
            }
        }
    }
}

