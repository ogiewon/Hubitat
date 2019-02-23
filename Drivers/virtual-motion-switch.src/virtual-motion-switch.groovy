/**
 *  Virtual Motion with Switch
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-motion-switch.src/virtual-motion-switch.groovy
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
 * 
 */

metadata {
	definition (name: "Virtual Motion with Switch", namespace: "ogiewon", author: "Daniel Ogorchock") {
		capability "Sensor"
		capability "Motion Sensor"
        capability "Switch"
	}   
}

def on() {
    sendEvent(name: "motion", value: "active")
    sendEvent(name: "switch", value: "on")
    runIn(3, off)
}

def off() {
    sendEvent(name: "motion", value: "inactive")
    sendEvent(name: "switch", value: "off")
}

def installed() {
}
