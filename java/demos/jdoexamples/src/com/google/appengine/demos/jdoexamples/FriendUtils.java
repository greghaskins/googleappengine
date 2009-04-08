package com.google.appengine.demos.jdoexamples;

import java.util.ArrayList;
import java.util.List;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

public class FriendUtils {

  public static Key getKeyForName(String lastName, String firstName) {
    return KeyFactory.createKey(Friend.class.getSimpleName(),
                                lastName + ", " + firstName);
  }

  public static void addFriendTo(String lastName, String firstName,
                                 String friendLastName,
                                 String friendFirstName) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    
    String meKey = KeyFactory.keyToString(getKeyForName(lastName, firstName));
    String otherKey = KeyFactory.keyToString(
        getKeyForName(friendLastName, friendFirstName));
    
    // First create the friend if he doesn't already exist.
    Friend other = null;
    try {
      pm.currentTransaction().begin();
      try {
        other = pm.getObjectById(Friend.class, otherKey);
        List<String> replacementFriends = new ArrayList<String>(
            other.getFriendKeys());
        replacementFriends.add(meKey);
        other.setFriendKeys(replacementFriends);
      } catch (JDOObjectNotFoundException e) {
        other = new Friend(friendLastName, friendFirstName);
        List<String> replacementFriends = new ArrayList<String>(
            other.getFriendKeys());
        replacementFriends.add(meKey);
        other.setFriendKeys(replacementFriends);
        pm.makePersistent(other);
      }
      pm.currentTransaction().commit();
    } finally {
      if (pm.currentTransaction().isActive()) {
        pm.currentTransaction().rollback();
      }
    }

    pm.close();
    pm = PMF.get().getPersistenceManager();

    // Then add a reference to the friend to my list.
    Friend me = null;
    try {
      pm.currentTransaction().begin();
      try {
        me = pm.getObjectById(Friend.class, meKey);
        List<String> replacementFriends = new ArrayList<String>(
            me.getFriendKeys());
        replacementFriends.add(otherKey);
        me.setFriendKeys(replacementFriends);
      } catch (JDOObjectNotFoundException e) {
        me = new Friend(lastName, firstName);
        List<String> replacementFriends = new ArrayList<String>(
            me.getFriendKeys());
        replacementFriends.add(otherKey);
        me.setFriendKeys(replacementFriends);
        pm.makePersistent(me);
      }
      pm.currentTransaction().commit();
    } finally {
      if (pm.currentTransaction().isActive()) {
        pm.currentTransaction().rollback();
      }
    }
  }
  
  public static List<Friend> getFriendsOf(String lastName, String firstName) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    
    Query query = pm.newQuery(Friend.class);
    String myKey = KeyFactory.keyToString(getKeyForName(lastName, firstName));
    query.declareParameters("String myKey");
    query.setFilter("friends == myKey");
    query.setOrdering("lastName ASC, firstName ASC");
    List<Friend> friends = (List<Friend>) query.execute(myKey);
    
    return friends;
  }
}
