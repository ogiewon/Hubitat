/**
 *  Child Alexa TTS
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/AlexaTTS/Drivers/child-alexa-tts.src/child-alexa-tts.groovy
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
 *    2020-07-29  nh.schottfam      Remove characters from message text that may cause issues
 *    2020-09-25  lgk               add volume
 *    2020-10-04  lgk               add set volume as command so it can be called from rule machine
 *    2020-11-04  Dan Ogorchock     Refactor volume functionality to use standard Audio Volume capability
 */

metadata {
    definition (name: "Child Alexa TTS", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/AlexaTTS/Drivers/child-alexa-tts.src/child-alexa-tts.groovy") {
        capability "Speech Synthesis"
        capability "AudioVolume"
        capability "Actuator"
    }
    
    preferences {
			input "TTSvolumeLevel", "number", title: "TTS Volume", description: "If defined, will set Alexa volume before each TTS", range: "1..100", displayDuringSetup: false, required: false
    }
}

def updated() 
{ 
    log.debug "updated() called"
}

def installed()
{
    log.debug "installed() called"
}

def speak(message) {
    String nmsg = message.replaceAll(/(\r\n|\r|\n|\\r\\n|\\r|\\n)+/, " ")
    log.debug "Speaking message = '${nmsg}'"
    
    if (TTSvolumeLevel) setVolume(TTSvolumeLevel)
    def name = device.deviceNetworkId.split("-")[-1]
	def vId = device.data.vcId
	if(vId) parent.childComm("speakMessage", nmsg.toString(), vId)
	else parent.speakMessage(nmsg.toString(), name)
}

def setVolume(volumelevel)
{
    def newLevel = volumelevel.toInteger()
    
    log.debug "setting volume to ${newLevel}"
    sendEvent(name: "volume", value: newLevel)

    def name = device.deviceNetworkId.split("-")[-1]
    def vId = device.data.vcId
    if(vId) parent.childComm("setVolume", newLevel, vId)
	else parent.setVolume(newLevel, name)   
}

def mute() 
{
    log.debug "mute() not implemented, please use setVolume()"
}

def unmute() 
{
    log.debug "unmute() not implemented, please use setVolume()"
}

def volumeUp() 
{
    log.debug "volumeUp() not implemented, please use setVolume()"
}

def volumeDown() 
{
    log.debug "volumeDown() not implemented, please use setVolume()"
}
