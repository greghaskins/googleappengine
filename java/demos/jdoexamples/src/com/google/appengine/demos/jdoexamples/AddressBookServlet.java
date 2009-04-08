package com.google.appengine.demos.jdoexamples;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AddressBookServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    AddressBookUtils.insertNew(req.getParameter("firstName"),
        req.getParameter("lastName"), req.getParameter("city"),
        req.getParameter("state"), req.getParameter("phoneNumber"));
    resp.sendRedirect("/addressbook.jsp");
  }

}
