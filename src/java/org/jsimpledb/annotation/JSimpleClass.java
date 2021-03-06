
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Java annotation for Java classes that are {@link org.jsimpledb.JSimpleDB} object model types.
 *
 * <p>
 * Typically such classes are declared {@code abstract} and contain {@code abstract} Java bean getter methods
 * with {@link JField &#64;JField}, {@link JSetField &#64;JSetField}, etc. annotations that
 * define the fields of the object type.
 * </p>
 *
 * <p>
 * The following annotations are supported on the methods of a {@link JSimpleClass &#64;JSimpleClass}-annotated class:
 * <ul>
 *  <li>{@link JField &#64;JField} - defines a simple value or reference field
 *  <li>{@link JSetField &#64;JSetField} - defines a {@link java.util.NavigableSet} field
 *  <li>{@link JListField &#64;JListField} - defines a {@link java.util.List} field
 *  <li>{@link JMapField &#64;JMapField} - defines a {@link java.util.NavigableMap} field
 *  <li>{@link OnChange &#64;OnChange} - annotates a method to be invoked when some field (possibly seen through
 *      a path of references) changes
 *  <li>{@link OnCreate &#64;OnCreate} - annotates a method to be invoked just after object creation
 *  <li>{@link OnDelete &#64;OnDelete} - annotates a method to be invoked just prior to object deletion
 *  <li>{@link OnVersionChange &#64;OnVersionChange} - annotates a method to be invoked when the object's schema version changes
 *  <li>{@link Validate &#64;Validate} - annotates a method to be invoked whenever the object is (re)validated
 * </ul>
 *
 * <p>
 * The above annotations may be present on any overridden supertype method, including methods in superclasses and superinterfaces
 * (whether or not annotated with {@link JSimpleClass &#64;JSimpleClass}).
 * </p>
 *
 * <p>
 * The subclass of the annotated class that is generated by {@link org.jsimpledb.JSimpleDB} will implement
 * {@link org.jsimpledb.JObject}, so the (abstract) annotated class may be declared that way.
 * The annotated class must have a zero-parameter constructor.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface JSimpleClass {

    /**
     * The name of this object type.
     *
     * <p>
     * If equal to the empty string (default value),
     * the {@linkplain Class#getSimpleName simple name} of the annotated Java class is used.
     * </p>
     *
     * @return object type name
     */
    String name() default "";

    /**
     * Storage ID for this object type. Value should be positive;
     * if zero, the configured {@link org.jsimpledb.StorageIdGenerator} will be consulted to auto-generate a value.
     *
     * @return object type storage ID
     * @see org.jsimpledb.StorageIdGenerator#generateClassStorageId StorageIdGenerator.generateClassStorageId()
     */
    int storageId() default 0;

    /**
     * Composite indexes (i.e., indexes on two or more fields) defined on this type.
     *
     * @return object type composite indexes
     */
    JCompositeIndex[] compositeIndexes() default {};

    /**
     * Whether to automatically generate database fields from un-annotated abstract Java bean methods.
     *
     * <p>
     * If true, database fields corresponding to all <b>abstract</b> bean property getter methods will
     * be auto-generated even if there is no {@link JField &#64;JField}, {@link JSetField &#64;JSetField},
     * {@link JListField &#64;JListField}, or {@link JMapField &#64;JMapField} annotation. Note <i>this includes
     * superclass and interface methods</i>.
     * </p>
     *
     * <p>
     * Getter methods with return type assignable to {@link java.util.Set}, {@link java.util.List}, and {@link java.util.Map}
     * will cause the corresponding collection fields to be created; other getter/setter method pairs will cause
     * the corresponding simple fields to be generated. Auto-generation of storage ID's is performed by the
     * configured {@link org.jsimpledb.StorageIdGenerator}.
     * </p>
     *
     * @return whether to auto-generate fields from abstract methods
     * @see org.jsimpledb.JSimpleDBFactory#setStorageIdGenerator JSimpleDBFactory.setStorageIdGenerator()
     */
    boolean autogenFields() default true;
}

