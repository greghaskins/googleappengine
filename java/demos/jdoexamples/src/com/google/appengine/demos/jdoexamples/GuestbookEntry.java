package com.google.appengine.demos.jdoexamples;

import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable(identityType=IdentityType.APPLICATION)
public class GuestbookEntry {

  @PrimaryKey
  @Persistent(valueStrategy=IdGeneratorStrategy.IDENTITY)
  private Long id;
  
  @Persistent
  private String who;
  
  @Persistent
  private Date when;
  
  @Persistent
  private String message;

  public GuestbookEntry(String who, String message) {
    this.message = message;
    this.who = who;
    this.when = new Date();
  }
  
  public String getWho() {
    return who;
  }

  public Date getWhen() {
    return when;
  }

  public String getMessage() {
    return message;
  }
 
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
