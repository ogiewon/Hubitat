/**
*   
*	File: Pushover_Driver.groovy
*	Platform: Hubitat
*   Modification History:
*       Date       Who            	What
*		2018-03-11 Dan Ogorchock  	Modified/Simplified for Hubitat
*		2018-03-23 Stephan Hackett	Added new preferences/features
*
*   Inspired by original work for SmartThings by: Zachary Priddy, https://zpriddy.com, me@zpriddy.com
*
*  Copyright 2018 Dan Ogorchock
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
*
*/
def version() {"v1.0.20180324"}

preferences {
    input("apiKey", "text", title: "API Key:", description: "Pushover API Key")
  	input("userKey", "text", title: "User Key:", description: "Pushover User Key")
    if(getValidated()){
  		input("deviceName", "enum", title: "Device Name (Blank = All Devices):", description: "", multiple: true, required: false, options: getValidated("deviceList"))
  		input("priority", "enum", title: "Default Message Priority (Blank = NORMAL):", description: "", defaultValue: "0", options:[["-1":"LOW"], ["0":"NORMAL"], ["1":"HIGH"]])
  		input("sound", "enum", title: "Notification Sound (Blank = App Default):", description: "", options: getSoundOptions())
  		input("url", "text", title: "Supplementary URL:", description: "")
    	input("urlTitle", "text", title: "URL Title:", description: "")
    }
}

metadata {
  	definition (name: "Pushover", namespace: "ogiewon", author: "Dan Ogorchock") {
    	capability "Notification"
    	capability "Actuator"
    	capability "Speech Synthesis"
  	}
}

def installed() {
 	initialize()
}

def updated() {
 	initialize()   
}

def initialize() {
    state.version = version()
}

def getValidated(type){
    if(type=="deviceList"){log.debug "Generating Device List..."}
	else {log.debug "Validating Keys..."}
    
    def validated = false
	
    def postBody = [
    	token: "$apiKey",
    	user: "$userKey",
        device: ""
  	]
    
    def params = [
    	uri: "https://api.pushover.net/1/users/validate.json",
    	body: postBody
  	]
    
    if ((apiKey =~ /[A-Za-z0-9]{30}/) && (userKey =~ /[A-Za-z0-9]{30}/)) {
        try{
        	httpPost(params){response ->
      			if(response.status != 200) {
        			sendPush("ERROR: 'Pushover Me When' received HTTP error ${response.status}. Check your keys!")
        			log.error "Received HTTP error ${response.status}. Check your keys!"
      			}
      			else {
                    if(type=="deviceList"){
                        log.debug "Device list generated"
                        deviceOptions = response.data.devices
                    }
                    else {
                        log.debug "Keys validated"
                        validated = true
                    }
      			}
    		}
        }
        catch (Exception e) {
        	log.error "An invalid key was probably entered. PushOver Server Returned: ${e}"
		} 
    }
    else {
    	// Do not sendPush() here, the user may have intentionally set up bad keys for testing.
    	log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
  	}
    if(type=="deviceList") return deviceOptions
    return validated
    
}

def getSoundOptions() {
	log.debug "Generating Notification List..."
	def myOptions =[]
    httpGet(uri: "https://api.pushover.net/1/sounds.json?token=${apiKey}"){response ->
   		if(response.status != 200) {
       		sendPush("ERROR: 'Pushover Me When' received HTTP error ${response.status}. Check your keys!")
       		log.error "Received HTTP error ${response.status}. Check your keys!"
   		}
   		else {
       		log.debug "Notification List Generated"
       		mySounds = response.data.sounds
            mySounds.each {eachSound->
	            myOptions << ["${eachSound.key}":"${eachSound.value}"]
            }
   		}
   	}   
  	return myOptions
}

def speak(message) {
    deviceNotification(message)
}

def deviceNotification(message) {
	if(message.startsWith("[L]")){ 
        customPriority = "-1"
        message = message.minus("[L]")
    }
    if(message.startsWith("[N]")){ 
        customPriority = "0"
        message = message.minus("[N]")
    }
    if(message.startsWith("[H]")){
        customPriority = "1"
        message = message.minus("[H]")
    }
    if(customPriority){ priority = customPriority}

  	// Define the initial postBody keys and values for all messages
  	def postBody = [
    	token: "$apiKey",
    	user: "$userKey",
   		message: "${message}",
    	priority: priority,
    	sound: sound,
        url: url,
        device: deviceName,
        url_title: urlTitle
  	]

  	if (deviceName) { log.debug "Sending Message: ${message} Priority: ${priority} to Device: $deviceName"}
  	else {log.debug "Sending Message: [${message}] Priority: [${priority}] to [All Devices]"}

  	// Prepare the package to be sent
  	def params = [
    	uri: "https://api.pushover.net/1/messages.json",
    	body: postBody
  	]

  	if ((apiKey =~ /[A-Za-z0-9]{30}/) && (userKey =~ /[A-Za-z0-9]{30}/)) {
    	httpPost(params){response ->
      		if(response.status != 200) {
        		sendPush("ERROR: 'Pushover Me When' received HTTP error ${response.status}. Check your keys!")
        		log.error "Received HTTP error ${response.status}. Check your keys!"
      		}
      		else {
        		log.debug "Message Received by Pushover Server"
      		}
    	}
  	}
  	else {
    	// Do not sendPush() here, the user may have intentionally set up bad keys for testing.
    	log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
  	}
}
