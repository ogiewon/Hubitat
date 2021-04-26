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
 *    2020-11-07  Dan Ogorchock     Improvements to prevent Amazon throttling due to new automatic volume adjustment feature & debug logging is now optional
 *    2021-04-26  Dan Ogorchock     Support changes to Hubitat Speech Synthesis capability to avoid errors in logs (AWS Poly Voice Name feature not supported)
 */

metadata {
    definition (name: "Child Alexa TTS", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/AlexaTTS/Drivers/child-alexa-tts.src/child-alexa-tts.groovy") {
        capability "Speech Synthesis"
        capability "AudioVolume"
        capability "Actuator"
    }
    
    preferences {
        input "TTSvolumeLevel", "number", title: "TTS Volume", description: "If defined, will set Alexa volume before each group of TTS messages. Warning: May increase chance of Amazon throttling.", range: "1..100", displayDuringSetup: false, required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging for 30 minutes", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated() 
{ 
    log.debug "updated() called"
    if (logEnable) runIn(1800,logsOff)
}

def installed()
{
    log.debug "installed() called"
    updated()
}


def speak(message, volume = null, voice = null) {
    if (voice) log.info "Voice Name feature not supported."
    
    String nmsg = message.replaceAll(/(\r\n|\r|\n|\\r\\n|\\r|\\n)+/, " ")
    if (logEnable) log.debug "Speaking message = '${nmsg}'"
        
    if ((volume == null) && TTSvolumeLevel)  {
        volume = TTSvolumeLevel
    }
    
    if (volume) {
        if (!state.setVolumeLastRanAt || now() >= state.setVolumeLastRanAt + 10000) {
            setVolume(volume)
	    }
	    else {
		    if (logEnable) log.debug "Automatic setVolume() ran within last 10 seconds. Bypassing to hopefully prevent Amazon throttling."
	    }   
    }

    def name = device.deviceNetworkId.split("-")[-1]
	def vId = device.data.vcId
	if(vId) parent.childComm("speakMessage", nmsg.toString(), vId)
	else parent.speakMessage(nmsg.toString(), name)
}

def setVolume(volumelevel)
{
    state.setVolumeLastRanAt = now()
    
    def newLevel = volumelevel.toInteger()
    if (logEnable) log.debug "setting volume to ${newLevel}"
    sendEvent(name: "volume", value: newLevel)

    def name = device.deviceNetworkId.split("-")[-1]
    def vId = device.data.vcId
    if(vId) parent.childComm("setVolume", newLevel, vId)
	else parent.setVolume(newLevel, name)   
}

def mute() 
{
    log.info "mute() not implemented, please use setVolume()"
}

def unmute() 
{
    log.info "unmute() not implemented, please use setVolume()"
}

def volumeUp() 
{
    log.info "volumeUp() not implemented, please use setVolume()"
}

def volumeDown() 
{
    log.info "volumeDown() not implemented, please use setVolume()"
}
