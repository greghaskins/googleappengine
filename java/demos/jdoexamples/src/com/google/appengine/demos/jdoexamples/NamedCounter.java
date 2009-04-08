package com.google.appengine.demos.jdoexamples;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

@PersistenceCapable(identityType=IdentityType.APPLICATION, detachable="true")
public class NamedCounter {

  @PrimaryKey
  private String name;
  
  @Persistent
  private int count;

  public NamedCounter(String name) {
    // You have to supply the keyName here, event though this
    // field later becomes the serialized Key.
    this.name = name;
    this.count = 0;
  }

  public int getCount() {
    return count;
  }

  public void add(int delta) {
    count += delta;
  }
  
  public String getName() {
    // Once submitted, the @PrimaryKey will be turned into
    // a serialized Key instance, which is why we need to
    // do this stuff.
    return KeyFactory.stringToKey(name).getName();
  }
}
