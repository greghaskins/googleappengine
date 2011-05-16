// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import com.google.storage.onestore.v3.OnestoreEntity.Path;

/**
 * {@code EntityTranslator} contains the logic to translate an {@code
 * Entity} into the protocol buffers that are used to pass it to the
 * implementation of the API.
 *
 */
public class EntityTranslator {

  public static Entity createFromPb(EntityProto proto) {
    Key key = KeyTranslator.createFromPb(proto.getKey());

    Entity entity = new Entity(key);
    entity.setEntityProto(proto);
    DataTypeTranslator.extractPropertiesFromPb(proto, entity.getPropertyMap());
    return entity;
  }

  public static EntityProto convertToPb(Entity entity) {
    Reference reference = KeyTranslator.convertToPb(entity.getKey());

    EntityProto proto = new EntityProto();
    proto.getMutableKey().mergeFrom(reference);

    Path entityGroup = proto.getMutableEntityGroup();
    Key key = entity.getKey();
    if (key.isComplete()) {
      entityGroup.addElement(reference.getPath().elements().get(0));
    }

    DataTypeTranslator.addPropertiesToPb(entity.getPropertyMap(), proto);
    return proto;
  }

  private EntityTranslator() {
  }
}
