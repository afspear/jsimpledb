
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.core;

import org.dellroad.stuff.java.Primitive;
import org.jsimpledb.util.ByteReader;
import org.jsimpledb.util.ByteWriter;

/**
 * {@link void} primitive type.
 */
class VoidType extends PrimitiveType<Void> {

    VoidType() {
       super(Primitive.VOID);
    }

    @Override
    public Void read(ByteReader reader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(ByteWriter writer, Void value) {
        throw new IllegalArgumentException("null value");
    }

    @Override
    public void skip(ByteReader reader) {
        throw new UnsupportedOperationException();
    }
}

