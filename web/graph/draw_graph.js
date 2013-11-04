
var nodeWidth = 30;
var nodeHeight = 30;
var terminalWidth = 30; //30;
var terminalHeight = 30; //30;
var minNodeDistance = 20;

function drawGraph(graph){

	var startPos = [200, 100];			//start position of topology aggregators grid
	var cursor = [20, 70];
	
	// line up all registered aggregators
	for(var i = 0; i < graph.registeredAggregators.length; i++){
		graph.registeredAggregators[i].position = cursor.slice(0);
		cursor[1] += nodeHeight + minNodeDistance;
	}
	
	// put topology aggregator nodes in startformation (grid)
	cursor = startPos.slice(0);
	var gridWidth = Math.ceil(Math.sqrt(graph.aggregators.length));
	for(var i = 0; i < graph.aggregators.length; i++){
		//graph.aggregators[i].position = [Math.floor(Math.random() * 400), Math.floor(Math.random() * 400)];
		
		
		graph.aggregators[i].position = cursor.slice(0);	//copy of cursor
		if(cursor[0] < startPos[0] + (gridWidth - 1) * (nodeWidth + minNodeDistance)){
			cursor[0] += nodeWidth + minNodeDistance;
		}
		else{
			cursor[0] = startPos[0];
			cursor[1] += nodeHeight + minNodeDistance;
		}
		
	}
	
	// put services in startformation (line above aggregator grid)
	cursor = [20, 20];
	for(var i = 0; i < graph.services.length; i++){
		graph.services[i].position = cursor.slice(0);
		//graph.services[i].position = [Math.floor(Math.random() * 400), Math.floor(Math.random() * 400)];
		cursor[0] += nodeWidth + minNodeDistance;
	}
	
	renderGraph(graph);

	// this is pretty resource-intensive.. :(
	//springEmbedder(graph);

	//alert($("#http\\:\\/\\/localhost\\:9003\\/aggregator3").size());
	//$("#http\\:\\/\\/localhost\\:9003\\/aggregator3").attr("style", "width: 60px; height: 60px; left: 700px; top: 100px;");
	//var container = graph.getAggregatorById("http://localhost:9003/aggregator3");
	//moveNode(container, 700, 200);
	//redrawArcs(graph);
	//alert("remove services");
	//hideServiceNodes(graph);
	//alert("draw arcs");
	//showArcs(graph);
	
}

function renderGraph(graph){
	// draw Aggregators
	var offsetX = (nodeWidth / 2) - (terminalWidth / 2);
	var offsetY = (nodeHeight / 2) - (terminalHeight / 2) + 1;
	if(offsetY == 0) offsetY++;
	for(var i = 0; i < graph.registeredAggregators.length; i++){
		// generate node
		graph.registeredAggregators[i].vertex = new WireIt.ImageContainer({
			terminals: [ {direction: [0,1], offsetPosition: {left: 0, top: 1}, editable: false, className: "WsaggrTerminal"},
			             {direction: [0,1], offsetPosition: {left: 0, top: 1}, editable: true, className: "FixedMappingTerminal", wireConfig: {drawingMethod: "arrows", bordercolor: "white", color: "blue"} }],
			title: graph.registeredAggregators[i].id,
			position: alignImage(graph.registeredAggregators[i].position.slice(0)),
			height: nodeHeight,
			width: nodeWidth,
			image: "./graph/aggregator.png",
			close: false
		}, graphLayer);
		graph.aggregators[i].vertex.terminals[1].eventAddWire.subscribe(wsaggrAddFixedMapping, true);
		graph.aggregators[i].vertex.terminals[1].eventRemoveWire.subscribe(wsaggrRemoveFixedMapping, true);
		
		// generate tooltip for metainformation
		$(graph.registeredAggregators[i].vertex.el).append("<input type='hidden' class='nodeuri' value='" + graph.registeredAggregators[i].id + "'/>");
		graph.registeredAggregators[i].metaData = new YAHOO.widget.Tooltip(graph.registeredAggregators[i].id + "TooltipMetaData", { 
    		context: graph.registeredAggregators[i].vertex.el.id, 
    		text: graph.registeredAggregators[i].id,
    		showDelay: 500
		});
	}
	
	// draw Services
	for(var i = 0; i < graph.services.length; i++){
		// generate node
		graph.services[i].vertex = new WireIt.ImageContainer({
			terminals: [ {direction: [0,1], offsetPosition: {left: offsetX, top: offsetY}, editable: false, className: "WsaggrTerminal" },
			             {direction: [0,1], offsetPosition: {left: 0, top: 1}, editable: true, className: "FixedMappingTerminal", wireConfig: {drawingMethod: "arrows", bordercolor: "white", color: "blue"} }],
			title: graph.services[i].id,
			position: alignImage(graph.services[i].position.slice(0)),
			height: nodeHeight,
			width: nodeWidth,
			image: "./graph/service.png",
			close: false            					
		}, graphLayer);
		graph.services[i].vertex.terminals[1].eventAddWire.subscribe(wsaggrAddFixedMapping, true);
		graph.services[i].vertex.terminals[1].eventRemoveWire.subscribe(wsaggrRemoveFixedMapping, true);
		
		graph.services[i].metaData = new YAHOO.widget.Tooltip(graph.services[i].id + "TooltipMetaData", { 
    		context: graph.services[i].vertex.el.id, 
    		text: graph.services[i].id,
    		showDelay: 500
		});
	}
	
	// draw edges between partner aggregators
	for(var i = 0; i < graph.partnerArcs.length; i++){	//iterate arcs
		// generate wire
		graph.partnerArcs[i].edge = new WireIt.Wire(graph.partnerArcs[i].source.vertex.terminals[0], 
				graph.partnerArcs[i].destination.vertex.terminals[0], 
				graphLayer.el, 
				{drawingMethod: "arrows", bordercolor: "white", color: "black"});
		graph.partnerArcs[i].edge.redraw();
	}
	
	// draw edges between aggregators and services
	for(var i = 0; i < graph.targetServiceArcs.length; i++){	//iterate arcs
		// generate wire
		graph.targetServiceArcs[i].edge = new WireIt.Wire(graph.targetServiceArcs[i].source.vertex.terminals[0], 
				graph.targetServiceArcs[i].destination.vertex.terminals[0], 
				graphLayer.el, 
				{drawingMethod: "arrows", bordercolor: "white", color: "black"});
		graph.targetServiceArcs[i].edge.redraw();
	}

}

function moveNode(node, x, y){
	if(!node) return;
	var imagePosition = alignImage([x, y]);
	new WireIt.util.Anim(node.vertex.terminals, 
			node.vertex.el, 
			{  left: { to: imagePosition[0] }, top: {to: imagePosition[1]} }, 2, 
			YAHOO.util.Easing.easeOut).animate();
	node.position = [x,y];
}

function alignImage(position){
	position[0] -= nodeWidth / 2;
	position[1] -= nodeWidth / 2;
	return position;
}

function showPartnerArcs(graph){
	for(var i = 0; i < graph.partnerArcs.length; i++){
		if(!graph.partnerArcs[i].edge) continue;
		$(graph.partnerArcs[i].edge.element).show();
	}
}

function showTargetServiceArcs(graph){
	for(var i = 0; i < graph.targetServiceArcs.length; i++){
		if(!graph.targetServiceArcs[i].edge) continue;
		$(graph.targetServiceArcs[i].edge.element).show();
	}
}

function showFixedMappings(graph){
	for(var i = 0; i < graph.fixedMappings.length; i++){
		if(!graph.fixedMappings[i].edge) continue;
		$(graph.fixedMappings[i].edge.element).show();
	}
}

function showArcs(graph){
	showPartnerArcs(graph);
	showTargetServiceArcs(graph);
	showFixedMappings(graph);
}

function hidePartnerArcs(graph){
	for(var i = 0; i < graph.partnerArcs.length; i++){
		if(!graph.partnerArcs[i].edge) continue;
		$(graph.partnerArcs[i].edge.element).hide();
	}
}

function hideTargetServiceArcs(graph){
	for(var i = 0; i < graph.targetServiceArcs.length; i++){
		if(!graph.targetServiceArcs[i].edge) continue;
		$(graph.targetServiceArcs[i].edge.element).hide();
	}
}

function hideFixedMappings(graph){
	for(var i = 0; i < graph.fixedMappings.length; i++){
		if(!graph.fixedMappings[i].edge) continue;
		$(graph.fixedMappings[i].edge.element).hide();
	}
}

function hideArcs(graph){
	hidePartnerArcs(graph);
	hideTargetServiceArcs(graph);
	hideFixedMappings(graph);
}

function hideServiceNodes(graph){
	for(var i = 0; i < graph.services.length; i++){
		if(!graph.services[i].vertex) continue;
		$(graph.services[i].vertex.el).hide();
	}
}

function hideAggregatorNodes(graph){
	for(var i = 0; i < graph.aggregators.length; i++){
		if(!graph.aggregators[i].vertex) continue;
		$(graph.aggregators[i].vertex.el).hide();
	}
}

function hideNodes(graph){
	hideServiceNodes(graph);
	hideAggregatorNodes(graph);
}

function showServiceNodes(graph){
	for(var i = 0; i < graph.services.length; i++){
		if(!graph.services[i].vertex) continue;
		$(graph.services[i].vertex.el).show();
	}
}

function showAggregatorNodes(graph){
	for(var i = 0; i < graph.aggregators.length; i++){
		if(!graph.aggregators[i].vertex) continue;
		$(graph.aggregators[i].vertex.el).show();
	}
}

function showNodes(graph){
	showServiceNodes(graph);
	showAggregatorNodes(graph);
}

