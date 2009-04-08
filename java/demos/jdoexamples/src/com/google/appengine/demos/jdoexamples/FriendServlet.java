package com.google.appengine.demos.jdoexamples;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FriendServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String lastName = req.getParameter("lastName");
    String firstName = req.getParameter("firstName");
    FriendUtils.addFriendTo(lastName, firstName,
                            req.getParameter("friendLastName"),
                            req.getParameter("friendFirstName"));
    resp.sendRedirect(
        "/friends.jsp?lastName=" + lastName + "&firstName=" + firstName);
  }
}
