<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
  <head>
    <title>memoreez</title>
    <style type="text/css">
      #content {
        width: 800px;
        margin-left: auto;
        margin-right: auto;
        text-align: center;
      }
      #cellPanel {
        width: 490px;
        margin-left: auto;
        margin-right: auto;
        margin-top: 25px;
      }
      .cell {
        background-color: silver;
        border: solid black 1px;
        float: left;
        height: 100px;
        width: 100px;
        margin-right: 20px;
        margin-bottom: 20px;
      }
    </style>
    <script type="text/javascript" src="static/memoreez.js"></script>
  </head>
  <body onload="memoreez.initialize()">
    <div id="content">
      <h1>Welcome to memoreez!</h1>
      <p>
        Head on over to the <a href="admin.jsp">administration page</a> to
        populate (or re-populate) the datastore</a>.
      </p>
      <div id="cellPanel"></div>
      <br clear="both"/>
    </div>
  </body>
</html>
