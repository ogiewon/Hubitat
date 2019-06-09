/**
 *  Google Home Assistant Relay
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/f82fb4fd5dac921e5093eedc0d55620afac25e66/Drivers/google-home-assistant-relay.src/google-home-assistant-relay.groovy
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
 *    2019-04-14  Dan Ogorchock  Added support for Swedish characters - thanks @chrbratt!
 *    2019-06-09  Dan Ogorchock  Added importUrl to definition
 *
 *    Credit goes to Greg Hesp's work on the SmartThings platform as a starting point for this version!
 */
metadata {
	definition (name: "Google Home Assistant Relay", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/f82fb4fd5dac921e5093eedc0d55620afac25e66/Drivers/google-home-assistant-relay.src/google-home-assistant-relay.groovy") {
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
    message = message.replaceAll("%20", " ")
    message = message.replace("Å", "\\u00C5")
    message = message.replace("å", "\\u00E5")
    message = message.replace("Ä", "\\u00C4")
    message = message.replace("ä", "\\u00E4")
    message = message.replace("Ö", "\\u00D6")
    message = message.replace("ö", "\\u00F6")
    message = message.replace("Ø", "\\u00D8")
    message = message.replace("Æ", "\\u00C6")
    message = message.replace("æ", "\\u00E6")
    message = message.replace("ø", "\\u00F8")
	
    def command = "/customBroadcast?text="
    def suffix = ""
    
    if(message.startsWith("[CC]")){ 
      command = "/custom?command="
      message = message.minus("[CC]")
    }  
    else if(message.startsWith("[CCC]")){ 
      command = "/custom?command="
      suffix = "&converse=true"
      message = message.minus("[CCC]")
    } 
    
    def text = URLEncoder.encode(message, "UTF-8");
    
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
    speak(message)
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
