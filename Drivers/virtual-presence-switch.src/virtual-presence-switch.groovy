/*
 * Virtual Presence with Switch
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-presence-switch.src/virtual-presence-switch.groovy
 *
 *  Copyright 2018 Daniel Ogorchock
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
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
 *    2018-08-04  Dan Ogorchock  Original Creation
 *    2018-11-18  Dan Ogorchock  Added arrived() and departed() custom commands
 *    2020-01-25  Dan Ogorchock  Added ImportURL metadata
 * 
 */

metadata {
    definition (name: "Virtual Presence with Switch", namespace: "ogiewon", author: "Daniel Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-presence-switch.src/virtual-presence-switch.groovy") {
        capability "Sensor"
        capability "Presence Sensor"
        capability "Switch"
		
        command "arrived"
        command "departed"
    }   
}

def arrived() {
	on()
}

def departed() {
	off()
}

def on() {
    sendEvent(name: "presence", value: "present")
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "switch", value: "off")
}

def installed() {
}
