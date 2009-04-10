<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="com.google.appengine.demos.oauth.WizardStep" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
%>
<div class="sidebarbox" id="navigationbox">
  <div class="sidebarboxtitle">Navigation</div>
  <div class="sidebarboxcontent">
  <%
    WizardStep step = WizardStep.WELCOME;
    int i = 1;
    while (step != null) {
      if (step == userInfo.getCurrentStep()) {
        out.print("<b>");
      }
      out.println(i + ") " +  StringEscapeUtils.escapeHtml(step.toString()) + "<br />");
      if (step == userInfo.getCurrentStep()) {
        out.print("</b>");
      }
      step = step.getNextStep();
      i++;
    }
  %>
  </div>
</div>