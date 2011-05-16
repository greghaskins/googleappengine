// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppAdminFactory.ConnectOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Connection to the AppEngine hosting service, as set by {@link ConnectOptions}
 *
 */
public abstract class AbstractServerConnection implements ServerConnection {

  private static final int MAX_SEND_TRIES = 3;

  interface DataPoster {
    void post(OutputStream s) throws IOException;
  }

  private static class FilePoster implements DataPoster {
    private static final int BUFFER_SIZE = 4 * 1024;
    private File file;

    public FilePoster(File file) {
      assert (file != null && file.exists());
      this.file = file;
    }

    public void post(OutputStream out) throws IOException {
      InputStream in = new FileInputStream(file);
      try {
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) != -1) {
          out.write(buf, 0, len);
        }
      } finally {
        in.close();
      }
    }
  }

  static class StringPoster implements DataPoster {
    private String str;

    public StringPoster(String s) {
      str = s;
    }

    public void post(OutputStream s) throws IOException {
      s.write(str.getBytes("UTF-8"));
    }
  }

  protected static final String POST = "POST";

  protected static final String GET = "GET";

  protected ConnectOptions options;

  protected static Logger logger =
      Logger.getLogger(AbstractServerConnection.class.getCanonicalName());

  protected AbstractServerConnection() {
  }

  protected AbstractServerConnection(ConnectOptions options) {
    this.options = options;
    if (System.getProperty("http.proxyHost") != null) {
      logger.info("proxying HTTP through " + System.getProperty("http.proxyHost") + ":"
          + System.getProperty("http.proxyPort"));
    }
    if (System.getProperty("https.proxyHost") != null) {
      logger.info("proxying HTTPS through " + System.getProperty("https.proxyHost") + ":"
          + System.getProperty("https.proxyPort"));
    }
  }

  protected String buildQuery(Map<String, String> params) throws UnsupportedEncodingException {
    StringBuffer buf = new StringBuffer();
    for (String key : params.keySet()) {
      buf.append(URLEncoder.encode(key, "UTF-8"));
      buf.append('=');
      buf.append(URLEncoder.encode(params.get(key), "UTF-8"));
      buf.append('&');
    }
    return buf.toString();
  }

  protected URL buildURL(String path) throws MalformedURLException {
    String protocol = options.getSecure() ? "https" : "http";
    return new URL(protocol + "://" + options.getServer() + path);
  }

  protected IOException connect(String method, HttpURLConnection conn, DataPoster data)
      throws IOException {
    doPreConnect(method, conn, data);
    conn.setInstanceFollowRedirects(false);
    conn.setRequestMethod(method);

    if (POST.equals(method)) {
      conn.setDoOutput(true);
      OutputStream out = conn.getOutputStream();
      if (data != null) {
        data.post(out);
      }
      out.close();
    }

    try {
      conn.getInputStream();
    } catch (IOException ex) {
      return ex;
    } finally {
      doPostConnect(method, conn, data);
    }

    return null;
  }

  protected String constructHttpErrorMessage(HttpURLConnection conn, BufferedReader reader)
      throws IOException {
    StringBuilder sb = new StringBuilder("Error posting to URL: ");
    sb.append(conn.getURL());
    sb.append('\n');
    sb.append(conn.getResponseCode());
    sb.append(' ');
    sb.append(conn.getResponseMessage());
    sb.append('\n');
    if (reader != null) {
      for (String line; (line = reader.readLine()) != null;) {
        sb.append(line);
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  protected abstract void doHandleSendErrors(int status, URL url, HttpURLConnection conn,
      BufferedReader connReader) throws IOException;

  protected abstract void doPostConnect(String method, HttpURLConnection conn, DataPoster data)
      throws IOException;

  protected abstract void doPreConnect(String method, HttpURLConnection conn, DataPoster data)
      throws IOException;

  public String get(String url, Map<String, String> params) throws IOException {
    return send(GET, url, null, null, params);
  }

  protected BufferedReader getReader(HttpURLConnection conn) {
    InputStream is;
    try {
      is = conn.getInputStream();
    } catch (IOException ex) {
      is = conn.getErrorStream();
    }
    if (is == null) {
      return null;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    return reader;
  }

  public String post(String url, File payload, String contentType, Map<String, String> params)
      throws IOException {
    return send(POST, url, new FilePoster(payload), contentType, params);
  }

  public String post(String url, File payload, String contentType, String... params)
      throws IOException {
    Map<String, String> paramMap = new HashMap<String, String>();
    for (int i = 0; i < params.length; i += 2) {
      paramMap.put(params[i], params[i + 1]);
    }
    return send(POST, url, new FilePoster(payload), contentType, paramMap);
  }

  public String post(String url, String payload, Map<String, String> params) throws IOException {
    return send(POST, url, new StringPoster(payload), null, params);
  }

  public String post(String url, String payload, String... params) throws IOException {
    Map<String, String> paramMap = new HashMap<String, String>();
    for (int i = 0; i < params.length; i += 2) {
      paramMap.put(params[i], params[i + 1]);
    }
    return send(POST, url, new StringPoster(payload), null, paramMap);
  }

  protected String send(String method, String path, DataPoster payload, String content_type,
      Map<String, String> params) throws IOException {

    URL url = buildURL(path + '?' + buildQuery(params));

    if (content_type == null) {
      content_type = "application/octet-stream";
    }

    int tries = 0;
    while (true) {
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("Content-type", content_type);
      conn.setRequestProperty("X-appcfg-api-version", "1");

      if (options.getHost() != null) {
        conn.setRequestProperty("Host", options.getHost());
      }

      IOException ioe = connect(method, conn, payload);

      int status = conn.getResponseCode();
      BufferedReader reader = getReader(conn);

      if (status == HttpURLConnection.HTTP_OK) {
        StringBuffer response = new StringBuffer();
        String line = null;
        while ((line = reader.readLine()) != null) {
          response.append(line);
          response.append("\n");
        }
        return response.toString();
      } else {
        logger.finer("Got http error " + status + ". this is try #" + tries);
        if (++tries > MAX_SEND_TRIES) {
          throw new IOException(constructHttpErrorMessage(conn, reader));
        }
        doHandleSendErrors(status, url, conn, reader);
      }
    }
  }
}
