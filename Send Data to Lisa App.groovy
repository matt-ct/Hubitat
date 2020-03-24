definition(
                                      
    name: "Send Data to Lisa",
    namespace: "hubitat",
    author: "ME",
    description: "Send Data to a MonaLisa Board to be passed on to a serial device",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this data transfer app", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
																	
			input "tempSensors", "capability.temperatureMeasurement", title: "Select Temperature Sensors", submitOnChange: true, required: true, multiple: true
			input "RHSensors", "capability.relativeHumidityMeasurement", title: "Select Relative Humidity Sensors", submitOnChange: true, required: true, multiple: true
            input "LuxSensors", "capability.illuminanceMeasurement", title: "Select Illuminance/Lux Sensors", submitOnChange: true, required: true, multiple: true
            input "PresSensors", "capability.pressureMeasurement", title: "Select Barametric Pressure Sensors", submitOnChange: true, required: false, multiple: true
            
            // input "WindSensors", "attribute.windSpeedMeasurement", title: "Select Wind Speed Sensors", submitOnChange: true, required: false, multiple: true // not capabilities 
            //input "WDegSensors", "capability.wind_degreeMeasurement", title: "Select Wind Degree Sensors", submitOnChange: true, required: false, multiple: true  //are attributes
            //input "WgusSensors", "capability.wind_gustMeasurement", title: "Select Wind Gust Sensors", submitOnChange: true, required: false, multiple: true
            
            // Output
			input "LisaDev", "capability.configuration", title: "Select configuration to send string to", submitOnChange: true, required: true, multiple: false // only one Lisa
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {                         // device type to get data from when they update. One needed for each type of input?                 
	subscribe(tempSensors, "temperature", handler)
    subscribe(RHSensors, "humidity", handler)
    subscribe(LuxSensors, "illuminance", handler)
    subscribe(PresSensors, "pressure", handler)
    // sendData() // forcing update on open and done with app
}

def sendData() {                // get Data to send to Lisa
	def n = 0
    def str = ""                   // Note MonaLisa board end of line is a ".". Need to remove any decimal points replace with "d" 
	def str2 = ""                 //  and add on one at the end of the string
    tempSensors.each {
        str = it.currentTemperature.toString()             // changing "." to "d" for decimal
        str2 = str.replace(".", "d")                       //Strings in Java are immutable. You'll have to create a new string removing the character you don't want.
        log.info "Sensor temperature = ${n} ${str2}°"     // Sends n and the  temps to the log 
        LisaDev.sendCommand "t${n}${str2}." 
        n = n + 1
	}
    n = 0
    RHSensors.each {
        str = it.currentHumidity.toString()
        log.info "RH Sensor = ${n} ${str}%"  // Sends o and the  RH to the log!
        LisaDev.sendCommand "R${n}${str}."
        n = n + 1
	}
    n = 0
    LuxSensors.each {
        str = it.currentIlluminance.toString()
        log.info "Lux Sensor = ${n} ${str}Lux"  
        LisaDev.sendCommand "L${n}${str}."
        n = n + 1
	}
     n = 0
    PresSensors.each {
        str = it.currentPressure.toString()   // not worried about decimal for pressure  
        str2 = str.replace(".", "")
        log.info "Barametric Pressure Sensor = ${n} ${str2}inHg"  
        LisaDev.sendCommand "B${n}${str2}.)."
        n = n + 1
	}
 }

def handler(evt) {  // on a subscribed change call sendData
    sendData()
}
