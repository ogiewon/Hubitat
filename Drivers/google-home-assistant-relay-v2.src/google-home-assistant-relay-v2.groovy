/**
 *  Google Home Assistant Relay v2
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
 *    2018-12-16  Dan Ogorchock  Original Creation of HUbitat Driver to work with Greg Hesp's Assistant Relay v2
 *    2018-12-18  Dan Ogorchock  Added support for Assistant Relay's PRESETs.  Just add a [P] before the preset you want to use
 *    2018-12-18  Mark Rorem     Added support for specifying a username - default is blank
 *
 *    Credit goes to Greg Hesp's work on the SmartThings platform as a starting point for this very simplified version!
 */
metadata {
	definition (name: "Google Home Assistant Relay v2", namespace: "ogiewon", author: "Dan Ogorchock") {
        capability "Speech Synthesis"
    	capability "Notification"
	}

	preferences {
		input(name: "deviceIP", type: "string", title:"Device IP Address", description: "Enter IP Address of your Assistant Relay Server", required: true, displayDuringSetup: true)
		input(name: "devicePort", type: "string", title:"Device Port", description: "Enter Port of your Assistant Relay Server (defaults to 3000)", defaultValue: "3000", required: true, displayDuringSetup: true)
		input(name: "user", type: "string", title:"Assistant Relay Username", description: "Enter the username for this device", defaultValue: "", required: false, displayDuringSetup: true)
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
    //if (logEnable) log.debug "Parsing ${description}"
}

def speak(message) {
    def myJSON = ""
    
    if(message.startsWith("[CC]")){ 
        message = message.minus("[CC]")
    if (user) {
            myJSON = "{ \"command\": \"${message}\",\"user\": \"${user}\" }"
        } else {
            myJSON = "{ \"command\": \"${message}\" }"
        }
    }  
    else if(message.startsWith("[CCC]")){ 
        message = message.minus("[CCC]")
    	if (user) {
            myJSON = "{ \"command\": \"${message}\",\"user\": \"${user}\",\"converse\": \"true\" }"
        } else {
            myJSON = "{ \"command\": \"${message}\",\"converse\": \"true\" }"
        }
    } 
    else if(message.startsWith("[P]")){ 
        message = message.minus("[P]")
        if (user) {
            myJSON = "{ \"preset\": \"${message}\",\"user\": \"${user}\" }"
        } else {
            myJSON = "{ \"preset\": \"${message}\" }"
        }
    } 
     else {
        if (user) {
            myJSON = "{ \"command\": \"${message}\",\"user\": \"${user}\",\"broadcast\": \"true\" }"
        } else {
            myJSON = "{ \"command\": \"${message}\",\"broadcast\": \"true\" }"
        }
    }

    httpPostJSON(myJSON)  

}

def deviceNotification(message) {
    speak(message)
}

def httpPostJSON(myJSON) {
    
	try {
        if (logEnable) log.debug "Sending ${myJSON} to ${deviceIP}:${devicePort}"

        def headers = [:]
        headers.put("HOST", "${deviceIP}:${devicePort}")
        headers.put("Content-Type", "application/json")
        def method = "POST"
        def path = "/assistant"
        def result = new hubitat.device.HubAction(
            method: method,
            path: path,
            body: myJSON,
            headers: headers
        )
        return result

	} catch (Exception e) {
		log.error "Error = ${e}"
	} 
}

def installed() {
	updated()
}

def updated() {
        if (logEnable) runIn(1800,logsOff)
}
