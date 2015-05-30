
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.gui;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.dellroad.stuff.vaadin7.PropertyDef;
import org.dellroad.stuff.vaadin7.PropertyExtractor;
import org.dellroad.stuff.vaadin7.ProvidesPropertyScanner;
import org.dellroad.stuff.vaadin7.SimpleKeyedContainer;
import org.jsimpledb.CopyState;
import org.jsimpledb.JClass;
import org.jsimpledb.JCollectionField;
import org.jsimpledb.JCounterField;
import org.jsimpledb.JField;
import org.jsimpledb.JFieldSwitchAdapter;
import org.jsimpledb.JMapField;
import org.jsimpledb.JObject;
import org.jsimpledb.JSimpleDB;
import org.jsimpledb.JSimpleField;
import org.jsimpledb.JTransaction;
import org.jsimpledb.SnapshotJTransaction;
import org.jsimpledb.core.DeletedObjectException;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.TypeNotInSchemaVersionException;
import org.jsimpledb.core.UnknownFieldException;
import org.jsimpledb.util.ChangeSummary;
import org.jsimpledb.util.ObjIdBiMultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General purpose support superclass for Vaadin {@link com.vaadin.data.Container}s backed by {@link JSimpleDB} database objects.
 * Automatically creates properties for object ID, database type, version, and fields, as well as any custom properties
 * defined by {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods in Java model classes,
 * and updates backing objects and/or their related objects based on {@link ChangeSummary}s.
 *
 * <p>
 * Instances are configured with a <b>type</b>, which can be any Java type (including interface types). The container
 * will then be restricted to database objects that are instances of the configured type. The type may be null, in which
 * case there is no restriction. The subclass method {@linkplain #queryForObjects queryForObjects()} determines which
 * objects are actually loaded into the container. The items in the container are backed by in-memory
 * {@link org.jsimpledb.SnapshotJTransaction} copies of the corresponding database objects, plus any related objects
 * required (see {@link #getDependencies getDependencies()}).
 * </p>
 *
 * <p><b>Container Properties</b></p>
 *
 * <p>
 * Instances will have the following container properties:
 * <ul>
 *  <li>{@link #OBJECT_ID_PROPERTY}: Object {@link ObjId}</li>
 *  <li>{@link #TYPE_PROPERTY}: Object type name (JSimpleDB type name, not Java type name, though these are typically the same)</li>
 *  <li>{@link #VERSION_PROPERTY}: Object schema version</li>
 *  <li>{@link #REFERENCE_LABEL_PROPERTY}: Object <b>reference label</b>, which is a short description identifying the
 *      object. Reference labels are used to provide "names" for objects that are more meaningful than object ID's
 *      and are used as such in other {@link JSimpleDB} GUI classes. To customize the reference label for a Java model class,
 *      annotate a method with {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}{@code (}{@link
 *      JObjectContainer#REFERENCE_LABEL_PROPERTY REFERENCE_LABEL_PROPERTY}{@code )};
 *      otherwise, the value of this property will be the same as {@link #OBJECT_ID_PROPERTY}.</li>
 *  <li>A property for every {@link JSimpleDB} field that is common to all object types that sub-type
 *      this instance's configured type. The property's ID is the field name; its value is as follows:
 *      <ul>
 *          <li>Simple fields will show their string values</li>
 *          <li>Reference fields show the {@link #REFERENCE_LABEL_PROPERTY} of the referred-to object, or "Null"
 *              if the reference is null</li>
 *          <li>Set, list, and map fields show the first few entries in the collection</li>
 *      </ul>
 *  </li>
 *  <li>A property for each {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated method
 *      in the specified <b>type</b>. These properties will add to (or override) the properties listed above.
 * </ul>
 *
 * <p>
 * Note that {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods may access
 * other indirectly referenced objects to generate their values. For example, consider a table of users that has
 * an account name column: the account name would presumably come from the account object referred to by each user
 * object, not the user object itself. This creates an indirect dependency of the container item corresponding to each
 * user on the associated account object. See {@link #getDependencies getDependencies()}) for how to declare these dependencies.
 * </p>
 *
 * <p><b>Loading</b></p>
 *
 * <p>
 * Instances may be (re)loaded at any time by invoking {@link #reload}. This causes the container
 * to query for {@link JObject}s within a new {@link JTransaction} via the subclass-provided method
 * {@link #queryForObjects queryForObjects()}, which returns an {@link Iterable Iterable&lt;JObject&gt;}. This container
 * copies the resulting database objects into the container's
 * in-memory {@link org.jsimpledb.SnapshotJTransaction}. The latter effectively serves as the cache for the container,
 * so that database transactions may be short-lived and are only required when (re)loading. Because the container contents
 * are in memory, with large data sets this container should only be loaded with one "page" of objects at a time (a "page"
 * typically being only what a human would be willing to scroll through at one time). It is up to
 * {@link #queryForObjects queryForObjects()} to determine what and how many objects are returned, their sort order, etc.
 * (if {@link #queryForObjects queryForObjects()} returns any objects that are not instances of the configured type,
 * they are ignored). In any case, the maximum number of objects that will be loaded is limited by a
 * {@linkplain #setMaxObjects configurable} maximum.
 * </p>
 *
 * <p>
 * When {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods are present on Java
 * model classes, these property values may derive from other objects related to the backing instance. In order for these
 * methods to work on the in-memory instance, the related objects must be copied into memory as well. The
 * {@link #getDependencies getDependencies()} method allows the subclass to specify what other database objects
 * some Vaadin property depends on.
 * </p>
 *
 * <p><b>Incremental Updates</b></p>
 *
 * <p>
 * Some common challenges with Vaadin containers backed by database records are:
 * <ul>
 *  <li>Determining which containers are affected by the changes in a newly committed transaction</li>
 *  <li>Efficiently updating only the affected items in the container</li>
 *  <li>Handling Vaadin properties that are derived from indirectly referenced objects</li>
 * </ul>
 * </p>
 *
 * <p>
 * {@link JObjectContainer} supports efficient incremental updates of container items without requiring a complete
 * container reload (if possible). The {@link #updateAfterChanges updateAfterChanges()} method takes a {@link ChangeSummary}
 * and updates the container as needed. In one design pattern, the {@link #connect} method registers a
 * {@link org.jsimpledb.util.ChangeSummary.Listener} to receive {@link ChangeSummary} notifications after each successfully
 * committed transaction, and invokes {@link #updateAfterChanges updateAfterChanges()} when notifications are received.
 * </p>
 *
 * <p>
 * This container keeps track of which related objects are indirectly depended on by each backing object according to
 * {@link #getDependencies getDependencies()}. Changes that affect a dependent object automatically cause
 * the corresponding dependent object(s) to be updated.
 * </p>
 *
 * <p><b>{@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty} Limitations</b></p>
 *
 * <p>
 * The use of {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty} methods has certain implications.
 * First, if the method reads any of the object's field(s) via their Java getter methods (as would normally be expected),
 * this will trigger an automatic schema upgrade of the object if needed. Normally this is not a problem; however, this
 * schema upgrade will occur in the
 * container's in-memory {@link org.jsimpledb.SnapshotJTransaction} rather than in a real database transaction, so the
 * {@link #VERSION_PROPERTY} will show a different schema version from what's actually in the database. This automatic schema
 * upgrade can be avoided if desired by reading fields using the appropriate {@link JTransaction} field access method
 * (e.g., {@link JTransaction#readSimpleField JTransaction.readSimpleField()}) and being prepared to handle a
 * {@link org.jsimpledb.core.UnknownFieldException} if/when the object has an older schema version that does not contain
 * the requested field.
 * </p>
 *
 * <p>
 * Secondly, because the values of reference fields (including complex sub-fields) are displayed using reference labels,
 * and these are typically derived from the referenced object's fields, those indirectly referenced objects need to be
 * copied into the container's {@link org.jsimpledb.SnapshotJTransaction} as well. To ensure these indirectly referenced
 * objects are also copied, override {@link #getDependencies getDependencies()} as described above.
 * </p>
 */
@SuppressWarnings("serial")
public abstract class JObjectContainer extends SimpleKeyedContainer<ObjId, JObject> {

    /**
     * Default maximum number of objects.
     */
    public static final int DEFAULT_MAX_OBJECTS = 1000;

    /**
     * Container property name for the reference label property, which has type {@link Component}.
     */
    public static final String REFERENCE_LABEL_PROPERTY = "$label";

    /**
     * Container property name for the object ID property.
     */
    public static final String OBJECT_ID_PROPERTY = "$objId";

    /**
     * Container property name for the object type property.
     */
    public static final String TYPE_PROPERTY = "$type";

    /**
     * Container property name for the object schema version property.
     */
    public static final String VERSION_PROPERTY = "$version";

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * The associated {@link JSimpleDB}, provding schema information.
     */
    protected final JSimpleDB jdb;

    private final ObjIdPropertyDef objIdPropertyDef = new ObjIdPropertyDef();
    private final ObjTypePropertyDef objTypePropertyDef = new ObjTypePropertyDef();
    private final ObjVersionPropertyDef objVersionPropertyDef = new ObjVersionPropertyDef();
    private final RefLabelPropertyDef refLabelPropertyDef = new RefLabelPropertyDef();
    private final ObjIdBiMultiMap dependencyMap = new ObjIdBiMultiMap();            // maps object to its dependencies

    private Class<?> type;
    private int maxObjects = DEFAULT_MAX_OBJECTS;
    private ProvidesPropertyScanner<?> propertyScanner;
    private List<String> orderedPropertyNames;
    private SnapshotJTransaction snapshotTransaction;

    /**
     * Constructor.
     *
     * @param jdb {@link JSimpleDB} database
     * @param type type restriction, or null for no restriction
     * @throws IllegalArgumentException if {@code jdb} is null
     */
    protected JObjectContainer(JSimpleDB jdb, Class<?> type) {
        if (jdb == null)
            throw new IllegalArgumentException("null jdb");
        this.jdb = jdb;
        this.setType(type);
        this.setPropertyExtractor(this);
    }

    /**
     * Get the type restriction associated with this instance.
     *
     * @return Java type restriction, or null if there is none
     */
    public Class<?> getType() {
        return this.type;
    }

    /**
     * Change the type restriction associated with this instance.
     * Triggers a {@link com.vaadin.data.Container.PropertySetChangeEvent} and typically requires a reload.
     *
     * @param type Java type restriction, or null for none
     * @param <T> Java type
     */
    public <T> void setType(Class<T> type) {
        this.type = type;
        this.propertyScanner = this.type != null ? new ProvidesPropertyScanner<T>(/*this.*/type) : null;
        final ArrayList<PropertyDef<?>> propertyDefs = new ArrayList<>(this.buildPropertyDefs());
        this.orderedPropertyNames = Collections.unmodifiableList(Lists.transform(propertyDefs,
          new Function<PropertyDef<?>, String>() {
            @Override
            public String apply(PropertyDef<?> propertyDef) {
                return propertyDef.getName();
            }
        }));
        this.setProperties(propertyDefs);
        this.fireContainerPropertySetChange();
    }

    /**
     * Configure the maximum number of objects.
     *
     * @param maxObjects maximum allowed objects
     */
    public void setMaxObjects(int maxObjects) {
        this.maxObjects = Math.max(maxObjects, 0);
    }

    /**
     * Get the properties of this container in preferred order.
     *
     * @return property names
     */
    public List<String> getOrderedPropertyNames() {
        return this.orderedPropertyNames;
    }

    @Override
    public ObjId getKeyFor(JObject jobj) {
        return jobj.getObjId();
    }

    /**
     * (Re)load this container.
     *
     * <p>
     * This method utilizes {@link #doInTransaction doInTransaction()} to join an existing transaction
     * or create a new one, and then invokes {@link #queryForObjects queryForObjects()} to load the backing objects.
     * </p>
     */
    public void reload() {
        this.doInTransaction(new Runnable() {
            @Override
            public void run() {
                JObjectContainer.this.doReloadInTransaction();
            }
        });
    }

// Updates

    /**
     * Update this container as needed after the specified changes have been committed.
     *
     * <p>
     * This method {@linkplain #reload reloads} this container, if necessary,
     * according to {@link #requiresReload requiresReload()}.
     * </p>
     *
     * @param summary summary of committed changes
     * @throws IllegalArgumentException if {@code summary} is null
     */
    public void updateAfterChanges(final ChangeSummary summary) {
        if (summary == null)
            throw new IllegalArgumentException("null summary");
        if (this.requiresReload(summary))
            this.reload();
    }

    /**
     * Determine whether the changes in the given {@link ChangeSummary} should cause this container to reload.
     *
     * <p>
     * The implementation in {@link JObjectContainer} looks for any created, deleted, or modified object that is
     * already contained in this container, or is depended on by any object in this container. It also examines
     * each created object, compares its type to this container's configured type, and if it matches, reloads
     * the container unless {@link #shouldNotBeAddedToContainer shouldNotBeAddedToContainer()} returns false.
     * </p>
     *
     * @param summary summary of committed changes
     * @return true to reload, false if no reload needed
     * @throws IllegalArgumentException if {@code summary} is null
     */
    protected boolean requiresReload(ChangeSummary summary) {

        // Sanity check
        if (summary == null)
            throw new IllegalArgumentException("null summary");

        // Check added objects
        for (ObjId id : summary.getCreated()) {

            // Does the new object's type exist in the current schema version?
            final JClass<?> jclass;
            try {
                jclass = this.jdb.getJClass(id);
            } catch (TypeNotInSchemaVersionException e) {
                continue;
            }

            // Is the new object's type assignable to this containers's configured type?
            if (this.type != null && !this.type.isAssignableFrom(jclass.getType()))
                continue;

            // Do we know for sure that the object should not be contained?
            if (!this.shouldNotBeAddedToContainer(this.jdb.getJObject(id)))
                continue;

            // Reload to (possibly) add new object
            return true;
        }

        // Check added, modified, and deleted objects
        for (ObjId id : Iterables.concat(summary.getCreated(), summary.getChanged(), summary.getDeleted())) {

            // Is the changed or deleted object in this container?
            if (this.getItem(id) != null)
                return true;

            // Are any other objects in this container dependent on the changed or deleted object?
            if (this.dependencyMap.getSources(id) != null)
                return true;
        }

        // Done
        return false;
    }

    /**
     * Determine whether a newly created object definitely does not need to be added to this container.
     * This is allows for an optimization: if a newly added object is known to not be contained by this
     * container, then this container can possibly avoid an unnecessary reload.
     *
     * <p>
     * The implementation in {@link JObjectContainer} always returns false (this is a conservative default).
     * </p>
     *
     * <p>
     * This method is invoked from {@link #requiresReload requiresReload()} (and indirectly from
     * {@link #updateAfterChanges updateAfterChanges()}); whether there is a transaction open in the current
     * thread depends on how that method is invoked.
     * </p>
     *
     * @param jobj newly created object that matches this instance's configured type
     * @return true if {@code jobj} definitely does not need to be added, false if it does need to be, or if it can't be determined
     */
    protected boolean shouldNotBeAddedToContainer(JObject jobj) {
        return false;
    }

    /**
     * Reload entire container.
     *
     * <p>
     * This method runs within a transaction.
     * </p>
     *
     * @param change change from a transaction
     * @param <T> type of the changed object
     */
    private void doReloadInTransaction() {

        // Get objects from subclass
        Iterable<? extends JObject> jobjs = this.queryForObjects();

        // Filter out any instances of the wrong type
        if (JObjectContainer.this.type != null) {
            jobjs = Iterables.filter(jobjs, new Predicate<JObject>() {
                @Override
                public boolean apply(JObject jobj) {
                    return JObjectContainer.this.type.isInstance(jobj);
                }
            });
        }

        // Copy database objects out of the database transaction into my in-memory transaction as we load them
        final JTransaction jtx = JTransaction.getCurrent();
        this.snapshotTransaction = jtx.getSnapshotTransaction();
        final CopyState copyState = new CopyState();
        jobjs = Iterables.transform(jobjs, new Function<JObject, JObject>() {
            @Override
            public JObject apply(JObject jobj) {
                return JObjectContainer.this.copyOutAndUpdateDependencies(jobj, copyState);
            }
        });

        // Limit the total number of objects in the container
        jobjs = Iterables.limit(jobjs, this.maxObjects);

        // Now actually load them
        this.dependencyMap.clear();
        this.load(jobjs);
    }

    private JObject copyOutAndUpdateDependencies(JObject jobj, CopyState copyState) {

        // Get dependencies
        final Iterable<? extends JObject> dependencies = this.getDependencies(jobj);

        // Filter out nulls and transform to object IDs
        final Iterable<ObjId> dependencyIds = Iterables.transform(
          Iterables.filter(dependencies, JObject.class), new Function<JObject, ObjId>() {
            @Override
            public ObjId apply(JObject jobj) {
                return jobj.getObjId();
            }
        });

        // Update tracking info for object
        final ObjId id = jobj.getObjId();
        this.dependencyMap.removeAll(id);
        this.dependencyMap.addAll(id, dependencyIds);

        // Copy out object and dependencies
        return JObjectContainer.this.copyOut(jobj, dependencies, JObjectContainer.this.snapshotTransaction, copyState);
    }

    /**
     * Copy the given database object, and any related objects (as determined by {@link #getDependencies getDependencies()}),
     * into the specified {@link org.jsimpledb.SnapshotJTransaction}.
     *
     * @param target the object to copy, or null (ignored)
     * @param dest destination transaction
     * @param copyState tracks what's already been copied
     * @return the copy of {@code target} in {@code dest}, or null if {@code target} is null
     * @throws IllegalArgumentException if {@code dest} or {@code copyState} is null
     */
    protected JObject copyOut(JObject target, SnapshotJTransaction dest, CopyState copyState) {
        if (dest == null)
            throw new IllegalArgumentException("null dest");
        if (copyState == null)
            throw new IllegalArgumentException("null copyState");
        if (target == null)
            return null;
        return this.copyOut(target, this.getDependencies(target), dest, copyState);
    }

    private JObject copyOut(JObject target, Iterable<? extends JObject> dependencies,
      SnapshotJTransaction dest, CopyState copyState) {

        // Ignore null
        if (target == null)
            return null;

        // Copy out object
        final ObjId targetId = target.getObjId();
        final JTransaction jtx = target.getTransaction();
        final JObject copy = target.copyTo(dest, null, copyState);

        // Copy out related objects
        if (dependencies != null) {
            dependencies = Iterables.filter(dependencies, JObject.class);                       // filter out nulls
            jtx.copyTo(dest, copyState, dependencies);
        }

        // Done
        return copy;
    }

    /**
     * Find objects related to the specified object that are needed by any
     * {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated
     * methods into the current {@link org.jsimpledb.SnapshotJTransaction}. This effectively defines
     * all of the other objects on which any container property of {@code jobj} may depend.
     *
     * <p>
     * The implementation in {@link JObjectContainer} returns all objects that are directly referenced by {@code jobj},
     * delegating to {@link JSimpleDB#getReferencedObjects JSimpleDB.getReferencedObjects()}.
     * Subclasses may override this method to refine the selection.
     * </p>
     *
     * @param jobj the object being copied
     * @return {@link Iterable} of additional objects to be copied, or null for none; any null values are ignored
     * @throws IllegalArgumentException if {@code jobj} is null
     */
    protected Iterable<? extends JObject> getDependencies(JObject jobj) {
        return this.jdb.getReferencedObjects(jobj);
    }

    /**
     * Perform the given action within a {@link JTransaction}.
     *
     * @param action the action to perform
     */
    protected abstract void doInTransaction(Runnable action);

    /**
     * Query for the database objects that will be used to fill this container. Objects should be returned in the
     * desired order; any duplicates will be ignored.
     *
     * <p>
     * A {@link JTransaction} will be open in the current thread when this method is invoked.
     * </p>
     *
     * @return database objects
     */
    protected abstract Iterable<? extends JObject> queryForObjects();

// Property derivation

    private Collection<PropertyDef<?>> buildPropertyDefs() {
        final PropertyDefHolder pdefs = new PropertyDefHolder();

        // Add properties shared by all JObjects
        pdefs.setPropertyDef(this.refLabelPropertyDef);
        pdefs.setPropertyDef(this.objIdPropertyDef);
        pdefs.setPropertyDef(this.objTypePropertyDef);
        pdefs.setPropertyDef(this.objVersionPropertyDef);

        // Add properties for all fields common to all sub-types of our configured type
        final SortedMap<Integer, JField> jfields = Util.getCommonJFields(this.jdb.getJClasses(this.type));
        if (jfields != null) {
            for (JField jfield : jfields.values())
                pdefs.setPropertyDef(new ObjFieldPropertyDef(jfield.getStorageId(), jfield.getName()));
        }

        // Apply any @ProvidesProperty-annotated method properties, possibly overridding jfields
        if (this.propertyScanner != null) {
            for (PropertyDef<?> propertyDef : this.propertyScanner.getPropertyDefs())
                pdefs.setPropertyDef(propertyDef);
        }

        // Done
        return pdefs.values();
    }

// PropertyExtractor

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getPropertyValue(JObject jobj, PropertyDef<V> propertyDef) {
        if (propertyDef instanceof ObjPropertyDef)
            return (V)((ObjPropertyDef<?>)propertyDef).extract(jobj);
        if (this.propertyScanner == null)
            throw new IllegalArgumentException("unknown property: " + propertyDef.getName());
        return JObjectContainer.extractProperty(this.propertyScanner.getPropertyExtractor(), propertyDef, jobj);
    }

    @SuppressWarnings("unchecked")
    private static <V> V extractProperty(PropertyExtractor<?> propertyExtractor, PropertyDef<V> propertyDef, JObject jobj) {
        try {
            return ((PropertyExtractor<JObject>)propertyExtractor).getPropertyValue(jobj, propertyDef);
        } catch (DeletedObjectException e) {
            try {
                return propertyDef.getType().cast(new SizedLabel("<i>Unavailable</i>", ContentMode.HTML));
            } catch (ClassCastException e2) {
                try {
                    return propertyDef.getType().cast("(Unavailable)");
                } catch (ClassCastException e3) {
                    return null;
                }
            }
        }
    }

// ObjPropertyDef

    /**
     * Support superclass for {@link PropertyDef} implementations that derive the property value from a {@link JObject}.
     */
    public abstract static class ObjPropertyDef<T> extends PropertyDef<T> {

        protected ObjPropertyDef(String name, Class<T> type) {
            super(name, type);
        }

        public abstract T extract(JObject jobj);
    }

// ObjIdPropertyDef

    /**
     * Implements the {@link JObjectContainer#OBJECT_ID_PROPERTY} property.
     */
    public static class ObjIdPropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjIdPropertyDef() {
            super(OBJECT_ID_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel("<code>" + jobj.getObjId() + "</code>", ContentMode.HTML);
        }
    }

// ObjTypePropertyDef

    /**
     * Implements the {@link JObjectContainer#TYPE_PROPERTY} property.
     */
    public static class ObjTypePropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjTypePropertyDef() {
            super(TYPE_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel(jobj.getTransaction().getTransaction().getSchemas()
              .getVersion(jobj.getSchemaVersion()).getObjType(jobj.getObjId().getStorageId()).getName());
        }
    }

// ObjVersionPropertyDef

    /**
     * Implements the {@link JObjectContainer#VERSION_PROPERTY} property.
     */
    public static class ObjVersionPropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjVersionPropertyDef() {
            super(VERSION_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel("" + jobj.getSchemaVersion());
        }
    }

// RefLabelPropertyDef

    /**
     * Implements the {@link JObjectContainer#REFERENCE_LABEL_PROPERTY} property.
     */
    public static class RefLabelPropertyDef extends ObjPropertyDef<Component> {

        public RefLabelPropertyDef() {
            super(REFERENCE_LABEL_PROPERTY, Component.class);
        }

        @Override
        public Component extract(JObject jobj) {
            final ReferenceMethodInfoCache.PropertyInfo<?> propertyInfo
              = ReferenceMethodInfoCache.getInstance().getReferenceMethodInfo(jobj.getClass());
            if (propertyInfo == ReferenceMethodInfoCache.NOT_FOUND)
                return new ObjIdPropertyDef().extract(jobj);
            final Object value = JObjectContainer.extractProperty(
              propertyInfo.getPropertyExtractor(), propertyInfo.getPropertyDef(), jobj);
            if (value instanceof Component)
                return (Component)value;
            return new SizedLabel(String.valueOf(value));
        }
    }

// ObjFieldPropertyDef

    /**
     * Implements a property reflecting the value of a {@link JSimpleDB} field.
     */
    public class ObjFieldPropertyDef extends ObjPropertyDef<Component> {

        private static final int MAX_ITEMS = 3;

        private final int storageId;

        public ObjFieldPropertyDef(int storageId, String name) {
            super(name, Component.class);
            this.storageId = storageId;
        }

        @Override
        public Component extract(final JObject jobj) {
            final JField jfield = JObjectContainer.this.jdb.getJClass(jobj.getObjId()).getJField(this.storageId, JField.class);
            try {
                return jfield.visit(new JFieldSwitchAdapter<Component>() {

                    @Override
                    public Component caseJSimpleField(JSimpleField field) {
                        return ObjFieldPropertyDef.this.handleValue(field.getValue(jobj));
                    }

                    @Override
                    public Component caseJCounterField(JCounterField field) {
                        return ObjFieldPropertyDef.this.handleValue(field.getValue(jobj).get());
                    }

                    @Override
                    protected Component caseJCollectionField(JCollectionField field) {
                        return ObjFieldPropertyDef.this.handleCollectionField(field.getValue(jobj));
                    }

                    @Override
                    public Component caseJMapField(JMapField field) {
                        return ObjFieldPropertyDef.this.handleMultiple(Iterables.transform(field.getValue(jobj).entrySet(),
                          new Function<Map.Entry<?, ?>, Component>() {
                            @Override
                            public Component apply(Map.Entry<?, ?> entry) {
                                final HorizontalLayout layout = new HorizontalLayout();
                                layout.setMargin(false);
                                layout.setSpacing(false);
                                layout.addComponent(ObjFieldPropertyDef.this.handleValue(entry.getKey()));
                                layout.addComponent(new SizedLabel(" \u21d2 "));        // RIGHTWARDS DOUBLE ARROW
                                layout.addComponent(ObjFieldPropertyDef.this.handleValue(entry.getValue()));
                                return layout;
                            }
                        }));
                    }
                });
            } catch (UnknownFieldException e) {
                return new SizedLabel("<i>NA</i>", ContentMode.HTML);
            }
        }

        private Component handleCollectionField(Collection<?> col) {
            return this.handleMultiple(Iterables.transform(col, new Function<Object, Component>() {
                @Override
                public Component apply(Object item) {
                    return ObjFieldPropertyDef.this.handleValue(item);
                }
            }));
        }

        private Component handleMultiple(Iterable<Component> components) {
            final HorizontalLayout layout = new HorizontalLayout();
            layout.setMargin(false);
            layout.setSpacing(false);
            int count = 0;
            for (Component component : components) {
                if (count >= MAX_ITEMS) {
                    layout.addComponent(new SizedLabel("..."));
                    break;
                }
                if (count > 0)
                    layout.addComponent(new SizedLabel(",&#160;", ContentMode.HTML));
                layout.addComponent(component);
                count++;
            }
            return layout;
        }

        @SuppressWarnings("unchecked")
        private <T> Component handleValue(Object value) {
            if (value == null)
                return new SizedLabel("<i>Null</i>", ContentMode.HTML);
            if (value instanceof JObject)
                return new RefLabelPropertyDef().extract((JObject)value);
            return new SizedLabel(String.valueOf(value));
        }
    }

// PropertyDefHolder

    private static class PropertyDefHolder extends LinkedHashMap<String, PropertyDef<?>> {

        public void setPropertyDef(PropertyDef<?> propertyDef) {
            this.put(propertyDef.getName(), propertyDef);
        }
    }
}

