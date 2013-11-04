var ajaxResultXML = null;
var ajaxResults = new Array();
var ajaxRequestTime = new Array();
var nodeList = new Array();

var djConfig = { isDebug: false /* , forceFlashComm: 8 */ };

function getNodeMonitoredData() {
	var request = "<tns:monitorData xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
      "</tns:monitorData>";
	var nodeURL = "http://localhost:9002/aggregator";
	invokeSOAP(nodeURL, request, function(ajaxResult) {
		
	});
}

function wsaggrNodeList() {
	while(ajaxResultXML == null) {
		setTimeout("wsaggrNodeList()", 200);
		return;
	}
	var xml = stringToXML(ajaxResultXML);
	var body = getBodyFromSoapEnvelope(xml);
	var result = body.childNodes[0];
	var i = 0;
	
	for(;i < result.childNodes.length; i++) {
		alert(result.childNodes[i].childNodes[0].firstChild.data);
		nodeList.push(result.childNodes[i].childNodes[0].firstChild.data);
	}
	alert(xmlToString(result));
	
}

function displayResult(resultID) {

	var resultXML = false;
	
	if(resultID != undefined) {
		if(ajaxResults[resultID]) {
			resultXML = ajaxResults[resultID];
			delete ajaxResults[resultID];
		} else {
			setTimeout("displayResult('" + resultID + "')", 200);
			return;
		}
	} else if(ajaxResultXML == null) {
		setTimeout("displayResult()", 200);
		return;
	} else {
		resultXML = ajaxResultXML;
	}

	wsaggrSetLoading(false);
	var finishTime = (new Date()).getTime();
	var duration = finishTime - ajaxRequestTime[resultID];
	$("#duration").text("Request took " + duration + "ms");
	var resultValue = "";
	var result = false;
	var graph = false;
	var debugs = false;

	try {
		var xml = stringToXML(resultXML);
		var body = getBodyFromSoapEnvelope(xml);
		result = $(body).children("result:first")[0];
		
		// if the response contains no result but a topologyID,
		// we deal with an active (event-based) query
		var topologyID = $(resultXML).find("topologyID")[0];
		var result = $(body).children("result:first")[0];
		//alert(topologyID + " - " + result);
		if(topologyID && !result) {
			topologyID = $(topologyID).text();
			$("#duration").html($("#duration").text() + ".<br/> New topologyID assigned:<br/> " + topologyID);
			var options = $("#activeQueryID").attr('options');
			options[options.length] = new Option(topologyID, topologyID, true, true);
			options.val() = topologyID;
			displayActiveQuery();
			return;
		}

		$("#xmlsoap").val(resultXML);
		
		debugs = $(body).children("debug");
		graph = $(body).children("graph:first")[0];
		if(body.nodeName.substr(body.nodeName.length - 5) == "Fault") {
			result = body;
			wsaggrDebugToConsole("Received SOAP Fault response: " + xmlToString(body));
		}
		var outString = "";
		var index;
		var children = wsaggrGetChildElements(result);
		for(index in children)
			outString += xmlToString(children[index]);
		resultValue = outString;
	} catch(e) {
		try {
			var result = resultXML;
			result = result.substr(result.indexOf('<S:Body') + 1);
			result = result.substr(result.indexOf('<') + 1);
			result = result.substr(result.indexOf('>') + 1);
			result = result.substr(0, result.indexOf('<'+'/S:Body>'));
			result = result.substr(0, result.lastIndexOf('<'));
			resultValue = result;
		} catch(e) {
			resultValue = resultXML;
		}
	}

	if ($("#debug").is(":checked")) {
		$("#tabs").tabs("option", "disabled", []);
		$("#tabs").tabs("select", 2);
		
		// handle debug messages
		wsaggrHandleDebugs(debugs);
		// draw graph
		wsaggrLoadGraph(graph, debugs);
	} else {
		$("#tabs").tabs("select", 1);
	}

	$("#response").val(resultValue);
	
	if(resultValue) {
	
		var resultHTML = result;
		while(resultHTML.nodeName == "result")
			resultHTML = resultHTML.childNodes[0];
		var outString = "";
		var children = $(result).children();
		for (var i=0; i < children.length; i++) {
			outString += xmlToString(children[i]);
		}
	
		var iframe = $("#responseHTML")[0];	
		var doc = iframe.document;
		if(iframe.contentDocument) {
		    doc = iframe.contentDocument; // For NS6
		} else if(iframe.contentWindow) {
		    doc = iframe.contentWindow.document; // For IE5.5 and IE6
		}
		// Put the content in the iframe
		doc.open();
		doc.writeln(outString);
		doc.close();
	}
}

function xmlToString(xml) {
	try {
		return new XMLSerializer().serializeToString(xml);
	} catch(e) {
		return;
	}
}

function stringToXML(string) {
	var xmlDoc = null;
	if (window.DOMParser) {
		var parser = new DOMParser();
		xmlDoc = parser.parseFromString(string,"text/xml");
	} else {
		xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
		xmlDoc.async = "false";
		xmlDoc.loadXML(string);
	}
	return xmlDoc;
}

function getBodyFromSoapEnvelope(env) {
	if(env.documentElement)
		env = env.documentElement;
	var i = 0;
	for(; i < env.childNodes.length; i++) {
		var name = env.childNodes[i].tagName;
		if(name && name.substr(name.length - 4) == "Body") {
			return getFirstChildElement(env.childNodes[i]);
		}
	}
}

function getHeadersFromSoapEnvelope(env) {
	if(env.documentElement)
		env = env.documentElement;
	var headers = new Array();
	var i = 0;
	for(; i < env.childNodes.length; i++) {
		var el = env.childNodes[i];
		var name = el.tagName;
		if(name && name.substr(name.length - 6) == "Header") {
			var j = 0;
			for(; j < el.childNodes.length; j++) { 
				if(el.childNodes[j].nodeType == 1) {
					headers.push(el.childNodes[j]);
				}
			}
		}
	}
	return headers;
}

function getFirstChildElement(element) {
	var j = 0;
	for(; j < element.childNodes.length; j++) { 
		if(element.childNodes[j].nodeType == 1)
			return element.childNodes[j];
	}
	return null;
}

function aggregate(gatewayURL, topologyID, inputs, query, intermediateQuery, preparationQuery) {
	
	if(inputs.substr(0, 8) != "<inputs>")
		inputs = "<inputs>" + inputs + "</inputs>";
	var requestBody = "<tns:aggregate xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			(topologyID ? "<topologyID>" + topologyID + "</topologyID>" : "") +
			inputs +
			"<queries>" +
			"<query><![CDATA[" + query + "]]></query>" +
			(intermediateQuery ? "<intermediateQuery><![CDATA[" + intermediateQuery + "]]></intermediateQuery>" : ""); 
	if(preparationQuery) {
		// check if this is an array
		if(preparationQuery.constructor.toString().indexOf("Array") == -1) {
			requestBody = requestBody + 
				"<preparationQuery><![CDATA[" + preparationQuery + "]]></preparationQuery>";
		} else {
			for(prep in preparationQuery) {
				requestBody = requestBody + preparationQuery[prep];
			}
		}
	}
	requestBody = requestBody + 
			"</queries>" +
			"</tns:aggregate>";
	
	invokeSOAP(gatewayURL, requestBody);
}

function wsaggrFaultCallback(soapFault) {
	$("#errorContainer").show();
	$("#errorContainer").html(
			"<p>" + $(soapFault).find("faultstring").text().replace(/</g,"&lt;") + "</p>");
}

function wsaggrGetDependencyGraph(gatewayURL) {
	var request = wsaggrConstructRequest("request");
	var requestBody = "<tns:getDependencies xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			request +
			"</tns:getDependencies>";
	invokeSOAP(gatewayURL, requestBody);
}

function wsaggrExecuteQuery() {
	$("#errorContainer").html("");
	$("#errorContainer").hide();
	wsaggrSetLoading(true);
	var gatewayURL = $("#wsaggrGatewayURL").val();
	var requestBody = wsaggrConstructRequest();
	var resultID = invokeSOAP(gatewayURL, requestBody);
	displayResult(resultID);
}

function wsaggrConstructRequest(rootElementName) {
	
	var topologyID = false;
	var inputs = wsaggrGetInputsXML();
	var asserts = wsaggrGetAssertionsXML();		
	var query = $("#wsaggrQueryFinal").val();
	
	var intermediateQuery = false;
	if ($("#IntermediateQueryContainer").is(":visible")) {
		intermediateQuery = $("#wsaggrQueryInterm").val();
	}
	
	var preparationQuery = wsaggrGetQueryPrepXML();
	
	if(inputs.substr(0, 8) != "<inputs>") {
		inputs = "<inputs>" + inputs + "</inputs>";
	}
	var requestBody = "<tns:aggregate xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">\n";
	
	
	if(rootElementName) {
		requestBody = "<" + rootElementName + ">\n";
	}
		
	requestBody = requestBody + (topologyID ? "<topologyID>" + topologyID + "</topologyID>" : "") + 
			inputs +
			asserts +
			"<queries>\n" +
			"<query><![CDATA[" + query + "]" +"]></query>\n" +
			(intermediateQuery ? "<intermediateQuery><![CDATA[" + intermediateQuery + "]" + "]></intermediateQuery>" : ""); 
	if (preparationQuery) {
		// check if this is an array
		if(preparationQuery.constructor.toString().indexOf("Array") == -1) {
			requestBody = requestBody + 
				"<preparationQuery><![CDATA[" + preparationQuery + "]" + "]></preparationQuery>";
		} else {
			for(prep in preparationQuery) {
				requestBody = requestBody + preparationQuery[prep];
			}
		}
	}
	// add termination queries
	var inputsContainer = $("#wsaggrInputsContainer");
	var elements = inputsContainer.children();
	var i = 0;
	for(i = 0; i < elements.length; i ++) {
		var element = $(elements[i]);
		var id = element.attr('id').substr(14);
		if($("#wsaggrInput" + id + "Type").val() == "SUBSCRIBE" &&
				$("#wsaggrTermQuery" + id + "Text").val() != "") {
			requestBody = 	requestBody + "\n<terminationQuery forInputs=\"" + id + "\"><![CDATA[" + 
							$("#wsaggrTermQuery" + id + "Text").val() + 
							"]" + "]></terminationQuery>";
		} 
	}
	requestBody = requestBody + "\n</queries>\n";

	if ($("#debug").is(":checked")) {
		requestBody += "<debug>true</debug>\n";
	}
	if ($("#noTimeout").is(":checked")) {
		requestBody += "<timeout>false</timeout>\n";
	}
	
	if (rootElementName) {
		requestBody = requestBody + "</" + rootElementName + ">";		
	} else {
		requestBody = requestBody + "\n</tns:aggregate>";
	}
	//alert(requestBody);
	
	return requestBody;
}

function wsaggrGetAssertionsXML() {
	var container = $("#wsaggrAssertContainer");
	
	elements = container.children();
	
	var result = "";
	
	if (elements.length > 0) {
		for (var i=0; i < elements.length; i++) {
			var element = $(elements[i]);
			
			var id = element.attr('id').substr(18);
						
			var expr = $("#wsaggrAssert" + id + "Expr").val();
			var ds = $("#wsaggrAssert" + id + "DS").val();
			var target = $("#wsaggrAssert" + id + "Target").val();
			var time = $("#wsaggrAssert" + id + "Time").val();
			result += "<assertion>\n" +
							"\t<ID>" + id + "</ID>\n" +
							"\t<expression><![CDATA[" + expr + "]]></expression>\n" +
							"\t<inputID>" + ds + "</inputID>\n" +
							"\t<assertTarget>" + target + "</assertTarget>\n" +
							"\t<assertTime>" + time + "</assertTime>\n" +
						"</assertion>\n";
			
		}
	}
	
	return result;
}

function wsaggrGetInputsXML() {
	var container = $("#wsaggrInputsContainer");
	
	var result = "<inputs>\n";
		
	var element;
	elements = container.children();
	
	if (elements.length > 0) {
		for (var i=0; i < elements.length; i++) {
			var element = $(elements[i]);
			
			var id = element.attr('id').substr(14);
			var type = $("#wsaggrInput" + id + "Type").val();
			
			if (type == "CONSTANT" || type == "COMPOSITE") {
				
				var inputContent = wsaggrTrim($("#wsaggrInput" + id + "Input").val());
				
				if(inputContent.substring(0,1) != "<") {
					inputContent = "<![" + "CDATA[" + inputContent + "]" + "]>";
				}
				
				var tagName = type == "CONSTANT" ? "constant" : "savedRequest";
				result = result + 
					"<" + tagName + " id=\"" + id + "\" contentType=\"waql\">" + 
					inputContent + 
					"</" + tagName + ">\n";
				
			} else {
			
				var to = $("#wsaggrInput" + id + "To").val();
				var doCache = $("#wsaggrInput" + id + "cache").is(":checked");
				var doMonitor = $("#wsaggrInput" + id + "monitor").is(":checked");
				var waql = $("#wsaggrInput" + id + "WAQL").is(":checked");
				var servOrFeat = $("#wsaggrInput" + id + "FeatOrServ").val();
				var servOrFeatValue = $("#wsaggrInput" + id + "FeatServ").val();
				if(servOrFeat == "Feature") {
					servOrFeat = " feature=\"" + servOrFeatValue + "\"";
				} else if(servOrFeat == "Endpoint") {
					servOrFeat = " serviceURL=\"" + servOrFeatValue + "\"";
				} else {
					servOrFeat = "";
				}
				
				// get HTTP headers:
				var headerContainer = $("#wsaggrInput" + id + "Headers");
				headerList = headerContainer.children();
				
				var headers = "";
				if (headerList.length > 0) {
					for (var i=0; i < headerList.length; i++) {
						var header = $(headerList[i]);
						var cookiename = header.children("input:first").val();
						var cookievalue = header.children("input:last").val();
						if(cookiename.length > 0 && cookievalue.length > 0) {
							headers += cookiename + ": " + cookievalue + "\\n";
						}
					}
				}
				
				if(headers) {
					headers = " httpHeaders=\"" + headers + "\"";
				}
				
				var inputContent = wsaggrTrim($("#wsaggrInput" + id + "Input").val());
				
				if(inputContent.substring(0, 1) != "<" || waql) {
					inputContent = "<![CDATA[" + inputContent + "]" + "]>";
				}
				
				if (type == "SUBSCRIBE") {
					
					result = result + 
						"<subscribe id=\"" + id + "\" to=\"" + to + "\"" + 
						(waql? " contentType=\"WAQL\"" : "") + 
						(doCache? " cache=\"true\"" : " cache=\"false\"") +
						servOrFeat + headers + ">\n" + 
						"<config>" +
						"<wse:Filter xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\">" +
						inputContent +
						"</wse:Filter>" +
						"</config>" + 
						"\n</subscribe>\n";
				} else if(type == "WEB_SEARCH") {
					result = result + 
					"<search id=\"" + id + 
					"\" type=\"" + type + 
					"\" to=\"" + to + "\"" + 
					(waql? " contentType=\"WAQL\"" : "") + 
					(doCache? " cache=\"true\"" : " cache=\"false\"") +
					(doMonitor? (" interval=\"" + $("#wsaggrInput" + id + "interval").val() + "\"") : " ") +
					servOrFeat + headers + ">" + 
					inputContent + 
					"</search>\n";
				} else {
					result = result + 
					"<input id=\"" + id + 
					"\" type=\"" + type + 
					"\" to=\"" + to + "\"" + 
					(waql? " contentType=\"WAQL\"" : "") + 
					(doCache? " cache=\"true\"" : " cache=\"false\"") +
					(doMonitor? (" interval=\"" + $("#wsaggrInput" + id + "interval").val() + "\"") : " ") +
					servOrFeat + headers + ">" + 
					inputContent + 
					"</input>\n";
				}
			}
		}
	}

	result = result + "\n</inputs>";
	
	return result;
}

function wsaggrGetQueryPrepXML() {
	
	var container = $("#wsaggrInputsContainer");
	
	var result = new Array();
	
	var element;
	elements = container.children();
	
	if (elements.length > 0) {
		for (var i=0; i < elements.length; i++) {
			var element = $(elements[i]);
			
			var id = element.attr('id').substr(14);
			if ($("#PrepQueryContainer" + id).is(":visible")) {
				var value = $("#wsaggrPrepQuery" + id + "Text").val();
				result.push("<preparationQuery forInputs=\"" + id + "\"><![CDATA[" + value + "]" + "]></preparationQuery>");
			}
		}
	}

	return result;
}

function wsaggrLoadRequest(request) {
	if(!request.nodeType || request.nodeType == "" || request.nodeType == undefined) {
		request = (new DOMParser()).parseFromString(request, "text/xml");
	}
	if(request.nodeType == 9) {
		request = wsaggrGetChildElements(request)[0];
	}
	var inputs = $(request).children("inputs")[0];

	if(!inputs) {
		return;
	}
	
	// remove all input rows
	$("#wsaggrInputsContainer").empty();
	
	var index;
	for (index = 0; index < inputs.children.length; index++) {
	
		var input = inputs.children[index];

		var id = addNewInput(input.getAttribute("id"));
		
		if (input.localName == "constant" || input.localName == "savedRequest") {
			
			$("#wsaggrInput" + id + "Type").val("CONSTANT");
			if(input.localName == "savedRequest") {
				$("#wsaggrInput" + id + "Type").val("COMPOSITE");
			}
			
			$("#wsaggrInput" + id + "Input").val("");
			if(input.childNodes[0]) {
				if(input.childNodes[0].nodeType == 3) { /* text node */
					$("#wsaggrInput" + id + "Input").val(input.childNodes[0].data);
				} else if(input.childNodes[0].nodeType == 1) { /* element node */
					$("#wsaggrInput" + id + "Input").val(xmlToString(input.childNodes[0]));
				}
			}
			
			$("#wsaggrInput" + id + "ToContainer").hide();
			$("#wsaggrInput" + id + "WAQLContainer").hide();
			$("#wsaggrInput" + id + "FeatServContainer").hide();
			$("#wsaggrInput" + id + "Headers").hide();
			$("#wsaggrPrepQuery" + id).hide();
			$("#wsaggrTermQuery" + id).hide();
			
		} else {
		
			var type = input.getAttribute("type");
			if(input.localName == "search") {
				type = "WEB_SEARCH";
			}
			var to = input.getAttribute("to");
			var isWAQL = input.getAttribute("contentType") == "XQuery" || input.getAttribute("contentType") == "WAQL";
			var doCache = input.getAttribute("cache") == "true";
			var doMonitor = input.getAttribute("monitor") && input.getAttribute("monitor") != "";

			var serviceURL = input.getAttribute("serviceURL");
			var feature = input.getAttribute("feature");
			var featOrServ;
			var featOrServText = "";
			if(feature) {
				featOrServ = "Feature";
				featOrServText = feature;
			} else if(serviceURL) {
	            featOrServ = "Endpoint";
	            featOrServText = serviceURL;
	        }

			$("#wsaggrInput" + id + "Type").val(type);
			$("#wsaggrInput" + id + "To").val(to);
			
			$("#wsaggrInput" + id + "FeatOrServ").val(featOrServ);
	        $("#wsaggrInput" + id + "FeatServ").val(featOrServText);

	        if (isWAQL){
	        	$("#wsaggrInput" + id + "WAQL").attr("checked", "checked");
	        }
	        if (doCache){
	        	$("#wsaggrInput" + id + "cache").attr("checked", "checked");
	        }
	        if (doMonitor){
	        	$("#wsaggrInput" + id + "monitor").attr("checked", "checked");
	        	var interval = parseInt("" + input.getAttribute("interval"));
	        	$("#wsaggrInput" + id + "interval").val(interval);
	        }
			$("#wsaggrInput" + id + "Input").val("");
			$("#wsaggrTermQuery" + id).hide();
			//alert(xmlToString(input));
			if(input.childNodes[0]) {
				var inputElements = wsaggrGetChildElements(input);
				if(inputElements.length > 0) {
					var inputString = "";
					if(inputElements[0])
						inputString = xmlToString(inputElements[0]);
					if(inputElements[0].nodeName == "config" && input.nodeName.indexOf("subscribe") >= 0) {
						$("#wsaggrTermQuery" + id).show();
						$("#wsaggrInput" + id + "Type").val("SUBSCRIBE");
						inputElements = wsaggrGetChildElements(inputElements[0]);
						if(inputElements[0].nodeName && inputElements[0].nodeName.indexOf("Filter") >= 0) {
							inputString = xmlToString(inputElements[0]);
							inputString = inputString.substring(inputString.indexOf(">") + 1);
							inputString = inputString.substring(0, inputString.lastIndexOf("</"));
						}
					}
					$("#wsaggrInput" + id + "Input").val(inputString);
				} else {
					$("#wsaggrInput" + id + "Input").val(input.childNodes[0].data);
				}
			}
			
			var headers = input.getAttribute("httpHeaders");
			if(headers) {
				var parts = headers.split("\\n");
				var i;
				for(i in parts) {
					var key = parts[i].substring(0, parts[0].indexOf(":"));
					var value = parts[i].substring(parts[0].indexOf(":") + 1);
					if(key && value) {
						var row = addNewInputHeader(id);
						$("#wsaggrInput" + id + "HeaderName" + row).val(wsaggrTrim(key));
						$("#wsaggrInput" + id + "HeaderValue" + row).val(wsaggrTrim(value));
					}
				}
			}
		}
	}
	
	var queries = $(request).children("queries")[0];
	
	var prepQueries = $(queries).children("preparationQuery");
	for (index = 0; index < prepQueries.length; index++) {
		var prep = prepQueries[index];
		
		id = $(prep).attr("forInputs");
		
		$("#showPrepQueryContainer" + id).hide();
		$("#PrepQueryContainer" + id).show();
		
		if(prep.childNodes[0]) {
			var data = prep.childNodes[0].data;
			if(prep.childNodes[0].nodeType == 4) { 
				// CDATA section
				data = prep.childNodes[0].textContent;
			}
			$("#wsaggrPrepQuery" + id + "Text").val(data);
		}
	}

	var termQueries = $(queries).children("terminationQuery");
	for (index = 0; index < termQueries.length; index++) {
		var term = termQueries[index];
		id = $(term).attr("forInputs");
		
		$("#wsaggrTermQuery" + id).show();
		
		if(term.childNodes[0]) {
			var data = term.childNodes[0].data;
			if(term.childNodes[0].nodeType == 4) { 
				// CDATA section
				data = term.childNodes[0].textContent;
			}
			$("#wsaggrTermQuery" + id + "Text").val(data);
		}
	}

	var debug = $(request).children("debug");
	$("#debug").attr("checked", "");
	if (debug.length == 1) {
		$("#assertionContainer").show();
		$("#debug").attr("checked", "checked");
	}
	var timeout = $(request).children("timeout");
	$("#noTimeout").attr("checked", "");
	if (timeout.length == 1 && $(timeout[0]).text() == "false") {
		$("#noTimeout").attr("checked", "checked");
	}
	
	// add assertions
	$("#wsaggrAssertContainer").empty();
	
	var asserts = $(request).children("assertion");
	for (index = 0; index < asserts.length; index++) {
		var ass = asserts[index];
		
		var id = addNewAssertion();
		
		$("#wsaggrAssert" + id + "Expr").val( 	$(ass).find("expression").text() );
		$("#wsaggrAssert" + id + "DS").val( 	$(ass).find("inputID").text() );
		$("#wsaggrAssert" + id + "Target").val( $(ass).find("assertTarget").text() );
		$("#wsaggrAssert" + id + "Time").val( 	$(ass).find("assertTime").text() );
				
	}
	
	var finalQuery = $(queries).children("query");
	
	$("#wsaggrQueryFinal").val("");
	if(finalQuery && finalQuery[0] && finalQuery[0].firstChild) {
		$("#wsaggrQueryFinal").val(finalQuery[0].firstChild.data);
	}
}

function wsaggrSetLoading(loading,doSetAjaxResultToFalse) {
	var loadingGif = $("#wsaggrLoadingGIF")[0];
	var elementToDisable = $("#tabs")[0];
	if(loading) {
		elementToDisable.style.opacity = "0.3";
		loadingGif.style.visibility = "visible";
		loadingGif.style.position = "absolute";
		loadingGif.style.top = "350px";
		loadingGif.style.left = "50%";
		loadingGif.style.marginLeft = "-16px";
		loadingGif.style.marginTop = "-16px";
		loadingGif.style.zindex = "10000";
	} else {
		if(doSetAjaxResultToFalse)
			ajaxResultXML = false;
		loadingGif.style.visibility = "hidden";
		elementToDisable.style.opacity = "1.0";
	}
}

function wsaggrInitialize() {

}

function wsaggrLoadQuery() {
	var request = "<tns:getPersistedAggregationRequest xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			"<name>" + $("#wsaggrStorageQueryName").val() + "</name>" +
			"</tns:getPersistedAggregationRequest>";
	var registryURL = $("#wsaggrGatewayURL").val();
	invokeSOAP(registryURL, request);
	wsaggrDoLoadQuery();
}

function wsaggrDoLoadQuery() {
	while(ajaxResultXML == null) {
		setTimeout("wsaggrDoLoadQuery()", 200);
		return;
	}
	var xml = stringToXML(ajaxResultXML);
	var body = getBodyFromSoapEnvelope(xml);
	var result = body.childNodes[0];
	//alert(xmlToString(result));
	wsaggrLoadRequest(xmlToString(result));
}

function wsaggrSaveGatewayURL() {
        var request =	"<tns:setGateway xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" + 
				"<gateway>" + 
					"<wsa:Address xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" + 
						$("#wsaggrGatewayURL").val() + 
					"</wsa:Address>" +
				"</gateway>" +
			"</tns:setGateway>";
        var registryURL = $("#wsaggrRegistryURL").val();
        invokeSOAP(registryURL, request);
}

function wsaggrSaveQuery(isPublic) {
	var toSave = wsaggrConstructRequest("request");
	var request = "<tns:persistAggregationRequest xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			"<name>" + $("#wsaggrStorageQueryName").val() + "</name>" +
			toSave +
			"<overwrite>true</overwrite>" +
			"<public>" + (isPublic ? "true" : "false") + "</public>" +
			"</tns:persistAggregationRequest>";
	var url = $("#wsaggrGatewayURL").val();
	invokeSOAP(url, request);
	var opString = "Save Query (PRIVATE)";
	if(isPublic) {
		opString = "Save Query (PUBLIC)";
	}
	wsaggrGetBooleanServiceResult(opString);
}

function wsaggrGetBooleanServiceResult(operationString) {
	while(ajaxResultXML == null) {
		setTimeout("wsaggrGetBooleanServiceResult('" + operationString + "')", 200);
		return;
	}
	var xml = stringToXML(ajaxResultXML);
	var body = getBodyFromSoapEnvelope(xml);
	var result = body.childNodes[0].firstChild.data;
	if(result != "true") {
		alert("Operation '" + operationString + "' did not succeed!");
	}
}

function wsaggrTrim(string) {
	return string.replace(/^\s+/, '').replace(/\s+$/, '');
}

function wsaggrChangeFont(diff) {
	var size = document.body.style.fontSize;
	if(!size)
		size = 13;
	else if(size.indexOf("p") >= 0)
		size = parseInt(size.substring(0, size.indexOf("p")));
	document.body.style.fontSize = (size + diff) + "px";
}

function wsaggrHandleDebugs(debugs) {
	var i = 0;
	for (i=0; i < debugs.length; i++) {
		var debug = debugs[i];
		// handle assertion results
		var asserts = $(debug).children("assertResult");
		 
		for (j=0; j < asserts.length; j++) {
			var result = asserts[j];
			
			var isOK = $(result).children("isOK")[0];
			if(isOK.firstChild) {
				isOK = isOK.firstChild.data;
			}
			var ID = $(result).children("ID")[0];
			if(ID.firstChild) {
				ID = ID.firstChild.data;
			}
			if(isOK != "true") {
				$("#wsaggrAssert" + ID + "Expr").addClass("error");
			}
		}
	}
	
	
	$("#wsaggrAssertContainer").children().each(function(index, Element) {
		var assertion = $(this).clone();
		
		//remove delete button
		assertion.children(":last").remove();
		
		var elements = assertion.find('[id|="wsaggrAssert"]');
		
		for (var i = 0; i < elements.length; i++) {
		    var elementId = $(elements[i]).attr("id");
		    elementId = elementId.replace(/wsaggrAssert/, "wsaggrDebugAssert");
		    $(elements[i]).attr("id", elementId);
		}
		
		assertion.find("input, select").each(function(index, Element) {
			$(this).attr("disabled", "disabled");
		});
		
		assertion.appendTo("#wsaggrAssertDebugContainer");
	});
}

function wsaggrGetChildElements(el, name) {
	var result = new Array();
	if(!el || !el.childNodes)
		return result;
	var i = 0; 
	for(; i < el.childNodes.length; i ++) {
		var child = el.childNodes[i];
		if(child.nodeType == 1) {
			if(!name || name == undefined || child.nodeName == name) {
				result.push(child);
			}
		}
	}
	return result;
}

function wsaggrUUID() {
	var result = '';
	for(var i = 0; i < 20; i ++)
		result += Math.floor(Math.random()*16).toString(16).toUpperCase();
	return result;
}

function doTearDownFramework(gatewayURL) {
	var request = "<tns:terminate xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
		"<params><recursive>true</recursive></params>" +
		"</tns:terminate>";
	invokeSOAP(gatewayURL, request);
}

