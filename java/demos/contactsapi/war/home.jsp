<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="com.google.appengine.demos.contactsapi.AuthSubManager" %>
<%@ page import="com.google.gdata.data.contacts.ContactFeed" %>
<%@ page import="com.google.gdata.data.contacts.ContactEntry" %>
<%@ page import="com.google.gdata.client.contacts.ContactsService" %>
<%@ page import="java.net.URL" %>
<html>
<head>
  <title>Google App Engine - Using the Google Contacts API</title>
  <style>
    #photos {
      float: left;
      width: 530px;
      margin: 0px 0px 0px 96px;
    }

    #content {
      float: left;
      width: 500px;
      margin: 0px 0px 0px 10px;
    }

    #signin {
      width: 500px;
      margin: 0px auto 0px auto;
    }

    .thumb {
      padding: 3px 3px 3px 3px;
    }
  </style>
</head>
<body>
<%
   AuthSubManager authSubManager = new AuthSubManager(request, response);
   if (authSubManager.isSignedIn(true) == false) {
%>
<div id="signin">

<%-- If the user is signed out, show them the link to signin. --%>
<p><a href="<%= StringEscapeUtils.escapeHtml(authSubManager.getSigninUrl()) %>">signin</a></p>

<p>This example demonstrates how to use the
<a href="http://code.google.com/apis/gdata/client-java.html">Google Data API's
Java Client</a> on Google's AppEngine.  The example focuses on three areas:</p>

<ul>
<li>Authenticating using
<a href="http://code.google.com/apis/accounts/docs/AuthSub.html">AuthSub</a> -
See AuthSubManager.java for the related code.</li>
<li>Loading contacts from the Contacts API - See home.jsp for the related
code.</li>
<li>Loading binary data from the <a href="http://code.google.com/apis/contacts/">Google
Contacts API</a> - See ShowImageServlet.java for
the related code</li>
</ul>

<p>This example should give you a sense of how to use the Google Data APIs
from end-to-end.  To get started with this example, click the "signin" link
above to signin using AuthSub.  This will redirect you to Google where you can
authenticate this example.  After that, you will be redirected to the
HandleToken servlet, which continues the signin process.</p>

</div>
<%
   } else {
%>
<div id="photos">
<%
  // Create the ContactsService object.
  ContactsService myService = new ContactsService(this.getServletContext().getInitParameter("application_name"));
  // Set the authentication token.
  myService.setAuthSubToken(authSubManager.getToken(), null);
  // Create the url of the feed to load.  This feed loads all of a user's
  // contacts, capped at 100 results max.
  URL feedUrl = new URL("http://www.google.com/m8/feeds/contacts/default/full?max-results=100");
  // Request the feed from the server.
  ContactFeed resultFeed = myService.getFeed(feedUrl, ContactFeed.class);
  // Loop through each feed, graph the photo link, and add it to the html.
  for (int i = 0; i < resultFeed.getEntries().size(); i++) {
    ContactEntry entry = resultFeed.getEntries().get(i);
    String name = entry.getTitle().getPlainText();
    String photoLink = entry.getContactPhotoLink().getHref();
    out.println("<img src=\"/ShowImage?link=" +
        StringEscapeUtils.escapeHtml(photoLink) + "\" alt=\"" +
        StringEscapeUtils.escapeHtml(name) + "\" height=\"96\" width=\"96\" " +
        "class=\"thumb\"> ");
  }
%>
</div>
<div id="content">
<a href="/Signout">signout</a>

<%-- Display the feed title on the page --%>
<h2><%
  out.println(StringEscapeUtils.escapeHtml(resultFeed.getTitle().getPlainText()));
%></h2>

<p>You have successfully signed in to the example!  You should see a bunch of
images on the left.  These are the profile images for each of your contacts; in
the case where the contact doesn't have an image, a default image is shown.</p>

<p>Loading the contact image is done in two parts:</p>

<ol>
<li>Load the user's contacts - See home.jsp for the related code.</li>
<li>Loop through the contacts, loading the image for each contact - Since
a user's image is protected data, we can't just embed the image link in an
"img src" tag.  Instead we must request the image with the appropriate
authentication header, and then stream the binary response to the user.  See
ShowImageServlet.java for the related code.</li>
</ol>

<p>Click the "signout" button above to signout of this example (the related code
for signing out can be found in SignoutServlet.java).  Hopefully this example 
gave you a sense of how to use the Google Data APIs from end-to-end.</p>
</div>
<% } %>
</body>
</html>
