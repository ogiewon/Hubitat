/**
 *  HTTP Momentary Switch
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
 *    2018-02-18  Dan Ogorchock  Original Creation
 *
 * 
 */
metadata {
	definition (name: "HTTP Momentary Switch", namespace: "ogiewon", author: "Dan Ogorchock") {
        capability "Switch"
        capability "Momentary"
	}

	preferences {
		input(name: "deviceIP", type: "string", title:"Device IP Address", description: "Enter IP Address of your HTTP server", required: true, displayDuringSetup: true)
		input(name: "devicePort", type: "string", title:"Device Port", description: "Enter Port of your HTTP server (defaults to 80)", defaultValue: "80", required: false, displayDuringSetup: true)
		input(name: "devicePath", type: "string", title:"URL Path", description: "Rest of the URL, include forward slash.", displayDuringSetup: true)
		input(name: "deviceMethod", type: "enum", title: "POST, GET, or PUT", options: ["POST","GET","PUT"], defaultValue: "POST", required: true, displayDuringSetup: true)
	}
}

def parse(String description) {
	log.debug(description)
}

def push() {
    //toggle the switch to generate events for anything that is subscribed
    sendEvent(name: "switch", value: "on", isStateChange: true)
    sendEvent(name: "switch", value: "off", isStateChange: true)
	runCmd(devicePath, deviceMethod)
}

def on() {
	push()
}

def off() {
	push()
}

def runCmd(String varCommand, String method) {
	def localDevicePort = (devicePort==null) ? "80" : devicePort
	def path = varCommand 
	def body = "" 
	def headers = [:] 
    headers.put("HOST", "${deviceIP}:${localDevicePort}")
	headers.put("Content-Type", "application/x-www-form-urlencoded")

	try {
		def hubAction = new hubitat.device.HubAction(
			method: method,
			path: path,
			body: body,
			headers: headers
			)
		log.debug hubAction
		return hubAction
	}
	catch (Exception e) {
        log.debug "runCmd hit exception ${e} on ${hubAction}"
	}  
}