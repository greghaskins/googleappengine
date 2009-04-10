<%@ page import="com.google.appengine.demos.oauth.ControllerServlet" %>
<%@ page import="com.google.appengine.demos.oauth.UserInfoContainer" %>
<%@ page import="com.google.appengine.demos.oauth.WizardStep" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%
UserInfoContainer userInfo = (UserInfoContainer) session.getAttribute("userinfocontainer");
%>
<html>
<head>
  <title>OAuth Example: <%= StringEscapeUtils.escapeHtml(userInfo.getCurrentStep().toString()) %></title>
  <style>
    body,
    html {
      margin:0;
      padding:0;
      color:#000;
    }
    body {
      min-width:750px;
    }
    #header {
      padding: 0px 10px 10px 10px;
    }
    #wrap {
      font-size:84%;font-family:Tahoma,Geneva,sans-serif;
      margin:0 auto;
      width:750px;
    }
    #sidebar {
      float:left;
      width:220px;
      padding: 10px 10px 10px 10px;
    }
    #main {
      float:right;
      width:485px;
      padding: 0px 10px 10px 10px;
    }
    #footer {
      clear:both;
      padding: 10px 10px 10px 10px;
    }
    .sidebarbox {
      border: 1px solid #003399;
    }
    .sidebarboxtitle {
      background: #003399;
      color: #ffffff;
      padding: 3px 3px 3px 3px;
      font-weight: bold;
    }
    .sidebarboxcontent {
      padding: 3px 3px 3px 3px;
    }
    #footernavstartover {
      float:left;
    }
    #footernavnext {
      float:right;
    }
    .error {
      border: 2px solid #ff0000;
      background: #ff9999;
      padding: 3px 3px 3px 3px;
      margin-bottom: 5px;
    }
  </style>
<% if (userInfo.getCurrentStep() == WizardStep.LOAD_DATA) { %>
    <script src="static/codemirror/js/codemirror.js" type="text/javascript"></script>
    <link rel="stylesheet" type="text/css" href="static/codemirror/css/docs.css"/>  
<% } %>
</head>
<body>
<div id="wrap">
  <div id="header"><h1>OAuth Example</div>
  <div id="sidebar">
    <jsp:include page="navigationbox.jsp" /><br /><br />
    <jsp:include page="infobox.jsp" /><br /><br />
  </div>
  <div id="main">
    <h2><%= StringEscapeUtils.escapeHtml(userInfo.getCurrentStep().toString()) %></h2>
  <%
  if (request.getAttribute(ControllerServlet.ERROR_KEY) != null) { %>
  <div class="error"><%= request.getAttribute(ControllerServlet.ERROR_KEY).toString() %></div>
  <%
  } %>
  <form method="POST">
