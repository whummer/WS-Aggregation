window.onbeforeunload = browserAggregatorLogout();

if (!window.WebSocket) {
	window.WebSocket = window.MozWebSocket;
	if (!window.WebSocket)
		alert("WebSocket not supported by this browser.\n"
				+ "Connection to Browser Aggregators won't be established.");
}

// LOW UI change - should be a button only, then connect and show everything else
// ALEX implement browser nodes list

function browserAggregatorHideAll() {
	/*
	$('#browserAggregatorSelection').each(function(index) {
		$(this).hide();
	});
	*/
	// LOW: shorten
	
	$('#browserAggregatorStatus').hide();
	$("#browserAggregatorPerformance").hide();
	$("#browserAggregatorRequests").hide();
	$("#browserAggregatorEvents").hide();
	$("#browserAggregatorResults").hide();
	$("#browserAggregatorClear").hide();
	$("#browserAggregatorContainer").hide();
	
}

function browserAggregatorSelector(selectID) {
	var value = $(selectID).val();
	var namePrefix = "#browserAggregator";
	browserAggregatorHideAll();
	$(namePrefix + value).show();
	
	var update = null;
	
	if(value == "Status") {
		$("#browserAggregatorContainer").show();
		update = "<message type=\"statusupdate\">";
	}
	if(value == "Events") {
		update = "<message type=\"eventsupdate\">";
	}
	if(value == "Requests") {
		update = "<message type=\"requestsupdate\">";
	}
	if(value == "Results") {
		update = "<message type=\"resultsupdate\">";
	}
	if(value == "Performance") {
		update = "<message type=\"performanceupdate\">";
	}
	send(update);
}

var protocol = "wsaggr";
var socketlocation = "wsaggr";
var websocket = null;

var clientID = null;
var messageID = null;
var requestID = null;

var requestDivID = "singleRequestDiv";

var queries = new Array();
var streamlist = new Array();

function browserAggregatorLogin() {
	var loc = document.location.toString();
	loc = loc.replace('http://', 'ws://').replace('https://', 'wss://') + "/" + socketlocation;
	
	// ALEX fix dynamic
	// ALEX param port
	loc = "ws://localhost:8082/" + socketlocation;
	
	websocket = new WebSocket(loc, protocol);
	websocket.onopen = wsaggr.onopen;
	websocket.onmessage = wsaggr.onmessage;
	websocket.onclose = wsaggr.onclose;
	websocket.onerror = wsaggr.onerror;
	$("#browserAggregatorLoginButton").hide();
	$("#browserAggregatorLogoutButton").show();
}

function browserAggregatorLogout() {
	if(clientID != null) {
		//send("logout");
		send("<message type=\"logout\" clientID=\"" + clientID + "\"></message>");
		postMessage("Logged out.");
		// ALEX if this is missing there is an error when logging in again
		//document.location.reload(true);
	}
	clear();
	websocket = null;
	protocol = null;
	clientID = null;
	$("#browserAggregatorLogoutButton").hide();
	$("#browserAggregatorLoginButton").show();
}

function send(message) {
	if (websocket) {
		websocket.send(message);
	}
}

/*
 * appends message to activity log panel
 */
function postMessage(m) {
	var panel = $("#browserAggregatorContainer");
	var text = document.createElement('span');
	var lineBreak = document.createElement('br');
	
	text.className = 'text';
	text.innerHTML = m;
	panel.append(text);
	panel.append(lineBreak);
}

function clear() {
	messageID = null;
	requestID = null;
	queries = null;
}

function stringToXML(request) {
	if (window.DOMParser) {
		parser = new DOMParser();
		var xmlDoc = parser.parseFromString(request, "text/xml");
	} else {// Internet Explorer
		xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
		xmlDoc.async = false;
		xmlDoc.loadXML(request);
	}
	return xmlDoc;
}

function XMLtoString(xmlFile) {
	var input = null;
	if (window.DOMParser) {
		input = (new XMLSerializer()).serializeToString(xmlFile);
	} else {
		input = xmlFile.xml;
	}
	return input;
}

function processStatistics(x) {
	var category = x.getAttribute("category");
	
	/*
	 * <message type ="statistics" category="category" content="content"></statistics>
	 */
	if(category == "users") {
		$("#baActiveUsers").text(x.getAttribute("content"));
	}
	
	if(category == "banodes") {
		$("#baActiveNodes").text(x.getAttribute("content"));
	}
	
	if(category == "avgspeed") {
		$("#baAvgSpeed").text(x.getAttribute("content"));
	}
	
	if(category == "minspeed") {
		$("#baMinSpeed").text(x.getAttribute("content"));
	}
	
	if(category == "maxspeed") {
		$("#baMaxSpeed").text(x.getAttribute("content"));
	}
	
	if(category == "reqspeed") {
		$("#baReqSpeed").text(x.getAttribute("content"));
	}
	
	/*
	 * <message type="statistics" category="category">
	 * 		<list>
	 * 			<request att1="" att2=""></request>
	 * 		</list>
	 * </message>
	 */
	if(category == "requests") {
		var list = x.getElementsByTagName("list")[0].childNodes;		

		for(i=0; i<list.length; i++) {
			var request = list[i];
			
			var result = request.getAttribute("result");
			var inputlength = request.getAttribute("inputlength");
			var qCount = request.getAttribute("querycount");
			var speed = request.getAttribute("speed");
			var source = request.getAttribute("source");
			
			$('#baRequestsTable').append(
					"<tr>" +
						"<td>" + result + "</td>" +
						"<td>" + inputlength + "</td>" +
						"<td>" + qCount + "</td>" +
						"<td>" + speed + "</td>" +
						"<td>" + source + "</td>" +
					"</tr>" );
		}
	}
	
	// ALEX test
	if(category == "events") {
		var list = x.getElementsByTagName("list")[0].childNodes;		

		for(i=0; i<list.length; i++) {
			var request = list[i];
			
			var stream = request.getAttribute("eventStreamID");
			var length = request.getAttribute("length");
			var source = request.getAttribute("source");
			
			$('#baRequestsTable').append(
					"<tr>" +
						"<td>" + stream + "</td>" +
						"<td>" + length + "</td>" +
						"<td>" + source + "</td>" +
					"</tr>" );
		}
	}
	
	if(category == "results") {
		var list = x.getElementsByTagName("list")[0].childNodes;		

		for(i=0; i<list.length; i++) {
			var request = list[i];

			var stream = request.getAttribute("eventStreamID");
			var eCount = request.getAttribute("eventcount");
			var length = request.getAttribute("length");
			var speed = request.getAttribute("speed");
			var source = request.getAttribute("source");
			
			$('#baRequestsTable').append(
					"<tr>" +
						"<td>" + stream + "</td>" +
						"<td>" + length + "</td>" +
						"<td>" + eCount + "</td>" +
						"<td>" + speed + "</td>" +
						"<td>" + source + "</td>" +
					"</tr>" );
		}
	}
}


function executeXQuery(contextItem, query, callback, eventStream) {
    var xh = XQueryHelper();
    xh.executeXQuery(contextItem, query, callback, eventStream);
}


function xqueryCallback(result, eventStream) {
	if(result[0]) {
		result = new XMLSerializer().serializeToString(result[0]);
	}
	
	// apply more than 1 query
	if(applySeveralQueriesToResult(result)) {
		return;
	}

	var msg = "<message type=\"result\" clientID=\"" + clientID + "\" requestID=\"" + requestID + "\" messageID=\"" + messageID + "\">" + result + "</message>";
	postMessage("Result: " + result);
	postMessage("reqid: " + eventStream);
	
	if(result.length > 1) {
		send(msg);
	}
	
	
	
	//$("#" + requestDivID).remove();
	clear();
}

// ALEX: test & implement for events
function applySeveralQueriesToResult(result) {	
	if(queries.length > 1) {
		for(i=0; i<queries.length; i++) {
			if(queries[i].applied == false) {
				
				$("#" + requestDivID).empty();
				$("#" + requestDivID).append(result);
				
				queries[i].applied = true;
				
				// ALEX change to iframe
				var xqibinput = "b:dom()//div[@id='" + requestDivID + "']";
				executeXQuery(xqibinput, queries[i], xqueryCallback, requestID);
				return true;
			}
		}
	}
	return false;
}

function xqueryCallbackEvents(result, eventStream) {
	if(result[0]) {
		result = new XMLSerializer().serializeToString(result[0]);
	}

	var msg = "<message type=\"eventresult\" clientID=\"" + clientID + "\" eventStreamID=\"" + eventStream + "\">" + result + "</message>";
	send(msg);
	postMessage(result);
	
	// ALEX: instead of remove - check div input count & trim if needed
	$("#ID" + eventStream).remove();
	clear();
}

/**
 * 	<message type="request" requestID="someid" clientID="clientID" messageID="messageID">
 * 		<queries>
 * 			<r>query</r> 
 * 		</queries>
 * 		<input>...data...</input> 
 * 	</message>
 */
function processReq(x) {
	requestID = x.getAttribute("requestID");
	messageID = x.getAttribute("messageID");
	clientID = x.getAttribute("clientID");

	var qlist = x.getElementsByTagName("queries")[0].childNodes;
	queries = new Array();
	for ( var i = 0; i < qlist.length; i++) {
		queries[i] = qlist[i];//qlist[i].childNodes[0].nodeValue;
		queries[i].applied = false;
	}
	var input = XMLtoString(x.getElementsByTagName("input")[0]);//.childNodes[0]);//getElementsByTagName("html")[0]);
	
	
	var iframe = $("#browserAggregatorIFrame")[0];	
	var doc = iframe.document;
	if(iframe.contentDocument) {
	    doc = iframe.contentDocument; // For NS6
	} else if(iframe.contentWindow) {
	    doc = iframe.contentWindow.document; // For IE5.5 and IE6
	}
	// Put the content in the iframe
	doc.open();
	
	var div = doc.createElement('div');
	div.id = requestDivID;
    div.nodeValue = input;
    div.textContent = input;
    doc.appendChild(div);
    
	//doc.writeln("");
	//doc.appendChild("<div id=\"" + requestDivID + "\">" + input + "</div>");
	doc.close();
	//$("#browserAggregatorFrameContainer").append("<div id=\"" + requestDivID + "\">" + input + "</div>");
	
	
	//$("#browserAggregatorIFrame").contents().find("body").append("<div id=\"" + requestDivID + "\">" + input + "</div>");
	

	//$("#browserAggregatorIFrame").append("<div id=\"" + requestDivID + "\">" + input + "</div>");
	//$("#browserAggregatorIFrame").append("<div id=\"" + requestDivID + "\" style=\"display:none;\">" + input + "</div>");
	
	//$("#tabs-7").append("<div id=\"" + requestDivID + "\" style=\"display:none;\">" + input + "</div>");
	
	
	var q = XMLtoString(queries[0]);
	queries[0].applied = true;
	
	
	//var xqibinput = "b:dom()//div[@id='" + requestDivID + "']";
	var xqibinput = "//iframe[@id='" + browserAggregatorIFrame + "']/div[@id='" + requestDivID + "']";
	
	
	postMessage("call xqib");
	postMessage("reqid: " + requestID);

	executeXQuery(xqibinput, q, xqueryCallback, requestID);
}

/**
 * <message type="queries" streamID="eventstreamid">
 * 		<queries>
 * 			<r>query</r> 
 * 		</queries>
 * </message>
 */
function processQueryList(x) {
	var streamID = x.getAttribute("streamID");
	var qlist = x.getElementsByTagName("queries")[0].childNodes;
	streamlist[streamID] = qlist; //streamID.queries = qlist;
}

/**
 * <message type="event" streamID="eventstreamid">
 * 		<input>event</input>
 * </message>
 */
function processEvent(x) {

	var streamID = x.getAttribute("streamID");
	var q = XMLtoString(streamlist[streamID][0]);
	var data = x.getElementsByTagName("input")[0];//.childNodes[0];
	postMessage(data);
	var input = XMLtoString(data);
	
	var divid = "ID" + streamID;
	
	if($("#" + divid).length == 0) {
		// ALEX append to iframe
		$("#tabs-7").append("<div id=\"" + divid + "\" style=\"display:none;\">" + input + "</div>");
	}
	else {
		$("#" + divid).append(input);
	}
	
	// ALEX change to iframe
	var xqibinput = "b:dom()//div[@id='" + divid + "']";

	postMessage("query: " + q + ". input: " + input);
	executeXQuery(xqibinput, q, xqueryCallbackEvents, streamID);
}



function checkIncomingMessage(msg) {
	if(msg.charAt(0) == "<") {
		var xmlDoc = stringToXML(msg);
		var x = xmlDoc.getElementsByTagName("message")[0];
		var type = x.getAttribute("type");
		
		// single request [synchronous request]
		if(type == "request") {
			if (messageID != null) {
				send("Busy processing another request.");
				return;
			}
			processReq(x);
			return;
		}
		
		// query list for event type requests
		if(type == "queries") {
			processQueryList(x);
			return;
		}
		
		// asynchronous events
		if(type == "event") {
			if (queries === null) {
				send("Can't process events without a query list.");
				return;
			}
			processEvent(x);
			return;
		}
		
		// statistical update
		if(type == "statistics") {
			processStatistics(x);
			return;
		}
		
		if(type == "login") {
			clientID = x.getAttribute("clientID");
			postMessage("Login ID: " + clientID);
			return;
		}
	}	
}

var wsaggr = {
	onopen : function() {},

	onmessage : function(m) {
		checkIncomingMessage(m.data);
	},

	onclose : function(m) {
		browserAggregatorLogout();
	},
	
	onerror : function(m) {
		browserAggregatorLogout();
	}
};


