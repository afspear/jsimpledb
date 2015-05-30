
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.gui;

import com.vaadin.server.VaadinSession;

import java.util.HashSet;

import org.dellroad.stuff.spring.AbstractBean;
import org.dellroad.stuff.spring.RetryTransaction;
import org.dellroad.stuff.vaadin7.VaadinUtil;
import org.jsimpledb.util.ChangeSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Singleton bean that updates {@link JObjectContainer}s after each successful transaction,
 * via {@link JObjectContainer#updateAfterChanges JObjectContainer.updateAfterChanges()}.
 * It is assumed that this bean is contained within a single {@link VaadinSession}.
 */
@Component
public class ChangePublisher extends AbstractBean implements ChangeSummary.Listener {

protected final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(this.getClass());

    private final HashSet<JObjectContainer> containers = new HashSet<>();
    private final VaadinSession session = VaadinSession.getCurrent();

// Lifecycle

    @Override
    public void afterPropertiesSet() throws Exception {
        assert this.session.hasLock();
        super.afterPropertiesSet();
this.log.info("ChangePublisher registering self");
        ChangeSummary.addChangeSummaryListener(this);
    }

    @Override
    public void destroy() throws Exception {
        assert this.session.hasLock();
this.log.info("ChangePublisher deregistering self");
        ChangeSummary.removeChangeSummaryListener(this);
        super.destroy();
    }

// API

    /**
     * Register a {@link JObjectContainer} for automatic updates.
     *
     * @param change change during the current transaction
     */
    public void addListeningContainer(JObjectContainer container) {
        assert this.session.hasLock();
        this.containers.add(container);
this.log.info("adding container " + container);
    }

    /**
     * De-register a {@link JObjectContainer} for automatic updates.
     *
     * @param jobj object changed during the current transaction
     */
    public void removeListeningContainer(JObjectContainer container) {
        assert this.session.hasLock();
        this.containers.remove(container);
this.log.info("removing container " + container);
    }

// ChangeSummary.Listener

    @Override
    public void notifyChanges(final ChangeSummary summary) {
this.log.info("got changes: " + summary);
        VaadinUtil.invoke(this.session, new Runnable() {    // to avoid deadlock, lock the session first, then create a transaction
            @Override
            public void run() {
                ChangePublisher.this.notifyChangesInSession(summary);
            }
        });
    }

    @RetryTransaction
    @Transactional("jsimpledbGuiTransactionManager")
    private void notifyChangesInSession(ChangeSummary summary) {
        assert this.session.hasLock();
this.log.info("updating changes to " + ChangePublisher.this.containers);
        for (JObjectContainer container : ChangePublisher.this.containers)
            container.updateAfterChanges(summary);
    }
}

