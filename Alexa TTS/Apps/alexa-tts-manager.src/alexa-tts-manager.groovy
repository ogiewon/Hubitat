/**
 *  Alexa TTS Manager
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
 *    Version   Date        Who             What
 *    -------   ----        ---             ----
 *     v0.1.0   2018-10-20  Dan Ogorchock   Original Creation - with help from Chuck Schwer!
 *     v0.1.1   2018-10-21  Dan Ogorchock   Trapped an error when invalid data returned from Amazon due to cookie issue
 *     v0.2.0   2018-10-21  Stephan Hackett Modified to include more Alexa Devices for selection
 *     v0.3.0   2018-10-22  Dan Ogorchock   Added support for Canada and United Kingdom, and ability to rename the app
 *     v0.4.0   2018-11-18  Stephan Hackett Added support for Virtual Container
 *     v0.4.1   2018-11-18  Dan Ogorchock   Optimized multi-country support code and added Notification support for errors
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
    page(name: "pageOne", title: "Alexa Cookie and Country selections", nextPage: "pageTwo", uninstall: true) {
        section("Please Enter your alexa.amazon.com 'cookie' file string here (end with a semicolon)") {
            input("alexaCookie", "text", required: true)
        }
        section("Please choose your country") {
			input "alexaCountry", "enum", multiple: false, required: true, options: getURLs().keySet().collect()
        }
        section("Notification Device") {
            paragraph "Optionally assign a device for error notifications (like when the cookie is invalid)"
            input "notificationDevice", "capability.notification", multiple: false, required: false

        }
        section("App Name") {
            label title: "Optionally assign a custom name for this app", required: false
        }
    }
    page(name: "pageTwo", title: "Amazon Alexa Device Selection", install: true, uninstall: true) {  
        section("Please select devices to create Alexa TTS child devices for") {
			input "alexaDevices", "enum", multiple: true, required: false, options: getDevices()
        }
		section("") {
			paragraph 	"<span style='color:red'>Warning!!\nChanging the option below will delete any previously created child devices!!\n"+
						"Virtual Container driver v1.1.20181118 or higher must be installed on your hub!!</span>"+
						"<a href='https://github.com/stephack/Hubitat/blob/master/drivers/Virtual%20Container/Virtual%20Container.groovy' target='_blank'> [driver] </a>"+
						"<a href='https://community.hubitat.com/t/release-virtual-container-driver/4440' target='_blank'> [notes] </a>"
			input "alexaVC", "bool", title: "Add Alexa TTS child devices to a Virtual Container?"
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
            def MEDIAOWNERCUSTOMERID = "${it.deviceOwnerCustomerId}"
            def LANGUAGE = getURLs()."${alexaCountry}".Language
            def TTS= ",\\\"textToSpeak\\\":\\\"${message}\\\""
            def command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"type\\\":\\\"${SEQUENCECMD}\\\",\\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",\\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"${TTS}}}}\",\"status\":\"ENABLED\"}"
        	def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
            
            def params = [uri: "https://" + getURLs()."${alexaCountry}".Alexa + "/api/behaviors/preview",
                          headers: ["Cookie":"""${alexaCookie}""",
                                    "Referer": "https://" + getURLs()."${alexaCountry}".Amazon + "/spa/index.html",
                                    "Origin": "https://" + getURLs()."${alexaCountry}".Amazon,
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
                    if (notificationDevice) {
                        notificationDevice.deviceNotification("Alexa TTS: Please check your cookie!")
                    }
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
    if (alexaCookie == null) {log.debug "No cookie yet"
                              return}   
    try{
        def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
        def params = [uri: "https://" + getURLs()."${alexaCountry}".Alexa + "/api/devices-v2/device?cached=false",
                      headers: ["Cookie":"""${alexaCookie}""",
                                "Referer": "https://" + getURLs()."${alexaCountry}".Amazon + "/spa/index.html",
                                "Origin": "https://" + getURLs()."${alexaCountry}".Amazon,
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
                if (notificationDevice) {
                    notificationDevice.deviceNotification("Alexa TTS: Please check your cookie!")
                }
                
                return "error"
            }
        }
    }
    catch (e) {
        log.error "httpGet() error = ${e}"
        if (notificationDevice) {
            notificationDevice.deviceNotification("Alexa TTS: Please check your cookie!")
        }
    }
}


private void createChildDevice(String deviceName) {
    log.debug "'createChildDevice()': Creating Child Device '${deviceName}'"
        
    try {
        def child = addChildDevice("ogiewon", "Child Alexa TTS", "AlexaTTS${app.id}-${deviceName}", null, [name: "AlexaTTS-${deviceName}", label: "AlexaTTS ${deviceName}", completedSetup: true]) 
    } catch (e) {
       	log.error "Child device creation failed with error = ${e}"
    }
}

def installed() {
    log.debug "'installed()' called"
	log.debug "'Installed' with settings: ${settings}"
    updated()
}

def uninstalled() {
    log.debug "'uninstalled()' called"
    childDevices.each { deleteChildDevice(it.deviceNetworkId) }
}


def getURLs() {
	def URLs = ["United States": [Alexa: "pitangui.amazon.com", Amazon: "alexa.amazon.com", Language: "en-US"], 
                "Canada": [Alexa: "alexa.amazon.ca", Amazon: "alexa.amazon.ca", Language: "en-US"], 
                "United Kingdom": [Alexa: "layla.amazon.co.uk", Amazon: "amazon.co.uk", Language: "en-GB"]]
    return URLs
}

def updated() {
    log.debug "'updated()' called"
    //log.debug "'Updated' with settings: ${settings}"
    //log.debug "AlexaJSON = ${atomicState.alexaJSON}"
    //log.debug "Alexa Devices = ${atomicState.alexaJSON.devices.accountName}"
	
	def devicesToRemove
	if(alexaVC) {
		devicesToRemove = getChildDevices().findAll{it.typeName == "Child Alexa TTS"}
		if(devicesToRemove) purgeNow(devicesToRemove)
		settings.alexaDevices.each {alexaName->
				createContainer(alexaName)
		}
	}
	else {
		devicesToRemove = getChildDevices().findAll{it.typeName == "Virtual Container"}
		if(devicesToRemove) purgeNow(devicesToRemove)
		
		try {
			settings.alexaDevices.each {alexaName->
				def childDevice = null
				if(childDevices) {
					childDevices.each {child->
						if (child.deviceNetworkId == "AlexaTTS${app.id}-${alexaName}") {
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
	}
}

def purgeNow(devices){
	log.debug "Purging: ${devices}"
    devices.each { deleteChildDevice(it.deviceNetworkId) }
}

def createContainer(alexaName){
	def container = getChildDevices().find{it.typeName == "Virtual Container"}
	if(!container){
		log.info "Creating Alexa TTS Virtual Container"
		try {
			container = addChildDevice("stephack", "Virtual Container", "AlexaTTS${app.id}", null, [name: "AlexaTTS-Container", label: "AlexaTTS Container", completedSetup: true]) 
		} catch (e) {
			log.error "Container device creation failed with error = ${e}"
		}
		createVchild(container, alexaName)
	}
	else {createVchild(container, alexaName)}
}

def createVchild(container, alexaName){
	def vChildren = container.childList()
	if(vChildren.find{it.data.vcId == "${alexaName}"}){
		log.info alexaName + " already exists...skipping"
	}
	else {
		log.info "Creating TTS Device: " + alexaName
		try{
			container.appCreateDevice("AlexaTTS ${alexaName}", "Child Alexa TTS", "ogiewon", "${alexaName}")
		}
		catch (e) {
			log.error "Child device creation failed with error = ${e}"
		}
	}
}

def initialize() {
    log.debug "'initialize()' called"
}
