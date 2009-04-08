package com.google.appengine.demos.jdoexamples;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

public class NamedCounterUtils {

  public static Key getKeyForName(String name) {
    return KeyFactory.createKey(NamedCounter.class.getSimpleName(), name);
  }
  
  public static NamedCounter getByName(String name) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    NamedCounter counter = null;
    
    try {
      counter = pm.getObjectById(NamedCounter.class,
          KeyFactory.keyToString(getKeyForName(name)));
    } catch (JDOObjectNotFoundException e) {
      counter = new NamedCounter(name);
    }
    return counter;
  }
  
  public static int addAndGet(String name, int delta) {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    NamedCounter counter = null;

    try {
      // Detach the persisted objects after changes are commited since we
      // access members after the transaction is commited. Without this
      // setting, the counter members could be wiped after the transaction is
      // closed.
      pm.setDetachAllOnCommit(true); 
      pm.currentTransaction().begin();
      try {
        counter = pm.getObjectById(NamedCounter.class,
            KeyFactory.keyToString(getKeyForName(name)));
        counter.add(delta);
      } catch (JDOObjectNotFoundException e) {
        counter = new NamedCounter(name);
        // Only need this make persistent call here, since any counter
        // already retrieved using JDO will be marked as persistent once
        // we increment the counter; JDO will automatically write it to
        // the Datastore on commit.
        counter.add(delta);
        pm.makePersistent(counter);
      }
      pm.currentTransaction().commit();
    } finally {
      if (pm.currentTransaction().isActive()) {
        pm.currentTransaction().rollback();
      }
    }
    return counter.getCount();
  }
  
  public static void reset(String name) {
    PersistenceManager pm = PMF.get().getPersistenceManager();

    try {
      pm.currentTransaction().begin();
      try {
        NamedCounter counter = pm.getObjectById(NamedCounter.class,
            KeyFactory.keyToString(getKeyForName(name)));
        pm.deletePersistent(counter);
      } catch (JDOObjectNotFoundException e) {
        // Object isn't there, so ignore it.
        return;
      }
      pm.currentTransaction().commit();
    } finally {
      if (pm.currentTransaction().isActive()) {
        pm.currentTransaction().rollback();
      }
    }
  }
}
