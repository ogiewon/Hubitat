/**
 *  Virtual Motion with Switch
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-motion-switch.src/virtual-motion-switch.groovy
 *
 *
 *
 *  Copyright 2018 Daniel Ogorchock
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
 *    2018-11-03  Dan Ogorchock  Original Creation
 *    2020-01-25  Dan Ogorchock  Added ImportURL metadata
 *    2021-01-07  Dan Ogorchock  Added auto off feature, Actuator Capability, and improved logic
 * 
 */

metadata {
    definition (name: "Virtual Motion Sensor with Switch", namespace: "ogiewon", author: "Daniel Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-motion-switch.src/virtual-motion-switch.groovy") {
        capability "Sensor"
		capability "Actuator"
        capability "Motion Sensor"
        capability "Switch"
		
        command "active"
        command "inactive"
    }
	preferences {
        input name: "reversed", type: "bool", title: "Reverse Action", defaultValue: false, required: true
		input name: "autoOffDuration", type: "enum", title: "Auto turn off", defaultValue: 0, required: true, multiple: false, options:[0:"Disabled", 1:"1 second", 2:"2 seconds", 5:"5 seconds"]
	}
}

def active(){
    if (reversed) off()
    else on()
}

def inactive(){
    if (reversed) on()
    else off()
}

def on(){
    sendEvent(name: "switch", value: "on")
	if(reversed==true) motionVal = "inactive"
	else motionVal = "active"
	sendEvent(name: "motion", value: motionVal)
	if (autoOffDuration.toInteger() > 0) runIn(autoOffDuration.toInteger(), 'off')
}

def off(){
    sendEvent(name: "switch", value: "off")
	if(reversed==true) motionVal = "active"
	else motionVal = "inactive"
	sendEvent(name: "motion", value: motionVal)
}

def installed(){
	initialize()
}

def initialize(){
	off()
}

def updated(){
    runIn(1, 'updatedDelayed')  //avoids requiring user to refresh device details page to see the change
}

def updatedDelayed() {
    //handle possibility of the user changing the 'reversed' user preference
	if (device.currentValue("switch") == "on") on()
	else off()    
}
