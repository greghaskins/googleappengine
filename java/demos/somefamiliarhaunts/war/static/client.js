// Utility function
function AE_CreateXMLHttpRequest() {
    var req = null;
    try {
        req = new ActiveXObject("Msxml2.XMLHTTP");
    } catch (e) {
        try {
	    req = new ActiveXObject("Microsoft.XMLHTTP");
        } catch (E) {
	    req = null;
        }
    }
    if (!req && typeof XMLHttpRequest!='undefined') {
        req = new XMLHttpRequest();
    }
    return req;
}

function AE_CallRemote(functionName, argv) {
  if (functionName.length < 1)
    throw new Exception("Incorrect arguments to CallRemote()");
  if (!argv)
    argv = new Array();
  var req = AE_CreateXMLHttpRequest();

  var query = "";
  var callback = null;
  var lastArg = argv.length;
  if (argv.length > 0 && 
      typeof argv[lastArg-1] == 'function') {
    callback = argv[lastArg-1];
    lastArg--;
  }
  query += "&action=" + encodeURIComponent(functionName);
  for (var i = 0; i < lastArg; ++i) {
    var name = "arg" + i;
    var arg = argv[i];
    var argStr = JSON.stringify(arg)
    query += "&" + name + "=" + encodeURIComponent(argStr);
  }
  query += "&time=" + new Date().getTime();
  var path = "/rpc/?" + query;
  req.open("GET", path, true);
  req.onreadystatechange = function() {
    if (req.readyState == 4 && req.status == 200) {
      if (callback) {
	var arg = null;
	try {
	  arg = JSON.parse(req.responseText);
	} catch (e) {}
	if (arg == null)
          arg = req.responseText;
        callback(arg);
      }
    }
  };
  req.send(null);
}


