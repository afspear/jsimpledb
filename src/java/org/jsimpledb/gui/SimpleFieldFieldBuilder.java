
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.gui;

import com.vaadin.shared.ui.datefield.Resolution;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Field;
import com.vaadin.ui.PopupDateField;
import com.vaadin.ui.TextField;

import java.util.Date;

import org.dellroad.stuff.vaadin7.EnumComboBox;
import org.jsimpledb.JReferenceField;
import org.jsimpledb.JSimpleField;
import org.jsimpledb.JTransaction;
import org.jsimpledb.core.FieldType;
import org.jsimpledb.parse.ParseSession;

/**
 * Builds {@link Field}s for editing {@link JSimpleField} values.
 */
@SuppressWarnings("serial")
public class SimpleFieldFieldBuilder {

    private final JTransaction jtx;
    private final JSimpleField jfield;
    private final ParseSession session;
    private final boolean allowNull;

    /**
     * Constructor.
     *
     * @param jtx target transaction used by {@link JObjectChooser}
     * @param jfield the database {@link JSimpleField} for which to build a Vaadin {@link Field}
     * @param session session used by {@link JObjectChooser}
     * @param allowNull whether null values are allowed
     */
    public SimpleFieldFieldBuilder(JTransaction jtx, JSimpleField jfield, ParseSession session, boolean allowNull) {
        if (jtx == null)
            throw new IllegalArgumentException("null jtx");
        if (jfield == null)
            throw new IllegalArgumentException("null jfield");
        if (session == null)
            throw new IllegalArgumentException("null session");
        this.jtx = jtx;
        this.jfield = jfield;
        this.session = session;
        this.allowNull = allowNull && !jfield.getTypeToken().isPrimitive();
    }

    /**
     * Build a {@link Field} appropriate for the configured {@link JSimpleField}.
     *
     * @return Vaadin {@link Field} for editing the field value
     */
    public Field<?> buildField() {

        // Get property type
        final Class<?> propertyType = jfield.getTypeToken().getRawType();

        // Build an appropriate field
        Field<?> field;
        if (jfield instanceof JReferenceField)                                          // use object choosers for references
            field = new ReferenceFieldField(this.jtx, this.session, jfield.getName(), jfield.getTypeToken().getRawType());
        else if (Enum.class.isAssignableFrom(propertyType)) {                           // use ComboBox for Enum's
            final EnumComboBox comboBox = this.createEnumComboBox(propertyType.asSubclass(Enum.class));
            comboBox.setInputPrompt("Null");
            field = comboBox;
        } else if (propertyType.isAssignableFrom(Date.class)) {                         // use DatePicker for dates
            final PopupDateField dateField = new PopupDateField();
            dateField.setResolution(Resolution.SECOND);
            field = dateField;
        } else if (propertyType == boolean.class || propertyType == Boolean.class)      // use CheckBox for booleans
            field = new CheckBox();
        else {                                                                          // use text field for all others
            final TextField textField = new TextField();
            textField.setWidth("100%");
            textField.setNullRepresentation("");
            textField.setNullSettingAllowed(false);
            final FieldType<?> fieldType = jfield.getFieldType();
            textField.setConverter(this.buildSimpleFieldConverter(fieldType));
            field = textField;
        }

        // Add a "Null" button if field can be null
        if (this.allowNull)
            field = this.addNullButton(field);

        // Done
        return field;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T extends Enum> EnumComboBox createEnumComboBox(Class<T> enumType) {
        return new EnumComboBox(enumType, this.allowNull);
    }

    // This method exists solely to bind the generic type parameters
    private <T> SimpleFieldConverter<T> buildSimpleFieldConverter(FieldType<T> fieldType) {
        return new SimpleFieldConverter<T>(fieldType);
    }

    // This method exists solely to bind the generic type parameters
    private <T> NullableField<T> addNullButton(Field<T> field) {
        return new NullableField<T>(field);
    }
}

