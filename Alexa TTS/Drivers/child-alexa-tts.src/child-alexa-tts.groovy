/**
 *  Child Alexa TTS
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
 *    Date        Who             	What
 *    ----        ---             	----
 *    2018-10-20  Dan Ogorchock   	Original Creation
 *    2018-11-18  Stephan Hackett	Added support for Virtual Containers
 *    2019-04-04  Thomas Howard     Added support for get/set volume
 * 
 */

metadata {
    definition (name: "Child Alexa TTS", namespace: "ogiewon", author: "Dan Ogorchock") {
        capability "Speech Synthesis"
		capability "AudioVolume"
		
		command "getVolume"
    }
}

preferences {  
}


def speak(message) {
    log.debug "Speaking message = '${message}'"
    def name = device.deviceNetworkId.split("-")[-1]
	def vId = device.data.vcId
	if(vId) parent.childComm("speakMessage", message, vId)
	else parent.speakMessage(message, name)
    
}

def setVolume(volumeLevel){
	
	def name = device.deviceNetworkId.split("-")[-1]
	def vId = device.data.vcId

	//Bounds check the volume
	volumeLevel = (volumeLevel > 100) ? 100 : volumeLevel;
	volumeLevel = (volumeLevel < 0) ? 0 : volumeLevel;
	volumeLevel = Math.max(Math.min(Math.round(volumeLevel), 100), 0);
	
	log.debug "Setting Volume to '${volumeLevel}'"
	
	if(vId) parent.childComm("setVolume", volumeLevel, vId)	
	else parent.setVolume(volumeLevel, name);
}

def getVolume() {
	def name = device.deviceNetworkId.split("-")[-1]
	def vId = device.data.vcId
	
	log.debug "Getting volume"
	
	if(vId) parent.childComm("getVolume", null, vId)	
	else parent.getVolume(name);
}

def installed() {
    initialize()
}

def updated() {
    initialize()   
}

def initialize() {
}
