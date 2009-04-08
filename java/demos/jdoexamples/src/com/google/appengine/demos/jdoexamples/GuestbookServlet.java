package com.google.appengine.demos.jdoexamples;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GuestbookServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    GuestbookUtils.insert(req.getParameter("who"), req.getParameter("message"));
    resp.sendRedirect("/guestbook.jsp");
  }
}
