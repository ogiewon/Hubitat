/*
 *  Alexa TTS Manager
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/AlexaTTS/Apps/alexa-tts-manager.src/alexa-tts-manager.groovy
 *
 *
 *  Copyright 2018 Daniel Ogorchock - Special thanks to Chuck Schwer for his tips and prodding me
 *                                    to not let this idea fall through the cracks!  
 *  Copyright 2018 Gabriele         - Automatic cookie refresh with Apollon77/Alexa-Cookie
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
 *     v0.4.2   2018-11-27  Dan Ogorchock   Improved error handling for notifications when cookie expires (via live logging and optoinally, via push notification)
 *     v0.4.3   2018-12-07  Dan Ogorchock   Prevent sending empty string TTS messages to Amazon.
 *     v0.4.4   2018-12-10  Dan Ogorchock   Detect and notify via logging and notification, message rate exceeded errors to avoid confusion with cookie expiration errors.
 *     v0.4.5   2018-12-14  Stephan Hackett Added ability to paste in the entire Raw Cookie.  No manual editing required.  Improved setup page flow.
 *     v0.4.6   2018-12-23  Dan Ogorchock   Added support for Italy.  Thank you @gabriele!
 *     v0.5.0   2019-01-02  Gabriele        Added support for automatic cookie refresh with external NodeJS webserver
 *     v0.5.1   2019-02-12  Dan Ogorchock   Corrected contentType to prevent errors in response parsing
 *     v0.5.2   2019-04-04  Thomas Howard   Added get/set Volume Control (not working currently - Dan O 4/6/19)
 *     v0.5.3   2019-04-16  Gabriele        Added app events to have some historic logging
 *     v0.5.4   2019-06-24  Dan Ogorchock   Attempt to add Australia
 *     v0.5.5   2019-07-18  Dan Ogorchock   Reduced Debug Logging
 *     v0.5.6   2020-01-02  Dan Ogorchock   Add support for All Echo Device Broadcast
 *     v0.5.7   2020-01-02  Bob Butler      Add an override switch that disables all voice messages when off 
 *     v0.5.8   2020-01-07  Marco Felicio   Added support for Brazil
 */

definition(
    name: "Alexa TTS Manager",
    namespace: "ogiewon",
    author: "Dan Ogorchock",
    description: "Manages your Alexa TTS Child Devices",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "pageOne")
    page(name: "pageTwo")
}

def pageOne(){
    dynamicPage(name: "pageOne", title: "Alexa Cookie and Country selections", nextPage: "pageTwo", uninstall: true) {
        section("Please Enter your alexa.amazon.com 'cookie' file string here (end with a semicolon)") {
            input("alexaCookie", "text", title: "Raw or edited Cookie", submitOnChange: true, required: true)
        }
        if(alexaCookie != null && alexaCookie.contains("Cookie: ")){
            def finalForm
            def preForm = alexaCookie.split("Cookie: ")
            if(preForm.size() > 1) finalForm = preForm[1]?.replace("\"", "") + ";"
            app.updateSetting("alexaCookie",[type:"text", value: finalForm])
        }
        section("Please enter settings for automatic cookie refresh with NodeJS") {
            input("alexaRefreshURL", "text", title: "NodeJS service URL", required: false)
            input("alexaRefreshUsername", "text", title: "NodeJS service Username (not Amazon one)", required: false)
            input("alexaRefreshPassword", "password", title: "NodeJS service Password (not Amazon one)", required: false)
            input("alexaRefreshOptions", "text", title: "Alexa cookie refresh options", required: false, submitOnChange: true)
            input("alexaRefresh", "bool", title: "Force refresh now? (Procedure will require 5 minutes)", submitOnChange: true)
        }
        if(alexaRefreshOptions == null) {
            unschedule()
        }
        else {
            // Schedule automatic update
            unschedule()
            schedule("0 0 2 1/6 * ? *", refreshCookie) //  Check for updates every 6 days at 2:00 AM
            //Extract cookie from options if cookie is empty
            if(alexaCookie == null){
                app.updateSetting("alexaCookie",[type:"text", value: getCookieFromOptions(alexaRefreshOptions)])
            }
        }
        if(alexaRefresh) {
            refreshCookie()
            app.updateSetting("alexaRefresh",[type:"bool", value: false])
        }
        section("Please choose your country") {
            input "alexaCountry", "enum", multiple: false, required: true, options: getURLs().keySet().collect()
        }
        section("Notification Device") {
            paragraph "Optionally assign a device for error notifications (like when the cookie is invalid or refresh fails)"
            input "notificationDevice", "capability.notification", multiple: false, required: false
        }
        section("Override Switch") {
            paragraph "Optionally assign a switch that will disable voice when turned off"
            input "overrideSwitch", "capability.switch", multiple: false, required: false
        }
        section("App Name") {
            label title: "Optionally assign a custom name for this app", required: false
        }
    }
}

def pageTwo(){
    dynamicPage(name: "pageTwo", title: "Amazon Alexa Device Selection", install: true, uninstall: true) {  
        section("Please select devices to create Alexa TTS child devices for") {
            input "alexaDevices", "enum", multiple: true, required: false, options: getDevices()
        }
        section("") {
            paragraph     "<span style='color:red'>Warning!!\nChanging the option below will delete any previously created child devices!!\n"+
                        "Virtual Container driver v1.1.20181118 or higher must be installed on your hub!!</span>"+
                        "<a href='https://github.com/stephack/Hubitat/blob/master/drivers/Virtual%20Container/Virtual%20Container.groovy' target='_blank'> [driver] </a>"+
                        "<a href='https://community.hubitat.com/t/release-virtual-container-driver/4440' target='_blank'> [notes] </a>"
            input "alexaVC", "bool", title: "Add Alexa TTS child devices to a Virtual Container?"
        }
    }
}


def speakMessage(String message, String device) {
    
    if (overrideSwitch != null && overrideSwitch.currentSwitch == 'off') {
        log.info "${overrideSwitch} is off, AlexaTTS will not speak message '${message}'"
        return
    } 
    
    log.debug "Sending '${message}' to '${device}'"
	sendEvent(name:"speakMessage", value: message, descriptionText: "Sending message to '${device}'")
    if (message == '' || message.length() == 0) {
        log.warn "Message is empty. Skipping sending request to Amazon"
    }
    else {
        atomicState.alexaJSON.devices.any {it->
            if ((it.accountName == device) || (device == "All Echos")) {
                //log.debug "${it.accountName}"
                //log.debug "${it.deviceType}"
                //log.debug "${it.serialNumber}"
                //log.debug "${it.deviceOwnerCustomerId}"

                try{
                    def SEQUENCECMD = "Alexa.Speak"
                    def DEVICETYPE = "${it.deviceType}"
                    def DEVICESERIALNUMBER = "${it.serialNumber}"
                    def MEDIAOWNERCUSTOMERID = "${it.deviceOwnerCustomerId}"
                    def LANGUAGE = getURLs()."${alexaCountry}".Language
                    def TTS= ",\\\"textToSpeak\\\":\\\"${message}\\\""
                    
                    def command = ""
                    if (device == "All Echos") { 
                        command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"operationPayload\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",\\\"expireAfter\\\":\\\"PT5S\\\",\\\"content\\\":[{\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"display\\\":{\\\"title\\\":\\\"AlexaTTS\\\",\\\"body\\\":\\\"${message}\\\"},\\\"speak\\\":{\\\"type\\\":\\\"text\\\",\\\"value\\\":\\\"${message}\\\"}}],\\\"target\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"}},\\\"type\\\":\\\"AlexaAnnouncement\\\"}}\",\"status\":\"ENABLED\"}"
                    }
                    else {
                        command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"type\\\":\\\"${SEQUENCECMD}\\\",\\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",\\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"${TTS}}}}\",\"status\":\"ENABLED\"}"
                    }
                    
                    def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]

                    def params = [uri: "https://" + getURLs()."${alexaCountry}".Alexa + "/api/behaviors/preview",
                                  headers: ["Cookie":"""${alexaCookie}""",
                                            "Referer": "https://" + getURLs()."${alexaCountry}".Amazon + "/spa/index.html",
                                            "Origin": "https://" + getURLs()."${alexaCountry}".Amazon,
                                            "csrf": "${csrf}",
                                            "Connection": "keep-alive",
                                            "DNT":"1"],
                                          //requestContentType: "application/json",
                                            contentType: "text/plain",
                                            body: command
                                ]
    				//log.debug "Command = ${params}"

                    httpPost(params) { resp ->
                        //log.debug resp.contentType
                        //log.debug resp.status
                        //log.debug resp.data   
                        if (resp.status != 200) {
                            log.error "'speakMessage()':  httpPost() resp.status = ${resp.status}"
                            notifyIfEnabled("Alexa TTS: Please check your cookie!")
                        }
                    }
                }
               catch (groovyx.net.http.HttpResponseException hre) {
                    //Noticed an error in parsing the http response.  For now, catch it to prevent errors from being logged
                    if (hre.getResponse().getStatus() != 200) {
                        log.error "'speakMessage()': Error making Call (Data): ${hre.getResponse().getData()}"
                        log.error "'speakMessage()': Error making Call (Status): ${hre.getResponse().getStatus()}"
                        log.error "'speakMessage()': Error making Call (getMessage): ${hre.getMessage()}"
                        if (hre.getResponse().getStatus() == 400) {
                            notifyIfEnabled("Alexa TTS: ${hre.getResponse().getData()}")
                        }
                        else {
                            notifyIfEnabled("Alexa TTS: Please check your cookie!")
                        }
                    }
                }
                catch (e) {
                    log.error "'speakMessage()': error = ${e}"
                    //log.error "'speakMessage()':  httpPost() resp.contentType = ${e.response.contentType}"
                    notifyIfEnabled("Alexa TTS: Please check your cookie!")
                }

                return true
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
                def validDevices = ["All Echos"]
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
                log.debug "getDevices(): validDevices = ${validDevices}"
                return validDevices
            }
            else {
                log.error "Encountered an error. http resp.status = '${resp.status}'. http resp.contentType = '${resp.contentType}'. Should be '200' and 'application/json'. Check your cookie string!"
                notifyIfEnabled("Alexa TTS: Please check your cookie!")
                return "error"
            }
        }
    }
    catch (e) {
        log.error "getDevices: error = ${e}"
        notifyIfEnabled("Alexa TTS: Please check your cookie!")
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
    log.debug "'Installed()' called with settings: ${settings}"
    updated()
}

def uninstalled() {
    log.debug "'uninstalled()' called"
    childDevices.each { deleteChildDevice(it.deviceNetworkId) }
}

def getURLs() {
    def URLs = ["United States": [Alexa: "pitangui.amazon.com", Amazon: "alexa.amazon.com", Language: "en-US"], 
                "Canada": [Alexa: "alexa.amazon.ca", Amazon: "alexa.amazon.ca", Language: "en-US"], 
                "United Kingdom": [Alexa: "layla.amazon.co.uk", Amazon: "amazon.co.uk", Language: "en-GB"], 
                "Italy": [Alexa: "alexa.amazon.it", Amazon: "alexa.amazon.it", Language: "it-IT"],
                "Australia": [Alexa: "alexa.amazon.com.au", Amazon: "alexa.amazon.com.au", Language: "en-AU"],
                "Brazil": [Alexa: "alexa.amazon.com.br", Amazon: "alexa.amazon.com.br", Language: "pt-BR"]]
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

private def getCookieFromOptions(options) {
    try
    {
        def cookie = new groovy.json.JsonSlurper().parseText(options)
        if (!cookie || cookie == "") {
            log.error("'getCookieFromOptions()': wrong options format!")
            notifyIfEnabled("Alexa TTS: Error parsing cookie, see logs for more information!")
            return ""
        }
        cookie = cookie.localCookie.replace('"',"")
        if(cookie.endsWith(",")) {
            cookie = cookie.reverse().drop(1).reverse()
        }
        cookie += ";"
        log.info("Alexa TTS: new cookie parsed succesfully")
        return cookie
    }
    catch(e)
    {
        log.error("'getCookieFromOptions()': error = ${e}")
        notifyIfEnabled("Alexa TTS: Error parsing cookie, see logs for more information!")
        return ""
    }
}

def refreshCookie() {
    log.info("Alexa TTS: starting cookie refresh procedure")
    try {
        def authHeaders = ""
        if(alexaRefreshUsername != "")
            authHeaders = "Basic " + (alexaRefreshUsername + ":" + alexaRefreshPassword).bytes.encodeBase64().toString() + "}"
        def params =[
            uri: alexaRefreshURL,
            headers: [
                "Authorization":"${authHeaders}",
                "Connection": "keep-alive",
                "DNT":"1"
            ],
            requestContentType: "application/json; charset=UTF-8",
            body: alexaRefreshOptions
        ]

       httpPost(params) { resp ->
            if ((resp.status == 200)) {
                //log.debug resp.contentType
                //log.debug resp.status
                //log.debug resp.data
                def respGuid = resp.data.toString()
                log.info("Alexa TTS: Request for new cookie sent succesfully, guid: " + respGuid)
                runIn(60*5, getCookie, [data: [guid: respGuid]])
            }
            else {
                log.error "Encountered an error. http resp.status = '${resp.status}'. http resp.contentType = '${resp.contentType}'. Should be '200' and 'application/json; charset=utf-8'"
                notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
                return "error"
            }
       }
    }
    catch (groovyx.net.http.HttpResponseException hre) {
        // Noticed an error in parsing the http response
        if (hre.getResponse().getStatus() != 200) {
            log.error "'refreshCookie()': Error making Call (Data): ${hre.getResponse().getData()}"
            log.error "'refreshCookie()': Error making Call (Status): ${hre.getResponse().getStatus()}"
            log.error "'refreshCookie()': Error making Call (getMessage): ${hre.getMessage()}"
            if (hre.getResponse().getStatus() == 400) {
                notifyIfEnabled("Alexa TTS: ${hre.getResponse().getData()}")
            }
            else {
                notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
            }
        }
    }
    catch (e) {
        log.error "'refreshCookie()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
    }
}
def getCookie(data){
    log.info("Alexa TTS: starting cookie download procedure")
    if(!data.guid || data.guid == "") {
        log.error "'getCookie()': error = guid not provided!"
        notifyIfEnabled("Alexa TTS: Error downloading cookie, see logs for more information!")
        return "error"
    }
    try {
        def authHeaders = ""
        if(alexaRefreshUsername != "")
            authHeaders = "Basic " + (alexaRefreshUsername + ":" + alexaRefreshPassword).bytes.encodeBase64().toString() + "}"
        def params =[
            uri: alexaRefreshURL,
            headers: [
                "Authorization":"${authHeaders}",
                "Connection": "keep-alive",
                "DNT":"1"
            ],
            requestContentType: "application/json; charset=UTF-8",
            query: [guid: data.guid]
        ]

       httpGet(params) { resp ->
            //log.debug resp.contentType
            //log.debug resp.status
            //log.debug resp.data
            if ((resp.status == 200) && (resp.contentType == "application/json")) {
                //If saved directly as resp.data then double quotes are stripped
                def newOptions = new groovy.json.JsonBuilder(resp.data).toString()
                app.updateSetting("alexaRefreshOptions",[type:"text", value: newOptions])
                log.info("Alexa TTS: cookie downloaded succesfully")
                app.updateSetting("alexaCookie",[type:"text", value: getCookieFromOptions(newOptions)])
				sendEvent(name:"GetCookie", descriptionText: "New cookie downloaded succesfully")
            }
            else {
                log.error "Encountered an error. http resp.status = '${resp.status}'. http resp.contentType = '${resp.contentType}'. Should be '200' and 'application/json; charset=utf-8'"
                notifyIfEnabled("Alexa TTS: Error downloading cookie, see logs for more information!")
                return "error"
            }
       }
    }
    catch (groovyx.net.http.HttpResponseException hre) {
        // Noticed an error in parsing the http response
        if (hre.getResponse().getStatus() != 200) {
            log.error "'getCookie()': Error making Call (Data): ${hre.getResponse().getData()}"
            log.error "'getCookie()': Error making Call (Status): ${hre.getResponse().getStatus()}"
            log.error "'getCookie()': Error making Call (getMessage): ${hre.getMessage()}"
            if (hre.getResponse().getStatus() == 400) {
                notifyIfEnabled("Alexa TTS: ${hre.getResponse().getData()}")
            }
            else {
                notifyIfEnabled("Alexa TTS: Error downloading cookie, see logs for more information!")
            }
        }
    }
    catch (e) {
        log.error "'getCookie()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error dowloading cookie, see logs for more information!")
    }
}
def notifyIfEnabled(message) {
    if (notificationDevice) {
        notificationDevice.deviceNotification(message)
    }
}
