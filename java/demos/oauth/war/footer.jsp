<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="com.google.appengine.demos.oauth.WizardStep" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
%>
    <br /><br />
    <div id="footernav">
    <div id="footernavstartover">
    <input type="button" name="btnStartOver" value="Start Over" tabindex="99" onclick="javascript:window.location.href='/StartOver'" />
    </div>
    <% if (userInfo.getCurrentStep() != WizardStep.LOAD_DATA) { %>
    <div id="footernavnext">
    <input type="submit" name="btnNext" value="Next &gt;&gt;" tabindex="98" />
    </div>
    <% } %>
    </div>
  </form>
  </div>
  <div id="footer"></div>
</div>
</body>
</html>
