
function wsaggrBindPerformanceInfo(){
	for(var i = 0; i < graph.aggregators.length; i++){
		$(graph.aggregators[i].vertex.el).click(wsaggrGetPerformanceInfo);
	}
}

function wsaggrGetPerformanceInfo(){
	
	xhr = new FlashXMLHttpRequest();
	var method = "POST"
	var contentType = "text/xml";
	var url = $(this).find("input.nodeuri").attr("value");
	
	var body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
		"<soap:Body>" +
		"<ns2:getPerformanceInfo xmlns:ns2=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
        "<doNotDeleteData>" +
            "true" +
        "</doNotDeleteData>" +
        "</ns2:getPerformanceInfo>" +
        "</soap:Body>"
        "</soap:Envelope>";
        
	xhr.onload = wsaggrPerformanceResponse;
		
	function wsaggrPerformanceResponse() {
		ajaxResultXML = xhr.responseXML ? xhr.responseXML : xhr.responseText;
		while(ajaxResultXML == null) {
			setTimeout("wsaggrPerformanceResponse()", 200);
			return;
		}
		$("#nodeinformationContainer").html(url + "<br/>");
		wsaggrExtractPerformanceInfo(ajaxResultXML);
	}
	
	xhr.open(method, url);
	xhr.setRequestHeader("Content-Type", contentType);
	xhr.send(body);
}

	
	

function wsaggrExtractPerformanceInfo(performanceInfoXML){
	$(performanceInfoXML).find("ns2\\:AggregatorPerformanceInfo").each(function(){
		var children = $(this).children();
		var performanceInfo = "";
		for(var i = 0; i < children.length; i++){
			performanceInfo += children[i].tagName + ": " + children[i].firstChild.nodeValue + "<br/>";
		}
		$("#nodeinformationContainer").append(performanceInfo);
	});	
}

