<%@ page import="com.google.appengine.demos.oauth.ServiceInfo" %>
<%@ page import="com.google.appengine.demos.oauth.ServiceInfoContainer" %>
<%@ page import="com.google.appengine.demos.oauth.SignatureMethod" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="com.google.appengine.demos.oauth.WizardStep" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
if (userInfo.getCurrentStep() != WizardStep.WELCOME && userInfo.getCurrentStep() != WizardStep.SELECT_SIGNATURE_INFO) { %>
<div class="sidebarbox" id="oauthinfobox">
  <div class="sidebarboxtitle">OAuth Info</div>
  <div class="sidebarboxcontent">
<%
    SignatureMethod signatureMethod = userInfo.getSignatureMethod();
    if (signatureMethod != null) {
      out.println("Signature Method: " +  StringEscapeUtils.escapeHtml(signatureMethod.toString()) + "<br />");
    }

    String consumerKey = userInfo.getConsumerKey();
    if (consumerKey != null && consumerKey != "") {
      out.println("Consumer Key: " +  StringEscapeUtils.escapeHtml(consumerKey) + "<br />");
    }

    ServiceInfoContainer serviceInfoContainer = userInfo.getServiceInfoContainer();
    if (serviceInfoContainer != null) {
      out.println("Scope(s):<br />");
      for (ServiceInfo serviceInfo : serviceInfoContainer.get()) {
        out.println("&nbsp;&nbsp;&nbsp;" + StringEscapeUtils.escapeHtml(serviceInfo.getScopeUri()) + "<br />");
      }
      out.println("<br />");
    }
    %>
  </div>
</div><br />
<% } %>