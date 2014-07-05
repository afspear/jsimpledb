
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.cli.cmd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Java annotation for classes that define custom {@link org.jsimpledb.JSimpleDB} command line interface (CLI) commands.
 *
 * <p>
 * Annotated classes must extend {@link org.jsimpledb.cli.cmd.Command}
 * and have a public constructor taking either zero parameters or a single {@link org.jsimpledb.cli.Session} parameter.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface CliCommand {
}
