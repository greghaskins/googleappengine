// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.demos.oauth;

/**
 * Stores information related to Google services.
 *
 * @author monsur@gmail.com (Monsur Hossain)
 */
public enum ServiceInfo {

  GOOGLE_BASE("Google Base Data API", "gbase",
      "http://www.google.com/base/feeds/",
      "http://www.google.com/base/feeds/snippets?bq=camera"),
  BLOGGER("Blogger Data API", "blogger", "http://www.blogger.com/feeds/",
      "http://www.blogger.com/feeds/default/blogs"),
  BOOK_SEARCH("Book Search Data API", "print",
      "http://www.google.com/books/feeds/",
      "http://www.google.com/books/feeds/volumes?q=javascript&max-results=15"
  ),
  CONTACTS("Contacts Data API", "cp", "http://www.google.com/m8/feeds/",
      "http://www.google.com/m8/feeds/contacts/default/base"),
  DOCUMENTS_LIST("Documents List Data API", "writely",
      "http://docs.google.com/feeds/",
      "http://docs.google.com/feeds/documents/private/full"),
  FINANCE("Finance Data API", "finance",
      "http://finance.google.com/finance/feeds/",
      "http://finance.google.com/finance/feeds/default/portfolios"),
  PICASA_WEB_ALBUMS("Picasa Web Albums Data API", "lh2",
      "http://picasaweb.google.com/data/",
      "http://picasaweb.google.com/data/feed/api/user/default"),
  SPREADSHEETS("Spreadsheets Data API", "wise",
      "http://spreadsheets.google.com/feeds/",
      "http://spreadsheets.google.com/feeds/spreadsheets/private/full"),
  YOUTUBE("YouTube Data API", "youtube", "http://gdata.youtube.com",
      "http://gdata.youtube.com/feeds/api/videos?q=skateboarding+dog&start-inde"
      + "x=21&max-results=10&v=2");

  private final String name;
  private final String serviceKey;
  private final String scopeUri;
  private final String feedUri;

  ServiceInfo(String name, String serviceKey, String scopeUri, String feedUri) {
    this.name = name;
    this.serviceKey = serviceKey;
    this.scopeUri = scopeUri;
    this.feedUri = feedUri;
  }

  public String getName() {
    return name;
  }

  public String getServiceKey() {
    return serviceKey;
  }

  public String getScopeUri() {
    return scopeUri;
  }

  public String getFeedUri() {
    return feedUri;
  }

  @Override
  public String toString() {
    return scopeUri + " (" + name + ")";
  }
}
