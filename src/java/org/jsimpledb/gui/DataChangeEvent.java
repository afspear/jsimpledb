
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.gui;

import com.google.common.base.Preconditions;

import org.jsimpledb.change.Change;
import org.jsimpledb.change.ChangeCopier;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event that notifies about a {@link Change} to the database.
 */
@SuppressWarnings("serial")
public class DataChangeEvent extends ApplicationEvent {

    private final Change<?> change;

    /**
     * Constructor. Assumes there is a current transaction open.
     *
     * @param source event source
     * @param change change information; will be copied out of the current transaction with a {@link ChangeCopier}
     */
    public DataChangeEvent(Object source, Change<?> change) {
        super(source);
        Preconditions.checkArgument(change != null, "null change");
        this.change = change.visit(new ChangeCopier());
    }

    /**
     * Get the change that occurred, already copied out of the transaction with a {@link ChangeCopier}.
     * Related objects are also copied.
     *
     * @return the associated change
     */
    public Change<?> getChange() {
        return this.change;
    }
}

