/*globals mxqueryjs*/

//Taken from 
//https://gist.github.com/900225/8d2618cd823bd65dd5fb43ddd20bf2cee76a2c08


// Tested in Chrome 11.0.696.28 beta, Firefox 3.6.16,
// Opera 9.64.10487.0 and 11.1.1190.0, Safari 5.0.4 (7533.20.27)

// Demo HTML available at https://gist.github.com/900398



// added by Waldemar Hummer (hummer@infosys.tuwien.ac.at)

xqueryResultCallbacks = new Array();
/* callback for XQIB, needs to be a global function.. */
function processXQueryResult (result, resultID) {
	callbackFunc = xqueryResultCallbacks[resultID];
	eventStream = xqueryResultCallbacks[requestID].eventStream;
	callbackFunc(result, eventStream);
}

// end added by Waldemar Hummer (hummer@infosys.tuwien.ac.at)


(function () {

var ct = 0;

function XQueryHelper () {
    if (!(this instanceof XQueryHelper)) {
        return new XQueryHelper();
    }
}


XQueryHelper.prototype.runXQIB = function (str, cb) {
    if (mxqueryjs) { // Global of XQIB
        setTimeout(function () {XQueryHelper.runXQIB(str, cb);}, 30);
        return;
    }
    
    // The following could be used instead of the following line, if we are placed inside the mxqueryjs() function
    //n.getElementById(R).contentWindow;
    var cw = document.getElementById('mxqueryjs').contentWindow;
    
    // XQIB means of accessing auto-inserted iframe's method for processing an XQIB XQuery string
    /* modified by Waldemar Hummer (hummer@infosys.tuwien.ac.at). 
     * NOTE: The funcs array needs to be adapted for each new release of XQIB (because of code obfuscation).. */
    var funcs = ['ec']; 
    for (var i=0, fl = funcs.length; i < fl; i++) {
        var func = cw[funcs[i]];
        if (func && func.length === 1) {
            func(str);
            break;
        }
    }
    
    if (cb) {
        cb();
    }
};


// Add class methods
['insertXQuery', 'attachXQueryInsertEvent', 'attachXQueryDeleteEvent', 'runXQIB', 'executeQueryWithCustomFiring', 'getXQueryResult'].forEach(function (method) {
    XQueryHelper[method] = function () {
        XQueryHelper.prototype[method].apply(null, arguments);
    };
});


//added by Waldemar Hummer (hummer@infosys.tuwien.ac.at)

XQueryHelper.prototype.generateUUID = function () {
	var result = '';
	for(var i = 0; i < 20; i ++)
		result += Math.floor(Math.random()*16).toString(16).toUpperCase();
	return result;
}
XQueryHelper.prototype.executeXQuery = function (contextItem, query, callback, eventStream) {
	if(!callback) {
		callback = function(result) {
			alert("XQuery result: " + result)
		};
	}
	var xh = XQueryHelper();
	requestID = xh.generateUUID();
	xqueryResultCallbacks[requestID] = callback;
	xqueryResultCallbacks[requestID].eventStream = eventStream;
	
	// tidy parse string in contextItem (get rid of non-well-formed HTML code)
	tmpDiv = document.createElement("div")
	tmpDiv.innerHTML = contextItem;
	contextItem = tmpDiv.innerHTML
	
	xh.runXQIB(
		'declare sequential function local:getResult($result) {' +
			'b:js-call("processXQueryResult", $result, "' + requestID + '"); ' +
		'};\n ' +
		'let $ctxItem := ' + contextItem + ' return ' +
		'local:getResult($ctxItem/(' + query + '))');
}

//end added by Waldemar Hummer (hummer@infosys.tuwien.ac.at)


// EXPORTS
this.XQueryHelper = XQueryHelper;

}());