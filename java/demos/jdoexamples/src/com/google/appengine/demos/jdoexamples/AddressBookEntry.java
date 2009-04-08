package com.google.appengine.demos.jdoexamples;

import javax.jdo.annotations.Embedded;
import javax.jdo.annotations.EmbeddedOnly;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable(identityType=IdentityType.APPLICATION)
public class AddressBookEntry {

  private static final int ENTITIES_PER_PAGE = 3;

  @PersistenceCapable
  @EmbeddedOnly
  public static class PersonalInfo {
    public String lastName;
    public String firstName;
    
    public PersonalInfo(String firstName, String lastName) {
      this.lastName = lastName;
      this.firstName = firstName;
    }
  }
  
  @PersistenceCapable
  @EmbeddedOnly
  public static class AddressInfo {
    public String city;
    public String state;
    
    public AddressInfo(String city, String state) {
      this.city = city;
      this.state = state;
    }
  }

  @PersistenceCapable
  @EmbeddedOnly
  public static class ContactInfo {
    public String phoneNumber;
    
    public ContactInfo(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }
  }
  
  @PrimaryKey
  @Persistent(valueStrategy=IdGeneratorStrategy.IDENTITY)
  private Long id;
  
  @Persistent
  @Embedded
  private PersonalInfo personalInfo;
  
  @Persistent
  @Embedded
  private AddressInfo addressInfo;
  
  @Persistent
  @Embedded
  private ContactInfo contactInfo;
  
  public PersonalInfo getPersonalInfo() {
    return personalInfo;
  }

  public void setPersonalInfo(PersonalInfo personalInfo) {
    this.personalInfo = personalInfo;
  }

  public ContactInfo getContactInfo() {
    return contactInfo;
  }

  public void setContactInfo(ContactInfo contactInfo) {
    this.contactInfo = contactInfo;
  }

  public void setAddressInfo(AddressInfo addressInfo) {
    this.addressInfo = addressInfo;
  }

  public AddressInfo getAddressInfo() {
    return addressInfo;
  }
  
  public Long getId() {
    return id;
  }
}
