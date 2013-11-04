var monitoringInterval;
var monitoringData = {};
var monitoringDataLength = 50;

var chartOptions = {
		highlighter: {
			show: true, 
			showTooltip: true, 
			tooltipAxes: 'y', 
			lineWidthAdjust: 2.5 
		}
	};
	
var cpuOptions = {
		legend: {
			show: true
		}, 
		series: [
			{label: "max CPU"},
			{label: "current CPU"}
		]
	};

$(document).ready(function(){

	$.extend(cpuOptions, chartOptions);

	$("#loadNodeListButton").click(function(){
		getNodeList();
	});
	
	$("#monitoring_refreshrate").change(function(){
		if ($(this).val() == -1) {
			window.clearInterval(monitoringInterval);
		} else {
			window.clearInterval(monitoringInterval);
			monitoringInterval = window.setInterval("refreshNodes()", $(this).val() * 1000);
			refreshNodes();
		}
	});
	
});

function getNodeList() {
	var request = "<tns:getAggregatorNodes xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
         "<numAggregatorNodes>?</numAggregatorNodes>" +
      "</tns:getAggregatorNodes>";
	var registryURL = $("#wsaggrRegistryURL").val();
	invokeSOAP(registryURL, request, function(ajaxResult) {
	
		//save Nodes
		nodeList = new Array();
		
		var xml = stringToXML(ajaxResult);
		var body = getBodyFromSoapEnvelope(xml);
		var result = body.childNodes[0];
		var i = 0;
	
		for(;i < result.childNodes.length; i++) {
			//alert(result.childNodes[i].childNodes[0].firstChild.data);
			nodeList.push(result.childNodes[i].childNodes[0].firstChild.data);
		}
		
		$("#nodeListContainer").empty();
		
		i = 0;
		for(; i < nodeList.length; i++) {

			var nodeHash = hex_md5(nodeList[i]);
			
			var newNode = $("#sampleNode").clone();
			newNode.attr("id", nodeHash + "Container");
			newNode.addClass("nodeContainer");
			var elements = newNode.find('[id*="__nodeHash__"]');
	
			for (var j = 0; j < elements.length; j++) {
				var elementId = $(elements[j]).attr("id");
				elementId = elementId.replace(/__nodeHash__/, nodeHash);
				$(elements[j]).attr("id", elementId);
			}
			newNode.show().appendTo("#nodeListContainer");
			
			$("#" + nodeHash + "Name").html("Name: " + nodeList[i]);
			
			$.jqplot(nodeHash + 'Memory', [[[0,0]]], chartOptions);
			$.jqplot(nodeHash + 'Cpu', [[[0,0]], [[0,0]]], cpuOptions);
			$.jqplot(nodeHash + 'RequestQueueLength', [[[0,0]]], chartOptions);
			$.jqplot(nodeHash + 'OpenFiles', [[[0,0]]], chartOptions);
		}
		
		//alert("nodes updated!");
	});
}

function refreshNodes() {

	for(var i = 0; i < nodeList.length; i++) {
		
		var nodeHash = hex_md5(nodeList[i]);
	
		getNodePerformanceInfo(nodeList[i], nodeHash);
		break; // for testing -> load data only from first node
	}
}

function getNodePerformanceInfo(nodeURL, nodeHash, doNotDeleteData, detailed) {
	var request = "<tns:getPerformanceInfo xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
				((doNotDeleteData != undefined) ? "<doNotDeleteData>" + doNotDeleteData + "</doNotDeleteData>" : "") +
				((detailed != undefined) ? "<detailed>" + detailed + "</detailed>" : "") +
				"<aggregatorEPR><wsa:Address " +
					"xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">" +
					nodeURL +
					"</wsa:Address>" +
				"</aggregatorEPR>" +
				"</tns:getPerformanceInfo>";

	var gatewayURL = $("#wsaggrGatewayURL").val();
	
	invokeSOAP(gatewayURL, request, function(ajaxResult) {
		var body = getBodyFromSoapEnvelope(stringToXML(ajaxResult));
		var result = body.childNodes[0];
		//alert(result);
		
		var data = new Object();
		
		var usedMemory;
		var requestQueueLength;
		var maxUsedCPUOverWindow;
		var currentUsedCPU;
		var windowSizeCPU;
		var openFiles;
		
		var i = 0;
		for(;i < result.childNodes.length; i++) {
			//alert(result.childNodes[i].childNodes[0].firstChild.data);
			if (result.childNodes[i].localName == "usedMemory") {
				//parse value
				var values = result.childNodes[i].firstChild.data.split("E");
			
				data.usedMemory = values[0] * Math.pow(10, values[1]) / 1000;
			}
			if (result.childNodes[i].localName == "requestQueueLength") {
				data.requestQueueLength = parseInt(result.childNodes[i].firstChild.data);
			}
			if (result.childNodes[i].localName == "windowSizeCPU") {
				data.windowSizeCPU = parseFloat(result.childNodes[i].firstChild.data);
			}
			if (result.childNodes[i].localName == "maxUsedCPUOverWindow") {
				data.maxUsedCPUOverWindow = parseFloat(result.childNodes[i].firstChild.data);
			}
			if (result.childNodes[i].localName == "currentUsedCPU") {
				data.currentUsedCPU = parseFloat(result.childNodes[i].firstChild.data);
			}
			if (result.childNodes[i].localName == "openFiles") {
				data.openFiles = parseInt(result.childNodes[i].firstChild.data);
			}
		}
		
		refreshNodePerfomanceGraphs(nodeHash, data);
		
		/*
		<usedMemory>1.1762872E8</usedMemory>
<requestQueueLength>0</requestQueueLength>
<windowSizeCPU>20000.0</windowSizeCPU>
<maxUsedCPUOverWindow>0.0077758736037056705</maxUsedCPUOverWindow>
<currentUsedCPU>0.007736179744167882</currentUsedCPU>
<openFiles>0</openFiles>
		
		*/
	});
}

function refreshNodePerfomanceGraphs(nodeHash, data) {
	
	initMonitoringData(nodeHash);
	
	var jetzt = new Date();
	
	monitoringData[nodeHash].lastupdate = jetzt.getTime();
	monitoringData[nodeHash].counter++;
	
	var x = (jetzt.getTime() - monitoringData[nodeHash].firstupdate) / 1000;
	
	updateMonitoringData(monitoringData[nodeHash].usedMemory, data.usedMemory, x);
	updateMonitoringData(monitoringData[nodeHash].windowSizeCPU, data.windowSizeCPU, x);
	updateMonitoringData(monitoringData[nodeHash].maxUsedCPUOverWindow, data.maxUsedCPUOverWindow, x);
	updateMonitoringData(monitoringData[nodeHash].currentUsedCPU, data.currentUsedCPU, x);
	updateMonitoringData(monitoringData[nodeHash].requestQueueLength, data.requestQueueLength, x);
	updateMonitoringData(monitoringData[nodeHash].openFiles, data.openFiles, x);
	
	$.jqplot(nodeHash + 'RequestQueueLength', [monitoringData[nodeHash].requestQueueLength.data], chartOptions).replot({resetAxes: true});
	$("#" + nodeHash + "RequestQueueLength" + "MinMax").text("min: " + monitoringData[nodeHash].requestQueueLength.min + " max: " + monitoringData[nodeHash].requestQueueLength.max + " avg: " + monitoringData[nodeHash].requestQueueLength.avg);
	$.jqplot(nodeHash + 'Memory', [monitoringData[nodeHash].usedMemory.data], chartOptions).replot({resetAxes: true});
	$("#" + nodeHash + "Memory" + "MinMax").text("min: " + monitoringData[nodeHash].usedMemory.min + " max: " + monitoringData[nodeHash].usedMemory.max + " avg: " + monitoringData[nodeHash].usedMemory.avg);
	$.jqplot(nodeHash + 'Cpu', [monitoringData[nodeHash].maxUsedCPUOverWindow.data, monitoringData[nodeHash].currentUsedCPU.data], cpuOptions).replot({resetAxes: true});
	$("#" + nodeHash + "Cpu" + "MinMax").html("MaxCPU: min: " + monitoringData[nodeHash].maxUsedCPUOverWindow.min + " max: " + monitoringData[nodeHash].maxUsedCPUOverWindow.max + " avg: " + monitoringData[nodeHash].maxUsedCPUOverWindow.avg
		+ "<br />" + "CurrentCPU: min: " + monitoringData[nodeHash].currentUsedCPU.min + " max: " + monitoringData[nodeHash].currentUsedCPU.max + " avg: " + monitoringData[nodeHash].currentUsedCPU.avg
	);
	$.jqplot(nodeHash + 'OpenFiles', [monitoringData[nodeHash].openFiles.data], chartOptions).replot({resetAxes: true});
	$("#" + nodeHash + "OpenFiles" + "MinMax").text("min: " + monitoringData[nodeHash].openFiles.min + " max: " + monitoringData[nodeHash].openFiles.max + " avg: " + monitoringData[nodeHash].openFiles.avg);
}

function initMonitoringData(nodeHash) {

	if (monitoringData[nodeHash] == undefined) {
	
		var jetzt = new Date();
		var savedData = ["usedMemory", "windowSizeCPU", "maxUsedCPUOverWindow", "currentUsedCPU", "requestQueueLength", "openFiles"]
		
		monitoringData[nodeHash] = new Object();
		monitoringData[nodeHash].counter = 0;
		monitoringData[nodeHash].firstupdate = jetzt.getTime();
		
		for (var i = 0; i < savedData.length; i++) {
			monitoringData[nodeHash][savedData[i]] = new Object();
			monitoringData[nodeHash][savedData[i]].min = 0;
			monitoringData[nodeHash][savedData[i]].max = 0;
			monitoringData[nodeHash][savedData[i]].avg = 0;
			monitoringData[nodeHash][savedData[i]].dataSum = 0;
			monitoringData[nodeHash][savedData[i]].data = new Array();
		}

	}
}

function updateMonitoringData(dataNode, value, xValue) {

	dataNode.data.push([xValue, value]);
	
	if (dataNode.max < value) {
		dataNode.max = value;
	}
	if (dataNode.min > value) {
		monitoringData[nodeHash][dataName].min = value;
	}
	
	if (dataNode.data.length > monitoringDataLength) {
		removedValue = dataNode.data.shift();
		dataNode.dataSum -= removedValue[1];
	}
		
	dataNode.dataSum += value;
	dataNode.avg = dataNode.dataSum / dataNode.data.length;
}
