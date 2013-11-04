var wsaggrInputLayouts = {
	"all" : 			{ "prepQuery" : true, "termQuery" : true, "eprLine" : true, "toOneAll" : true, "idLine" : false, "input" : true, "more" : true, "waql" : true, "headers" : true },
	"std" : 			{ "prepQuery" : true, "termQuery" : false, "eprLine" : true, "toOneAll" : true, "idLine" : false, "input" : true, "more" : true, "waql" : true, "headers" : true },
	"const" : 			{ "prepQuery" : false, "termQuery" : false, "eprLine" : false, "toOneAll" : false, "idLine" : false, "input" : true, "more" : false, "waql" : false, "headers" : false },
	"inputWithPrep" : 	{ "prepQuery" : true, "termQuery" : false, "eprLine" : false, "toOneAll" : false, "idLine" : false, "input" : true, "more" : true, "waql" : true, "headers" : false },
}
var wsaggrInputLayout = {
	"CONSTANT" : wsaggrInputLayouts["const"],
	"HTTP_GET" : wsaggrInputLayouts["std"],
	"WEB_SEARCH" : wsaggrInputLayouts["inputWithPrep"],
	"HTTP_POST" : wsaggrInputLayouts["std"],
	"SOAP11" : wsaggrInputLayouts["std"],
	"SOAP12" : wsaggrInputLayouts["std"],
	"SOAP" : wsaggrInputLayouts["std"],
	"SUBSCRIBE" : wsaggrInputLayouts["all"],
	"COMPOSITE" : wsaggrInputLayouts["const"]
}

function wsaggrChangeInputLayout(inputID, newType) {

	var id = inputID;

	/* first, disable/hide all fields.. */
	$("#wsaggrInput" + id + "ToContainer").hide();
	$("#wsaggrInput" + id + "WAQLContainer").hide();
	$("#wsaggrInput" + id + "FeatServContainer").hide();
	$("#wsaggrInput" + id + "Headers").hide();
	$("#wsaggrInput" + id + "MoreContainer").hide();
	$("#showPrepQueryContainer" + id).hide();
	$("#wsaggrPrepQuery" + id).hide();
	$("#wsaggrTermQuery" + id).hide();
	$("#PrepQueryContainer" + id).hide();

	/* now, enable/show specific fields.. */
	var layout = wsaggrInputLayout[newType];

	if(layout.eprLine)
		$("#wsaggrInput" + id + "ToContainer").show();
	if(layout.waql)
		$("#wsaggrInput" + id + "WAQLContainer").show();
	if(layout.eprLine)
		$("#wsaggrInput" + id + "FeatServContainer").show();
	if(layout.headers)
		$("#wsaggrInput" + id + "Headers").show();
	if(layout.more)
		$("#wsaggrInput" + id + "MoreContainer").show();
	if(layout.prepQuery)
		$("#wsaggrPrepQuery" + id).show();
	if(!layout.prepQuery)
		$("#showPrepQueryContainer" + id).show();
	if(layout.termQuery) {
		$("#wsaggrTermQuery" + id).show();
		$("#wsaggrPrepQuery" + id).css("height", "53%");
		$("#wsaggrPrepQuery" + id + "Text").attr("rows", "1");
	} else {
		$("#wsaggrPrepQuery" + id).css("height", "90%");
		$("#wsaggrPrepQuery" + id + "Text").attr("rows", "5");
	}
	if(layout.prepQuery)
		$("#PrepQueryContainer" + id).show();

}

$(document).ready(function() {
	
	//OUTPUT FIELDS
	$("#verticalOutput").click(function() {
		$(this).hide();
		$("#horizontalOutput").show();
		
		$("#response").removeClass("horizontal");
		$("#responseHTML").removeClass("horizontal");
		
		$("#response").addClass("vertical");
		$("#responseHTML").addClass("vertical");
		
		resizeOutputFields();
	});
	
	$("#horizontalOutput").click(function() {
		$(this).hide();
		$("#verticalOutput").show();
		
		$("#response").removeClass("vertical");
		$("#responseHTML").removeClass("vertical");
		
		$("#response").addClass("horizontal");
		$("#responseHTML").addClass("horizontal");
		
		resizeOutputFields();
	});
	
	
	// INPUT ROWS
	$("#wsaggrAddInput").click(function() {
		addNewInput();
	});
	
	$(".removeInput").live('click', function() {
		$(this).parents(".inputRow").remove();
	});

	//HEADERS
	$(".addHeader").live('click', function(event) {
		event.preventDefault();
		
		var inputId = $(this).attr("id").substr(15);
		addNewInputHeader(inputId);
	});
	
	$(".removeHeader").live('click', function(event) {
		event.preventDefault();
		$(this).parents(".headerRow").remove();
	});
	
	
	$(".inputType").live('change', function() {
		var search = /wsaggrInput(\d+)Type/;
		var id = search.exec($(this).attr('id'))[1];
		wsaggrChangeInputLayout(id, $(this).val());
	});
	
	$(".featOrServ").live('change', function() {
		
		var search = /wsaggrInput(\d+)FeatOrServ/;
		var id = search.exec($(this).attr('id'))[1];
		
		var toSelect = $("#wsaggrInput" + id + "To");
		if ($(this).val() == "Endpoint") { 
			toSelect.attr("disabled", "disabled");
		} else {
			toSelect.removeAttr("disabled");
		}
	});
	
	$(".toggleMore").live('click', function() {
		var id = $(this).attr("id").substr("wsaggrInput".length);
		id = id.substr(0, id.length - "toggleMore".length);
		//alert("#wsaggrInput" + id + "more " + $("#wsaggrInput" + id + "more"));
		
		if($(this).text() == 'more') {
			$(this).text('less');
			$("#wsaggrInput" + id + "more").show();
		} else {
			$(this).text('more');
			$("#wsaggrInput" + id + "more").hide();
		}
	});
	
	//Preparation Query
	$(".addPrepQuery").live('click', function(event) {
		event.preventDefault();
		
		var id = $(this).parent().attr("id").substr(22);
		
		$(this).parent().hide();
		$("#PrepQueryContainer" + id).show();
		
	});
	
	$(".removePrepQuery").live('click', function(event) {
		event.preventDefault();
		var parent = $(this).parent().parent().parent().parent().parent();
		var id = parent.attr("id").substr(18);
		parent.hide();
		$("#showPrepQueryContainer" + id).show();
	});
	
	
	$("#queryToggle").click(function(event) {
		event.preventDefault();
		
		if ($("#IntermediateQueryContainer").is(":visible")) {
			$("#wsaggrQueryToggleImg").attr("src", "media/control_fastforward_blue.png");
			$("#IntermediateQueryContainer").hide();
			$("#FinalizationQueryContainer").attr("width", "100%");
		} else {
			$("#wsaggrQueryToggleImg").attr("src", "media/control_rewind_blue.png");
			$("#IntermediateQueryContainer").show();
			$("#FinalizationQueryContainer").attr("width", "50%");
		}
	});
	
	$("#debug").change(function(event) {
		
		if ($(this).is(":checked")) {
			$("#assertionContainer").show();
			$("#tabs").tabs("option", "disabled", []);
		} else {
			$("#assertionContainer").hide();
			$("#tabs").tabs("option", "disabled", [2]);
		}
	});
	
	$("#addAssertion").click(function(event) {
		event.preventDefault();
		addNewAssertion();
	});
	
	$(".removeAssertion").live('click', function(event) {
		event.preventDefault();
		
		$(this).parents(".assertionRow").remove();
	});
	
	$("#showRequest").click(function() {
		var request = wsaggrConstructRequest();
		$("#requestDialogTextarea").val(request);
		$("#requestDialog").dialog('open');
	});

	$("#wsaggrSyncRequest").click(function() {
		var req = $("#requestDialogTextarea").val();
		req = stringToXML(req);
		wsaggrLoadRequest(req);
	});
	
	$("#loadActiveQueries").click(function() {
		loadActiveQueries();
	});
	
	$("#displayActiveQuery").click(function() {
		displayActiveQuery();
	});
	
	$("#wsaggrStorageQueryName").live("focus", function() {
		loadPersistedQueries();
	});
});

function addNewInput(id) {
	if(id && parseInt(id)) {
		id = parseInt(id);
	}
	if(!id) {
		id = $("#nextInputIndex").val();
	}
	if(parseInt("" + id)) {
		$("#nextInputIndex").val(parseInt("" + id) + 1);
	} else {
		$("#nextInputIndex").val("2");
		id = 1;
	}
	
	var newInput = $("#sampleInput").clone();
	newInput.attr("id", "wsaggrInputRow" + id);
	
	var elements = newInput.find('[id*="__id__"]');
	
	for (var i = 0; i < elements.length; i++) {
		var elementId = $(elements[i]).attr("id");
		elementId = elementId.replace(/__id__/, id);
		if(elementId == "wsaggrInput" + id + "ID") {
			$(elements[i]).html("" + id);
		}
		$(elements[i]).attr("id", elementId);
	}

	newInput.show().appendTo("#wsaggrInputsContainer");

	newInput.resizable({
            minHeight: 100,
            minWidth: 700,
            
            /*resize: function(event, ui) {
                ui.size.width = this.offsetWidth - 2;
            },
            stop:  function(event, ui) {
                $(this).css("width", "100%");
                $("#queryTable").css("width", "100%");
            }*/
        });
	newInput.resize();
	
	return id;
}

function addNewInputHeader(inputId) {
	var headerContainer = $("#wsaggrInput" + inputId + "Headers");
	
	var row = $("#nextHeader" + inputId + "Row").val();
	$("#nextHeader" + inputId + "Row").val(parseInt(row) + 1);
	
	var newHeader = $("#sampleHeader").clone();
	newHeader.attr("id", "wsaggrHeaderRow" + inputId + "_" + row);
	var elements = newHeader.find('[id*="__id__"]');
	
	for (var i = 0; i < elements.length; i++) {
	    var elementId = $(elements[i]).attr("id");
	    elementId = elementId.replace(/__id__/, inputId);
	    elementId = elementId.replace(/__row__/, row);
	    $(elements[i]).attr("id", elementId);
	}
	headerContainer.append(newHeader);
	newHeader.show();
	
	return row;
}

function addNewAssertion() {
	id = $("#nextAssertIndex").val();
	$("#nextAssertIndex").val(parseInt(id) + 1);
	
	var newAssertion = $("#sampleAssertion").clone();
	newAssertion.attr("id", "wsaggrAssertionRow" + id);

	var elements = newAssertion.find('[id*="__id__"]');
	
	for (var i = 0; i < elements.length; i++) {
	    var elementId = $(elements[i]).attr("id");
	    elementId = elementId.replace(/__id__/, id);
	    $(elements[i]).attr("id", elementId);
	}
	
	newAssertion.show().appendTo("#wsaggrAssertContainer");
	
	return id;
}

function resizeOutputFields() {
	//resize output fields
	
	var navHeigth = $(".ui-tabs-nav")[0].offsetHeight;
	var tabsHeigth = $("#tabs")[0].offsetHeight * 0.9;
	var windowHeigth = Math.round(tabsHeigth - navHeigth);
	
	if ($("#verticalOutput").css("display") != "none") {
				
		$("#response").css("width", "");
		$("#responseHTML").css("width", "");
		
		$("#response").css("height", Math.round(windowHeigth / 2 * 0.9));
		$("#responseHTML").css("height", Math.round(windowHeigth / 2 * 0.9));
	} else {
		var tabsHeigth = $("#tabs")[0].offsetWidth * 0.85;
		var windowWidth = Math.round(tabsHeigth);
		
		$("#response").css("height", windowHeigth);
		$("#responseHTML").css("height", windowHeigth);
		
		$("#response").css("width", Math.round(windowWidth / 2));
		$("#responseHTML").css("width", Math.round(windowWidth / 2));
	}
}

function loadActiveQueries(resultID) {
	if(resultID != undefined) {
		if(ajaxResults[resultID] != undefined) {
			var env = ajaxResults[resultID];
			if(!env.childNodes)
				env = stringToXML(env);
			var theBody = getBodyFromSoapEnvelope(env);
			var ids = $(theBody).children("queryID");
			$("#activeQueryID").find('option').remove();
			var options = $("#activeQueryID").attr('options');
			options[options.length] = new Option("none", "none", true, true);
			for (var i=0; i < ids.length; i++) {
				var id = $(ids[i]).text();
				options[options.length] = new Option(id, id, true, true);
			}
			delete ajaxResults[resultID];
		} else {
			setTimeout("loadActiveQueries('" + resultID + "')", 500);
		}
		return;
	}
	var requestBody = "<tns:getActiveQueries xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			"</tns:getActiveQueries>";
	var gatewayURL = $("#wsaggrGatewayURL").val();
	var uuid = invokeSOAP(gatewayURL, requestBody);
	loadActiveQueries(uuid);
}

function displayActiveQuery(resultID, clientID) {
	if(resultID != undefined) {
		if(ajaxResults[resultID] != undefined) {
			var env = ajaxResults[resultID];
			delete ajaxResults[resultID];
			if(!env.childNodes)
				env = stringToXML(env);
			var theBody = getBodyFromSoapEnvelope(env);
			var result = $(theBody).children("result")[0];
			var result = $(result).children("result")[0];
			result = xmlToString(result);
			
			$("#response").val(result);
			
			var iframe = $("#responseHTML")[0];	
			var doc = iframe.document;
			if(iframe.contentDocument) {
			    doc = iframe.contentDocument; // For NS6
			} else if(iframe.contentWindow) {
			    doc = iframe.contentWindow.document; // For IE5.5 and IE6
			}
			// Put the content in the iframe
			doc.open();
			doc.writeln(result);
			doc.close();
			
			setTimeout("displayActiveQuery(undefined,'" + clientID + "')", 
					ACTIVE_QUERY_POLL_INTERVAL_MS);
		} else {
			setTimeout("displayActiveQuery('" + resultID + "')", 500);
		}
		return;
	}
	
	var id = $("#activeQueryID").val();
	if(id == "none")
		return;
	var requestBody = "<tns:pollActiveQueryResult xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
		"<topologyID>" + id + "</topologyID>" +
		(clientID != undefined ? ("<clientID>" + clientID + "</clientID>") : "") +
	"</tns:pollActiveQueryResult>";
	var gatewayURL = $("#wsaggrGatewayURL").val();
	var uuid = invokeSOAP(gatewayURL, requestBody);
	displayActiveQuery(uuid);
}

var wsaggrQueryNamesLoadTime = 0;
function loadPersistedQueries(resultID) {
	var timeNow = (new Date()).getTime();
	if(!resultID && ((timeNow - wsaggrQueryNamesLoadTime) < 1000*60)) {
		return;
	}
	wsaggrQueryNamesLoadTime = (new Date()).getTime();
	if(resultID != undefined) {
		if(ajaxResults[resultID] != undefined) {
			var env = ajaxResults[resultID];
			if(!env.childNodes)
				env = stringToXML(env);
			var theBody = getBodyFromSoapEnvelope(env);
			var names = $(theBody).children("requestName");
			var allNames = new Array();
			for (var i=0; i < names.length; i++) {
				var name = $(names[i]).text();
				allNames[allNames.length] = name;
			}
			$("#wsaggrStorageQueryName").autocomplete({
			    source: allNames
			});
			delete ajaxResults[resultID];
		}  else {
			setTimeout("loadPersistedQueries('" + resultID + "')", 500);
		}
		return;
	}
	var pattern = $("#wsaggrStorageQueryName").val() + "%";
	var requestBody = "<tns:getAllPersistedRequests xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
				"<pattern>" + pattern + "</pattern>" +
			"</tns:getAllPersistedRequests>";
	var registryURL = $("#wsaggrRegistryURL").val();
	var uuid = invokeSOAP(registryURL, requestBody);
	loadPersistedQueries(uuid);
}

function wsaggrLoadGatewayURL(resultID) {
	var registryURL = $("#wsaggrRegistryURL").val();
	if(resultID != undefined) {
		if(ajaxResults[resultID] != undefined) {
			var env = ajaxResults[resultID];
			if(!env.childNodes)
				env = stringToXML(env);
			var theBody = getBodyFromSoapEnvelope(env);
			var result = $(theBody).children("result");
			result = $(result.children()[0]);
			result = $(result.children()[0]);
			var result = $(result).text();
			var urls = [
			   result
           	];
			$("#wsaggrGatewayURL").autocomplete({
			    source: urls
			});
			$("#wsaggrGatewayURL").val(result);
				
			delete ajaxResults[resultID];
		}  else {
			setTimeout("wsaggrLoadGatewayURL('" + resultID + "')", 500);
		}
		return;
	}
	var requestBody = 	"<tns:getGateway xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\"/>";
	var uuid = invokeSOAP(registryURL, requestBody);
	$("#wsaggrGatewayURL").val("loading...");
	wsaggrLoadGatewayURL(uuid);
}

function tearDownFramework() {
	var c = confirm("Do you really want to terminate (and restart) ALL running nodes of the framework?");
	if(c) {
		var url = $("#wsaggrRegistryURL").val();
		doTearDownFramework(url);
	}
}

