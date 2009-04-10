<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
%>
<jsp:include page="header.jsp" />
<p>You have successfully authorized with Google.  Behind-the-scenes, a request
was made to Google in order to
<a href="http://code.google.com/apis/accounts/docs/OAuth.html#AccessToken" target="_blank">exchange
the authorized request token for an access token</a>:</p>

<blockquote>Access Token = <%= StringEscapeUtils.escapeHtml(userInfo.getAccessToken()) %></blockquote>

<p>This access token can be used to load data from Google.  Click "Next" to load a sample feed.</p>
<jsp:include page="footer.jsp" />
