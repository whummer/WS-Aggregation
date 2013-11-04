

function wsaggrAddFixedMapping(e,params) {
	var wire=params[0];
	if(!wire.terminal2){
		return;
	}
	if(graph.getFixedMapping(wire.terminal1.container.options.title, wire.terminal2.container.options.title)){	//mapping already exists
		return;
	}
	var source = graph.getAggregatorById(wire.terminal1.container.options.title);
	if(source){
		var destination = graph.getAggregatorById(wire.terminal2.container.options.title);
		if(!destination)
			destination = graph.getServiceById(wire.terminal2.container.options.title);
		if(!destination)	// destination is neither Aggregator nor Service
			return;
		var arc = new WsaggrArc();
		arc.source = source;
		arc.destination = destination;
		arc.edge = wire;
		graph.fixedMappings.push(arc);
		/*
		 * call gateway to set up fixed mapping
		 */
	}
	else{
		// Invalid Mapping (servicenode cannot be source of mapping)
		wire.terminal2.removeWire(wire);
	}
	//graph.printWires();
}

function wsaggrRemoveFixedMapping(e,params) {
	var wire=params[0];
	if(!wire.terminal2 || !wire.terminal1){
		return;
	}
	graph.removeFixedMapping(wire.terminal1.container.options.title, wire.terminal2.container.options.title);
	/*
	 * call gateway to set up fixed mapping
	 */
	//graph.printWires();
}
