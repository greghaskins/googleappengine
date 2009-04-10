<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<jsp:include page="header.jsp" />
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
if (request.getAttribute("feed_raw_xml") != null) { %>
<p>Using the OAuth access token, the following feed was retrieved from the
<%= StringEscapeUtils.escapeHtml(userInfo.getServiceInfoContainer().get().get(0).getName()) %>:</p>

<blockquote><%= StringEscapeUtils.escapeHtml(userInfo.getServiceInfoContainer().get().get(0).getFeedUri()) %></blockquote>

<p>Here is the response from the feed in XML:</p>

<div style="border: 1px solid black; padding: 3px;">
<textarea id="code" cols="120" rows="30">
<%= StringEscapeUtils.escapeHtml(request.getAttribute("feed_raw_xml").toString()) %>
</textarea>
</div>

<script type="text/javascript">
  var editor = CodeMirror.fromTextArea('code', {
    height: "350px",
    parserfile: "parsexml.js",
    stylesheet: "static/codemirror/css/xmlcolors.css",
    path: "static/codemirror/js/",
    continuousScanning: 500
  });
</script>
<% } %>
<jsp:include page="footer.jsp" />
