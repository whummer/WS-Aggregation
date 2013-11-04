var wsaggrSessionID = null;
var wsaggrSessionUser = null;
var wsaggrSessionExpiry = null;

function wsaggrLogin() {
	var url = $("#wsaggrGatewayURL").val();
	var username = $("#wsaggrUsername").val();
	var passhash = hex_md5($("#wsaggrPassword").val());
	var request = 
			"<tns:login xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
				"<username>" + username + "</username>" +
				"<passhash>" + passhash + "</passhash>" +
			"</tns:login>";

	wsaggrSetLoading(true);
	invokeSOAP(url, request, function(result) {
		result = stringToXML(result);
		var body = getBodyFromSoapEnvelope(result);
		var success = wsaggrGetChildElements(body, "success")[0];
		wsaggrSetLoading(false);
		if(success && success.textContent && success.textContent == "true") {
			wsaggrSessionID = wsaggrGetChildElements(body, "sessionID")[0].textContent;
			wsaggrSessionUser = username;
			wsaggrSessionExpiry = wsaggrGetChildElements(body, "expiry")[0].textContent;
			wsaggrDoLogin();
		} else if(success && success.textContent && success.textContent == "false") {
			wsaggrDoLogout();
			alert("Invalid username/password combination. Please try again.");
		} else {
			alert("Unable to login at the specified gateway. Please check gateway URL or try again later.");
		}
	});
}

function wsaggrToggleLogin() {
	if(wsaggrSessionID != null) {
		wsaggrDoLogout();
	}
}
function wsaggrDoLogin() {
	$('#tabs').tabs({ disabled: [2,7] });
	$('#tabs').tabs('select', 0);
	$('#wsaggrLoginInfo').html("User: " + wsaggrSessionUser);
	$('#wsaggrAccountLink').html("Logout");
}
function wsaggrDoLogout() {
	$('#tabs').tabs({ disabled: [0,1,2,3,4,5,6] });
	$('#tabs').tabs('select', 7);
	$('#tabs').tabs({ disabled: [0,1,2,3,4,5,6] });
	wsaggrSessionID = null;
	wsaggrSessionUser = null;
	wsaggrSessionExpiry = null;
	$('#wsaggrLoginInfo').html("");
	$('#wsaggrAccountLink').html("Login");
}

function wsaggrShowRegister() {
	$("#registerDialog").dialog('open');
	var recaptchaPublicKey = RECAPTCHA_PUBLIC_KEY;
	if(recaptchaPublicKey && recaptchaPublicKey != "") {
		var recaptchaDivId = "wsaggrCaptcha";
		Recaptcha.create(
			recaptchaPublicKey,
			recaptchaDivId,
			{
				theme: "red",
				callback: Recaptcha.focus_response_field
			}
		);
	}
}

function wsaggrRegister() {
	var username = $("#wsaggrRegUsername").val();
	var email = $("#wsaggrRegEmail").val();
	var passhash = hex_md5($("#wsaggrRegPassword").val());
	var captchaChall = Recaptcha.get_challenge();
	var captchaResp = Recaptcha.get_response();
	var url = $("#wsaggrGatewayURL").val();
	var request = 
		"<tns:register xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" +
			"<username>" + username + "</username>" +
			"<email>" + email + "</email>" +
			"<passhash>" + passhash + "</passhash>" +
			"<captchaChallenge>" + captchaChall + "</captchaChallenge>" +
			"<captchaResponse>" + captchaResp + "</captchaResponse>" +
		"</tns:register>";

	wsaggrSetLoading(true);
	invokeSOAP(url, request, function(result) {
		result = stringToXML(result);
		var body = getBodyFromSoapEnvelope(result);
		var success = wsaggrGetChildElements(body, "success")[0];
		wsaggrSetLoading(false);
		if(success && success.textContent && success.textContent == "true") {
			alert("Registration successful. You may now login.");
			$("#registerDialog").dialog('close');
		} else {
			var msg = wsaggrGetChildElements(body, "message")[0];
			alert("Registration was not successful. Error details: \n\n" + (msg ? msg.textContent : "n/a"));
		}
	});
	
	Recaptcha.reload();
}

function wsaggrGetAuthSOAPHeaders() {
	var res = new Array();
	if(wsaggrSessionUser == null || wsaggrSessionID == null) {
		return res;
	}
	res.push("<tns:username xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" + wsaggrSessionUser + "</tns:username>");
	res.push("<tns:sessionID xmlns:tns=\"http://infosys.tuwien.ac.at/WS-Aggregation\">" + wsaggrSessionID + "</tns:sessionID>");
	return res;
}