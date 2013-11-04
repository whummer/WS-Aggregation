
var wsaggrHistorySize = 5;

function WsaggrGatewayHistory(){
	
}

WsaggrGatewayHistory.prototype.update = function(){
	var gateway = $("#wsaggrGatewayURL").val();
	var topology = $("#topologyName").attr("value");
	if(!topology || topology == "__ALL__") {
		$("#topologyName").attr("value", "__ALL__");
	}
	
	if(this[gateway] == undefined){		// history for gateway undefined?
		this[gateway] = new Array();	// generate history for gateway
	}
	else if(this[gateway].length >= wsaggrHistorySize){	// maximum size of history reached ?
		this[gateway].reverse();
		this[gateway].pop();
		this[gateway].reverse();
	}
	this[gateway].push(topology);		// add topology name on top
	for(var i = this[gateway].length - 2; i >= 0; i--){
		if(this[gateway][i] == topology){	// check if topology is in history twice
			for(; i < this[gateway].length - 1; i++){		// shift history
				this[gateway][i] = this[gateway][i+1];
			}
			this[gateway].pop();
			break;
		}
	}
	this.updateDom();
};

WsaggrGatewayHistory.prototype.updateDom = function(){
	var gateway = $("#wsaggrGatewayURL").val();
	var output = "";
	if(this[gateway] != undefined){
		this[gateway].reverse();
		for(var i = 0; i < this[gateway].length; i++){
			output += "<a href='#' class='wsaggrHistorylink'>" + this[gateway][i] + "</a><br/>";
		}
		this[gateway].reverse();
	}
	$("#historyContainer").html(output);
	$("a.wsaggrHistorylink").click(function(){
		$("#topologyName").attr("value", $(this).text());
		wsaggrLoadTopology();
	});
};

