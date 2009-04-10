/* Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


var AutoShoppe = {};
AutoShoppe.httpRequest = null;
AutoShoppe.business = {};
AutoShoppe.business.page = 0;
AutoShoppe.business.heading = new Array();
AutoShoppe.business.heading[0] = 'Buy';
AutoShoppe.business.heading[1] = 'Type';
AutoShoppe.business.heading[2] = 'Car';
AutoShoppe.business.heading[3] = 'Year';
AutoShoppe.business.heading[4] = 'Mileage';
AutoShoppe.business.heading[5] = 'Price';
AutoShoppe.business.paginatingCallBack = AutoShoppe.business.getAllCars;

/**
 * Sets up XHR.
 * @return {XMLHttpRequest} An instance of XHR for the client.
 */
AutoShoppe.isHttpRequestSupported = function() {
  var httpRequestSupported = true;
  if (AutoShoppe.httpRequest == null) {
    try {
      if (window.XMLHttpRequest) {
        // code for all new browsers
        AutoShoppe.httpRequest = new XMLHttpRequest();
      } else if (window.ActiveXObject) {
        AutoShoppe.httpRequest = new ActiveXObject('Microsoft.XMLHTTP');
        if (AutoShoppe.httpRequest == null) {
          AutoShoppe.httpRequest = new ActiveXObject('Msxml2.XMLHTTP');
        }
      }
    } catch (e) {
      try {
        AutoShoppe.httpRequest = new ActiveXObject('Msxml2.XMLHTTP');
      } catch (e) {
        httpRequestSupported = false;
        document.write();
        var text = document.createTextNode('Please use ' +
                                           'Firefox/IE/Safari/Chrome/Opera ' +
                                           'for this Sample');
        document.getElementById("menuFirst").appendChild(text);
      }
    }
  }
  return AutoShoppe.httpRequest;
};

/**
 * Issues an AJAX request for the sent parameters and URL.
 *
 * @param url The server URL to fetch data asynchronously
 * @param callback The callback for receiving data and updating DOM
 * @param method The HTTP method to use
 * @param length The length of the content for POST
 * @param data The POST data to be sent
 */
AutoShoppe.makeHttpRequest = function(url, callback, method, length, data) {
  // check if supported
  AutoShoppe.httpRequest = AutoShoppe.isHttpRequestSupported();
  if (!AutoShoppe.httpRequest) {
    return false;
  }

  try {
    AutoShoppe.httpRequest.open(method, url, true);
    if (method == 'POST') {
      AutoShoppe.httpRequest.setRequestHeader('Content-type',
          'application/x-www-form-urlencoded');
    }
    AutoShoppe.httpRequest.setRequestHeader('Content-length', length);
    AutoShoppe.httpRequest.send(data);
  } catch (e) {
    var text = document.createTextNode('Sorry! An unknown error occurred');
    document.getElementById("menuFirst").appendChild(text);
  }
  // Map the response to the callback function
  AutoShoppe.httpRequest.onreadystatechange = function() {
    if (AutoShoppe.httpRequest.readyState == 4) {
      //populate the list at table model association
      callback(AutoShoppe.httpRequest.responseText);
    }
  };
}; // end function


/**
 * Client call to fetch all models asynchronously.
 */
AutoShoppe.business.getAllCars = function() {
  var callback = AutoShoppe.business.populateModels;
  AutoShoppe.makeHttpRequest(
      '/business/getAllVehicles.do?page=' + AutoShoppe.business.page,
      callback, 'GET', 0, null);
};

/**
 * Paints the results on the screen on a Table. Callback for getAllCars().
 * @param {JSON} responseJson The data received from the server by getAllCars().
 */
AutoShoppe.business.populateModels = function(responseJson) {
  var divCarList = document.createElement('div');
  var values = JSON.parse(responseJson);
  if (values.length == 0 && AutoShoppe.business.page > 1) {
    AutoShoppe.business.page = 0;
    AutoShoppe.business.getNextPage();
    return;
  }
  var html = [];
  var oTable = document.createElement('TABLE');
  var oTHead = document.createElement('THEAD');
  var oTBody0 = document.createElement('TBODY');
  var oTBody1 = document.createElement('TBODY');
  var oTFoot = document.createElement('TFOOT');
  var oCaption = document.createElement('CAPTION');
  var oRow, oCell;
  var i;
  oTable.id = 'restable';
  // Insert the created elements into oTable.
  oTable.appendChild(oTHead);
  oTable.appendChild(oTBody0);
  oTable.appendChild(oTBody1);
  oTable.appendChild(oTFoot);
  oTable.appendChild(oCaption);

  // Set the table's border width and colors.
  oTable.bgColor = 'white';
  oTable.style.width = '100%';

  oRow = document.createElement('TR');
  oTHead.appendChild(oRow);
  oTHead.setAttribute('bgColor', 'lightskyblue');

  // Create and insert cells into the header row.
  for (i = 0; i < AutoShoppe.business.heading.length; i++) {
    oCell = document.createElement('TH');
    oCell.innerHTML = AutoShoppe.business.heading[i];
    oRow.appendChild(oCell);
  }

  // Insert rows and cells into bodies.
  for (i = 0; i < values.length; i++) {
    var oBody = oTBody1;
    oRow = document.createElement('TR');
    oBody.appendChild(oRow);
    oCell = document.createElement('TD');
    oCell.style.width = '14%';
    oCell.innerHTML = '<button onclick="AutoShoppe.business.blockCar(\'' +
                      values[i]['id'] +
                      '\',' + i + ');"> Express Interest</button>';
    oRow.appendChild(oCell);
    oCell = document.createElement('TD');
    oCell.innerHTML = values[i]['type'];
    oRow.appendChild(oCell);
    oCell = document.createElement('TD');
    oCell.innerHTML = values[i]['make'] + ' ' + values[i]['model'] +
                      '<input type=hidden id=hidden' + i + ' value=' +
                      values[i]['id'] + '/>';
    oRow.appendChild(oCell);
    oCell = document.createElement('TD');
    oCell.innerHTML = values[i]['year'];
    oRow.appendChild(oCell);
    oCell = document.createElement('TD');
    oCell.innerHTML = values[i]['mileage'];
    oCell.style.width = '10%';
    oRow.appendChild(oCell);
    oCell = document.createElement('TD');
    oCell.innerHTML = values[i]['price'];

    if (i % 2 == 0) {
      oRow.setAttribute('bgColor', '#BBBBBB');
    } else {
      oRow.setAttribute('bgColor', '#FFFFFF');
    }
    oRow.appendChild(oCell);
  }

  oTBody1.setAttribute('bgColor', 'goldenrod');

  // Create and insert rows and cells into the footer row. Set the innerHTML
  // of the caption and position it at the bottom of the
  // table.
  oCaption.innerHTML = '<button onclick=\'' +
                       'AutoShoppe.business.getPrevPage();\'>' +
                       '&lt;&lt;</button> Page ' + AutoShoppe.business.page +
                       ' <button ' +
                       'onclick=\'AutoShoppe.business.getNextPage();\'>' +
                       '&gt;&gt;</button>';
  oCaption.style.fontSize = '10px';
  oCaption.align = 'bottom';

  // Insert the table into the document tree.
  divCarList.appendChild(oTable);
  var resultDiv = document.getElementById('result');
  resultDiv.innerHTML = '';
  resultDiv.appendChild(divCarList);
};

/**
 * Get next page of results.
 */
AutoShoppe.business.getNextPage = function() {
  AutoShoppe.business.page++;
  var resultDiv = document.getElementById('result');
  resultDiv.innerHTML = '<br/><br/><img src=\'static/images/loading_bar.' +
                        'gif\' style=\'padding-left:350px;\' width=\'100\' ' +
                        'height=\'20\' align=\'center\' alt=\'Loading..' +
                        '\'/><br/> <p align=center> Loading</p>';

  AutoShoppe.business.paginatingCallBack();
}

/**
 * Get previous page of results.
 */
AutoShoppe.business.getPrevPage = function() {
  if (AutoShoppe.business.page == 1) {
    return;
  }
  var resultDiv = document.getElementById('result');
  resultDiv.innerHTML = '<br/><br/><img src=\'static/images/loading_bar.' +
                        'gif\' style=\'padding-left:350px;\' width=\'100\' ' +
                        'height=\'20\' align=\'center\' alt=\'Loading..' +
                        '\'/><br/> <p align=center> Loading</p>';

  AutoShoppe.business.page--;
  AutoShoppe.business.paginatingCallBack();
}

/**
 * A paginated search based on custom filters.
 */
AutoShoppe.business.paginateCustomSearch = function() {
  AutoShoppe.business.page = 0;
  ;
  AutoShoppe.business.paginatingCallBack = AutoShoppe.business.customSearch;
  AutoShoppe.business.getNextPage();
}

/**
 * A paginated search on all cars.
 */
AutoShoppe.business.paginateAllCars = function() {
  AutoShoppe.business.page = 0;
  AutoShoppe.business.paginatingCallBack = AutoShoppe.business.getAllCars;
  AutoShoppe.business.getNextPage();
}

/**
 * Adds a new vehicle by invoking Service asynchronously.
 */
AutoShoppe.business.addVehicle = function() {
  var callback = AutoShoppe.business.vehicleAddedCallback;
  var year = document.getElementById('year').value;
  var model = document.getElementById('model').value;
  var make = document.getElementById('make').value;
  var mileage = document.getElementById('mileage').value;
  var type = document.getElementById('type').value;
  var price = document.getElementById('price').value;
  var addResult = document.getElementById('addResult');
  if (!AutoShoppe.isValidNumber(price) || !AutoShoppe.isValidNumber(mileage)) {
    addResult.innerHTML = '<br/><font color=\'red\'>' +
                          'Enter a valid numeric value for Price & Mileage' +
                          '</font>';
    return;
  }

  if (model.length == 0 || make.length == 0 || price.length == 0 ||
      mileage.length == 0 || year.length == 0) {
    addResult.innerHTML = '<br/><font color=\'red\'>Enter all values</font>';
    return;
  }
  var queryUrl = 'year=' + year;
  queryUrl += '&model=' + encodeURIComponent(model);
  queryUrl += '&make=' + encodeURIComponent(make);
  queryUrl += '&mileage=' + mileage;
  queryUrl += '&type=' + type;
  queryUrl += '&price=' + price;
  AutoShoppe.makeHttpRequest('/business/addVehicle.do', callback, 'POST',
      queryUrl.length, queryUrl);
  addResult.innerHTML = '';
};

/**
 * Marks a vehicle as being sold.
 * @param {String} key The unique id of the vehicle.
 * @param {HTMLElement} cell A table element corresponding the display cell.
 */
AutoShoppe.business.blockCar = function(key, cell) {
  var callback = AutoShoppe.business.markSoldEvent;
  var table = document.getElementById('restable');
  var td = table.rows[cell + 1].firstChild;
  td.firstChild.disabled = true;
  td.removeChild(td.firstChild);
  var load = new Image();
  load.src = 'static/images/loading_bar.gif';
  var img = document.createElement('IMG');
  img.src = 'static/images/loading_bar.gif';
  img.id = 'buyingImage';
  img.setAttribute('width', '106');
  td.appendChild(img);

  var data = 'key=' + key;
  AutoShoppe.makeHttpRequest('/business/markSold.do', callback,
      'POST', data.length, data);
};

/**
 * Callback for vehicle add operation. Displays the result.
 * @param {JSON} responseJson The data received from the server.
 */

AutoShoppe.business.vehicleAddedCallback = function(responseJson) {
  var addResult = document.getElementById('addResult');
  if (responseJson.search(/Car/) != -1) {
    addResult.innerHTML = '<br/><b>Your car is now listed in our database</b>';
    document.getElementById('model').value = '';
    document.getElementById('make').value = '';
    document.getElementById('mileage').value = '';
    document.getElementById('price').value = '';
  } else {
    addResult.innerHTML = 'An error occurred. Please try again';
  }

};

/**
 * Callback for vehicle sold event.
 * @param {JSON} responseJson Data received from the server.
 */
AutoShoppe.business.markSoldEvent = function(responseJson) {
  var image = document.getElementById('buyingImage');
  var td = image.parentNode;
  td.setAttribute('bgColor', '#000000');
  td.innerHTML = '<font color=\'white\'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' +
                 'Email Sent</font>';
  if (responseJson == 'true') {
    td.nextSibling.nextSibling.innerHTML = '<b>Congrats! Car blocked. ' +
                                           'Please check your Email</b>';
  } else {
    td.nextSibling.nextSibling.innerHTML = 'This vehicle is not available' +
                                           ' any longer. Please choose ' +
                                           'another one.';
  }
};

/**
 * Picks up random values for Vehicle add operation.
 */
AutoShoppe.business.randomAddValues = function() {
  var random = Math.random();
  document.getElementById('year').value = 1995 + Math.floor(random * 10);
  document.getElementById('model').value = 'Civic';
  document.getElementById('make').value = 'Honda';
  document.getElementById('mileage').value = Math.floor(10000 * random * 10);
  document.getElementById('type').value = 'Sedan';
  document.getElementById('price').value = Math.floor(5000 * random * 10);
};

/**
 * Control navigation by toggling pseudo-tabs.
 * @param {String} action The action corresponding to the tab to be activated.
 */
function toggleDiv(action) {

  var div = null;
  var loginDiv = document.getElementById('login');
  if (loginDiv.innerHTML.search('Hello') && action != 'login')
  {
    if (loginDiv.innerHTML.search('red') < 0) {
      loginDiv.innerHTML = '<font color=red>You must sign in first!</font> ' +
                           loginDiv.innerHTML;
    }
    return;
  }

  if (action == 'search') {
    document.getElementById('addCar').style.display = 'none';
    document.getElementById('search').style.display = '';
    AutoShoppe.business.page = 0;
  } else if (action == 'add') {
    document.getElementById('search').style.display = 'none';
    document.getElementById('addCar').style.display = 'block';
    var resultDiv = document.getElementById('result');
    resultDiv.innerHTML = '';
    var addResult = document.getElementById('addResult');
    addResult.innerHTML = '';
  } else {
    document.getElementById('search').style.display = 'none';
    document.getElementById('addCar').style.display = 'none';
  }
}
;

/**
 * Performs a login operation and retrieves the URLs.
 */
AutoShoppe.business.login = function() {
  toggleDiv('login');
  var callback = AutoShoppe.business.loggedInCallback;
  AutoShoppe.makeHttpRequest('/business/login.do', callback, 'GET', 0, null);

};

/**
 * Sets up the Sign in/off URLs. Callback for login().
 * @param {String} responseJson Data received from the Server.
 */
AutoShoppe.business.loggedInCallback = function(responseJson) {
  var results = JSON.parse(responseJson);
  var user = results['user'];
  var html = [];
  if (user.length > 1) {
    html.push('Hello <i>' + user + '</i>');
    html.push(' <a href=' + results['logoutUrl'] + '>Log off</a>');
  } else {
    html.push('<a href=' + results['loginUrl'] + '>Sign in</a>');
  }

  var element = document.getElementById('login');
  html.join();
  element.innerHTML = html;
};

/**
 * Checks for a valid number.
 * @param {Object} field Data to be validated.
 * @return {boolean} True, if number.
 */
AutoShoppe.isValidNumber = function(field) {
  var ValidChars = '0123456789.';
  var isNumber = true;
  var Char;
  for (i = 0; i < field.length && isNumber == true; i++) {
    Char = field.charAt(i);
    if (ValidChars.indexOf(Char) == -1) {
      isNumber = false;
    }
  }
  return isNumber;
};

/**
 * Aplhanumeric validity check.
 * @param {Object} field Data to be tested.
 * @return {boolean} True, if alphanumeric.
 */
AutoShoppe.validateAlphaNumberic = function(field) {
  if (/[^a-zA-Z0-9\s]/.test(field)) {
    return true;
  }
  return false;
};

/**
 * Performs a custom search by invoking the service.
 */
AutoShoppe.business.customSearch = function() {
  var callback = AutoShoppe.business.populateModels;
  var type = document.getElementById('searchtype').value;
  var priceOp = document.getElementById('searchpriceoperator').value;
  var price = document.getElementById('searchprice').value;
  var postData = '';
  var resultDiv = document.getElementById('result');
  resultDiv.innerHTML = '';


  if (price != '' && price != null && price != 0 && priceOp != 'Any') {
    if (!AutoShoppe.isValidNumber(price)) {
      resultDiv.innerHTML = '<font color=\'red\'>Enter a valid price</font>';
      return;
    }
    switch (priceOp) {
      case '=': postData += 'priceOp=0&';
        break;
      case '<=': postData += 'priceOp=1&';
        break;
      case '>=': postData += 'priceOp=2&';
        break;
    }
    postData = postData + 'price=' + price + '&';
  }

  if (type != '' && type != null && type != 'Any') {
    postData = postData + 'type=' + type;
  }

  if (postData.length == 0) {
    AutoShoppe.business.paginateAllCars();
  } else {
    AutoShoppe.makeHttpRequest('/business/getVehiclesByCustomSearch.do?page=' +
                               AutoShoppe.business.page,
        callback, 'POST', postData.length, postData);
  }
};

/**
 * IE has issues with scriptaculous which is not so important for this
 * application.
 */
AutoShoppe.intiLightBoxEffects = function() {
  var browser = navigator.appName;
  var b_version = navigator.appVersion;
  if (browser == 'Microsoft Internet Explorer') {
    return;
  } else {
    initLightbox();
  }
};
