/**
 * Global namespace representing the "memoreez" module.
 * @type {Object}
 */
var memoreez = {};

/**
 * Binds function to target object so object can be referenced using 'this'
 * keyword when invoked.
 * @param {Function} functionName Reference of function to be bound.
 * @param {Object} target Object that function reference will be bound to.
 * @return {Function} Reference to original function that has been bound to
 *     target argument.
 */
memoreez.bind = function(functionName, target) {
  return function() {
    if (functionName.apply) {
      return functionName.apply(target, arguments);
    }
  };
};

/**
 * Instantiates a new Controller component which, in turn, initializes the data
 * model and dynamically loads the user interface.
 */
memoreez.initialize = function() {
  new memoreez.Controller();
};

/**
 * Creates and returns Element object with the specified properties.
 * @param {string} name Name of element to create (e.g. 'div', 'input').
 * @param {string} innerText Text to include in a text node child of the
 *     returned element.
 * @param {Object} properties Object with key-value pairs representing
 *     element attributes, keyed on attribute name.
 * @return {Element} Reference to new Element object with the specified
 *     properties.
 */
memoreez.createElement = function(name, innerText, properties) {
  var element = document.createElement(name);

  for (x in properties) {
    element[x] = properties[x];
  }

  if (innerText && innerText != '') {
    element.appendChild(document.createTextNode(innerText));
  }

  return element;
};

/**
 * Adds element argument to target argument as a child node.
 * @param {Element} element Element to append to target argument.
 * @param {Node|string} target Node or string ID of Node to soon become
 *     parent of element argument.
 */
memoreez.appendNode = function(element, target) {
  if (target) {
    var type = typeof target;

    if (type == 'string') {
      var targetElmt = document.getElementById(target);
      targetElmt.appendChild(element);
    } else {
      target.appendChild(element);
    }
  }
};

/**
 * Returns the host/domain on which this application is currently running,
 * e.g. http://localhost:8080 or http://myapp.appspot.com
 */
memoreez.getHost = function() {
  var hostRegExp = new RegExp('http://(.*?)/');
  var match = hostRegExp.exec(window.location);
  if (match != null) {
    return 'http://' + match[1];
  }

  return '';
};

/**
 * Class representing Controller component in classic MVC paradigm;
 * instantiates Model and View components, responds to user input events via
 * callback methods passed into View, and updates Model and View accordingly
 * @constructor
 */
memoreez.Controller = function() {
  var callbacks = {
    onCellClick: memoreez.bind(this.onCellClick, this)
  };

  /**
   * Instantiation of Model component.
   * @type {Object}
   */
  this.model = new memoreez.Model();

  /**
   * Instantiation of View component.
   * @type {Object}
   */
  this.view = new memoreez.View(callbacks);

  var request = new memoreez.AJAXRequest(
      memoreez.getHost() + '/memoreez',
      memoreez.bind(this.onCellCount, this),
      memoreez.bind(this.onError, this));

  request.send(true);
};

memoreez.Controller.prototype.onError = function(error) {
};

memoreez.Controller.prototype.onCellCount = function(serverResponse) {
  this.view.drawCells(+serverResponse);
};

/**
 * Handles the server's response when queried for a cell's color -- updates
 * the model's state and refreshes the view accordingly.
 * @param {string} cellId ID or position of the cell whose color is returned
 */
memoreez.Controller.prototype.onCellColor = function(cellId) {
  var me = this;
  return function(serverResponse) {
    var cellColor = serverResponse;

    if (!me.model.cellSelected()) {
      // If no cell is currently "selected" -- if the user is not attempting
      // match cell color with another, already revealed cell -- set this cell
      // as the current cell and display it to the user
      me.model.setSelectedCell(cellId, cellColor);
      me.view.revealCell(cellId, cellColor);
    } else {
      if (me.model.getSelectedCellColor() == cellColor) {
        // If a cell is "selected" and the color returned by the server matches
        // the already revealed cell, display it to the user
        me.view.revealCell(cellId, cellColor);
      } else {
        // If a cell is "selected" but the color returned is different from
        // that of the already revealed cell, display it but set a timeout
        // function which hides both after a short period
        var selectedCellId = me.model.getSelectedCellId();
        me.view.revealCell(cellId, cellColor);
        setTimeout(
          function() {
            me.view.hideCell(selectedCellId);
            me.view.hideCell(cellId);
          },
          500);
      }

      // Unset the selected cell to continue the execution flow
      me.model.unsetSelectedCell();
    }
  }
};

/**
 * Sends an asychronous request to the server requesting the color of the cell
 * with the passed ID
 * @param {string} cellId ID or position of the clicked cell
 */
memoreez.Controller.prototype.onCellClick = function(cellId) {
  var request = new memoreez.AJAXRequest(
      memoreez.getHost() + '/memoreez?cell=' + cellId,
      this.onCellColor(cellId),
      memoreez.bind(this.onError, this));

  request.send(true);
};

/**
 * Class representing Model component in classic MVC paradigm; stores requst
 * object which is used by the View to render the script preview and updated
 * by the Controller as users interact with the interface.
 * @constructor
 */
memoreez.Model = function() {
  /**
   * ID of the selected cell or null if no cell is selected.
   * @type {String}
   */
  this.selectedCellId = null;

  /**
   * Color of the selected cell or null if no cell is selected.
   * @type {String}
   */
  this.selectedCellColor = null;
};

memoreez.Model.prototype.cellSelected = function() {
  return (this.selectedCellId != null);
};

memoreez.Model.prototype.setSelectedCell = function(cellId, cellColor) {
  this.selectedCellId = cellId;
  this.selectedCellColor = cellColor;
};

memoreez.Model.prototype.unsetSelectedCell = function() {
  this.selectedCellId = null;
  this.selectedCellColor = null;
};

memoreez.Model.prototype.getSelectedCellId = function() {
  return this.selectedCellId;
};

memoreez.Model.prototype.getSelectedCellColor = function() {
  return this.selectedCellColor;
};

/**
 * Class representing View component in classic MVC paradigm; generates user
 * interface and establishes event handlers that call Controller methods
 * when users interact with the input controls  
 * @param {Object} eventCallbacks Object with function references assigned to
 *     properties; stores references to Controller methods so Controller can
 *     update the Model accordingly when users interact with the interface.
 * @constructor
 */
memoreez.View = function(eventCallbacks) {
  /**
   * Object with function references assigned to properties; stores references
   * to Controller methods.
   * @type {Element}
   */
  this.callbacks = eventCallbacks;

  this.cellPanel = document.getElementById('cellPanel');
  this.cells = [];
};

/**
 * Creates and displays a number of "cells," represented here as empty div
 * elements, each with an onclick handler which queries the server for its
 * associated color.
 * @param {Number} cellCount Number of cells to draw
 */
memoreez.View.prototype.drawCells = function(cellCount) {
  for (var i=0; i<cellCount; i++) {
    var cellOptions = {
      'id': '' + i,
      'className': 'cell',
      'onclick': this.onCellClick()
    };

    var cell = memoreez.createElement('div', '', cellOptions);
    memoreez.appendNode(cell, this.cellPanel);
    this.cells.push(cell);
  }
};

memoreez.View.prototype.displayError = function(e) {
  alert('displayError: ' + e);
};

/**
 * "Reveals" the cell at the passed location by painting it the passed color
 * and removing its onclick handler
 * @param {String} cellId ID or position of the cell to be revealed
 * @param {String} cellColor Color to paint the cell's background with
 */
memoreez.View.prototype.revealCell = function(cellId, cellColor) {
  for (var i=0; i<this.cells.length; i++) {
    var cell = this.cells[i];

    if (cell.id == cellId) {
      cell.style.backgroundColor = cellColor;
      cell.onclick = null;
      break;
    }
  }
};

/**
 * "Hides" the cell at the passed location by painting it gray and adding
 * an onclick handler so users can query the server for its color in later
 * turns
 * @param {String} cellId ID or position of the cell to be hidden
 */
memoreez.View.prototype.hideCell = function(cellId) {
  for (var i=0; i<this.cells.length; i++) {
    var cell = this.cells[i];

    if (cell.id == cellId) {
      cell.style.backgroundColor = 'silver';
      cell.onclick = this.onCellClick();
      break;
    }
  }
}

/**
 * Returns new function which executes Controller-provided callback function
 * with invoking event's target ID; executed when user clicks on cell; uses a
 * JavaScript closure.
 * @return {Function} Reference to new function.
 */
memoreez.View.prototype.onCellClick = function() {
  var me = this;
  return function(event) {
    if (window.event) {
      target = window.event.srcElement;
    } else if (event) {
      target = event.target;
    }

    me.callbacks.onCellClick(target.id);
  }
}

memoreez.AJAXRequest = function(url, onLoad, onError) {
  this.transport = this.getTransport();

  this.onError = onError;
  this.onLoad = onLoad;
  this.url = url;
};

memoreez.AJAXRequest.prototype.send = function(asynchronous) {
  if (asynchronous == null) {
    asynchronous = true;
  }

  if (this.transport) {
    try {
      var requestObjt = this;
      this.transport.onreadystatechange = function() {
        requestObjt.onReadyState.call(requestObjt);
      }

      this.transport.open('GET', this.url, asynchronous);
      this.transport.send(null);
    }
    catch (e) {
      if (this.onError) {
        this.onError(e);
      }
    }
  }
};

memoreez.AJAXRequest.prototype.onReadyState = function() {
  if (this.transport.readyState == 4) {
    if (this.transport.status==200 || this.transport.status==0) {
      if (this.onLoad) {
        this.onLoad(this.transport.responseText, this.transport.responseXML);
      }
    } else {
      if (this.onError) {
        this.onError();
      }
    }
  }
};

memoreez.AJAXRequest.prototype.getTransport = function() {
  if (window.XMLHttpRequest) {
    return new XMLHttpRequest();
  } else if (window.ActiveXObject) {
    return new ActiveXObject('Microsoft.XMLHTTP');
  }

  return null;
};
