// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.tools.development;

import org.mortbay.io.WriterOutputStream;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.HttpMethods;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.resource.Resource;
import org.mortbay.util.URIUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code StaticFileUtils} is a collection of utilities shared by
 * {@link LocalResourceFileServlet} and {@link StaticFileFilter}.
 *
 */
public class StaticFileUtils {
  private static final Logger logger = Logger.getLogger(StaticFileUtils.class.getName());

  private static final String CACHE_CONTROL_VALUE = "private";

  private final ContextHandler.SContext servletContext;

  public StaticFileUtils(ContextHandler.SContext servletContext) {
    this.servletContext = servletContext;
  }

  public boolean serveWelcomeFileAsRedirect(String path,
                                            boolean included,
                                            HttpServletRequest request,
                                            HttpServletResponse response)
      throws IOException {
    if (included) {
      return false;
    }

    response.setContentLength(0);
    String q = request.getQueryString();
    if (q != null && q.length() != 0) {
      response.sendRedirect(path + "?" + q);
    } else {
      response.sendRedirect(path);
    }
    return true;
  }

  public boolean serveWelcomeFileAsForward(RequestDispatcher dispatcher,
                                           boolean included,
                                           HttpServletRequest request,
                                           HttpServletResponse response)
      throws IOException, ServletException {
    if (!included && !request.getRequestURI().endsWith(URIUtil.SLASH)) {
      redirectToAddSlash(request, response);
      return true;
    }

    request.setAttribute("com.google.appengine.tools.development.isWelcomeFile", true);
    if (dispatcher != null) {
      if (included) {
        dispatcher.include(request, response);
      } else {
        dispatcher.forward(request, response);
      }
      return true;
    }
    return false;
  }

  public void redirectToAddSlash(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    StringBuffer buf = request.getRequestURL();
    int param = buf.lastIndexOf(";");
    if (param < 0) {
      buf.append('/');
    } else {
      buf.insert(param, '/');
    }
    String q = request.getQueryString();
    if (q != null && q.length() != 0) {
      buf.append('?');
      buf.append(q);
    }
    response.setContentLength(0);
    response.sendRedirect(response.encodeRedirectURL(buf.toString()));
  }

  /**
   * Check the headers to see if content needs to be sent.
   * @return true if the content should be sent, false otherwise.
   */
  public boolean passConditionalHeaders(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Resource resource) throws IOException {
    if (!request.getMethod().equals(HttpMethods.HEAD)) {
      String ifms = request.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
      if (ifms != null) {
        long ifmsl = -1;
        try {
          ifmsl = request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
        } catch (IllegalArgumentException e) {
        }
        if (ifmsl != -1) {
          if (resource.lastModified() <= ifmsl) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.flushBuffer();
            return false;
          }
        }
      }

      long date = -1;
      try {
        date = request.getDateHeader(HttpHeaders.IF_UNMODIFIED_SINCE);
      } catch (IllegalArgumentException e) {
      }
      if (date != -1) {
        if (resource.lastModified() > date) {
          response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Write or include the specified resource.
   */
  public void sendData(HttpServletRequest request,
                        HttpServletResponse response,
                        boolean include,
                        Resource resource) throws IOException {
    long contentLength = resource.length();
    if (!include) {
      writeHeaders(response, resource, contentLength);
    }

    OutputStream out = null;
    try {
      out = response.getOutputStream();}
    catch (IllegalStateException e) {
      out = new WriterOutputStream(response.getWriter());
    }
    resource.writeTo(out, 0, contentLength);
  }

  /**
   * Write the headers that should accompany the specified resource.
   */
  public void writeHeaders(HttpServletResponse response, Resource resource, long count)
      throws IOException {
    String contentType = servletContext.getMimeType(resource.getName());
    if (contentType != null) {
      response.setContentType(contentType);
    }

    if (count != -1) {
      if (count < Integer.MAX_VALUE) {
        response.setContentLength((int) count);
      } else {
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(count));
      }
    }

    response.setDateHeader(HttpHeaders.LAST_MODIFIED, resource.lastModified());
    response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
  }
}
