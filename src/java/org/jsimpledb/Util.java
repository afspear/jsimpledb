
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.Constraint;

import org.jsimpledb.annotation.Validate;

/**
 * Various utility routines.
 */
public final class Util {

    private static final WildcardType QUESTION_MARK = new WildcardType() {

        @Override
        public Type[] getUpperBounds() {
            return new Type[] { Object.class };
        }

        @Override
        public Type[] getLowerBounds() {
            return new Type[0];
        }

        @Override
        public String toString() {
            return "?";
        }
    };

    private static Method newParameterizedTypeMethod;

    private Util() {
    }

    /**
     * Determine if instances of the given type require any validation.
     *
     * <p>
     * This will be true if {@code type} or any of its declared methods has a JSR 303 (<i>public</i> methods only)
     * or {@link Validate &#64;Validate} annotation, or if any of its super-types requires validation.
     * </p>
     *
     * @param type object type
     * @return true if {@code type} has any validation requirements
     * @throws IllegalArgumentException if {@code type} is null
     * @see ValidationMode
     */
    public static boolean requiresValidation(Class<?> type) {
        Preconditions.checkArgument(type != null, "null type");
        if (Util.hasValidationAnnotation(type))
            return true;
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Validate.class)
              || ((method.getModifiers() & Modifier.PUBLIC) != 0 && Util.hasValidationAnnotation(method)))
                return true;
        }
        for (TypeToken<?> typeToken : TypeToken.of(type).getTypes()) {
            final Class<?> superType = typeToken.getRawType();
            if (superType != type && Util.requiresValidation(superType))
                return true;
        }
        return false;
    }

    /**
     * Determine if the given getter method, or any method it overrides, has a JSR 303 validation constraint.
     *
     * @param method annotated method
     * @return true if {@code obj} has one or more JSR 303 annotations
     * @throws IllegalArgumentException if {@code method} is null
     */
    public static boolean requiresValidation(Method method) {
        Preconditions.checkArgument(method != null, "null method");
        for (TypeToken<?> typeToken : TypeToken.of(method.getDeclaringClass()).getTypes()) {
            final Class<?> superType = typeToken.getRawType();
            try {
                method = superType.getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (Util.hasValidationAnnotation(method))
                return true;
        }
        return false;
    }

    /**
     * Determine whether the given object has any JSR 303 annotation(s).
     *
     * @param obj annotated element
     * @return true if {@code obj} has one or more JSR 303 annotations
     * @throws IllegalArgumentException if {@code obj} is null
     */
    public static boolean hasValidationAnnotation(AnnotatedElement obj) {
        Preconditions.checkArgument(obj != null, "null obj");
        for (Annotation annotation : obj.getAnnotations()) {
            final Class<?> annotationType = annotation.annotationType();
            if (annotationType.isAnnotationPresent(Constraint.class))
                return true;
        }
        return false;
    }

    /**
     * Find the setter method corresponding to a getter method.
     *
     * @param type Java type (possibly a sub-type of the type in which {@code getter} is declared)
     * @param getter Java bean property getter method
     * @return Java bean property setter method
     * @throws IllegalArgumentException if no corresponding setter method exists
     */
    public static Method findSetterMethod(Class<?> type, Method getter) {
        final Matcher matcher = Pattern.compile("(is|get)(.+)").matcher(getter.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("can't infer setter method name from getter method "
              + getter.getName() + "() because name does not follow Java bean naming conventions");
        }
        final String setterName = "set" + matcher.group(2);
        for (TypeToken<?> superType : TypeToken.of(type).getTypes()) {
            try {
                final Method setter = superType.getRawType().getMethod(setterName, getter.getReturnType());
                if (setter.getReturnType() != Void.TYPE)
                    continue;
                return setter;
            } catch (NoSuchMethodException e) {
                continue;
            }
        }
        throw new IllegalArgumentException("can't find any setter method " + setterName
          + "() corresponding to getter method " + getter.getName() + "() taking " + getter.getReturnType()
          + " and returning void");
    }

    /**
     * Find the narrowest type that is a supertype of all of the given types.
     *
     * <p>
     * This method delegates to {@link #findLowestCommonAncestor findLowestCommonAncestor()}
     * after converting the {@link Class} instances to {@link TypeToken}s.
     * </p>
     *
     * @param types sub-types
     * @return narrowest common super-type
     * @see #findLowestCommonAncestor findLowestCommonAncestor()
     */
    public static TypeToken<?> findLowestCommonAncestorOfClasses(Iterable<Class<?>> types) {
        return Util.findLowestCommonAncestor(Iterables.transform(types, new Function<Class<?>, TypeToken<?>>() {
            @Override
            public TypeToken<?> apply(Class<?> type) {
                return TypeToken.of(type);
            }
        }));
    }

    /**
     * Find the narrowest type that is a supertype of all of the given types.
     *
     * <p>
     * When there is more than one choice, heuristics are used. For example, we prefer
     * non-interface types, and {@link JObject} over other interface types.
     * </p>
     *
     * @param types sub-types
     * @return narrowest common super-type
     */
    public static TypeToken<?> findLowestCommonAncestor(Iterable<TypeToken<?>> types) {

        // Gather all supertypes of types recursively
        final HashSet<TypeToken<?>> supertypes = new HashSet<>();
        for (TypeToken<?> type : types)
            Util.addSupertypes(supertypes, type);

        // Throw out all supertypes that are not supertypes of every type
        for (Iterator<TypeToken<?>> i = supertypes.iterator(); i.hasNext(); ) {
            final TypeToken<?> supertype = i.next();
            for (TypeToken<?> type : types) {
                if (!supertype.isAssignableFrom(type)) {
                    i.remove();
                    break;
                }
            }
        }

        // Throw out all supertypes that are supertypes of some other supertype
        for (Iterator<TypeToken<?>> i = supertypes.iterator(); i.hasNext(); ) {
            final TypeToken<?> supertype = i.next();
            for (TypeToken<?> supertype2 : supertypes) {
                if (supertype2 != supertype && supertype.isAssignableFrom(supertype2)) {
                    i.remove();
                    break;
                }
            }
        }

        // Pick the best candidate that's not Object, if possible
        final TypeToken<Object> objectType = TypeToken.of(Object.class);
        supertypes.remove(objectType);
        switch (supertypes.size()) {
        case 0:
            return objectType;
        case 1:
            return supertypes.iterator().next();
        default:
            break;
        }

        // Pick the one that's not an interface, if any (it will be the the only non-interface type)
        for (TypeToken<?> supertype : supertypes) {
            if (!supertype.getRawType().isInterface())
                return supertype;
        }

        // There are now only interfaces to choose from (or Object)... try JObject
        final TypeToken<JObject> jobjectType = TypeToken.of(JObject.class);
        if (supertypes.contains(jobjectType))
            return jobjectType;

        // Last resort is Object
        return objectType;
    }

    @SuppressWarnings("unchecked")
    private static <T> void addSupertypes(Set<TypeToken<?>> types, TypeToken<T> type) {
        if (type == null || !types.add(type))
            return;
        final Class<? super T> rawType = type.getRawType();
        types.add(TypeToken.of(rawType));
        types.add(Util.getWildcardedType(rawType));
        final Class<? super T> superclass = rawType.getSuperclass();
        if (superclass != null) {
            Util.addSupertypes(types, TypeToken.of(superclass));
            Util.addSupertypes(types, type.getSupertype(superclass));
        }
        for (Class<?> iface : rawType.getInterfaces())
            Util.addSupertypes(types, type.getSupertype((Class<? super T>)iface));
    }

    /**
     * Parameterize the raw type with wildcards.
     *
     * @param type raw type
     * @param <T> raw type
     * @return {@code type} genericized with wildcards
     */
    public static <T> TypeToken<? extends T> getWildcardedType(Class<T> type) {
        Preconditions.checkArgument(type != null, "null type");
        final TypeVariable<Class<T>>[] typeVariables = type.getTypeParameters();
        if (typeVariables.length == 0)
            return TypeToken.of(type);
        final WildcardType[] questionMarks = new WildcardType[typeVariables.length];
        Arrays.fill(questionMarks, QUESTION_MARK);
        return Util.newParameterizedType(type, questionMarks);
    }

    /**
     * Get the n'th generic type parameter.
     *
     * @param type parameterized generic type
     * @param index type parameter index (zero based)
     * @return type parameter at {@code index}
     * @throws IllegalArgumentException if {@code type} is not a parameterized type with more than {@code index} type variables
     */
    public static Type getTypeParameter(Type type, int index) {
        Preconditions.checkArgument(type instanceof ParameterizedType, "type is missing generic type parameter(s)");
        final ParameterizedType parameterizedType = (ParameterizedType)type;
        final Type[] parameters = parameterizedType.getActualTypeArguments();
        if (index >= parameters.length)
            throw new IllegalArgumentException("type is missing generic type parameter(s)");
        return parameters[index];
    }

    /**
     * Invoke method via reflection and re-throw any checked exception wrapped in an {@link JSimpleDBException}.
     *
     * @param method method to invoke
     * @param target instance, or null if method is static
     * @param params method parameters
     * @return method return value
     */
    public static Object invoke(Method method, Object target, Object... params) {
        try {
            return method.invoke(target, params);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error)
                throw (Error)e.getCause();
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException)e.getCause();
            throw new JSimpleDBException("unexpected error invoking method " + method + " on " + target, e);
        } catch (Error e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new JSimpleDBException("unexpected error invoking method " + method + " on " + target, e);
        }
    }

    /**
     * Convert a raw class back into its generic type using caller-supplied type parameters.
     *
     * @param target raw class
     * @param params type parameters
     * @param <T> raw class type
     * @return generic {@link TypeToken} for {@code target}
     * @see <a href="https://github.com/google/guava/issues/1645">Guava Issue #1645</a>
     */
    @SuppressWarnings("unchecked")
    public static <T> TypeToken<? extends T> newParameterizedType(Class<T> target, Type[] params) {
        Type type;
        try {
            if (Util.newParameterizedTypeMethod == null) {
                Util.newParameterizedTypeMethod = Class.forName("com.google.common.reflect.Types",
                  false, Thread.currentThread().getContextClassLoader()).getDeclaredMethod(
                  "newParameterizedType", Class.class, Type[].class);
                Util.newParameterizedTypeMethod.setAccessible(true);
            }
            type = (Type)Util.newParameterizedTypeMethod.invoke(null, target, params);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("unexpected exception", e);
        }
        return (TypeToken<T>)TypeToken.of(type);
    }
}

