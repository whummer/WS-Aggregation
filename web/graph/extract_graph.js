
function extractGraph(xmlTopology, graph){
	
	// extract aggregators from topology
	$(xmlTopology).find("aggregatorNode > epr > ns2\\:Address").each(function(){
		if(!graph.getAggregatorById($(this).text())){	// aggregator not registered || registered aggregators not loaded by client
			var aggregator;
			aggregator = new WsaggrAggregator();
			aggregator.id = $(this).text();
			aggregator.position = [0,0];
			aggregator.vertex = null;
			graph.registeredAggregators.push(aggregator);
			graph.aggregators.push(aggregator);
		}
		else{	// aggregator registered
			if(graph.getTopologyAggregatorById($(this).text())) return;		// aggregator already added
			graph.aggregators.push(graph.getAggregatorById($(this).text()));
		}
	});
	
	//graph.printAggregators();
	
	// extract services from topology
	$(xmlTopology).find("dataServiceNode > epr > ns2\\:Address").each(function(){
		if(graph.getServiceById($(this).text())) return;
		var service = new WsaggrService();
		service.id = $(this).text();
		service.position = [0, 0];
		service.vertex = null;
		graph.services.push(service);
	});
	
	//graph.printServices();
	
	// extract partner aggregators
	$(xmlTopology).find("partners > j\\:e").each(function(){
		//var edge = new Object();
		var sourcenode = graph.getAggregatorById($(this).find("k > aggregatorNode > epr > ns2\\:Address").text());
		$(this).find("v > j\\:list > j\\:i > aggregatorNode > epr > ns2\\:Address").each(function(){
			var destinationNode = graph.getAggregatorById($(this).text());
			var arc = new WsaggrArc();
			arc.source = sourcenode;
			arc.destination = destinationNode;
			arc.edge = null;			
			sourcenode.partners.push(destinationNode);
			graph.partnerArcs.push(arc);
		});
		//alert($(this).find("k > aggregatorNode > epr > ns2\\:Address").text());
	});
	
	// extract target services
	$(xmlTopology).find("targetServiceRequests > j\\:e").each(function(){
		var sourcenode = graph.getAggregatorById($(this).find("k > aggregatorNode > epr > ns2\\:Address").text());
		$(this).find("v > j\\:map > j\\:e > k >  dataServiceNode > epr > ns2\\:Address").each(function(){
			var targetServiceNode = graph.getServiceById($(this).text());
			var arc = new WsaggrArc();
			arc.source = sourcenode;
			arc.destination = targetServiceNode;
			arc.edge = null;
			sourcenode.targetServices.push(targetServiceNode);
			graph.targetServiceArcs.push(arc);
		});
	});
	//graph.printWires();
	
	return graph;
}

function wsaggrExtractAggregators(xmlAggregators, graph){
	/*
	 * XML Representation of Aggregator
	  <ns2:EndpointReference xmlns:ns2="http://schemas.xmlsoap.org/ws/2004/08/addressing">
        <ns2:Address>http://localhost:9007/aggregator7</ns2:Address>
        <ns2:ServiceName xmlns:tns="http://infosys.tuwien.ac.at/WS-Aggregation" PortName="AggregatorNodePort">tns:AggregatorNodeService</ns2:ServiceName>
      </ns2:EndpointReference>
	 */
	$(xmlAggregators).find("*").each(function() {
		if(this.nodeName.toLowerCase().indexOf("address") >= 0) {
			if(this.parentNode.nodeName.toLowerCase().indexOf("endpointreference") >= 0) {
				var aggregator = new WsaggrAggregator();
				aggregator.id = $(this).text();
				aggregator.position = [0,0];
				aggregator.vertex = null;
				graph.registeredAggregators.push(aggregator);
				graph.aggregators.push(aggregator);
			}
		}
	});
}

function WsaggrArc(){
	
}

WsaggrArc.prototype.source;
WsaggrArc.prototype.destination;
WsaggrArc.prototype.edge;

function WsaggrService(){
	
}

WsaggrService.prototype.id;
WsaggrService.prototype.position;
WsaggrService.prototype.vertex;

function WsaggrAggregator(){
	this.partners = new Array();
	this.targetServices = new Array();
}

WsaggrAggregator.prototype.id;
WsaggrAggregator.prototype.position;
WsaggrAggregator.prototype.vertex;
WsaggrAggregator.prototype.partners;
WsaggrAggregator.prototype.targetServices;

function WsaggrGraph(){
	this.aggregators = new Array();
	this.services = new Array();
	this.partnerArcs = new Array();
	this.targetServiceArcs = new Array();
	this.registeredAggregators = new Array();
	this.fixedMappings = new Array();
}

WsaggrGraph.prototype.aggregators;
WsaggrGraph.prototype.registeredAggregators;
WsaggrGraph.prototype.services;
WsaggrGraph.prototype.partnerArcs;
WsaggrGraph.prototype.targetServiceArcs;
WsaggrGraph.prototype.fixedMappings;

WsaggrGraph.prototype.clearUI = function(){
	// remove aggregator nodes from ui
	if(this.registeredAggregators){
		for(var i = 0; i < this.registeredAggregators.length; i++){
			if(this.registeredAggregators[i].vertex)
				this.registeredAggregators[i].vertex.remove();
			if(this.registeredAggregators[i].metaData)
				this.registeredAggregators[i].metaData.destroy();	//remove tooltip
		}
	}
	// remove service nodes from ui
	if(this.services){
		for(var i = 0; i < this.services.length; i++){
			if(this.services[i].vertex)
				this.services[i].vertex.remove();
			if(this.services[i].metaData)
				this.services[i].metaData.destroy();
		}
	}
}
WsaggrGraph.prototype.clear = function(){
	this.clearUI();
	delete this.aggregators;
	delete this.services;
	delete this.partnerArcs;
	delete this.targetServiceArcs;
	delete this.registeredAggregators;
	delete this.fixedMappings;
}

WsaggrGraph.prototype.getAggregatorById = function(id){
	var agg = null;
	for(var i = 0; i < this.registeredAggregators.length; i++){
		if(this.registeredAggregators[i].id && this.registeredAggregators[i].id == id) agg = this.registeredAggregators[i];
	}
	return agg;
}

WsaggrGraph.prototype.getTopologyAggregatorById = function(id){
	var agg = null;
	for(var i = 0; i < this.aggregators.length; i++){
		if(this.aggregators[i].id && this.aggregators[i].id == id) agg = this.aggregators[i];
	}
	return agg;
}


WsaggrGraph.prototype.getServiceById = function(id){
	var service = null;
	for(var i = 0; i < this.services.length; i++){
		if(this.services[i].id && this.services[i].id == id) service = this.services[i];
	}
	return service;
}

WsaggrGraph.prototype.getFixedMapping = function(sourceId, destId){
	if(!sourceId || !destId)return;
	for(var i = 0; i < this.fixedMappings.length; i++){
		if(this.fixedMappings[i] && this.fixedMappings[i].source.id == sourceId && 
				this.fixedMappings[i].destination.id == destId)
			return this.fixedMappings[i];
	}
	return null;
}

WsaggrGraph.prototype.removeFixedMapping = function(sourceId, destId){
	if(!sourceId || !destId)return;
	for(var i = 0; i < this.fixedMappings.length; i++){
		if(this.fixedMappings[i] && this.fixedMappings[i].source.id == sourceId && 
				this.fixedMappings[i].destination.id == destId){
			var frontSlice = this.fixedMappings.slice(0,i);
			var backSlice = i < this.fixedMappings.length - 1 ? this.fixedMappings.slice(i+1,this.fixedMappings.length) : new Array(); 
			this.fixedMappings = frontSlice.concat(backSlice);
		}
	}
}

WsaggrGraph.prototype.printAggregators = function(){
	var output = "[";
	for(var i = 0; i < this.aggregators.length; i++){
		try {
			if(this.aggregators[i].id) output += this.aggregators[i].id + ", ";
		} catch(e) {
			output += "\n" + e + "\n";
		}
	}
	output += "]";
	alert(output);
}

WsaggrGraph.prototype.printServices = function(){
	var output = "[";
	for(var i = 0; i < this.services.length; i++){
		if(this.services[i].id) output += this.services[i].id + ", ";
	}
	output += "]";
	alert(output);
}

WsaggrGraph.prototype.printWires = function(){
	var output = "{";
	for(var i = 0; i < this.partnerArcs.length; i++){
		output += "[" + this.partnerArcs[i].source.id + "->" + this.partnerArcs[i].destination.id + "], ";
	}
	for(var i = 0; i < this.targetServiceArcs.length; i++){
		output += "[" + this.targetServiceArcs[i].source.id + "->" + this.targetServiceArcs[i].destination.id + "], ";
	}
	for(var i = 0; i < this.fixedMappings.length; i++){
		output += "[" + this.fixedMappings[i].source.id + "->" + this.fixedMappings[i].destination.id + "], ";
	}
	output += "}";
	alert(output);
}





