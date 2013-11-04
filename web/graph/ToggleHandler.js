
function ToggleHandler(imageId, containerId){
	this.imageId = imageId;
	this.containerId = containerId;
	if($("#" + this.imageId).rotateLeft)
		$("#" + this.imageId).rotateLeft(90);
}

ToggleHandler.prototype.imageId;
ToggleHandler.prototype.containerId;
ToggleHandler.prototype.toggle = function (degrees) {
	$("#" + this.imageId).rotateLeft(degrees);
	$("#" + this.containerId).toggle("blind", {}, "fast");
};
