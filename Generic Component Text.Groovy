/*
Generic Component Text

  
Note Parent driver has a send command to send text to the MonaLisa board.

	Copyright 2016, 2017, 2018, 2019 Hubitat Inc. All Rights Reserved
	2019-02-25 2.1.11 ME
        -created Text Component/driver
    2019-02-23 2.1.10 Dan Ogorchock
        -created voltage sensor driver based on Hubitat's Dimmer example code
    2019-09-07 2.1.5 maxwell
        -refactor declarations
	2018-12-15 maxwell
	    -initial pub
		

*/
metadata {
    definition(name: "Generic Component Text", namespace: "hubitat", author: "ME", component: true) {
        capability "ColorTemperature"  //Attributes colorname - STRING  
            attribute   "ColorTemperature", "string"
        capability "Refresh"
		capability "Configuration"
		
		command "sendCommand", ["string"] //allows arbitrary command to be sent		not on hub yet
		
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List <Map> description) {
    description.each {
        if (it.name in ["text"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}


void refresh() {
    parent?.componentRefresh(this.device)
}