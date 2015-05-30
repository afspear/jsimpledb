
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.gui;

import com.google.common.collect.Iterables;

import java.util.Collections;

import org.dellroad.stuff.vaadin7.VaadinConfigurable;
import org.jsimpledb.JObject;
import org.jsimpledb.JSimpleDB;
import org.jsimpledb.parse.ParseContext;
import org.jsimpledb.parse.ParseException;
import org.jsimpledb.parse.ParseSession;
import org.jsimpledb.parse.expr.EvalException;
import org.jsimpledb.parse.expr.ExprParser;
import org.jsimpledb.parse.expr.Node;
import org.jsimpledb.util.CastFunction;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link JObjectContainer} whose contents are determined by a Java expression.
 */
@SuppressWarnings("serial")
@VaadinConfigurable(preConstruction = true)
public class ExprQueryJObjectContainer extends JObjectContainer {

    private final ParseSession session;

    @Autowired
    private ChangePublisher changePublisher;

    private String contentExpression;

    /**
     * Constructor.
     *
     * @param jdb underlying database
     * @param session session for parsing expressions
     */
    public ExprQueryJObjectContainer(JSimpleDB jdb, ParseSession session) {
        this(jdb, null, session);
    }

    /**
     * Constructor.
     *
     * @param jdb underlying database
     * @param type type restriction, or null for no restriction
     * @param session session for parsing expressions
     */
    public ExprQueryJObjectContainer(JSimpleDB jdb, Class<?> type, ParseSession session) {
        super(jdb, type);
        if (session == null)
            throw new IllegalArgumentException("null session");
        this.session = session;
    }

    /**
     * Configure the expression that returns this container's content. Container will automatically reload.
     *
     * @param contentExpression Java expression for container content
     */
    public void setContentExpression(String contentExpression) {
        this.contentExpression = contentExpression;
        this.reload();
    }

    @Override
    protected void doInTransaction(final Runnable action) {
        this.session.perform(new ParseSession.Action() {
            @Override
            public void run(ParseSession session) {
                action.run();
            }
        });
    }

    @Override
    protected Iterable<? extends JObject> queryForObjects() {

        // Any content?
        if (this.contentExpression == null)
            return Collections.<JObject>emptySet();

        // Parse expression
        final ParseContext ctx = new ParseContext(this.contentExpression);
        final Node node = new ExprParser().parse(this.session, ctx, false);
        ctx.skipWhitespace();
        if (!ctx.isEOF())
            throw new ParseException(ctx, "syntax error");

        // Evaluate parsed expression
        final Object content = node.evaluate(this.session).get(this.session);
        if (!(content instanceof Iterable)) {
            throw new EvalException("expression must evaluate to an Iterable; found "
              + (content != null ? content : "null") + " instead");
        }

        // Reload container with results of expression
        return Iterables.transform((Iterable<?>)content, new CastFunction<JObject>(JObject.class));
    }

// Connectable

    @Override
    public void connect() {
        super.connect();
        if (this.changePublisher != null)
            this.changePublisher.addListeningContainer(this);
    }

    @Override
    public void disconnect() {
        if (this.changePublisher != null)
            this.changePublisher.removeListeningContainer(this);
        super.disconnect();
    }
}

