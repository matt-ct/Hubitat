/*
	Generic Component Relative Humidity Sensor
	
	log.warn "parse(String description) not implemented"
	
	Copyright 2016, 2017, 2018, 2019 Hubitat Inc. All Rights Reserved

    2019-02-23 2.1.10 Dan Ogorchock
        -created relative humidity sensor driver based on Hubitat's Dimmer example code
    2019-09-07 2.1.5 maxwell
        -refactor declarations
	2018-12-15 maxwell
	    -initial pub

*/
metadata {
    definition(name: "Generic Component Relative Humidity Sensor", namespace: "hubitat", author: "Dan Ogorchock", component: true) {
        capability "Relative Humidity Measurement"
        capability "Refresh"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
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

void parse(List<Map> description) {
    description.each {
        if (it.name in ["humidity"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}


void refresh() {
    parent?.componentRefresh(this.device)
}