package com.google.appengine.demos.jdoexamples;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class NamedCounterServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String action = req.getParameter("submit");
    if (action == null) {
      throw new ServletException("No action supplied!");
    }
    if (action.equals("Reset")) {
      NamedCounterUtils.reset(req.getParameter("name"));
    } else if (action.equals("Offset")) {
      NamedCounterUtils.addAndGet(req.getParameter("name"),
                                  Integer.parseInt(req.getParameter("delta")));
    } else if (action.equals("Get")) {
      // Do nothing. The redirect will handle this.
    }
    resp.sendRedirect("/namedcounter.jsp?name=" + req.getParameter("name"));
  }
}
