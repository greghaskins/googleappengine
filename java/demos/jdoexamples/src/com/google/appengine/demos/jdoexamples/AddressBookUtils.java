package com.google.appengine.demos.jdoexamples;

import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

public class AddressBookUtils {

  private static final int ENTITIES_PER_PAGE = 3;

  public static String insertNew(
      String firstName, String lastName, String city,
      String state, String phoneNumber) {
    AddressBookEntry entry = new AddressBookEntry();
    entry.setPersonalInfo(new AddressBookEntry.PersonalInfo(firstName,
        lastName));
    entry.setAddressInfo(new AddressBookEntry.AddressInfo(city, state));
    entry.setContactInfo(new AddressBookEntry.ContactInfo(phoneNumber));
    
    PersistenceManager pm = PMF.get().getPersistenceManager();
    pm.makePersistent(entry);

    System.out.println(
        "The ID of the new entry is: " + entry.getId().toString());
    
    return entry.getId().toString();
  }
  
  public static List<AddressBookEntry> getPage(
        Long keyOffset, int indexOffset, String lastName, String state) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    
    Query query = pm.newQuery(AddressBookEntry.class);
    query.declareParameters(
        "Long keyOffset, String lastNameSelected, String stateSelected");
    StringBuilder filter = new StringBuilder();
    String combine = "";
    if (keyOffset != null && !keyOffset.equals("")) {
      if (filter.length() != 0) {
        filter.append(" && ");
      }
      filter.append("id >= keyOffset");
    }
    if (lastName != null && !lastName.equals("")) {
      if (filter.length() != 0) {
        filter.append(" && ");
      }
      filter.append("personalInfo.lastName == lastNameSelected");
    }
    if (state != null && !state.equals("")) {
      if (filter.length() != 0) {
        filter.append(" && ");
      }
      filter.append("addressInfo.state == stateSelected");
    }
    System.out.println("Filter is: " + filter.toString());
    if (filter.length() > 0) {
      query.setFilter(filter.toString());
    }
    query.setRange(indexOffset, indexOffset + ENTITIES_PER_PAGE + 1);
    return (List<AddressBookEntry>) query.execute(keyOffset, lastName, state);
  }
}
