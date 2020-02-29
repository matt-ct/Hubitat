/*
2/29/20/3PM good
 Lisa ZigBee Parent.groovy
 Expects:
Generic Component Voltage Sensor.Groovy 
Generic Component Text.Groovy 
Generic Component Carbon Dioxide Sensor.Groovy 
Generic Component Illuminance Sensor.Groovy 
Generic Component Relative Humidity Sensor.Groovy  these all need to be in your driver's code as there own file with the parent
Note I don't use any of the MonaLisa built in I/O, The code is here though.


Matt's Notes:

This is a Hubitat device driver for Andy Haas's Monalisa ZigBee board communicating to an Arduino Uno R3.
Driver is from Andy Haas's Hubitat_driver_HaasTIThing.Groovy and Dan G Ogorchock's HaasTI_Zigbee_Parent.groovy drivers.

The MonaLisa board:
2 digital inputs
4 digital outputs w/leds
4 Analog inputs -0.4V to 2.8V scaled to 8191 (2^13)
ZigBee radio to comunicate with hubitat
Serial port to comunicate with Arduino. 16 char max
The MonaLisa board can be standalone or connected to any serial device.  See Andy's MonaLisa cc2530 Guide.pdf

Operation:
MonaLisa board sends hub a string. (string is also eccoed to the serial port)
Hub then sends the string to this driver to be parsed.
Parced data is then sent to hub to update the parent device and child devices.
Hub sends driver updates from parent or child devices.  Device driver then converts to string to be sent to MonaLisa board.
A string recieved by the MonaLisa board from the hub is eccoed to serial port.
A string recieved by the MonaLisa board from the serial port is eccoed to hub.


 * Dan's butchered driver :)
 *
 *  HaasTI_Zigbee_Parent.groovy  // Old Name
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/haasti-zigbee-parent.src/haasti-zigbee-parent.groovy
 *
 *  Copyright 2020 Dan G Ogorchock & Andy Haas
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2020-02-03  Dan Ogorchock  Original Creation - based on the original HaasTI Zigbee driver written by Andy Haas
 *	  Feb 21		ME				Butchered to add Arduino childern
 *    2020-02-23  Dan Ogorchock  Switched to use Voltage Sensors instead of Temperature Sensors
 *	  Feb 24		ME			 Incorperated Dan's Voltage changes.  
 *    Feb 29        ME           Added Arduino support and child drivers
 */

import hubitat.device.HubAction
import hubitat.device.HubMultiAction
import hubitat.device.Protocol

metadata {
	definition (name: "Lisa ZigBee Parent", namespace: "ogiewon", author: "Dan Matt", importUrl: "Local") {
	  capability "Actuator"
	  capability "Configuration"
	  capability "Switch"
      capability "Pushable Button"
        
        command "sendCommand", ["string"] //allows arbitrary command to be sent

        // command "deleteAllChildDevices"  //used for debugging driver only
        //command "gettext"                //used for debugging driver only
        //attribute "text","string"        //used for debugging driver only
        
        fingerprint inClusters: "0000,0003,0004,0005,0006", outClusters: "0000", profileId: "0104", manufacturer: "TexasInstruments", model: "TI0001", deviceJoinName: "HaasTI Thing"
	}
    // Preferences
	preferences {
        input name: "diginPullUpDown", type: "enum", title: "Digital Inputs 1 and 2 Pull Mode", options: ["pullup":"Pull Up","pulldown":"Pull Down"], defaultValue: "pullup", required: true
        input name: "input1PullEnable", type: "enum", title: "Digital Input 1 Pull Enable/Disable", options: ["pull1":"Pull","nopull1":"No Pull"], defaultValue: "pull1", required: true
        input name: "input2PullEnable", type: "enum", title: "Digital Input 2 Pull Enable/Disable", options: ["pull2":"Pull","nopull2":"No Pull"], defaultValue: "pull2", required: true
        
        input name: "input1PollingEnable", type: "bool", title: "Enable Polling of Input 1", defaultValue: true, required: true
        input name: "input2PollingEnable", type: "bool", title: "Enable Polling of Input 2", defaultValue: true, required: true
        input name: "input3PollingEnable", type: "bool", title: "Enable Polling of Input 3 (HW v1.2+ only)", defaultValue: true, required: true     
        
        input name: "pingInterval", type: "enum", title: "Ping Interval", options: ["ping_0":"Disabled","ping_6":"1 min","ping_12":"2 min","ping_30":"5 min","ping_90":"15 min","ping_360":"1 hour"], defaultValue: "ping_12", required: true       
        input name: "gpioPollingInterval", type: "enum", title: "GPIO Polling Interval", options: ["1":"0.1 sec","2":"0.2 sec","5":"0.5 sec","10":"1 sec","50":"5 sec","100":"10 sec"], defaultValue: "5", required: true
        input name: "adcDeadband", type: "number", title: "Analog Input Deadband in counts", defaultValue: 100, range: "1..8191", required: true

        input name: "adc0PollingEnable", type: "bool", title: "Enable Polling of adc0", defaultValue: true, required: true
        input name: "adc0Slope", type: "decimal", title: "Slope for adc0 Engineering Units Conversion", defaultValue: 1.0, required: true
        input name: "adc0Offset", type: "decimal", title: "Offset for adc0 Engineering Units Conversion", defaultValue: 0.0, required: true

        input name: "adc1PollingEnable", type: "bool", title: "Enable Polling of adc1", defaultValue: true, required: true
        input name: "adc1Slope", type: "decimal", title: "Slope for adc1 Engineering Units Conversion", defaultValue: 1.0, required: true
        input name: "adc1Offset", type: "decimal", title: "Offset for adc1 Engineering Units Conversion", defaultValue: 0.0, required: true

        input name: "adc4PollingEnable", type: "bool", title: "Enable Polling of adc4", defaultValue: true, required: true
        input name: "adc4Slope", type: "decimal", title: "Slope for adc4 Engineering Units Conversion", defaultValue: 1.0, required: true
        input name: "adc4Offset", type: "decimal", title: "Offset for adc4 Engineering Units Conversion", defaultValue: 0.0, required: true

        input name: "adc5PollingEnable", type: "bool", title: "Enable Polling of adc5", defaultValue: true, required: true
        input name: "adc5Slope", type: "decimal", title: "Slope for adc5 Engineering Units Conversion", defaultValue: 1.0, required: true
        input name: "adc5Offset", type: "decimal", title: "Offset for adc5 Engineering Units Conversion", defaultValue: 0.0, required: true
		
		input name: "adc6PollingEnable", type: "bool", title: "Enable Polling of adc6", defaultValue: true, required: true                     
        input name: "adc6Slope", type: "decimal", title: "Slope for adc6 Engineering Units Conversion", defaultValue: 2.0, required: true
        input name: "adc6Offset", type: "decimal", title: "Offset for adc6 Engineering Units Conversion", defaultValue: 0.0, required: true
        
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
	if (logEnable) log.debug "description is $description"
    Map map = [:]
	def event = zigbee.getEvent(description)
	if (event) {
        if (txtEnable) log.debug "parsed zigbee event = '${event}"
		sendEvent(event)
	}
    else if (description?.startsWith("catchall:")) {
    	 if (logEnable) log.debug "catchall is $description"
    }
    else if (description?.startsWith("read attr -")) {
		def descMap = zigbee.parseDescriptionAsMap(description)
		if (logEnable) log.debug "Desc Map: $descMap"
		if (descMap.clusterInt == 0) {
            def text = descMap.value                     // This is where text from Lisa comes back to Hub ME
            if (txtEnable) log.info "parsing '${text}'"
            if (text.startsWith("ping.")) return
            
          //Update child devices
            def index = text.replace(".","").trim()  //remove trailing padding periods

            //Handle switch updates (Digital Outputs)
            if ((index.size() == 3) && (index.startsWith("on"))) {
                index = index.replace("on","")
                index = index.toInteger()
                fetchChild("Switch", index).parse([[name:"switch", value:"on", descriptionText:"switch set to on"]])
            } 
            else if ((index.size() == 4) && (index.startsWith("off"))) {
                index = index.replace("off","")
                index = index.toInteger()
                fetchChild("Switch", index).parse([[name:"switch", value:"off", descriptionText:"switch set to off"]])
            } 
            //Handle digital input 'button' updates as buttons (parent device) and contact sensors (child devices)
            else if(text.startsWith("Button")) {
                index = index.replace("Button","")
                def value
                if (index.contains("yes")) {
                    value = "closed"
                    index = index.replace("yes","")
                } else if (index.contains("not")) {
                    value = "open"
                    index = index.replace("not","")
                }  
                index = index.trim().toInteger()
                //update contact sensor child device
                fetchChild("Contact Sensor", index).parse([[name:"contact", value: value, descriptionText:"contact set to ${value}"]])
                //create button pushed event
        	    sendEvent(name: "pushed", value: index, isStateChange: true, descriptionText: "button ${index} pushed")
            } 
            //Handle analog input (ADC) updates as voltage sensors (for now) with linear conversion to engineering units
            else if(text.startsWith("0_") || text.startsWith("1_") || text.startsWith("4_") || text.startsWith("5_")) { 
                float slope
                float offset
                float value
                value = Float.parseFloat(index.substring(2))
                index = index.substring(0,1).toInteger()  
                
                switch (index) {
                    case "0":
                        slope = adc0Slope.toFloat()
                        offset = adc0Offset.toFloat()
                        break
                    case "1":
                        slope = adc1Slope.toFloat()
                        offset = adc1Offset.toFloat()
                        break
                    case "4":
                        slope = adc4Slope.toFloat()
                        offset = adc4Offset.toFloat()
                        break
                    case "5":
                        slope = adc5Slope.toFloat()
                        offset = adc5Offset.toFloat()
                        break
	
                    default :
                        log.warn "Unknow ADC index = ${index}"
                        break
                }
                
                value = slope * value + offset
                fetchChild("Voltage Sensor", index).parse([[name:"voltage", value: value, descriptionText:"voltage set to ${value}"]])
            }
			
			//Handle SCD30 updates From Arduino as CO, Te, RH, and (Lu)=Illuminance, and Tx = Text ADDED ME
            else if(text.startsWith("CO") || text.startsWith("Te") || text.startsWith("RH") || text.startsWith("Lu") || text.startsWith("Tx")) {
                float value
                int test1
                
                String hold = index.substring(2)         // returns everything but the first two char
                String index_s = index.substring(0,2)   // Returns first two char of text!!! Need index_s in switch.
                											
                switch (index_s) { 
                    case "CO":
                        value = Float.parseFloat(index.substring(2)) // go figure doesn't like being passed a string had to move into switch because I pass a string for "Text"
                        test1 = value.toInteger()  //get rid of .0
						fetchChild("Carbon Dioxide Sensor", 1).parse([[name:"carbonDioxide", value: test1, descriptionText:"Carbon Dioxide is ${test1}"]])
                        break
                    case "Te":
                        value = Float.parseFloat(index.substring(2)) 
						value = value/100
                        value = value.round(1)
                        fetchChild("Temperature Sensor", 1).parse([[name:"temperature", value: value, descriptionText:"Temperature set to ${test1}"]])
																		 
                        break
                    case "RH":
                        value = Float.parseFloat(index.substring(2)) 
                        value = value/100
                        value = value.round(1) 
                        fetchChild("Relative Humidity Sensor", 1).parse([[name:"humidity", value: value, descriptionText:"Relative Humidity is ${value}"]])
                        break
                    case "Lu":
                        value = Float.parseFloat(index.substring(2))
                        test1 = value.toInteger()  //get rid of .0
                        fetchChild("Illuminance Sensor", 1).parse([[name:"illuminance", value: test1, descriptionText:"Illuminance is ${test1}"]])
                        break
					case "Tx":
                       def now = new Date().format('HH:mm:ss', location.timeZone)  // Add time stamp to text
                        hold = hold + " " + now
                        fetchChild("Text", 1).parse([[name:"text", value: hold, descriptionText:"Text is ${hold}"]]) 
                        if (logEnable) log.debug " Text = $hold"
                        break
                    
                    default :
                        log.warn "Unknown SCD30 text index = ${index}"
                        break
                }
			}
            //Update the Parent's Switch status based on the child switch statuses
            //  If any child switch is one, the Parent will show as on.  
            //  If all child switches are off, the Parent will show as off.
            runIn(1, updateParentSwitchStatus) //delay processing of this code for 1 second to avoid race condition
            
            //Update the parent's custom 'text' attribute
            //sendEvent(name: "text", value: "$text")  //used for debugging driver only
		}
        else {
			log.warn "Not an attribute we can decode"
		}
	} 
	else {
		log.warn "DID NOT PARSE MESSAGE for description : $description"
		if (logEnable) log.debug zigbee.parseDescriptionAsMap(description)
	}
}

def updateParentSwitchStatus() {
    //Update Parent's Switch Status based of status of all 4 child switch devices
    if ((fetchChild("Switch", 1).currentValue("switch") == "off") && (fetchChild("Switch", 2).currentSwitch == "off") && 
        (fetchChild("Switch", 3).currentSwitch == "off") && (fetchChild("Switch", 4).currentSwitch == "off")) {
        sendEvent(name: "switch", value: "off", descriptionText:"all child switches off, parent set to off")
    }
    else {
        sendEvent(name: "switch", value: "on", descriptionText:"child switch on, parent set to on")
    }
}

def off() {
    //update all child switch devices since the master 'off' command was issued
    fetchChild("Switch", 1).parse([[name:"switch", value:"off", descriptionText:"parent all off() called, switch set to off"]])
    fetchChild("Switch", 2).parse([[name:"switch", value:"off", descriptionText:"parent all off() called, switch set to off"]])
    fetchChild("Switch", 3).parse([[name:"switch", value:"off", descriptionText:"parent all off() called, switch set to off"]])
    fetchChild("Switch", 4).parse([[name:"switch", value:"off", descriptionText:"parent all off() called, switch set to off"]])

	zigbee.off()
}

def on() {
    //update all child switch devices since the master 'on' command was issued
    fetchChild("Switch", 1).parse([[name:"switch", value:"on", descriptionText:"parent all on() called, switch set to on"]])
    fetchChild("Switch", 2).parse([[name:"switch", value:"on", descriptionText:"parent all on() called, switch set to on"]])
    fetchChild("Switch", 3).parse([[name:"switch", value:"on", descriptionText:"parent all on() called, switch set to on"]])
    fetchChild("Switch", 4).parse([[name:"switch", value:"on", descriptionText:"parent all on() called, switch set to on"]])
    
    zigbee.on()
}

//def gettext(){ // read some attribute string from the device
//	if (txtEnable) log.info "gettext"
//    //zigbee.readAttribute(0x000, 0x0006) // gets the last thing the device tried to send to us
//    zigbee.readAttribute(0x000, 0x0010) // gets the last command the device heard us send
//}

def sendtodevice(String mystr){
    if (txtEnable) log.info "sending '${mystr}'"
    mystr=mystr.padRight(16,".") // mystr should be 16 bytes!  
    def packed = hubitat.helper.HexUtils.byteArrayToHexString(mystr.getBytes())
    if (logEnable) log.info "sending '${mystr}', packed is ${packed}"
    def commandtosend = "he wattr 0x${device.deviceNetworkId} 8 0x000 0x010 0x42 {10"+packed+"}" // SAMPLELIGHT_ENDPOINT is defined as 8 in device code // the 10 on the end means 16 bytes length
    if (logEnable) log.debug "$commandtosend"
    return commandtosend
}

def sendCommand(String msg) {
    if (txtEnable) log.info "sendCommand - ${msg}"
    sendHubCommand(new HubAction(sendtodevice(msg), Protocol.ZIGBEE))
}


def configure() {
	if (txtEnable) log.info "Configuring Reporting and Bindings."
	zigbee.onOffRefresh() + zigbee.onOffConfig()
}


def installed() {
	if (txtEnable) log.info "Executing 'installed()'"
    updated()
}

def uninstalled() {
    if (txtEnable) log.info "Executing 'uninstalled()'"
    deleteAllChildDevices()
}

def initialize() {
	if (txtEnable) log.info "Executing 'initialize()'"
}

def updated() {
    if (txtEnable) log.info "Executing 'updated()'"
    
    if (logEnable) {
        log.info "Enabling Debug Logging for 30 minutes" 
        runIn(3600,logsOff) // one hour of debug logging
    } else {
        unschedule(logsoff)
    }
    
    //Update the number of buttons attribute required for the Pushable Button Capability
    sendEvent(name: "numberOfButtons", value: 2)
    
    List<String> cmds = []
    
    //If necessary, create each of the child devices 
    fetchChild("Switch", 1)
    fetchChild("Switch", 2)
    fetchChild("Switch", 3)
    fetchChild("Switch", 4)    
    fetchChild("Contact Sensor", 1)
    fetchChild("Contact Sensor", 2)  
    fetchChild("Voltage Sensor", 0)    
    fetchChild("Voltage Sensor", 1)    
    fetchChild("Voltage Sensor", 4)    
    fetchChild("Voltage Sensor", 5)    
	
	//  Children Created for Arduino Added ME
	
	fetchChild("Temperature Sensor", 1)  // will use for SDC30 Temp!
	
	// Custom Generic Component Children
	
	fetchChild("Carbon Dioxide Sensor", 1) 
	fetchChild("Relative Humidity Sensor", 1) 
	fetchChild("Illuminance Sensor", 1) 
	fetchChild("Text", 1)
	
    //get initial values for the contact sensors and analog inputs
    cmds.add sendtodevice("getbutt1")
    cmds.add sendtodevice("getbutt2")
    cmds.add sendtodevice("getadc0")
    cmds.add sendtodevice("getadc1")
    cmds.add sendtodevice("getadc4")
    cmds.add sendtodevice("getadc5")
    
    if (gpioPollingInterval) {
        def cmd = "poll_${gpioPollingInterval.toInteger()}"
        cmds.add sendtodevice(cmd)
    }

    if (input1PollingEnable) {
        cmds.add sendtodevice("pollbutt1") 
    } else {
        cmds.add sendtodevice("nopollbutt1") 
    }
    if (input2PollingEnable) {
        cmds.add sendtodevice("pollbutt2") 
    } else {
        cmds.add sendtodevice("nopollbutt2") 
    }
    if (input3PollingEnable) {
        cmds.add sendtodevice("pollbutt3") 
    } else {
        cmds.add sendtodevice("nopollbutt3") 
    }
    
    if (adc0PollingEnable) {
        cmds.add sendtodevice("polladc0") 
    } else {
        cmds.add sendtodevice("nopolladc0") 
    }
    if (adc1PollingEnable) {
        cmds.add sendtodevice("polladc1") 
    } else {
        cmds.add sendtodevice("nopolladc1") 
    }
    if (adc4PollingEnable) {
        cmds.add sendtodevice("polladc4") 
    } else {
        cmds.add sendtodevice("nopolladc4") 
    }
    if (adc5PollingEnable) {
        cmds.add sendtodevice("polladc5") 
    } else {
        cmds.add sendtodevice("nopolladc5") 
    }
    if (adcDeadband) {
        cmds.add sendtodevice("adcdiff_${adcDeadband.toInteger()}")
    }
    if (diginPullUpDown) {
        cmds.add sendtodevice("${diginPullUpDown}")
    }
    if (input1PullEnable) {
        cmds.add sendtodevice("${input1PullEnable}")
    }
    if (input2PullEnable) {
        cmds.add sendtodevice("${input2PullEnable}")
    }
    if (pingInterval) {
        cmds.add sendtodevice("${pingInterval}")
    }
   
    sendHubCommand(new HubMultiAction(delayBetween(cmds,500), Protocol.ZIGBEE))

}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def fetchChild(String type, int index){    // Create the children if needed ME
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${type}${index}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component ${type}", "${thisId}-${type}${index}", [name: "${device.displayName} ${type}${index}", isComponent: true])
        if (type == "Switch") {
            sendtodevice("off${index}")
        }
    }
    return cd 
}

def deleteAllChildDevices() {
    if (txtEnable) log.info "Uninstalling all Child Devices"
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
       }
}

//child device methods
void componentRefresh(cd) {
    if (txtEnable) log.info "received refresh request from ${cd.displayName}"
}

def componentOn(cd) {
    if (txtEnable) log.info "received on request from DN = ${cd.displayName}, DNI = ${cd.deviceNetworkId}"
    def msg = "on" + cd.deviceNetworkId.substring(cd.deviceNetworkId.size() - 1)
    //sendtodevice(msg)
    sendHubCommand(new HubAction(sendtodevice(msg), Protocol.ZIGBEE))
}

def componentOff(cd) {
    if (txtEnable) log.info "received off request from ${cd.displayName}"
    def msg = "off" + cd.deviceNetworkId.substring(cd.deviceNetworkId.size() - 1)  
    //sendtodevice(msg)
    sendHubCommand(new HubAction(sendtodevice(msg), Protocol.ZIGBEE))
}

