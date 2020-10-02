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
 *     v0.5.9   2020-01-26  Dan Ogorchock   Changed automatic cookie refresh time to 1am to avoid hub maintenance window
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
        section(hideable:true, hidden:true, "Settings for automatic cookie refresh") {
            paragraph(
                """
In order to send commands to Amazon's servers (which in turn relay those commands to your Alexa device(s) and thereby cause your Alexa device(s) to speak), this app needs to have a valid cookie -- a string that this app sends along with each transmission to Amazon's servers. Amazon's servers, by design, require a valid cookie to be sent with each transmission in order to know who the request is coming from, and in order to know which Amazon account the request is related to. A computer program (like this app) obtains a valid cookie by communicating with Amazon's servers, which will send a valid cookie to the computer program once the computer program has convinced Amazon's servers to do so. 

There are two ways for a computer program to convince Amazon's servers to cough up a valid cookie: (1) by sending to Amazon's servers your Amazon username and password and (2) by sending, to Amazon's servers, an existing valid cookie, along with a few other parameters generated while obtaining the last valid cookie -- the collection of those few parameters and the exisitng valid cookie (that is, the collection of all information needed to obtain a new valid cookie) are called, collectively, the &quot;Alexa cookie refresh options&quot; (this app stores the Alexa cookie refresh options as a single string). In either case, Amazon's servers will generate a fresh cookie and send that new cookie to the computer program. 

Each cookie expires about 2 weeks after Amazon's servers issue it. Therefore, in order for this app to be able to communciate with Amazon's servers indefinitely, this app needs to periodically 'refresh' its stored cookie, obtaining a fresh cookie from Amazon's servers before its current cookie expires. Due to limitations inherent in the Groovy environment in which this app runs (and due to the inherent technical difficulty of the problem), this app is not capable of obtaining a valid cookie using method 1; this app is not capable of using your Amazon username and password to obtain a valid cookie. However, this app is capable of obtaining a valid cookie via method 2; once this app has a valid set of Alexa cookie refresh options, it should, in principle be able to keep itself supplied with a fresh cookie indefinitely, provided that your Hubitat does not remain continuously offline long enough for the current cookie to expire. 

To bootstrap the cookie refresh process when you first install this app, you will need to manually come up with a valid set of Alexa cookie refresh options, in the form of a string, and paste that string into the Alexa cookie refresh options field. This can be accomplished by means of <a href="https://github.com/gabriele-v/hubitat/tree/master/AlexaCookieNodeJs/AlexaCookieNodeJs" target="_blank">gabriele-v's AlexaCookieNodeJs program</a>, which you can run on your own computer. The AlexaCookieNodeJs program will prompt you for your Amazon username and password, will perform the special handshake with Amazon's servers, and will then spit out a string suitable for pasting into the Alexa cookie refresh options field. 

If so desired (but by no means a requirement), you can leave gabriele-v's AlexaCookieNodeJs program running continuously on your own NodeJS server, and punch the server's URL, username, and password into the fields below. The AlexaCookieNodeJs program is designed to run as a server and to perform the cookie refresh procedure using method 2 on behalf of computer programs (like this app) that communicate with it over the network. 

When this app goes to refresh its cookie (which it does every 6th day at 1:00 am), this app will first attempt to use a running instance of the AlexaCookieNodeJs program at the URL that you supply below to refresh its cookie. If this attempt fails (as it will if you enter no URL whatsoever), then this app will perform method 2 itself. 
                """
            )
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
            schedule("0 0 1 1/6 * ? *", refreshCookie) //  Check for updates every 6 days at 1:00 AM
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
                    
                    def command = ""
                    if (device == "All Echos") { 
                      //command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"operationPayload\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",\\\"expireAfter\\\":\\\"PT5S\\\",\\\"content\\\":[{\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"display\\\":{\\\"title\\\":\\\"AlexaTTS\\\",\\\"body\\\":\\\"${message}\\\"},\\\"speak\\\":{\\\"type\\\":\\\"text\\\",\\\"value\\\":\\\"${message}\\\"}}],\\\"target\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"}},\\\"type\\\":\\\"AlexaAnnouncement\\\"}}\",\"status\":\"ENABLED\"}"
                        command = "{\"behaviorId\":\"PREVIEW\",\
                                    \"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\
                                                        \\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\
                                                                           \\\"operationPayload\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",\
                                                                           \\\"expireAfter\\\":\\\"PT5S\\\",\
                                                                           \\\"content\\\":[{\\\"locale\\\":\\\"${LANGUAGE}\\\",\
                                                                                             \\\"display\\\":{\\\"title\\\":\\\"AlexaTTS\\\",\
                                                                                                              \\\"body\\\":\\\"${message}\\\"},\
                                                                                                              \\\"speak\\\":{\\\"type\\\":\\\"text\\\",\
                                                                                                                             \\\"value\\\":\\\"${message}\\\"}}],\
                                                                           \\\"target\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"}},\
                                                                           \\\"type\\\":\\\"AlexaAnnouncement\\\"}}\",\
                                    \"status\":\"ENABLED\"}"
                    }
                    else {
                      //command = "{\"behaviorId\":\"PREVIEW\",\"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\\\"type\\\":\\\"${SEQUENCECMD}\\\",\\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",\\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",\\\"locale\\\":\\\"${LANGUAGE}\\\",\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"${TTS}}}}\",\"status\":\"ENABLED\"}"
                        command = "{\"behaviorId\":\"PREVIEW\",\
                                    \"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",\
                                                        \\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",\
                                                        \\\"type\\\":\\\"${SEQUENCECMD}\\\",\
                                                        \\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",\
                                                                                  \\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",\
                                                                                  \\\"locale\\\":\\\"${LANGUAGE}\\\",\
                                                                                  \\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",\
                                                                                  \\\"textToSpeak\\\":\\\"${message}\\\"}}}\",\
                                    \"status\":\"ENABLED\"}"
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
                refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
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
        refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
    }
    catch (e) {
        log.error "'refreshCookie()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
        refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
    }
}
def getCookie(data){
    log.info("Alexa TTS: starting cookie download procedure")
    if(!data.guid || data.guid == "") {
        log.error "'getCookie()': error = guid not provided!"
        notifyIfEnabled("Alexa TTS: Error downloading cookie, see logs for more information!")
        refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
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
                refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
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
        refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
    }
    catch (e) {
        log.error "'getCookie()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error dowloading cookie, see logs for more information!")
        refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
    }
}
def notifyIfEnabled(message) {
    if (notificationDevice) {
        notificationDevice.deviceNotification(message)
    }
}

/** refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()
 *  This function is invoked by the logic within the refreshCookie() and getCookie() functions
 *  whenever we encounter a failure while attempting to refresh the cookie by means of the NodeJS server.
 *  Provided that the current value of alexaRefreshOptions is valid, and contains an unexpired cookie,
 *  this function will refresh the cookie without relying on an external NodeJS server.
 */
def refreshAlexaCookieWithoutRelyingOnTheNodeJsServer() {
    log.info("Alexa TTS: starting the cookie refresh procedure that does not rely on the Node JS server")
    try {
//This file is intended to be included into a hubitat app source code file.

Map alexaCookieUtility = {

    // Map _ = [:]; //there's nothing special about the identifier "_", we are just using it because it's short and doesn't impair the readability of the code too much.  We are using it as the identifier for the object that we are construction and will return.

    def proxyServer;
    Map _options = [:];
    String Cookie='';
    final String defaultAmazonPage = 'amazon.de';
    final String defaultUserAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:99.0) Gecko/20100101 Firefox/99.0';
    final String defaultUserAgentLinux = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36';
    final String defaultAcceptLanguage = 'de-DE';

    final List<String> csrfPathCandidates = [
        '/api/language',
        '/spa/index.html',
        '/api/devices-v2/device?cached=false',
        '/templates/oobe/d-device-pick.handlebars',
        '/api/strings'
    ].asImmutable();
    //Groovy does not respect my final, nor my "<String>" type specification, but they are my intent nonetheless.

    /**

    *   prettyPrint() serves only to make the debugging messages look nicer.

    */
    Closure prettyPrint = {
            return groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(it));
    };

    /**

    * Parse a cookie header.

    *

    * Parse the given cookie header string into an object

    * The object has the various cookies as keys(names) => values

    *

    * @param {string} str

    * @param {object} [options]

    * @return {object}

    * @public

    */
    // Closure cookie_parse = {String str, Map options=[:] ->
    Closure cookie_parse = {String str, Map options=[:] ->
        if (! str instanceof String ) {
            throw new Exception("argument str must be a string");
        }
        if(!str){str='';}

        //these were originally global variables:
        def pairSplitRegExp = "; *";
        // def decode = decodeURIComponent;
        // def encode = encodeURIComponent;

        def obj = [:];
        def opt = options + [:];
        def pairs = str.split(pairSplitRegExp);

        for (def i = 0; i < pairs.size(); i++) {
            def pair = pairs[i];
            def eq_idx = pair.indexOf('=');

            // skip things that don't look like key=value
            if (eq_idx < 0) {
                continue;
            }

            def key = pair.substring(0, eq_idx).trim();
            def val = pair.substring(++eq_idx, pair.length()).trim();

            // quoted values
            if ('"' == val[0]) {
                val = val[1..-2]; //remove the first and last characters from val.
            }

            // only assign once
            if (! obj.containsKey(key)) {
                obj[key] = URLDecoder.decode(val);
            }
        }

        return obj;
    };

    /**

    *  applies any cookies that may be present in 

    *  a set of http headers (an iterable of org.apache.http.Header)  to an existing Cookie string (adding any cookies that

    *  that do not already exist, and updating any that do.)

    *  Returns the updated version of the cookie string.

    */
    Closure addCookies = {String cookie, headers ->
        String internalDebugMessage = "";
        internalDebugMessage += "addCookies run summary:" + "\n";
        internalDebugMessage += "starting with: " + cookie + "\n";
        String returnValue;
        // if (!headers || !('set-cookie' in headers)){
        if (!headers || !headers.any{it.name.toLowerCase() == "set-cookie"} ){
            internalDebugMessage += ("could not find a 'set-cookie' header in headers." + "\n");
            returnValue =  cookie; 
        } else {
            if(!cookie){
                cookie='';
            }   

            // original javascript:   
            //      const cookies = cookieTools.parse(Cookie);
            def cookies = cookie_parse(cookie); 

            

            // original javascript:   
            //    for (let cookie of headers['set-cookie']) {
            // according to https://nodejs.org/api/http.html#http_message_headers ,
            // headers['set-cookie'] will always be an array. (This especially makes sense when the response contains multiple 'set-cookie' 
            // headers (which I guess is allowed (i.e. it seems that the collection of headers is not strictly an associative map, because 
            // you can have multiple entries having the same 'key'.  I guess the collection of headers is more like a list 
            // of (name, value) pairs.)

            for (def headerValue in headers.findAll{it.name.toLowerCase() == "set-cookie"}.collect{it.value}){
                // original javascript: cookie = cookie.match(/^([^=]+)=([^;]+);.*/);
                // we expect headerValue to be a string that looks like "foo=blabbedy blabbedy blabbedy ;"
                
                cookieMatch = (~/^([^=]+)=([^;]+);.*/).matcher(headerValue)[0];

                //original javascript:  if (cookie && cookie.length === 3) {
                if (cookieMatch && cookieMatch.size() == 3) {
                    
                    // original javascript:  if (cookie[1] === 'ap-fid' && cookie[2] === '""') continue;
                    if (cookieMatch[1] == 'ap-fid' && cookieMatch[2] == '""'){ continue;}
                    
                    //original javascript: if (cookies[cookie[1]] && cookies[cookie[1]] !== cookie[2]) {
                    if( (cookieMatch[1] in cookies) && (cookies[cookieMatch[1]] != cookieMatch[2]) ){
                        //original javascript: _options.logger && _options.logger('Alexa-Cookie: Update Cookie ' + cookie[1] + ' = ' + cookie[2]);
                        internalDebugMessage += ('Alexa-Cookie: Update Cookie ' + cookieMatch[1] + ' = ' + cookieMatch[2]) + "\n";
                    } else if (!(cookieMatch[1] in cookies) ) {
                        internalDebugMessage += ('Alexa-Cookie: Add Cookie ' + cookieMatch[1] + ' = ' + cookieMatch[2]) + "\n";
                    } else {
                        //in this case, (cookieMatch[1] in cookies) && (cookies[cookieMatch[1]] == cookieMatch[2])
                        //in other words, a cookie of the same name and value already exists in cookies.
                    } 

                    //original javascript: cookies[cookie[1]] = cookie[2];
                    cookies[cookieMatch[1]] = cookieMatch[2];
                }
            }

            //rebuild the cookie string from the newly-updated cookies map.

            //>    Cookie = '';
            //>    for (let name in cookies) {
            //>        if (!cookies.hasOwnProperty(name)) continue;
            //>        Cookie += name + '=' + cookies[name] + '; ';
            //>    }
            //>    Cookie = Cookie.replace(/[; ]*$/, '');
            // cookie = '';
            // for (name in cookies.keySet()){
            //     cookie += name + '=' + cookies[name] + '; ';
            // }

            // return cookie;  //>    return Cookie;
            returnValue = cookies.collect{it.key + "=" + it.value}.join("; ");
        }
        internalDebugMessage += "addCookies is returning: " + returnValue + "\n";
        _options.logger && _options.logger(internalDebugMessage);
        return returnValue;
    };

    Closure getFields = {String body ->
        Map returnValue = [:];
        //replace carriage returns and newlines with spaces
        body = body.replace("\r", ' ').replace("\n", ' ');
        fieldBlockMatcher = (~/^.*?("hidden"\s*name=".*$)/).matcher(body);
        if (fieldBlockMatcher.find()) {
            fieldMatcher = (~/.*?name="([^"]+)"[\s^\s]*value="([^"]+).*?"/).matcher(fieldBlockMatcher.group(1));
            while (fieldMatcher.find()) {
                if (fieldMatcher.group(1) != 'rememberMe') {
                    returnValue[fieldMatcher.group(1)] = fieldMatcher.group(2);
                }
            }
        }
        return returnValue; 
    };

    Closure initConfig = {
        _options.logger = _options.logger ?: Closure.IDENTITY; //default logger is to do nothing (I choose to use Closure.IDENTITY instead of a literal closure expression here in the hopes that Closure.IDENTITY will incur less runtime overhead.)

        _options.amazonPage = _options.amazonPage ?: _options.formerRegistrationData?.amazonPage ?: defaultAmazonPage;
        _options.logger('Alexa-Cookie: Use as Login-Amazon-URL: ' + _options.amazonPage);

        _options.userAgent = _options.userAgent ?: defaultUserAgentLinux;
        _options.logger('Alexa-Cookie: Use as User-Agent: ' + _options.userAgent);
        
        _options.acceptLanguage = _options.acceptLanguage ?: defaultAcceptLanguage;
        _options.logger('Alexa-Cookie: Use as Accept-Language: ' + _options.acceptLanguage);

        if (_options.setupProxy && !_options.proxyOwnIp) {
            _options.logger('Alexa-Cookie: Own-IP Setting missing for Proxy. Disabling!');
            _options.setupProxy = false;
        }
        if (_options.setupProxy) {
            _options.setupProxy = true;
            _options.proxyPort = _options.proxyPort ?: 0;
            _options.proxyListenBind = _options.proxyListenBind ?: '0.0.0.0';
            _options.logger('Alexa-Cookie: Proxy-Mode enabled if needed: ' + _options.proxyOwnIp + ':' + _options.proxyPort + ' to listen on ' + _options.proxyListenBind);
        } else {
            _options.setupProxy = false;
            _options.logger('Alexa-Cookie: Proxy mode disabled');
        }
        _options.proxyLogLevel = _options.proxyLogLevel ?: 'warn';
        _options.amazonPageProxyLanguage = _options.amazonPageProxyLanguage ?: 'de_DE';

        if(_options.formerRegistrationData){ _options.proxyOnly = true; }
    };

    Closure getCSRFFromCookies = {Map namedArgs  -> 
        String cookie = namedArgs.cookie;
        Map options = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback;
        
        String csrf = null; //our goal is to obtain a csrf token and assign it to this string. 
        for(csrfPathCandidate in csrfPathCandidates){
            options.logger && options.logger('Alexa-Cookie: Step 4: get CSRF via ' + csrfPathCandidate);
            httpGet(
                [
                    uri: "https://alexa." + options.amazonPage + csrfPathCandidate,
                    'headers': [
                        'DNT': '1',
                        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36',
                        'Connection': 'keep-alive',
                        'Referer': 'https://alexa.' + options.amazonPage + '/spa/index.html',
                        'Cookie': cookie,
                        'Accept': '*/*',
                        'Origin': 'https://alexa.' + options.amazonPage  
                    ]
                ],
                {response ->
                    cookie = addCookies(cookie, response.headers);
                    java.util.regex.Matcher csrfMatcher = (~/csrf=([^;]+)/).matcher(cookie);
                    if(csrfMatcher.find()){
                        csrf = csrfMatcher.group(1);
                        options.logger && options.logger('Alexa-Cookie: Result: csrf=' + csrf.toString() + ', Cookie=' + cookie);
                    }
                }
            );
            if(csrf){
                callback && callback(null, [
                    'cookie':cookie,
                    'csrf':csrf
                ]);
                return;
            }
        }

        //it seems like we should do something here to handle the case where no csrf could be obtained,
        // but the original javascript does not seem to do any such error handling.
    };

    Closure getLocalCookies = {Map namedArgs ->
        String amazonPage = namedArgs.amazonPage;
        String refreshToken = namedArgs.refreshToken;
        Closure callback = namedArgs.callback;

        Cookie = ''; //comment from original javascript: reset because we are switching domains
        //comment from original javascript: Token Exchange to Amazon Country Page
        Map exchangeParams = [
            'di.os.name': 'iOS',
            'app_version': '2.2.223830.0',
            'domain': '.' + amazonPage,
            'source_token': refreshToken,
            'requested_token_type': 'auth_cookies',
            'source_token_type': 'refresh_token',
            'di.hw.version': 'iPhone',
            'di.sdk.version': '6.10.0',
            'cookies': ('{„cookies“:{".' + amazonPage + '":[]}}').bytes.encodeBase64().toString(),
            'app_name': 'Amazon Alexa',
            'di.os.version': '11.4.1'
        ];
        Map requestParams = [
            uri: 'https://' + 'www.' + amazonPage + '/ap/exchangetoken',
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': _options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': '*/*'
            ],
            contentType: groovyx.net.http.ContentType.JSON, // type of content that we expect the response to contain //this influences the type of object that the system passes to the callback. ,
            requestContentType: groovyx.net.http.ContentType.URLENC, //type of content that the request will contain.  corresponds to the 'Content-Type' header of the request. By default, this is assumed to be the same as the expected content type of the response, unless explicitly specified //this influences how the system treats the body of the request.   
            body: exchangeParams 
        ];
        _options.logger('Alexa-Cookie: Exchange tokens for ' + amazonPage);
        _options.logger(prettyPrint(requestParams));
        httpPost(requestParams,
            {response ->
                //TODO: handle response errors here (or maybe outside with a try{}catch(){} statement.)
                //TODO: handle malformed response data here.
                _options.logger('Exchange Token Response: ' + prettyPrint(response.data));
                // if (!body.response || !body.response.tokens || !body.response.tokens.cookies) {
                if (!response.data.response?.tokens?.cookies) {
                    callback && callback('No cookies in Exchange response', null);
                    return;
                }
                if (!response.data.response.tokens.cookies['.' + amazonPage]) {
                    callback && callback('No cookies for ' + amazonPage + ' in Exchange response', null);
                    return;
                }

                Cookie = addCookies(Cookie, response.headers);
                Map cookies = cookie_parse(Cookie);
                response.data.response.tokens.cookies['.' + amazonPage].each {cookie ->
                    if (cookies[cookie.Name] && cookies[cookie.Name] != cookie.Value) {
                        _options.logger('Alexa-Cookie: Update Cookie ' + cookie.Name + ' = ' + cookie.Value);
                    } else if (!cookies[cookie.Name]) {
                        _options.logger('Alexa-Cookie: Add Cookie ' + cookie.Name + ' = ' + cookie.Value);
                    }
                    cookies[cookie.Name] = cookie.Value;
                };

                // String localCookie = '';
                // for (String name in cookies.keySet()) {
                //     localCookie += name + '=' + cookies[name] + '; ';
                // }
                // localCookie = localCookie.replace(/[; ]*$/, '');

                String localCookie = cookies.collect{it.key + "=" + it.value}.join("; ");
                callback && callback(null, localCookie);
            }
        );
    };

    Closure handleTokenRegistration = {Map namedArgs ->
        Map options = namedArgs.options ?: [:];
        Map loginData = namedArgs.loginData ?: [:];
        Closure callback = namedArgs.callback;
        options.logger && options.logger('Handle token registration Start: ' + prettyPrint(loginData));
        String deviceSerial;
        if(options.formerRegistrationData?.deviceSerial){
            options.logger && options.logger('Proxy Init: reuse deviceSerial from former data');
            deviceSerial = options.formerRegistrationData.deviceSerial;
        } else {
            Byte[] deviceSerialBuffer = new Byte[16];
            for (def i = 0; i < deviceSerialBuffer.size(); i++) {
                deviceSerialBuffer[i] = floor(random() * 255);
            }
            deviceSerial = deviceSerialBuffer.encodeHex().toString();
        }
        loginData.deviceSerial = deviceSerial;
        Map cookies = cookie_parse(loginData.loginCookie);
        Cookie = loginData.loginCookie;

        //comment from original javascript: Register App
        Map registerData = [
            "requested_extensions": [
                "device_info",
                "customer_info"
            ],
            "cookies": [
                "website_cookies": cookies.collect{ ["Value": it.value,  "Name": it.key] },
                "domain": ".amazon.com"
            ],
            "registration_data": [
                "domain": "Device",
                "app_version": "2.2.223830.0",
                "device_type": "A2IVLV5VM2W81",
                "device_name": "%FIRST_NAME%\u0027s%DUPE_STRATEGY_1ST%ioBroker Alexa2",
                "os_version": "11.4.1",
                "device_serial": deviceSerial,
                "device_model": "iPhone",
                "app_name": "ioBroker Alexa2",
                "software_version": "1"
            ],
            "auth_data": [
                "access_token": loginData.accessToken
            ],
            "user_context_map": [
                "frc": cookies.frc
            ],
            "requested_token_type": [
                "bearer",
                "mac_dms",
                "website_cookies"
            ]
        ];

        Map requestParams0 = [
            uri: "https://api.amazon.com/auth/register",
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/json',
                'Cookie': loginData.loginCookie,
                'Accept': '*/*',
                'x-amzn-identity-auth-domain': 'api.amazon.com'
            ],
            contentType: groovyx.net.http.ContentType.JSON, //this influences the type of object that the system passes to the callback. ,
            requestContentType: groovyx.net.http.ContentType.JSON,  //this influences how the system treats the body of the request.   
            body: registerData
        ];
        options.logger && options.logger('Alexa-Cookie: Register App');
        options.logger && options.logger(prettyPrint(requestParams0));
        httpPost(requestParams0,
            {response0 ->
                //TODO: handle response errors here (or maybe outside with a try{}catch(){} statement.)
                //TODO: handle malformed response data here.

                options.logger && options.logger('Register App Response: ' + prettyPrint(response0.data));

                if(! response0.data.response?.success?.tokens?.bearer){
                    callback && callback('No tokens in Register response', null);
                    return;
                }
                Cookie = addCookies(Cookie, response0.headers);
                loginData.refreshToken = response0.data.response.success.tokens.bearer.refresh_token;
                loginData.tokenDate = now();

                //comment from original javascript: Get Amazon Marketplace Country
                Map requestParams1 = [
                    uri: "https://alexa.amazon.com/api/users/me?platform=ios&version=2.2.223830.0",
                    headers: [
                        'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                        'Accept-Language': options.acceptLanguage,
                        'Accept-Charset': 'utf-8',
                        'Connection': 'keep-alive',
                        'Accept': 'application/json',
                        'Cookie': Cookie
                    ],
                    contentType: groovyx.net.http.ContentType.JSON, //this influences the type of object that the system passes to the callback. ,
                ];
                options.logger && options.logger('Alexa-Cookie: Get User data');
                options.logger && options.logger(prettyPrint(requestParams1));
                httpGet(requestParams1,
                    {response1 -> 
                        //TODO: handle response errors here (or maybe outside with a try{}catch(){} statement.)
                        options.logger && options.logger('Get User data Response: ' + prettyPrint(response1.data));
                        Cookie = addCookies(Cookie, response1.headers);
                        if (response1.data.marketPlaceDomainName) {
                            java.util.regex.Matcher amazonPageMatcher = (~/^[^\.]*\.([\S\s]*)$/).matcher(response1.data.marketPlaceDomainName);
                            if(amazonPageMatcher.find()){
                                options.amazonPage = amazonPageMatcher.group(1);
                            }
                        }
                        loginData.amazonPage = options.amazonPage;
                        loginData.loginCookie = Cookie;
                        getLocalCookies(
                            amazonPage: loginData.amazonPage, 
                            refreshToken: loginData.refreshToken, 
                            callback: {String err0, String localCookie ->
                                if (err0) {
                                    callback && callback(err0, null);
                                }
                                loginData.localCookie = localCookie;
                                getCSRFFromCookies(
                                    cookie: loginData.localCookie, 
                                    options: options,
                                    callback: {String err1, Map resData ->
                                        if (err1) {
                                            callback && callback('Error getting csrf for ' + loginData.amazonPage + ':' + err1, null);
                                            return;
                                        }
                                        loginData.localCookie = resData.cookie;
                                        loginData.csrf = resData.csrf;
                                        // loginData.removeAll{key, value -> key == 'accessToken'};
                                        loginData.remove('accessToken');
                                        options.logger && options.logger('Final Registraton Result: ' + prettyPrint(loginData));
                                        callback && callback(null, loginData);
                                    }
                                );
                            }
                        );
                    }
                );

            }
        );
    };

    //======== publicly exposed methods: ==============
    Closure generateAlexaCookie = {Map namedArgs  ->
        String email     = namedArgs.email;
        String password  = namedArgs.password;
        Map __options    = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback; //expected to be a callback having signature:  void callback(String err, Map data)
        if (!email || !password) {__options.proxyOnly = true;}
        _options = __options;
        initConfig();

        if(_options.proxyOnly){
            //TO-DO: start the proxy server (not yet implemented) and instruct the user to go attempt to login using the proxy server.
        } else {
            // comment from the original javascript: get first cookie and write redirection target into referer
            _options.logger('Alexa-Cookie: Step 1: get first cookie and authentication redirect');
            Map requestParams0 = [
                'uri': "https://" +  'alexa.' + _options.amazonPage,
                'headers': [
                    'DNT': '1',
                    'Upgrade-Insecure-Requests': '1',
                    'User-Agent': _options.userAgent,
                    'Accept-Language': _options.acceptLanguage,
                    'Connection': 'keep-alive',
                    'Accept': '*/*'
                ],
                'contentType':groovyx.net.http.ContentType.TEXT //this influences the type of object that the system passes to the callback.  if we omit this request parameter, the resultant response.data object is a groovy...nodeChild (I forget the exact class name), and we are unable to extract the complete text of the response.
            ];
            _options.logger("working on step 1, we will now attempt to get " + requestParams0.uri);
            httpGet(requestParams0,
                {response0 ->
                    _options.logger('First response has been received.');
                    _options.logger("\n" + "response0.headers: " + "\n" + response0.headers.collect{"\t"*1 + it.name + ": " + it.value}.join("\n\n") + "\n");
                    _options.logger('Alexa-Cookie: Step 2: login empty to generate session');
                    //TO-DO: handle request errors here.
                    Cookie = addCookies(Cookie, response0.headers);
                    String response0Text = response0.data.getText();
                    

                    _options.logger("Cookie: " + Cookie);
                    _options.logger("cookie_parse(Cookie): " + prettyPrint(cookie_parse(Cookie)));
                    _options.logger("\n" + "response0.getContext()['http.request'].getRequestLine().getUri(): " + response0.getContext()['http.request'].getRequestLine().getUri());
                    _options.logger("\n" + "response0.getContext()['http.request'].getOriginal().getRequestLine().getUri(): " + response0.getContext()['http.request'].getOriginal().getRequestLine().getUri() + "\n");
                    _options.logger("response0.contentType: " + response0.contentType);
                    // _options.logger("response0Text.class: " + response0Text.class);
                    _options.logger("response0Text.length(): " + response0Text.length());
                    
                    // response0Text seems to be quite large, and therefoer takse a long to process (mainly, what we need to do with it is read the fields).
                    // We could probably be more intelligent about consuming response0.data (which is an input stream), to stop reading as soon as we have found all the fields, rather tahn converting the whole 
                    // enormous thing into an enormous string, and then trying to find the fields in the string.
                    // _options.logger("response0Text: " + response0Text);
                    Map response0Fields = getFields(response0Text);
                    _options.logger('response0Fields: ' + prettyPrint(response0Fields) + "\n\n" )
                    // return;
                    // _options.logger(prettyPrint(getFields(response0.data.toString())));
                    // _options.logger("\n\n" );
                    // _options.logger('response0.data: ' + response0.data.dump().take(300) + "\n\n" );
                    // _options.logger('response0.data.parent(): ' + response0.data.parent().dump().take(300)  + "\n\n" );
                    
                    // _options.logger("response0.data.text: " + response0.data.text.take(25) + (response0.data.text().length() > 25 ? "..." : '')); //throws an exception java.io.IOException: Stream closed, becauase we can only read the string once
                    // _options.logger("response0Text: " + response0Text.take(25) + (response0Text.length() > 25 ? "..." : ''));

                    // _options.logger("response0.data.toString(): " + response0.data.text.toString());
                    // _options.logger('response0.data.size(): ' + response0.data.size() + "\n\n" );
                    // _options.logger("response0.getParams(): " + response0.getParams().dump());
                    // _options.logger("response0.getContext()['http.response'].getParams(): " + response0.getContext()['http.response'].getParams().dump());
                    // _options.logger("response0.getContext()['http.response'].entity.content: " + response0.getContext()['http.response'].entity.content.getText())
                    // _options.logger("response0.entity.content: " + response0.entity.content.getText())
                    // _options.logger("groovyx.net.http.ContentType.TEXT.toString(): " + groovyx.net.http.ContentType.TEXT.toString())
                    requestParams1 = requestParams0 + [
                        'uri': "https://" +  'www.' + _options.amazonPage + '/ap/signin',
                        'body': response0Fields,
                        'requestContentType': groovyx.net.http.ContentType.URLENC                     
                    ];
                    requestParams1.headers += [
                        'Cookie': Cookie,
                        'Referer': 'https://' + {it.host + it.path}(new java.net.URI(response0.getContext()['http.request'].getOriginal().getRequestLine().getUri())),
                        'Content-Type': 'application/x-www-form-urlencoded'
                    ];

                    _options.logger("working on step 2, we will now attempt to post to " + requestParams1.uri + " the following " + prettyPrint(requestParams1.body));
                    httpPost(requestParams1, 
                        {response1 ->
                            _options.logger('Second response has been received.');
                            //TO-DO: handle request errors here.
                            //comment from the original javascript: // login with filled out form
                            //comment from the original javascript://  !!! referer now contains session in URL
                            Cookie = addCookies(Cookie, response1.headers);
                            String response1Text = response1.data.getText();
                            _options.logger("Cookie: " + Cookie);
                            _options.logger("cookie_parse(Cookie): " + prettyPrint(cookie_parse(Cookie)));

                            _options.logger("response1.contentType: " + response1.contentType);
                            _options.logger("response1Text.length(): " + response1Text.length());
                            Map response1Fields = getFields(response1Text);
                            _options.logger('response1Fields: ' + prettyPrint(response1Fields) + "\n\n" );

                            _options.logger('Alexa-Cookie: Step 3: login with filled form, referer contains session id');
                            requestParams2 = requestParams1 + [
                                'body': 
                                    response1Fields + [
                                        'email': email ?: '',
                                        'password': password ?: ''
                                    ]
                            ];
                            requestParams2.headers += [
                                'Cookie': Cookie,
                                'Referer': "https://www.${_options.amazonPage}/ap/signin/" + (~/session-id=([^;]+)/).matcher(Cookie)[0][1]
                            ];

                            _options.logger("working on step 3, we will now attempt to post to " + requestParams2.uri + " the following: " + prettyPrint(requestParams2.body));
                            httpPost(requestParams2,
                                {response2 ->
                                    _options.logger('Third response has been received.');
                                    //TO-DO: handle request errors here.
                                    //comment form original javascript: // check whether the login has been successful or exit otherwise
                                    
                                    String response2Text = response2.data.getText();
                                    _options.logger("response2.contentType: " + response2.contentType);
                                    _options.logger("response2Text.length(): " + response2Text.length());

                                    if({it.host.startsWith('alexa') && it.path.endsWith('.html')}(new java.net.URI(response0.getContext()['http.request'].getOriginal().getRequestLine().getUri())) ){
                                        //success
                                        return getCSRFFromCookies('cookie':Cookie, 'options':_options, 'callback':callback);
                                        // getCSRFFromCookies calls the callback and passes, as the first argument, an error message (if any), and as a second argument, either null, or a map containg exactly two keys 'cookie' and 'csrf'
                                        // The refresh cookie mechanism requires the 'formerRegistrationData' (which is intended to be what the callback was passed as an argumnet as a result of running generateCookie())
                                        // to contain keys 'loginCookie', 'refreshToken', and the alexa-tts-manager app expects the generateCookie function to be called with a map containing the key 'localCookie' (and possibly other keys).
                                        // If the non-proxy-based flow for generateAlexaCookie did succeed (i.e. if the above test for success were to pass), then callback would end up being called with a second argument being a map that is not suitable for future 
                                        // handling by the refresh cookie process.
                                        // This makes me think that the non-proxy-server-based flow for generateAlexaCookie() is outdated and no longer relevant.  I have observed that, in practice,
                                        // the non-proxy-server-based flow always results in Amazon's servers redirecting to a login page containing a captcha and failing the above test for success.
                                        //  I think the code here should be changed to get rid of the non-proxy based authentication flow (or at least giving it a fighting chance of returning a useable result).

                                        //It is worth noting that the refresh cookie process does not depend on the proxy server at all, and does not even invoke the proxy server as a fallback strategy in case of failure of the automatied strategy.
                                        // This makes me think that the cookie refresh process is probably much more reliable and well-understood than the process of initially acquiring the first formerRegistrationData.
                                    } else {
                                        String errMessage = 'Login unsuccessfull. Please check credentials.';
                                        java.util.regex.Matcher amazonMessageMatcher = (~/auth-warning-message-box[\S\s]*"a-alert-heading">([^<]*)[\S\s]*<li><[^>]*>\s*([^<\n]*)\s*</).matcher(response2Text);
                                        if(amazonMessageMatcher.find()){
                                            errMessage = "Amazon-Login-Error: ${amazonMessageMatcher.group(1)}: ${amazonMessageMatcher.group(2)}";
                                        }
                                        if (_options.setupProxy) {
                                            //TO-DO: present the user with the fallback option of using a proxy server (which we have not yet implemented)
                                        }
                                        callback && callback(errMessage, null);
                                        return;
                                    }
                                }
                            );                          
                        }
                    );
                }
            );
        }
    };

    Closure refreshAlexaCookie = {Map namedArgs -> 
        Map options = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback;
        // namedArgs is expected to have keys 'options' and 'callback'.
        // namedArgs.callback is expected to be a closure having signature void callback(String errorMessage, Map result) .
        // callback will be called with the errorMessage argument being non-null iff. some error has occured.
        // if no errors occur, callback will be called with the 'result' argument being a Map that will contain the keys
        // 'localCookie' (whose value can be messaged to create a cookie string suitabel for calling the Alexa speech api)
        // 'loginCookie' (whose value is used as part of future cookie refresh operations)
        // 'refreshToken' (whose value is used as part of future cookie refresh operations)

        //when making a future call to refreshAlexaCookie, it is expected that namedArgs.options.formerRegistrationData will be 
        // precisely the Map that, in the last call to refreshAlexaCookie, was passed as the 'result' argument to the callback function.

        // we require that we have namedArgs.options.formerRegistrationData.loginCookie and that we have namedArgs.options.formerRegistrationData.refreshToken .
        // If these two values are not available, then we cannot proceed.

        
        
        if(!(options.formerRegistrationData?.loginCookie && options.formerRegistrationData?.refreshToken )){
            callback && callback('No former registration data provided for Cookie Refresh', null);
            return;
        }

        _options = options;
        _options.proxyOnly = true; //it is not obvious that the _options.proxyOnly key serves any real purpose.
        initConfig();

        Map refreshData = [
            "app_name": "ioBroker Alexa2",
            "app_version": "2.2.223830.0",
            "di.sdk.version": "6.10.0",
            "source_token": _options.formerRegistrationData.refreshToken,
            "package_name": "com.amazon.echo",
            "di.hw.version": "iPhone",
            "platform": "iOS",
            "requested_token_type": "access_token",
            "source_token_type": "refresh_token",
            "di.os.name": "iOS",
            "di.os.version": "11.4.1",
            "current_version": "6.10.0"
        ];
        Cookie = _options.formerRegistrationData.loginCookie;
        Map requestParams = [
            uri: "https://api.amazon.com/auth/token",
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': _options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Cookie': Cookie,
                'Accept': 'application/json',
                'x-amzn-identity-auth-domain': 'api.amazon.com'
            ],
            contentType: groovyx.net.http.ContentType.JSON, //this influences the type of object that the system passes to the callback. ,
            requestContentType: groovyx.net.http.ContentType.URLENC, //this influences how the system treats the body of the request.   
            body: refreshData 
        ];
        
        _options.logger('Alexa-Cookie: Refresh Token');
        _options.logger(prettyPrint(requestParams));
        httpPost(requestParams,
            {response ->
                //TODO: handle response errors here (or maybe outside with a try{}catch(){} statement.)
                //TODO: handle malformed response data here.
                _options.logger('Refresh Token Response: ' + prettyPrint(response.data));
                _options.formerRegistrationData.loginCookie = addCookies(_options.formerRegistrationData.loginCookie, response.headers);
                if (!response.data.access_token) {
                    callback && callback('No new access token in Refresh Token response', null);
                    return;
                }
                _options.formerRegistrationData.accessToken = response.data.access_token;
                getLocalCookies(
                    amazonPage: 'amazon.com', 
                    refreshToken: _options.formerRegistrationData.refreshToken, 
                    callback: {String err, String comCookie -> 
                        if (err) {
                            callback && callback(err, null);
                            // In the original alexa-cookie.js, there was no return statement here.
                            // However, I am guessing that there ought to be one, because if 
                            // we have failed to getLocalCookies, we are screwed.
                            // Actually maybe that is not the case.
                            // Even if we have failed to get local cookies this time around,
                            // we might be able to make another attempt later.
                        }
                        //comment from original javascript: // Restore frc and map-md
                        _options.logger("_options.formerRegistrationData.loginCookie: " + _options.formerRegistrationData.loginCookie + "\n");
                        Map initCookies = cookie_parse(_options.formerRegistrationData.loginCookie);
                        _options.logger("initCookies: " + "\n" + prettyPrint(initCookies) + "\n\n");
                        String newCookie = 'frc=' + initCookies.frc + '; ';
                        newCookie += 'map-md=' + initCookies['map-md'] + '; ';
                        newCookie += comCookie ?: '';
                        _options.logger("newCookie: " + newCookie + "\n");
                        _options.formerRegistrationData.loginCookie = newCookie;
                        handleTokenRegistration(
                            options: _options, 
                            loginData: _options.formerRegistrationData, 
                            callback: callback
                        );
                    }
                );
            }
        );



    };

    return [
        'refreshAlexaCookie': refreshAlexaCookie,
        'generateAlexaCookie': generateAlexaCookie,
        //'addCookies': addCookies, //just for debugging
        //'cookie_parse': cookie_parse //just for debugging
    ].asImmutable();
}();
        
        alexaCookieUtility.refreshAlexaCookie(
            options: [
                logger: {log.debug("refreshCookie: " + it + "\n");},
                formerRegistrationData: (new groovy.json.JsonSlurper()).parseText(alexaRefreshOptions)
            ],
            callback: {String error, Map result ->
                if(error){
                    log.debug("error string that resulted from attempting to refresh the alexa cookie: " + error + "\n");
                    notifyIfEnabled("Alexa TTS: Error refreshing cookie, see logs for more information!");
                } else if(!result){
                    log.debug("alexaCookieUtility.refreshAlexaCookie did not return an explicit error, but returned a null result." + "\n");
                    notifyIfEnabled("Alexa TTS: Error refreshing cookie, see logs for more information!");
                } else {
                    log.debug("alexaCookieUtility.refreshAlexaCookie returned the following succesfull result: " + "\n" + groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result)) + "\n");
                    def newOptions = new groovy.json.JsonBuilder(result).toString()
                    app.updateSetting("alexaRefreshOptions",[type:"text", value: newOptions])
                    log.info("Alexa TTS: cookie downloaded succesfully")
                    app.updateSetting("alexaCookie",[type:"text", value: getCookieFromOptions(newOptions)])
                    sendEvent(name:"GetCookie", descriptionText: "New cookie downloaded succesfully")
                }
            }
        );
    }
    catch (e) {
        log.error "'refreshAlexaCookieWithoutRelyingOnTheNodeJsServer()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error refreshing the cookie locally, see logs for more information!")
    }
}

//TODO: insert calls to refreshAlexaCookieWithoutRelyingOnTheNodeJsServer() in the various error handling statements in refreshCookie() and getCookie(), above, so that,
// if the default cookie retrieval process fails in any way, we will then attempt the refreshAlexaCookieWithoutRelyingOnTheNodeJsServer() procedure as a fallback.
