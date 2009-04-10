<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.demos.oauth.RedirectToGoogleControllerServlet" %>
<%@ page import="com.google.appengine.demos.oauth.SignatureMethod" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
%>
<jsp:include page="header.jsp" />
<script type="text/javascript">
function submitForm() {
  document.forms[0].submit();
  return false;
}
</script>
<% if (request.getAttribute(RedirectToGoogleControllerServlet.USER_AUTHORIZATION_URL_KEY) != null) { %>
<p>Based on the information you provided, a behind-the-scenes request was made
to Google in order to obtain a
<a href="http://code.google.com/apis/accounts/docs/OAuth.html#RequestToken" target="_blank">request token</a>:

<blockquote>Request Token = <%= userInfo.getRequestToken() %></blockquote>

<% if (userInfo.getSignatureMethod() == SignatureMethod.HMAC) { %>
<p>Since you are using HMAC-SHA1, you also received a token secret:</p>
<blockquote>Token Secret = <%= userInfo.getTokenSecret() %></blockquote>
<p>The token secret is used along with the consumer secret when signing the
request (See <a href="http://oauth.net/core/1.0/#anchor16" target="_blank">Section 9.2 of the
OAuth spec</a>).</p>
<% } %>

<p>The next step is to
<a href="http://code.google.com/apis/accounts/docs/OAuth.html#GetAuth" target="_blank">authorize
the request token</a> at Google.  The url
below will redirect you to Google, where you can sign in (if you haven't already)
and then authorize this sample.  Once you have successfully authorized, you will
be redirected back to this sample where you will continue the OAuth process.
The authorization url is:</p>
<p><a onclick="return submitForm();" href="<%=  StringEscapeUtils.escapeHtml(request.getAttribute(RedirectToGoogleControllerServlet.USER_AUTHORIZATION_URL_KEY).toString()) %>"><%=  StringEscapeUtils.escapeHtml(request.getAttribute(RedirectToGoogleControllerServlet.USER_AUTHORIZATION_URL_KEY).toString()) %></a></p>
<input type="hidden" name="<%= RedirectToGoogleControllerServlet.USER_AUTHORIZATION_URL_KEY %>" value="<%=  StringEscapeUtils.escapeHtml(request.getAttribute(RedirectToGoogleControllerServlet.USER_AUTHORIZATION_URL_KEY).toString()) %>" />
<% } %>
<jsp:include page="footer.jsp" />
