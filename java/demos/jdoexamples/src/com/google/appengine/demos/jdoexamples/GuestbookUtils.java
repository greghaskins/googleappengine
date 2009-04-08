package com.google.appengine.demos.jdoexamples;

import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

public class GuestbookUtils {

  public static void insert(String who, String message) {
    GuestbookEntry entry = new GuestbookEntry(who, message);
    PersistenceManager pm = PMF.get().getPersistenceManager();
    pm.makePersistent(entry);
  }
  
  public static List<GuestbookEntry> getEntries() {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query query = pm.newQuery(GuestbookEntry.class);
    query.setOrdering("when DESC");
    List<GuestbookEntry> entries = (List<GuestbookEntry>) query.execute();
    return entries;
  }
}
