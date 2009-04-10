<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>

<%@ page import="com.google.appengine.demos.memoreez.Cell" %>
<%@ page import="com.google.appengine.demos.memoreez.PMF" %>

<%@ page import="java.util.List" %>

<%@ page import="javax.jdo.JDOHelper" %>
<%@ page import="javax.jdo.PersistenceManager" %>
<%@ page import="javax.jdo.PersistenceManagerFactory" %>

<%@ page contentType="text/html; charset=UTF-8" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
  <head>
    <title>memoreez: admin</title>
  </head>
  <body>

<%
  UserService userService = UserServiceFactory.getUserService();
  String logoutUrl = userService.createLogoutURL(request.getRequestURI());
  String loginUrl = userService.createLoginURL(request.getRequestURI());

  if (!userService.isUserLoggedIn()) {
    response.sendRedirect(loginUrl);
  } else if (!userService.isUserAdmin()) {
%>

    <h2>Oops!</h2>
    <p>
      This page is for administrators only&mdash;try signing in again
      <a href="<%= loginUrl %>">here</a>.
    </p>

<%    
  } else {
%>

    <h2>Welcome to the administrator console for memoreez!</h2>
    <p>
      <a href="<%= logoutUrl %>">Sign out</a>
      <br/>
      <a href="index.jsp">Return to memoreez</a>
    </p>

    <h3>Datastore snapshot:</h3>
    <%
      PersistenceManager pm = PMF.get().getPersistenceManager();

      String query =
        "SELECT FROM " + Cell.class.getName() + " ORDER BY position ASC";
      List<Cell> cells = (List<Cell>) pm.newQuery(query).execute();

      for (Cell c : cells) {
        out.println("Cell " + c.getPosition() + ": " + c.getColor() + "<br/>");
      }
    %>

    <h3>Actions:</h3>
    <form action="admin">
      <table>
        <tr>
          <td>
            <input type="radio" name="op" value="repopulate" checked="checked"/>
          </td>
          <td>Populate (or re-populate) datastore</td>
        </tr>
      </table>
      <input type="submit" value="Execute"/>
    </form>

<%
  }
%>

  </body>
</html>
