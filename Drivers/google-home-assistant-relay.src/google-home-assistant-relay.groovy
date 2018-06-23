/**
 *  Google Home Assistant Relay
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
 *    2018-06-22  Dan Ogorchock  Original Creation
 *
 *    Credit goes to Greg Hesp's work on the SmartThings platform as a starting point for this very simplified version!
 */
metadata {
	definition (name: "Google Home Assistant Relay", namespace: "ogiewon", author: "Dan Ogorchock") {
        capability "Speech Synthesis"
    	capability "Notification"
	}

	preferences {
		input(name: "deviceIP", type: "string", title:"Device IP Address", description: "Enter IP Address of your Assistant Relay Server", required: true, displayDuringSetup: true)
		input(name: "devicePort", type: "string", title:"Device Port", description: "Enter Port of your Assistant Relay Server (defaults to 3000)", defaultValue: "3000", required: true, displayDuringSetup: true)
	}
}

def parse(String description) {
    log.debug "Parsing ${description}"
}

def speak(message) {
  def text = URLEncoder.encode(message, "UTF-8");
  httpPostJSON("/customBroadcast?text=${text}")  
}

def deviceNotification(message) {
    speak(message)
}

def httpPostJSON(path) {
    def hostUri = "${deviceIP}:${devicePort}"
    log.debug "Sending command ${path} to ${hostUri}"
    def result = new hubitat.device.HubAction(
            method: "POST",
            path: path,
            headers: [
                HOST: hostUri
            ]
    )
    //log.debug "Request: ${result.requestId}"
    return result
}