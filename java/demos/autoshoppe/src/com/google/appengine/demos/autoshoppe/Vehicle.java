/*
 * Created by IntelliJ IDEA.
 * User: anirudhd
 * Date: Feb 24, 2009
 * Time: 6:24:50 AM
 */
package com.google.appengine.demos.autoshoppe;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PrimaryKey;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.IdGeneratorStrategy;

/**
 * @author zoso@google.com (Anirudh Dewani)
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class Vehicle {

  @PrimaryKey
  @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
  private Long id = null;

  @Persistent
  private int year = 0;

  @Persistent
  private long date = 0;

  @Persistent
  private String type = null;

  @Persistent
  private String owner = null;

  @Persistent
  private String model = null;

  @Persistent
  private String make = null;

  @Persistent
  private long mileage = 0;

  @Persistent
  private long price = 0;

  @Persistent
  private String status = null;

  @Persistent
  private String buyer = null;

  @Persistent
  private String color = null;

  @Persistent
  private String image = null;

  public Vehicle() {
    //default contructor
  }

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public long getDate() {
    return date;
  }

  public void setDate(long date) {
    this.date = date;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getMake() {
    return make;
  }

  public void setMake(String make) {
    this.make = make;
  }

  public long getMileage() {
    return mileage;
  }

  public void setMileage(long mileage) {
    this.mileage = mileage;
  }

  public long getPrice() {
    return price;
  }

  public void setPrice(long price) {
    this.price = price;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBuyer() {
    return buyer;
  }

  public void setBuyer(String buyer) {
    this.buyer = buyer;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }


  public String getId() {
    return id.toString();
  }

  public void setId(String id) {
    this.id = Long.parseLong(id);
  }
}