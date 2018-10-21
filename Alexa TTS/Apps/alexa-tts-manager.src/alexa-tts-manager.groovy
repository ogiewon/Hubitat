/**
 *  Alexa TTS Manager v0.2
 *
 *  Copyright 2018 Daniel Ogorchock - Special thanks to Chuck Schwer for his tips and prodding me
 *                                    to not let this idea fall through the cracks!  
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
 *    2018-10-20  Dan Ogorchock  Original Creation - with help from Chuck Schwer!
 *    2018-10-21  Dan Ogorchock  Trapped an error when invalid data returned from Amazon due to cookie issue.
 *    2018-10-21  Stephan Hackett Modified to include more Alexa Devices for selection
 *	
 */
 
definition(
    name: "Alexa TTS Manager",
    namespace: "ogiewon",
    author: "Dan Ogorchock",
    description: "Manages your Alexa TTS Child Devices",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "pageOne", title: "Enter information from your alexa.amazon.com cookie file", nextPage: "pageTwo", uninstall: true) {
        section("Enter your alexa.amazon.com 'cookie' file string here") {
            input("alexaCookie", "text", required: true)
        }    
    }
    page(name: "pageTwo", title: "Select your Amazon Alexa Devices", install: true, uninstall: true) {  
        section("Select the Alexa Devices you would like to use") {
            input "alexaDevices", "enum", multiple: true, required: false, options: getDevices()
        }  
    }
}


def speakMessage(String message, String device) {
    log.debug "Sending '${message}' to '${device}"
    
    atomicState.alexaJSON.devices.each {it->
        if (it.accountName == device) {
            //log.debug "${it.accountName}"
            //log.debug "${it.deviceType}"
            //log.debug "${it.serialNumber}"
            //log.debug "${it.deviceOwnerCustomerId}"

            def SEQUENCECMD = "Alexa.Speak"
            def DEVICETYPE = "${it.deviceType}"
            def DEVICESERIALNUMBER = "${it.serialNumber}"
            def LANGUAGE = "en-US"
            def MEDIAOWNERCUSTOMERID = "${it.deviceOwnerCustomerId}"
            def TTS= ",\\\"textToSpeak\\\":\\\"${message}\\\""
            def command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"type\\\":\\\"${SEQUENCECMD}\\\",\\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",\\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"${TTS}}}}\",\"status\":\"ENABLED\"}"
        	def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
            
            def params = [uri: "https://pitangui.amazon.com/api/behaviors/preview",
                          headers: ["Cookie":"""${alexaCookie}""",
                            "Referer": "https://alexa.amazon.com/spa/index.html",
                            "Origin": "https://alexa.amazon.com",
                            "csrf": "${csrf}",
                            "Connection": "keep-alive",
                            "DNT":"1"],
                        //requestContentType: "application/json",
                        //contentType: "application/json; charset=UTF-8",
                        body: command
                      ]
            try{    
                httpPost(params) { resp ->
                    //log.debug resp.contentType
                    //log.debug resp.status
                    //log.debug resp.data    
                }
            }
            catch (groovyx.net.http.HttpResponseException hre) {
                //Noticed an error in parsing the http response.  For now, catch it to prevent errors from being logged
                if (hre.getResponse().getStatus() != 200) {
                    log.error "'speakMessage()': Error making Call (Data): ${hre.getResponse().getData()}"
                    log.error "'speakMessage()': Error making Call (Status): ${hre.getResponse().getStatus()}"
                    log.error "'speakMessage()': Error making Call (getMessage): ${hre.getMessage()}"
                }
            }
            catch (e) {
                log.error "'speakMessage()':  httpPost() error = ${e}"
                log.error "'speakMessage()':  httpPost() resp.contentType = ${e.response.contentType}"
            }        
        }
    }
}

def getDevices() {
    if (alexaCookie == null) {//log.debug "No cookie yet"
                              return}
    try{
        def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
        def params = [uri: "https://pitangui.amazon.com/api/devices-v2/device?cached=false",
                      headers: ["Cookie":"""${alexaCookie}""",
                      "Referer": "https://alexa.amazon.com/spa/index.html",
                      "Origin": "https://alexa.amazon.com",
                      "csrf": "${csrf}",
                      "Connection": "keep-alive",
                      "DNT":"1"],
                    requestContentType: "application/json; charset=UTF-8"
                  ]
 
		httpGet(params) { resp ->
        	//log.debug resp.contentType
        	//log.debug resp.status
    		//log.debug resp.data
        	if ((resp.status == 200) && (resp.contentType == "application/json")) {
                def validDevices = []
                atomicState.alexaJSON = resp.data
                //log.debug state.alexaJSON.devices.accountName
                atomicState.alexaJSON.devices.each {it->
        	    if (it.deviceFamily in ["ECHO", "ROOK", "KNIGHT", "THIRD_PARTY_AVS_SONOS_BOOTLEG", "TABLET"]) {
                        //log.debug "${it.accountName} is valid"
                        validDevices << it.accountName
                    }
                    if (it.deviceFamily == "THIRD_PARTY_AVS_MEDIA_DISPLAY" && it.capabilities.contains("AUDIBLE")) {
                        validDevices << it.accountName
                    }
                }
            	return validDevices
        	}
        	else {
            	log.error "Encountered an error. http resp.status = '${resp.status}'. http resp.contentType = '${resp.contentType}'. Should be '200' and 'application/json'. Check your cookie string!"
            	return "error"
        	}
        }
    }
    catch (e) {
        log.error "httpGet() error = ${e}"
    }
}


private void createChildDevice(String deviceName) {
    
    log.debug "'createChildDevice()': Creating Child Device '${deviceName}'"
        
    try {
        def child = addChildDevice("ogiewon", "Child Alexa TTS", "${app.label}-${deviceName}", null, [name: "AlexaTTS-${deviceName}", label: "AlexaTTS ${deviceName}", completedSetup: true]) 
    } catch (e) {
       	log.error "Child device creation failed with error = ${e}"
    }
}

def installed() {
    log.debug "'installed()' called"
	log.debug "'Installed' with settings: ${settings}"
    updated()
    //subscribe()
}

def uninstalled() {
    log.debug "'uninstalled()' called"
    childDevices.each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    log.debug "'updated()' called"
    //log.debug "'Updated' with settings: ${settings}"
    //log.debug "AlexaJSON = ${atomicState.alexaJSON}"
    //log.debug "Alexa Devices = ${atomicState.alexaJSON.devices.accountName}"
    
    try {
        settings.alexaDevices.each {alexaName->
            def childDevice = null
            if(childDevices) {
                childDevices.each {child->
                    if (child.deviceNetworkId == "${app.label}-${alexaName}") {
                        childDevice = child
                        //log.debug "Child ${app.label}-${alexaName} already exists"
                    }
        	}
            }
            if (childDevice == null) {
                createChildDevice(alexaName)
                log.debug "Child ${app.label}-${alexaName} has been created"
            }
        }
    }
    catch (e) {
        log.error "Error in updated() routine, error = ${e}"
    }   
    //unsubscribe()
    //subscribe()
}

def subscribe() {
    log.debug "'subscribe()' called"
}


def initialize() {
    log.debug "'initialize()' called"
}
