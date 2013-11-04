
window.addEventListener("DOMContentLoaded", initView, false);
var graphLayer; 
var graph;
var wsaggrGatewayHistory = new WsaggrGatewayHistory();

function initView(){
	graphLayerOptions = new Object();
	graphLayerOptions.parentEl = $("#graphContainer")[0];
	graphLayer = new WireIt.Layer(graphLayerOptions);
	graph = new WsaggrGraph();
	
	$("#chkShowPartnerArcs").click(function(event){
		if(graph){
			if($(this).attr("checked") == true)
				showPartnerArcs(graph);
			else
				hidePartnerArcs(graph);
		}
	});
	$("#chkShowTargetServiceArcs").click(function(event){
		if(graph){
			if($(this).attr("checked") == true)
				showTargetServiceArcs(graph);
			else
				hideTargetServiceArcs(graph);
		}
	});
	$("#chkShowAggregatorNodes").click(function(event){
		if(graph){
			if($(this).attr("checked") == true)
				showAggregatorNodes(graph);
			else
				hideAggregatorNodes(graph);
		}
	});
	$("#chkShowServiceNodes").click(function(event){
		if(graph){
			if($(this).attr("checked") == true)
				showServiceNodes(graph);
			else
				hideServiceNodes(graph);
		}
	});
	
	$("#chkShowFixedMappings").click(function(event){
		if(graph){
			if($(this).attr("checked") == true)
				showFixedMappings(graph);
			else
				hideFixedMappings(graph);
		}
	});
	
	$("#wsaggrGatewayURL").change(function(event){
		wsaggrGatewayHistory.updateDom();
	});
	
	$("#btnLoadGraph").click(function(event){
		wsaggrGatewayHistory.update();
	});
}

function wsaggrLoadTopology(){

	if(graph)
		graph.clear();
	graph = new WsaggrGraph();
	
	wsaggrLoadAggregators();
	
	var xhr = new FlashXMLHttpRequest();
	var method = "POST"
	var contentType = "text/xml";
	
	var topID = $("#topologyName").attr("value");
	
	var body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
			"<soap:Body>";

	if(!topID || topID == "__ALL__") {
		$("#topologyName").attr("value", "__ALL__");
		body +=	"<ns2:collectAllTopologies xmlns:ns2=\"http://infosys.tuwien.ac.at/WS-Aggregation\"/>";
	} else {
		body +=	"<ns2:getTopology xmlns:ns2=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
		"<topologyID>" +
		    $("#topologyName").attr("value") +
		"</topologyID>" +
		"</ns2:getTopology>";
	}
	body += "</soap:Body></soap:Envelope>";

	var url = $("#wsaggrGatewayURL").val();	// selected gateway from input tab

	function wsaggrGraphDoLoadResponse() {
		ajaxResultXML = xhr.responseXML ? xhr.responseXML : xhr.responseText;
		while(!ajaxResultXML) {
			setTimeout("wsaggrGraphDoLoadResponse()", 200);
			return;
		}
		graph = extractGraph(ajaxResultXML, graph);
		drawGraph(graph);
		wsaggrBindPerformanceInfo();
	}
	
	xhr.onload = wsaggrGraphDoLoadResponse;
		
	xhr.open(method, url);
	xhr.setRequestHeader("Content-Type", contentType);
	xhr.send(body);
	
}

function wsaggrLoadAggregators(){
	var xhr = new FlashXMLHttpRequest();
	var method = "POST"
	var contentType = "text/xml";
	
	var body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
		"<soap:Body>" +
		"<ns2:getAggregatorNodes xmlns:ns2=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
		"</ns2:getAggregatorNodes>" +
		"</soap:Body>"
		"</soap:Envelope>";

	var url = $("#wsaggrRegistryURL").val();
	xhr.onload = wsaggrAggregatorsDoLoadResponse;
		
	xhr.open(method, url);
	xhr.setRequestHeader("Content-Type", contentType);
	xhr.send(body);
		
	function wsaggrAggregatorsDoLoadResponse() {
		ajaxResultXML = xhr.responseXML ? xhr.responseXML : xhr.responseText;
		while(ajaxResultXML == null) {
			setTimeout("wsaggrAggregatorsDoLoadResponse()", 200);
			return;
		}
		wsaggrExtractAggregators(ajaxResultXML, graph);
	}
}

//helper function to indent unformattet xml data
function formatXml(xml) { 
    var formatted = ''; 
    var reg = /(>)(<)(\/*)/g; 
    xml = xml.replace(reg, '$1\r\n$2$3'); 
    var pad = 0; 
    jQuery.each(xml.split('\r\n'), function(index, node) { 
        var indent = 0; 
        if (node.match( /.+<\/\w[^>]*>$/ )) { 
            indent = 0; 
        } else if (node.match( /^<\/\w/ )) { 
            if (pad != 0) { 
                pad -= 1; 
            } 
        } else if (node.match( /^<\w[^>]*[^\/]>.*$/ )) { 
            indent = 1; 
        } else { 
            indent = 0; 
        } 
        var padding = ''; 
        for (var i = 0; i < pad; i++) { 
            padding += '  '; 
        } 
        formatted += padding + node + '\r\n'; 
        pad += indent; 
    }); 
    return formatted; 
} 
