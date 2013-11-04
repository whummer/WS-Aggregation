
var wsaggrGraphDiv;
var wsaggrWireitLayer = null;
var wsaggrGraphXDistance = 100;
var wsaggrGraphYDistance = 70;

function wsaggrGraphOnNodeSelect(e,params) {
	//alert(e);
}

function wsaggrRefreshGraph() {
	var gatewayURL = $("#wsaggrGatewayURL").val();
	wsaggrGetDependencyGraph(gatewayURL);
	wsaggrDoGetDependencyGraph();
}

function wsaggrDoGetDependencyGraph() {
	while(ajaxResultXML == null) {
		setTimeout("wsaggrDoGetDependencyGraph()", 100);
		return;
	}
}

function wsaggrIndexOf(someArray,someValue) {
	var i = 0;
	for(i in someArray) {
		if(someArray[i] == someValue)
			return i;
	}
	return -1;
}

function wsaggrLoadGraph(graph, debugs) {

	if(!graph)
		return;

	var nodes = Array();
	var nodeIDs = Array();
	var wires = Array();
	var errors = Array();
	var i = 0;
	
	var nodesXML = $(graph).children("node");
	for(i = 0; i < nodesXML.length; i++) {
		var node = $(nodesXML[i]);
		var id = node.attr("ID");

		nodeIDs.push(id);
		nodes[id] = new Object(); 
		nodes[id]["name"] = node.attr("name"); 
		nodes[id]["from"] = new Array();
		nodes[id]["to"] = new Array();
		nodes[id]["column"] = 0;
		nodes[id]["row"] = 0;
	
	}
	
	var edgesXML = $(graph).children("edge");
	for(i = 0; i < edgesXML.length; i++) {
		var edge = $(edgesXML[i]);
		
		var from = edge.attr("from");
		var to = edge.attr("to");
		
		var wire = new Array(from, to);
		nodes[from]["to"].push(to);
		nodes[to]["from"].push(from);
		wires.push(wire);
	}
	
	for(i = 0; i < debugs.length; i++) {
		// handle data source errors
		var inResponseTo = parseInt(debugs[i].getAttribute("inResponseTo"));
		errors[inResponseTo] = false;
		var dsErrors = wsaggrGetChildElements(debugs[i], "dataSourceError");
		for(j in dsErrors) {
			errors[inResponseTo] = true;
			wsaggrDebugToConsole(dsErrors[j].firstChild.data);
		}
	}
	
	if(!wsaggrWireitLayer) {
		wsaggrWireitLayer = new WireIt.Layer({name: "node", parentEl: YAHOO.util.Dom.get('wiring-editor')});
	}
	var layer = wsaggrWireitLayer;
	layer.clear();
	
	wsaggrGraphGetNodePositions(graph, nodes, nodeIDs);
	
	for(index = 0; index < nodeIDs.length ; index++) {
		var id = nodeIDs[index];
		var node = nodes[id];
		node["containerId"] = index;
		
		var x = wsaggrGraphXDistance * node["column"];
		var y = wsaggrGraphYDistance * node["row"];
		
		var container = {
			terminals: [{name: "from", offsetPosition: {top: -2, left: -12},
				editable: false },
				{name: "to", offsetPosition: {top: -2, left: 32},
				editable: false }
			],
			className: errors[i] ? "wsaggrGraphNodeError" : "wsaggrGraphNode",
			title: node["name"],
			width: 50,
			position: new Array(x, y),
			close: false
		};
		container = layer.addContainer(container);
		YAHOO.util.Event.addListener(container.el, "mousedown", wsaggrGraphOnNodeSelect, container, true);

	}
	
	for(var i = 0 ; i < wires.length ; i++) {
		var wc = wires[i];
		var srcModuleId = nodes[wc[0]]["containerId"];
		var tgtModuleId = nodes[wc[1]]["containerId"];
		layer.addWire({
			src: {moduleId: srcModuleId, terminal: "to"},
			tgt: {moduleId: tgtModuleId, terminal: "from"},
			drawingMethod: "arrows"
		});
	}
	
	layer.initContainers();
	layer.initWires();
	
	//layer.dynamicLayout.init();
	//layer.startDynamicLayout();
	//layer.stopDynamicLayout();
	
};

function wsaggrGraphGetNodePositions(graph, nodes, nodeIDs) {
	
	var unProccessedIDs = nodeIDs;
	
	var values = { maxRow: 0, unProccessedIDs: nodeIDs }
	
	var index;
	
	for(index in unProccessedIDs) {
		var id = unProccessedIDs[index];
		var node = nodes[id];
		
		//starting nodes without dependencies
		if ((node["from"].length) == 0) {
			wsaggrGetNodePosRecursive(nodes, node, 1, values.maxRow + 1, values);
		}
	}
}

function wsaggrGetNodePosRecursive(nodes, node, column, row, values) {
	
	//process only new nodes
	if (node["column"] == 0) {
	
		node["column"] = column;
		node["row"] = row;
		
		for (var i = 0; i < node["to"].length; i++) {
			var nextNode = nodes[ node["to"][i] ];
			row = values.maxRow + 1;
			wsaggrGetNodePosRecursive(nodes, nextNode, column + 1, row, values);
		}
		
		if (row > values.maxRow) {
			values.maxRow = row;
		}
	}
}
