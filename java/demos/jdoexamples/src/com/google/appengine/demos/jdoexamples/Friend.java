package com.google.appengine.demos.jdoexamples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.KeyFactory;

@PersistenceCapable(identityType=IdentityType.APPLICATION)
public class Friend {

  @PrimaryKey
  private String key;
  
  @Persistent
  private String lastName;
  
  @Persistent
  private String firstName;

  @Persistent(defaultFetchGroup = "true")
  private Collection<String> friends;
  
  public Friend(String lastName, String firstName) {
    this.key = lastName + ", " + firstName;
    this.lastName = lastName;
    this.firstName = firstName;
    this.friends = new ArrayList<String>();
  }
  
  public String getKey() {
    return key;
  }
  
  public String getLastName() {
    return lastName;
  }
  
  public String getFirstName() {
    return firstName;
  }

  public Collection<String> getFriendKeys() {
    return friends;
  }

  public void setFriendKeys(Collection<String> friends) {
    this.friends = friends;
  }
  
  public List<String> getFriends() {
    List<String> friendList = new ArrayList<String>();
    for (String friendKey : friends) {
      friendList.add(KeyFactory.stringToKey(friendKey).getName());
    }
    return friendList;
  }
}
