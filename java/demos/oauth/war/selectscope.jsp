<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.demos.oauth.ServiceInfo" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<jsp:include page="header.jsp" />
<p>Each Google service defines a scope value which determines a token's access
to a specific service (or services).  This is not a part of the OAuth spec, but
it is required to use OAuth with Google services.  The scope is a url; the
service associated with each scope is in parenthesis below.  Select one or more
scopes for the services you wish to authenticate against.</p>
<%
int tabindex = 0;
for (ServiceInfo serviceInfo : ServiceInfo.values()) {
  out.println("<input type=\"checkbox\" name=\"scope\" value=\"" +  StringEscapeUtils.escapeHtml(serviceInfo.getScopeUri()) + "\" tabindex=\"" + (++tabindex) + "\" /> " +  StringEscapeUtils.escapeHtml(serviceInfo.toString()) + "<br />");
}
%>
<jsp:include page="footer.jsp" />
