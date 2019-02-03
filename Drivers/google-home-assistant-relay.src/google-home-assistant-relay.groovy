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
 *    2018-07-01  Dan Ogorchock  Add support for Custom Commands - prefix string with [CC] to POST /custom?command= instead of /customBroadcast?text=
 *    2018-07-02  Dan Ogorchock  Add support for Confirmation - prefix sting with [CCC] to POST /custom?command=<your command>&converse=true
 *    2018-10-18  Dan Ogorchock  Reduced debug logging
 *    2019-02-03  Ryan Casler    Added replaceAll for removing "%20" as an artifact of the MakerAPI.
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
    //log.debug "Parsing ${description}"
}

def speak(message) {
    def command = "/customBroadcast?text="
    def suffix = ""
    
    if(message.startsWith("[CC]")){ 
      command = "/custom?command="
      message = message.minus("[CC]")
	  message = message.replaceAll("%20", " ")
    }  
    else if(message.startsWith("[CCC]")){ 
      command = "/custom?command="
      suffix = "&converse=true"
      message = message.minus("[CCC]")
	  message = message.replaceAll("%20", " ")
    } 
    
    def text = URLEncoder.encode(message.replaceAll("%20"," "), "UTF-8");
    
    if (suffix == "") {
        //log.debug "${command}${text}"
        httpPostJSON("${command}${text}")  
    }
    else {
        //log.debug "${command}${text}${suffix}"
        httpPostJSON("${command}${text}${suffix}")
    }
}

def deviceNotification(message) {
    speak(message.replaceAll("%20"," "))
}

def httpPostJSON(path) {
    def hostUri = "${deviceIP}:${devicePort}"
    //log.debug "Sending command ${path} to ${hostUri}"
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
