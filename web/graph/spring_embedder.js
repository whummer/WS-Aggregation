
var desiredNodeDistance = 80;
var edges;
var nodes;
var forceConstant = 100;
var forceConstantA = desiredNodeDistance / Math.log(desiredNodeDistance); //3;
var forceConstantR = Math.pow(desiredNodeDistance, 3); // / 10;
var forceScalar = 1/10

function springEmbedder(graph){
	
	//hideArcs(graph);
	
	//edges = graph.partnerArcs.slice(0).concat(graph.targetServiceArcs.slice(0));
	//nodes = graph.aggregators.slice(0).concat(graph.services.slice(0));
	squareNodes = cartesianProductNodes(graph);
	setNewPositionAttribute(graph);
	for(var j = 0; j < 50; j++){
		// compute attracting forces of aggregator partner arcs
		for(var i = 0; i < graph.partnerArcs.length; i++){
			var arc = graph.partnerArcs[i];
			var forceVector = attractingForceVector(arc.source, arc.destination);	//get offset
			// add offset position
			addOffsetVector(arc.destination.newPosition, negativeForce(forceVector));
		}
		// compute attracting forces of service  arcs
		/*for(var i = 0; i < graph.targetServiceArcs.length; i++){
			var arc = graph.targetServiceArcs[i];
			var forceVector = attractingForceVector(arc.source, arc.destination);	//get offset
			// add offset position
			addOffsetVector(arc.destination.newPosition, negativeForce(forceVector));
		}*/
		
		alignNodes(graph);
		// compute compulsing forces
		for(var i = 0; i < squareNodes.length; i++){
			var forceVector = repulsingForceVector(squareNodes[i][0], squareNodes[i][1]);
			// add offset vector
			addOffsetVector(squareNodes[i][1].newPosition, forceVector);
		}
		
		alignNodes(graph);
		refreshNodesPosition(graph.aggregators);
		refreshNodesPosition(graph.services);
		
	}

	for(var i = 0; i < graph.aggregators.length; i++){
		moveNode(graph.aggregators[i], graph.aggregators[i].newPosition[0], graph.aggregators[i].newPosition[1]);
	}
	/*for(var i = 0; i < graph.services.length; i++){
		moveNode(graph.services[i], graph.services[i].newPosition[0], graph.services[i].newPosition[1]);
	}*/
	
	/*
	for(var i = 0; i < graph.partnerArcs.length; i++){
		alert("distance: " + euclidianDistance(graph.partnerArcs[i].source, graph.partnerArcs[i].destination)
			+ "\npositions: " + graph.partnerArcs[i].source.position + graph.partnerArcs[i].destination.position);
	}*/
	
}

function euclidianDistance(nodeU, nodeV){
	return Math.floor(Math.sqrt(Math.pow(deltaX(nodeU, nodeV), 2) + Math.pow(deltaY(nodeU, nodeV), 2)));
}

function deltaX(nodeU, nodeV){
	return Math.abs(nodeU.position[0] - nodeV.position[0]);
}

function deltaY(nodeU, nodeV){
	return Math.abs(nodeU.position[1] - nodeV.position[1]);
}

// returns offset attracting force vector for nodeU
function attractingForceVector(nodeU, nodeV){
	var distance = euclidianDistance(nodeU, nodeV);
	var offsetX;
	var offsetY;
	
	if(distance > 81){	// move nodes by applying attracting force
		var attractingForce = forceConstantA * Math.log(distance);
		//var attractingForce = Math.pow(distance, 2) / 80;
		offsetX = attractingForce * (nodeV.position[0] - nodeU.position[0]) / distance;
		offsetY = attractingForce *	(nodeV.position[1] - nodeU.position[1]) / distance;
	}else if(distance < 79){ // move nodes to desired node distance
		var difference = desiredNodeDistance - distance;
		offsetX = difference * -1 * (nodeV.position[0] - nodeU.position[0]) / Math.pow(distance, 1);
		offsetY = difference * -1 * (nodeV.position[1] - nodeU.position[1]) / Math.pow(distance, 1);
	}
	else{ // keep nodes in desired node distance
		offsetX = 0;
		offsetY = 0;
	}
	
	return [Math.floor(offsetX), Math.floor(offsetY)];
}

//returns offset repulsing force vector for nodeU
function repulsingForceVector(nodeU, nodeV){
	var distance = euclidianDistance(nodeU, nodeV);
	 // / Math.pow(distance, 2);
	var offsetX;  
	var offsetY;	
	var fieldReach = 150;
	var uCopy = new Object();
	uCopy.position = nodeU.newPosition.slice(0);
	var vCopy = new Object();
	vCopy.position = nodeV.newPosition.slice(0);
	var attractingDistance = euclidianDistance(uCopy, vCopy);
	
	if(distance > 81 && distance < fieldReach){ // move nodes by applying repulsive force
		var repulsingForce = forceConstantR / Math.pow(distance, 2);
		//var repulsingForce = Math.pow(80, 2) / distance;
		offsetX = repulsingForce * (nodeV.position[0] - nodeU.position[0]) / distance / 2;
		offsetY = repulsingForce * (nodeV.position[1] - nodeU.position[1]) / distance / 2;
	}
	else if(distance <= 81 /*&& distance > 65*/){ // move nodes to desired node distance
		
		var uCopy = new Object();
		uCopy.position = nodeU.newPosition.slice(0);
		var vCopy = new Object();
		vCopy.position = nodeV.newPosition.slice(0);
		var attractingDistance = euclidianDistance(uCopy, vCopy);
		offsetX = attractingDistance <= 81 && attractingDistance >= 79 ? 0 : (80 - distance + 20) * (nodeV.position[0] - nodeU.position[0]) / distance / 2;
		offsetY = attractingDistance <= 81 && attractingDistance >= 79 ? 0 : (80 - distance + 20) * (nodeV.position[1] - nodeU.position[1]) / distance / 2;
		
	}
	else{ // nodes out of field
		offsetX = 0;
		offsetY = 0;
	}
	
	return [Math.floor(offsetX), Math.floor(offsetY)];
}

function alignNodes(graph){
	var minX = 0;
	var minY = 0;
	if(graph.aggregators[0]){
		minX = graph.aggregators[0].newPosition[0];
		minY = graph.aggregators[0].newPosition[1];
	}
	else if(graph.services[0]){
		minX = graph.services[0].newPosition[0];
		minY = graph.services[0].newPosition[1];
	}
	else{
		return;
	}
	// determine lowest x and y value
	for(var i = 0; i < graph.aggregators.length; i++){
		if(graph.aggregators[i].newPosition[0] < minX)
			minX = graph.aggregators[i].newPosition[0];
		if(graph.aggregators[i].newPosition[1] < minY)
			minY = graph.aggregators[i].newPosition[1];
	}
	for(var i = 0; i < graph.services.length; i++){
		if(graph.services[i].newPosition[0] < minX)
			minX = graph.services[i].newPosition[0];
		if(graph.services[i].newPosition[1] < minY)
			minY = graph.services[i].newPosition[1];
	}
	// adjust nodes position
	minX = (-1 * minX) + 100;
	minY = (-1 * minY) + 100;
	for(var i = 0; i < graph.aggregators.length; i++){
		graph.aggregators[i].newPosition[0] += minX;
		graph.aggregators[i].newPosition[1] += minY;
	}
	for(var i = 0; i < graph.services.length; i++){
		graph.services[i].newPosition[0] += minX;
		graph.services[i].newPosition[1] += minY;
	}
}

function refreshNodesPosition(nodes){
	for(var i = 0; i< nodes.length; i++){
		nodes[i].position = nodes[i].newPosition.slice(0);
	}
}


function setNewPositionAttribute(graph){
	for(var i = 0; i < graph.aggregators.length; i++){
		graph.aggregators[i].newPosition = graph.aggregators[i].position.slice(0);
	}
	for(var i = 0; i < graph.services.length; i++){
		graph.services[i].newPosition = graph.services[i].position.slice(0);
	}
}

function cartesianProductNodes(graph){
	cartesianProduct = new Array();
	for(var i = 0; i < graph.aggregators.length; i++){
		for(var j = 0; j < graph.aggregators.length; j++){
			if(graph.aggregators[i].id == graph.aggregators[j].id) continue;
			cartesianProduct.push([graph.aggregators[i], graph.aggregators[j]]);
		}
		/*for(var j = 0; j < graph.services.length; j++){
			if(graph.aggregators[i].id == graph.services[j].id) continue;
			cartesianProduct.push([graph.aggregators[i], graph.services[j]]);
		}*/
	}
	/*for(var i = 0; i < graph.services.length; i++){
		for(var j = 0; j < graph.aggregators.length; j++){
			if(graph.services[i].id == graph.aggregators[j].id) continue;
			cartesianProduct.push([graph.services[i], graph.aggregators[j]]);
		}
		for(var j = 0; j < graph.services.length; j++){
			if(graph.services[i].id == graph.services[j].id) continue;
			cartesianProduct.push([graph.services[i], graph.services[j]]);
		}
	}*/
	return cartesianProduct;
}

function addOffsetVector(position, vector){
	position[0] += vector[0];
	position[1] += vector[1];
}

function negativeForce(forceVector){
	forceVector[0] *= -1;
	forceVector[1] *= -1;
	return forceVector;
}

