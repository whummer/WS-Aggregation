
/**
 * The request parameter may be either 
 * - a string that represents the SOAP body, or
 * - an array of strings, containing multiple SOAP headers 
 *   and as the last entry the SOAP body string
 */
function invokeSOAP(url, request, callback, faultCallback) {
	if(url == undefined || url == null)
		url = $("#wsaggrGatewayURL").val();
	var body = request;
	
	var headers = null;
	if(wsaggrGetAuthSOAPHeaders) {
		headers = wsaggrGetAuthSOAPHeaders();
	}
	
	var header = "";
	if($.isArray(request)) {
		var i;
		for(i = 0; i < request.length - 1; i ++) {
			header += request[i] + "\n";
		}
		body = request[request.length - 1];
	}
	if(headers && $.isArray(headers)) {
		var i;
		for(i = 0; i < headers.length; i ++) {
			header += headers[i] + "\n";
		}
	}

	var requestString = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
		"<soap:Envelope xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
		"<soap:Header>" + header + "</soap:Header>" + 
		"<soap:Body>" +
		body +
		"</soap:Body>" +
		"</soap:Envelope>";
	//alert(requestString);
	var uuid = ajaxRequest(url, requestString, callback, faultCallback);
	return uuid;
}


function ajaxRequest(url, body, callback, faultCallback) {
	var xhr = new XMLHttpRequest();
	var method = body ? "POST" : "GET";
	var contentType = "text/xml";
	ajaxResultXML = null;
    
	var uuid = "";

	if (!callback) {
		uuid = wsaggrUUID();
	}

	xhr.onload = function() {
		var returnValue = xhr.responseXML ? xhr.responseXML : xhr.responseText;
		
		if(returnValue) {
			/* check if the return value contains a SOAP message
			 * with the following SOAP header:
			 * <tns:username xmlns:tns="http://infosys.tuwien.ac.at/WS-Aggregation">..</tns:username>
			 * This indicates that the server has rejected the request and has
			 * returned the original request message. */
			var bodyXML = returnValue;
			if(!bodyXML.nodeName)
				bodyXML = stringToXML(bodyXML);
			if(bodyXML.documentElement)
				bodyXML = bodyXML.documentElement;
			if(bodyXML.nodeName && bodyXML.nodeName.indexOf("Envelope") >= 0) {
				var headers = getHeadersFromSoapEnvelope(bodyXML);
				var i = 0;
				for(; i < headers.length; i ++) {
					if(headers[i].nodeName.indexOf("username") >= 0 &&
							headers[i].namespaceURI && headers[i].namespaceURI == 
								"http://infosys.tuwien.ac.at/WS-Aggregation") {
						alert("Request authorization failed, possibly due to " +
								"a session timeout. \n\nPlease logout and login again.");
						break;
					}
				}
			}
			
			if (callback != undefined) {
				callback(returnValue);
			} else {
				ajaxResults[uuid] = returnValue;
				ajaxResultXML = returnValue;
			}
		
			if(!returnValue.nodeName) {
				xml = stringToXML(returnValue);
				if(xml) {
					var body = getBodyFromSoapEnvelope(xml);
					if(body && body.nodeName && body.nodeName.indexOf(":Fault") > 0) {
						if (faultCallback != undefined) {
							faultCallback(body);
						} else {
							wsaggrFaultCallback(body);
						}
					}
				}
			}
			
		} else {
			if(body != null) {
				alert("No result body received from call to gateway, perhaps " +
						"you need to reload the page and login again. " +
						"\nURL " + url + " \nbody: " + body);
			}
		}
	}
	
	xhr.open(method, url);

	// note: Content-Type is a special header (other headers are not supported yet)
	xhr.setRequestHeader("Content-Type", contentType);

	ajaxRequestTime[uuid] = (new Date()).getTime();
	xhr.send(body);

	return uuid;
}

// TODO deprecated
function ajaxRequestFlash(url, body, callback, faultCallback) {
	var xhr = new FlashXMLHttpRequest();
	var method = body ? "POST" : "GET";
	var contentType = "text/xml";
	ajaxResultXML = null;
    
	var uuid = "";

	if (!callback) {
		uuid = wsaggrUUID();
	}

	xhr.onload = function() {
		var returnValue = xhr.responseXML ? xhr.responseXML : xhr.responseText;
		
		if(returnValue) {
			/* check if the return value contains a SOAP message
			 * with the following SOAP header:
			 * <tns:username xmlns:tns="http://infosys.tuwien.ac.at/WS-Aggregation">..</tns:username>
			 * This indicates that the server has rejected the request and has
			 * returned the original request message. */
			var bodyXML = returnValue;
			if(!bodyXML.nodeName)
				bodyXML = stringToXML(bodyXML);
			if(bodyXML.documentElement)
				bodyXML = bodyXML.documentElement;
			if(bodyXML.nodeName && bodyXML.nodeName.indexOf("Envelope") >= 0) {
				var headers = getHeadersFromSoapEnvelope(bodyXML);
				var i = 0;
				for(; i < headers.length; i ++) {
					if(headers[i].nodeName.indexOf("username") >= 0 &&
							headers[i].namespaceURI && headers[i].namespaceURI == 
								"http://infosys.tuwien.ac.at/WS-Aggregation") {
						alert("Request authorization failed, possibly due to " +
								"a session timeout. \n\nPlease logout and login again.");
						break;
					}
				}
			}
			
			if (callback != undefined) {
				callback(returnValue);
			} else {
				ajaxResults[uuid] = returnValue;
				ajaxResultXML = returnValue;
			}
		
			if(!returnValue.nodeName) {
				xml = stringToXML(returnValue);
				if(xml) {
					var body = getBodyFromSoapEnvelope(xml);
					if(body && body.nodeName && body.nodeName.indexOf(":Fault") > 0) {
						if (faultCallback != undefined) {
							faultCallback(body);
						} else {
							wsaggrFaultCallback(body);
						}
					}
				}
			}
			
		} else {
			if(body != null) {
				alert("No result body received from call to gateway, perhaps " +
						"you need to reload the page and login again. " +
						"\nURL " + url + " \nbody: " + body);
			}
		}
	}
	
	xhr.open(method, url);

	// note: Content-Type is a special header (other headers are not supported yet)
	xhr.setRequestHeader("Content-Type", contentType);

	ajaxRequestTime[uuid] = (new Date()).getTime();
	xhr.send(body);

	return uuid;
}

