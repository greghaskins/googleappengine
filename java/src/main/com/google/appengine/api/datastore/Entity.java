// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.storage.onestore.v3.OnestoreEntity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code Entity} is the fundamental unit of data storage.  It has an
 * immutable identifier (contained in the {@code Key}) object, a
 * reference to an optional parent {@code Entity}, a kind (represented
 * as an arbitrary string), and a set of zero or more typed
 * properties.
 *
 */
public final class Entity implements Cloneable, Serializable {

  static final long serialVersionUID = -836647825120453511L;

  /**
   * A reserved property name used to refer to the key of the entity.
   * This string can be used for filtering and sorting by the entity
   * key itself.
   */
  public static final String KEY_RESERVED_PROPERTY = "__key__";

  /**
   * A reserved property name used to refer to the scatter property of the
   * entity.
   * Used for finding split points (e.g. for mapping over a kind).
   */
  public static final String SCATTER_RESERVED_PROPERTY = "__scatter__";

  private final Key key;
  private final Map<String, Object> propertyMap;

  private transient OnestoreEntity.EntityProto entityProto;

  static final class UnindexedValue implements Serializable {
    private final Object value;

    /**
     * @param value may be null
     */
    UnindexedValue(Object value) {
      this.value = value;
    }

    public Object getValue() {
      return value;
    }

    @Override
    public boolean equals(Object that) {
      if (that instanceof UnindexedValue) {
        UnindexedValue uv = (UnindexedValue) that;
        return (value == null) ? uv.value == null : value.equals(uv.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (value == null) ? 0 : value.hashCode();
    }

    @Override
    public String toString() {
      return value + " (unindexed)";
    }
  }

  /**
   * Create a new {@code Entity} with the specified kind and no
   * parent {@code Entity}.  The instantiated {@code Entity} will have an
   * incomplete {@link Key} when this constructor returns.  The
   * {@link Key} will remain incomplete until you put the {@code Entity},
   * after which time the {@link Key} will have its {@code id} set.
   */
  public Entity(String kind) {
    this(kind, (Key) null);
  }

  /**
   * Create a new {@code Entity} with the specified kind and parent
   * {@code Entity}.  The instantiated {@code Entity} will have an
   * incomplete {@link Key} when this constructor returns.  The
   * {@link Key} will remain incomplete until you put the {@code Entity},
   * after which time the {@link Key} will have its {@code id} set.
   */
  public Entity(String kind, Key parent) {

    this(new Key(kind, parent));
  }

  /**
   * Create a new {@code Entity} with the specified kind and key name and no
   * parent {@code Entity}.  The instantiated {@code Entity} will have a
   * complete {@link Key} when this constructor returns.  The
   * {@link Key Key's} {@code name} field will be set to the value of
   * {@code keyName}.
   */
  public Entity(String kind, String keyName) {
    this(KeyFactory.createKey(kind, keyName));
  }

  /**
   * Create a new {@code Entity} with the specified kind and ID and no
   * parent {@code Entity}.  The instantiated {@code Entity} will have a
   * complete {@link Key} when this constructor returns.  The
   * {@link Key Key's} {@code id} field will be set to the value of
   * {@code id}.
   *
   * <p>Creating an entity for the purpose of insertion (as opposed to
   * update) with this constructor is discouraged unless the id was
   * obtained from a key returned by a {@link KeyRange} obtained from
   * {@link AsyncDatastoreService#allocateIds(String, long)} or
   * {@link DatastoreService#allocateIds(String, long)} for the same
   * kind.
   */
  public Entity(String kind, long id) {
    this(KeyFactory.createKey(kind, id));
  }

  /**
   * Create a new {@code Entity} with the specified kind, key name, and
   * parent {@code Entity}.  The instantiated {@code Entity} will have a
   * complete {@link Key} when this constructor returns.  The
   * {@link Key Key's} {@code name} field will be set to the value of
   * {@code keyName}.
   */
  public Entity(String kind, String keyName, Key parent) {
    this(KeyFactory.createKey(parent, kind, keyName));
  }

  /**
   * Create a new {@code Entity} with the specified kind and ID and
   * parent {@code Entity}.  The instantiated {@code Entity} will have a
   * complete {@link Key} when this constructor returns.  The
   * {@link Key Key's} {@code id} field will be set to the value of
   * {@code id}.
   *
   * <p>Creating an entity for the purpose of insertion (as opposed to
   * update) with this constructor is discouraged unless the id was
   * obtained from a key returned by a {@link KeyRange} obtained from
   * {@link AsyncDatastoreService#allocateIds(Key, String, long)} or
   * {@link DatastoreService#allocateIds(Key, String, long)} for the same
   * parent and kind.
   */
  public Entity(String kind, long id, Key parent) {
    this(KeyFactory.createKey(parent, kind, id));
  }

  /**
   * Create a new {@code Entity} uniquely identified by the provided
   * {@link Key}.  Creating an entity for the purpose of insertion (as opposed
   * to update) with a key that has its {@code id} field set is strongly
   * discouraged unless the key was returned by a {@link KeyRange}.
   *
   * @see KeyRange
   */
  public Entity(Key key) {
    this.key = key;
    this.propertyMap = new HashMap<String, Object>();
  }

  /**
   * Two {@code Entity} objects are considered equal if they refer to
   * the same entity (i.e. their {@code Key} objects match).
   */
  @Override
  public boolean equals(Object object) {
    if (object instanceof Entity) {
      Entity otherEntity = (Entity) object;
      return key.equals(otherEntity.key);
    }
    return false;
  }

  /**
   * Returns the {@code Key} that represents this {@code Entity}.  If
   * the entity has not yet been saved (e.g. via {@code
   * DatastoreService.put}), this {@code Key} will not be fully
   * specified and cannot be used for certain operations (like {@code
   * DatastoreService.get}).  Once the {@code Entity} has been saved,
   * its {@code Key} will be updated to be fully specified.
   */
  public Key getKey() {
    return key;
  }

  /**
   * Returns a logical type that is associated with this {@code
   * Entity}.  This is simply a convenience method that forwards to
   * the {@code Key} for this {@code Entity}.
   */
  public String getKind() {
    return key.getKind();
  }

  /**
   * Get a {@code Key} that corresponds to this the parent {@code
   * Entity} of this {@code Entity}.  This is simply a convenience
   * method that forwards to the {@code Key} for this {@code Entity}.
   */
  public Key getParent() {
    return key.getParent();
  }

  /**
   * Gets the property with the specified name. The value returned
   * may not be the same type as originally set via {@link #setProperty}.
   *
   * @return the property corresponding to {@code propertyName}.
   */
  public Object getProperty(String propertyName) {
    return unwrapValue(propertyMap.get(propertyName));
  }

  /**
   * Gets all of the properties belonging to this {@code Entity}.
   *
   * @return an unmodifiable {@code Map} of properties.
   */
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>(propertyMap.size());

    for (Map.Entry<String, Object> entry : propertyMap.entrySet()) {
      properties.put(entry.getKey(), unwrapValue(entry.getValue()));
    }

    return Collections.unmodifiableMap(properties);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  /**
   * Returns true if a property has been set. This function can
   * be used to test if a property has been specifically set
   * to {@code null}.
   *
   * @return true iff the property named {@code propertyName} exists.
   */
  public boolean hasProperty(String propertyName) {
    return propertyMap.containsKey(propertyName);
  }

  /**
   * Removes any property with the specified name.  If there is no
   * property with this name set, simply does nothing.
   *
   * @throws NullPointerException If {@code propertyName} is null.
   */
  public void removeProperty(String propertyName) {
    propertyMap.remove(propertyName);
  }

  /**
   * Sets the property named, {@code propertyName}, to {@code value}.
   * <p>
   * As the value is stored in the datastore, it is converted to the
   * datastore's native type. This may include widening, such as
   * converting a {@code Short} to a {@code Long}.
   * <p>
   * All {@code Collections} are prone
   * to losing their sort order and their original types as they are
   * stored in the datastore. For example, a {@code TreeSet} may be
   * returned as a {@code List} from {@link #getProperty}, with an
   * arbitrary re-ordering of elements.
   * <p>
   * Overrides any existing value for this property, whether indexed or
   * unindexed.
   * <p>
   * Note that {@link Blob} and {@code Text} property values are never indexed
   * by the built-in single property indexes. To store other types without
   * being indexed, use {@code #setUnindexedProperty}.
   *
   * @param value may be one of the supported datatypes, a heterogenous
   * {@code Collection} of one of the supported datatypes, or an
   * {@code UnindexedValue} wrapping one of the supported datatypes.
   *
   * @throws IllegalArgumentException If the value is not of a type that
   * the data store supports.
   *
   * @see #setUnindexedProperty
   */
  public void setProperty(String propertyName, Object value) {
    DataTypeUtils.checkSupportedValue(propertyName, value);
    propertyMap.put(propertyName, value);
  }

  /**
   * Like {@code #setProperty}, but doesn't index the property in the built-in
   * single property indexes.
   * <p>
   * @param value may be one of the supported datatypes, or a heterogenous
   * {@code Collection} of one of the supported datatypes.
   * <p>
   * Overrides any existing value for this property, whether indexed or
   * unindexed.
   *
   * @throws IllegalArgumentException If the value is not of a type that
   * the data store supports.
   *
   * @see #setProperty
   */
  public void setUnindexedProperty(String propertyName, Object value) {
    DataTypeUtils.checkSupportedValue(propertyName, value);
    propertyMap.put(propertyName, new UnindexedValue(value));
  }

  /**
   * Returns true if {@code propertyName} has a value that will not be
   * indexed. This includes {@link Text}, {@link Blob}, and any property
   * added using {@link #setUnindexedProperty}.
   */
  public boolean isUnindexedProperty(String propertyName) {
    Object value = propertyMap.get(propertyName);
    return (value instanceof UnindexedValue) || (value instanceof Text) ||
          (value instanceof Blob);
  }

  @Override
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("<Entity [" + key + "]:\n");
    for (Map.Entry<String, Object> entry : propertyMap.entrySet()) {
      buffer.append("\t" + entry.getKey() + " = " + entry.getValue() + "\n");
    }
    buffer.append(">\n");
    return buffer.toString();
  }

  /**
   * Returns the identifier of the application that owns this {@code
   * Entity}.  This is simply a convenience method that forwards to
   * the {@code Key} for this {@code Entity}.
   */
  public String getAppId() {
    return key.getAppId();
  }

  /**
   * Returns the AppIdNamespace of the application/namespace that owns
   * this {@code Entity}.  This is simply a convenience method that forwards to
   * the {@code Key} for this {@code Entity}.
   */
  AppIdNamespace getAppIdNamespace() {
    return key.getAppIdNamespace();
  }

  /**
   * Returns the namespace of the application/namespace that owns
   * this {@code Entity}.  This is simply a convenience method that forwards to
   * the {@code Key} for this {@code Entity}.
   */
  public String getNamespace() {
    return key.getNamespace();
  }

  /**
   * Returns a shallow copy of this {@code Entity} instance. {@code Collection}
   * properties are cloned as an {@code ArrayList}, the type returned from the
   * datastore. Instances of mutable datastore types are cloned as well.
   * Instances of all other types are reused.
   *
   * @return a shallow copy of this {@code Entity}
   */
  @Override
  public Entity clone() {
    Entity entity = new Entity(key);
    entity.setPropertiesFrom(this);
    return entity;
  }

  /**
   * A convenience method that populates the properties of this {@code Entity}
   * with the properties set on the provided {@code Entity}.  This method
   * transfers information about unindexed properties.
   *
   * @param src The entity from which we will populate ourself.
   */
  public void setPropertiesFrom(Entity src) {
    for (Map.Entry<String, Object> entry : src.propertyMap.entrySet()) {
      String name = entry.getKey();
      Object entryValue = entry.getValue();

      boolean indexed = entryValue instanceof UnindexedValue;
      Object valueToAdd = unwrapValue(entryValue);

      if (valueToAdd instanceof Collection<?>) {
        Collection<?> srcColl = (Collection<?>) valueToAdd;
        Collection<Object> destColl = new ArrayList<Object>(srcColl.size());
        valueToAdd = destColl;
        for (Object element : srcColl) {
          destColl.add(cloneIfMutable(element));
        }
      } else {
        valueToAdd = cloneIfMutable(valueToAdd);
      }

      if (indexed) {
        valueToAdd = new UnindexedValue(valueToAdd);
      }

      propertyMap.put(name, valueToAdd);
    }
  }

  /**
   * Returns a clone of the provided object if it is mutable, otherwise
   * just return the provided object.
   */
  private Object cloneIfMutable(Object obj) {
    if (obj instanceof Date) {
      return ((Date) obj).clone();
    }
    return obj;
  }

  /**
   * If obj is an {@code UnindexedValue}, returns the value it wraps.
   * Otherwise, returns {@code obj}.
   *
   * @param obj may be null
   */
  static Object unwrapValue(Object obj) {
    if (obj instanceof UnindexedValue) {
      return ((UnindexedValue) obj).getValue();
    } else {
      return obj;
    }
  }

  Map<String, Object> getPropertyMap() {
    return propertyMap;
  }

  void setEntityProto(OnestoreEntity.EntityProto entityProto) {
    this.entityProto = entityProto;
  }

  OnestoreEntity.EntityProto getEntityProto() {
    return entityProto;
  }
}
