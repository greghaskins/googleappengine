<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.users.User" %>
<%@ page import="com.google.appengine.api.users.UserService" %>
<%@ page import="com.google.appengine.api.users.UserServiceFactory" %>
<%@ page import="com.google.appengine.api.datastore.*" %>

<html>
  <head>
  <title>Some Familiar Haunts</title>
  <link href="/static/css/myhangouts.css" rel="stylesheet" type="text/css"/>
  </head>
  <body onload="initMap()" onunload="GUnload()">
  
  <div class="wrapper" id="wrapper">
  <div class="header" class="header"><h1>Some Familiar Haunts</h1>
    <div class="login">
       <% 
         UserService userService = UserServiceFactory.getUserService();
         User user = userService.getCurrentUser();
         DatastoreService datastoreService = 
             DatastoreServiceFactory.getDatastoreService();

         if (user != null) {
       %>
           <b><% user.getNickname(); %></b> | 
           <a href="<%= 
                   userService.createLogoutURL(request.getRequestURI())
               %>">Sign Out</a>
       <%
         } else {
       %>
          <a href="<%= 
                  userService.createLoginURL(request.getRequestURI())
              %>">Sign In</a>
       <%
         }

         String displayUserEmail = request.getParameter("user");
         if (displayUserEmail == null && user != null) {
           displayUserEmail = user.getEmail();
         }
       %>
    </div>

  <div>  <form name="user" class="right">
    Select a user:
    <select onChange="server.GetLocations(this.value, DisplayLocations);
                      server.display_user = this.value;"
            name="user"/>
    <%
      if (displayUserEmail != null) {
    %>
        <option value="<%= displayUserEmail %>"><%= displayUserEmail %></option>
    <%
      }
      Query userQuery = new Query("User");
      // TODO: limit to 100 users.
      for (Entity currentUser : datastoreService.prepare(userQuery)
          .asIterable()) {
        // provide a drop down menu option containing the user's email address
        String currentEmail = ((User)currentUser.getProperty("user"))
            .getEmail();
        if (displayUserEmail != null 
            && !currentEmail.equals(displayUserEmail)) {
    %>
          <option value="<%= currentEmail %>"><%= currentEmail %></option>
    <%
        }
      }
    %>
    </select>
  </form>
 
  </div>
  <div class="map" id="map"> </div>
  <div class="list" id="list"> </div>
  <div class="top">
  <form class="left" onSubmit="return false;">
    <input type=submit value="Update Display" 
           onclick="server.GetLocations(server.display_user,
                                        DisplayLocations);"/>
  </form>
  </div>
  <div class="body" id="body">
  <%
    if (user != null) {
  %>
<form name=addr 
      onSubmit="GeocodeAddLocation(this.address.value, this.name.value);
                this.reset();
                return false;">
  <table>
    <tr>
      <td align=right nowrap>Location Name: </td>
      <td colspan=3><input type=text name=name size=40/></td>
    </tr>
    <tr>
      <td align=right nowrap>Address: </td>
      <td colspan=2><input type=text name=address size=40/></td>
      <td><input type=submit value="Add" /></td>
    </tr>
  </table>
</form>
  <div id="error" style="color: red;"></div>
  <%
    } else {
  %>
    Please <a href="<%= 
            userService.createLoginURL(request.getRequestURI())
        %>">sign in</a> to add a new location.
  <%
    } // LEFT OFF HERE
  %>
<div> <!-- End Wrapper -->
</script>
<script language="javascript" src="/static/json.js"> </script>
<script language="javascript" src="/static/client.js"> </script>

<!-- The key for the Google Maps API will work only for http://localhost:8080. 
     To register a new key go to http://code.google.com/apis/maps/signup.html -->
<script language="javascript" src="http://maps.google.com/maps?file=js&v=2&amp;key=ABQIAAAALrh5mwxiKkNrHuqNwGGHJRTwM0brOpm-All5BF6PoaKBxRWWERQ6Ykd3tAT6M4xWsQW4l2QU6snihg"> </script>
<!-- The following key is registered for http://somefamiliarhaunts.appspot.com -->
<!--<script type="text/javascript" src="http://maps.google.com/maps?file=js&amp;v=2&amp;key=ABQIAAAAzj95UxFwpBmMNGv3g8X-5xSjF2zGFQdW5_dWfNMM24bcBoBvaxTxYiYMIfl5BcntMNrL3u8_ztRnoA&amp;sensor=false"> </script>-->
<script type="text/javascript" src="/static/MochiKit/MochiKit.js"> </script>

<script>
<%
  if (user != null) {
%>
server = { user: "<%= user.getNickname() %>",
<%
  } else {
%>
server = { user: "",
<%
  }
  if (displayUserEmail != null) {
%>
           display_user: "<%= displayUserEmail %>",
<%
  } else {
%>
           display_user: "",
<%
  }
%>
           login_url: "<%= 
                   userService.createLoginURL(request.getRequestURI())
               %>" };
function InstallFunction(name) {
  server[name] = function() { AE_CallRemote(name, arguments); }
}
InstallFunction("AddLocation");
InstallFunction("GetLocations");
InstallFunction("RemoveLocation");

</script>

<script language="javascript">
  //<![CDATA[

  // Set up the map
  var map = null;
  var geocoder = null;
  var clickMarker = null;
  var selectionMarker = null;
  var list = null;

  function initMap() {
    map = new GMap2(document.getElementById("map"));
    map.setCenter(new GLatLng(37.4419, -122.1419), 13);
    map.addControl(new GSmallMapControl());
    // Set up globals
    geocoder = new GClientGeocoder();
    clickMarker = null;
    selectionMarker = null;
    list = document.getElementById('list');
    list.selection = null;
    list.deselect = function() {
      if (this.selection)
        this.selection.elem.className = "item";
      this.selection = null;
    }
    // Reset the display
    initialized = false;
    server.GetLocations(server.display_user, DisplayLocations);
  }

  // Location class
  function Location(parent, map, obj) {
    this.longd = obj['longd'];
    this.latd = obj['latd'];
    this.name = obj['name'];
    this.key = obj['key'];
    this.map = map;
    this.point = new GLatLng(this.latd, this.longd);
    this.marker = new GMarker(this.point);
    this.marker.loc = this;
    this.parent = parent;
    
    this.elem = DIV({ 'class' : 'item',
                      'onclick' : "this.loc.click();"}, 
                      this.name);
    if (server.display_user == server.user)
      replaceChildNodes(this.elem,
                        DIV({ 'class' : 'remove',
                              'onclick' : "this.parentNode.loc.remove();"
                            },
                            'Delete'),
                            this.name);
    this.elem.loc = this;
  }

  Location.prototype.display = function() {
    this.map.setCenter(this.point);
    this.map.addOverlay(this.marker);
    appendChildNodes(this.parent, this.elem);
    if (!initialized) {
      this.map.setZoom(4);
      initialized = true;
    }
  };

  Location.prototype.remove = function() {
    var self = this;
    function removeFromList() {
      self.parent.removeChild(self.elem);
      if (self.parent.selection == self)
      self.parent.deselect();
      self.map.removeOverlay(self.marker);
    }
    server.RemoveLocation(
      this.key, 
      removeFromList);
  };

  Location.prototype.select = function() {
    this.parent.deselect();
    this.elem.className = "selection";
    this.parent.selection = this;
  };

  Location.prototype.click = function() {
    this.map.panTo(this.point);
    if (this.parent.selection == this)
      this.parent.deselect();
    else 
      this.select();
  };

  // Add a listner for the map to add new locations
  GEvent.addListener(map, 'click', function(overlay, point) {
    list.deselect();
    if (overlay && overlay.loc) {
      overlay.loc.select();
    }
    else if (point) {
      var form = document.forms['latlong'];
      form.longd.value = point.x;
      form.latd.value = point.y;
      if (clickMarker != null)
      map.removeOverlay(clickMarker);
      clickMarker = new GMarker(point);
      map.addOverlay(clickMarker);
    }
  });

  // Add a single location, and display it
  function AddDisplayLocation(latd, longd, name) {
    if (!server.user)
      document.location = server.login_url
      server.AddLocation(latd, longd, name, 
      function(obj) {
        if (server.display_user != server.user) {
           document.forms['user'].user.value = server.user;
           document.forms['user'].submit();
        } else {
          var loc = new Location(list, map, obj);
          loc.display(); 
          loc.select();
        }
    });
  }

  // Display all locations
  function DisplayLocations(locs) {
    map.clearOverlays();
    list.deselect();
    replaceChildNodes(list);

    for (var i = 0; i < locs.length; ++i) {
      var loc = new Location(list, map, locs[i]);
      loc.display();
    }

    // If no locations, display default location
    if (locs.length == 0) {
      map.setCenter(new GLatLng(37.4419, -122.1419), 13);
      return;
    }
  };

  // Call Geocode RPC, and display resulting location
  function GeocodeAddLocation(address, name) {
    geocoder.getLatLng(address,
      function(point) {
        if (!point) {
          document.getElementById('error').innerHTML =  'Address not found';
        } else {
          document.getElementById('error').innerHTML =  '';
          AddDisplayLocation(point.lat(), point.lng(), name);
        }
      }
    );
  }

</script>

</body>
</html>
