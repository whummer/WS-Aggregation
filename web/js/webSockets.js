if (!window.WebSocket) {
	window.WebSocket = window.MozWebSocket;
	if (!window.WebSocket)
		alert("WebSocket not supported by this browser");
}

function $() {
	return document.getElementById(arguments[0]);
}
function $F() {
	return document.getElementById(arguments[0]).value;
}

function getKeyCode(ev) {
	if (window.event)
		return window.event.keyCode;
	return ev.keyCode;
}

function dump(obj) {
	string = "";
	for (prop in obj) {
		string += prop + " = " + obj[prop] + "\n";
	}
	return string;
}

var wsaggr = {
	join : function() {
		var location = document.location.toString().replace('http://', 'ws://')
				.replace('https://', 'wss://')
				+ "chat";
		location = "ws://localhost:8082/chat";
		this._ws = new WebSocket(location, "chat");
		this._ws.onopen = this.onopen;
		this._ws.onmessage = this.onmessage;
		this._ws.onclose = this.onclose;
	},

	onopen : function() {
		$('join').className = 'hidden';
		$('joined').className = '';
		$('phrase').focus();
		this._queries = new Array();
		this._messageID = null;
	},

	send : function(message) {
		if (this._ws) {
			this._ws.send(message);
		}
	},

	exit : function() {
		this._ws = null;
		this._clientID = null;
		$('join').className = '';
		$('joined').className = 'hidden';
		$('chat').innerHTML = '';
	},

	clear : function() {
		this._messageID = null;
		this._queries = null;
		this._requestID = null;
	},
	
	logout : function() {
		wsaggr.send("logout");
		wsaggr.exit();
	},

	/*
	 * 	<message type="request" requestID="someid" clientID="clientID" messageID="messageID">
	 * 		<queries>
	 * 			<r>query</r> 
	 * 		</queries>
	 * 		<input>...data...</input> 
	 * 	</message>
	 */
	
	processReq : function(request) {
		if (window.DOMParser) {
			parser = new DOMParser();
			var xmlDoc = parser.parseFromString(request, "text/xml");
		} else {// Internet Explorer
			xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
			xmlDoc.async = false;
			xmlDoc.loadXML(request);
		}

		var x = xmlDoc.getElementsByTagName("message")[0];
		this._requestID = x.getAttribute("requestID"); 
		this._messageID = x.getAttribute("messageID");
		this._clientID = x.getAttribute("clientID");

		var qlist = x.getElementsByTagName("queries")[0].childNodes;
		this._queries = new Array();
		for(var i=0; i<qlist.length; i++) {
			this._queries[i] = qlist[i].childNodes[0].nodeValue;
		}

		// TODO multiple inputs
		//var input = x.getElementsByTagName("input")[0];
		var input = null;
		if(window.DOMParser) {
			input = (new XMLSerializer()).serializeToString(x.getElementsByTagName("input")[0]);
		}
		else {
			input = x.getElementsByTagName("input")[0].xml;
		}
		
		wsaggr.postMessage("Input is: \n" + input);
		wsaggr.computeResult(input);
		wsaggr.clear();
	},
	
	computeResult : function(input) {
		var result = wsaggr.callXQuery(input);
		var msg = "<message type=\"result\" clientID=\"" + this._clientID
		+ "\" requestID=\"" + this._requestID + "\" messageID=\"" + this._messageID + "\"><result>"
		+ result + "</result></message>";
		
		wsaggr.send(msg);
		wsaggr.postMessage("Result is: " + msg);
	},
	
	
	callXQuery : function(input) {
		var result = "foobar";
		
		//TODO compute actual result
		/*var xh = XQueryHelper();
		xh.getXQueryResult(
			this._queries[0], 
			function (text) {
            	alert('Got it!: ' + text);
            	result = text;
        	}
		);*/
		
		return result;
	},

	postMessage : function(m) {
		var chat = $('chat');
		var spanText = document.createElement('span');
		spanText.className = 'text';
		spanText.innerHTML = m;
		var lineBreak = document.createElement('br');
		chat.appendChild(spanText);
		chat.appendChild(lineBreak);
		chat.scrollTop = chat.scrollHeight - chat.clientHeight;
	},


	onmessage : function(m) {
		if (m.data) {
			if (m.data.charAt(0) == "<") {
				if(this._messageID == null) {
					// TODO process multiple message requests
					wsaggr.processReq(m.data);
				}
				else {
					wsaggr.send("Already processing a request, can't process another at this time.");
				}
			}
			
			if (m.data.lastIndexOf("LoginID") === 0) {
				this._clientID = m.data.substring(9);
				wsaggr.postMessage(m.data);
				return;
			}

		}
	},

	onclose : function(m) {
		wsaggr.exit();
	}
};

function init() {
	$('joinB').onclick = function(event) {
		wsaggr.join();
		return false;
	};
	$('phrase').setAttribute('autocomplete', 'OFF');
	$('phrase').onkeyup = function(ev) {
		var keyc = getKeyCode(ev);
		if (keyc == 13 || keyc == 10) {
			wsaggr.computeResult($F('phrase'));
			$('phrase').value = '';
			return false;
		}
		return true;
	};
	$('sendB').onclick = function(event) {
		wsaggr.computeResult($F('phrase'));
		$('phrase').value = '';
		return false;
	};
	$('logoutB').onclick = function(event) {
		wsaggr.logout();
		return false;
	};
}