/**
*   
*   File: pushover-notifications.groovy
*   Platform: Hubitat
*   Modification History:
*       Date       Who                   What
*       2018-03-11 Dan Ogorchock         Modified/Simplified for Hubitat
*       2018-03-23 Stephan Hackett       Added new preferences/features
*       2018-08-02 Dan and Stephan       Add contentType/requestContentType to httpPost calls (for FW v1.2.1)
*       2020-01-25 Dan Ogorchock         Added ImportURL Metadata & Minor code cleanup - no functionality changest
*       2020-08-13 Steven Dale (tmleafs) Added title and sound options from the message. encase your title in ^^ sound in ##, added default title to preferences
*       2020-09-23 Dan Ogorchock         Added support for [HTML] formatting of messages
*       2020-09-27 @s1godfrey            Added device name option from the message.  Encase your device name in **, e.g. "[L]*MyPhone*This is a test!"
*       2021-11-16 @Tsaaek               Added supplementary URL.  Encase your URL in §§, e.g. "[L]§http://example.com§ ¤Example¤This is a test!"
*       2021-11-16 @Tsaaek               Added supplementary URL Title  Encase your URL Title in ¤¤, e.g. "[L]§http://example.com§ ¤Example¤This is a test!"
*       2022-08-26 @Seattle              Added [OPEN] and [CLOSE] text substitutions for "<" and ">" as HSM was stripping those characters out
*       2022-10-05 Dan Ogorchock         Added option to enable/disable debug logging
*       2022-12-04 Neerav Modi           Added support for new Priority [S] for Lowest Priority (-2) see https://pushover.net/api#priority for details
*       2024-07-29 @woodsby              Added image URL support - based on code posted by @younes.  Encase your URL in ¨¨, e.g. "¨http://example.com/example.jpeg¨"
*       2024-08-16 Dan Ogorchock         Fixed typo for 'deviceName' found in the 20240729 version
*       2024-08-17 Dan Ogorchock         Corrected function prototype for 'speak()' command to avoid runtime error when optional args are submitted
*       2024-09-27 @ritchierich          Added support for carriage returns by entering '\n' within the message
*       2025-01-10 @garz                 Added ability to include Emergency RETRY Interval (&...&) and EXPIRE timeout (%...%) embedded in the message.
*       2024-01-12 Dan Ogorchock         Changed embeeded character as follows - Emergency RETRY Interval (©...©) and EXPIRE timeout (™...™) - to prevent conflicts in Rule Machine
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
def version() {return "v1.0.20250112"}

metadata {
    definition (name: "Pushover", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/pushover-notifications.src/pushover-notifications.groovy") {
        capability "Notification"
        capability "Actuator"
        capability "Speech Synthesis"
    }
    
    preferences {
        input("apiKey", "text", title: "API Key:", description: "Pushover API Key")
        input("userKey", "text", title: "User Key:", description: "Pushover User Key")
        if(getValidated()){
            input("deviceName", "enum", title: "Device Name (Blank = All Devices):", description: "", multiple: true, required: false, options: getValidated("deviceList"))
            input("priority", "enum", title: "Default Message Priority (Blank = NORMAL):", description: "", defaultValue: "0", options:[["-1":"LOW"], ["0":"NORMAL"], ["1":"HIGH"]])
            input("sound", "enum", title: "Notification Sound (Blank = App Default):", description: "", options: getSoundOptions())
            input("url", "text", title: "Supplementary URL:", description: "")
            input("urlTitle", "text", title: "URL Title:", description: "")
            input("retry", "number", title: "Retry Interval in seconds:(30 minimum)", description: "Applies to Emergency Requests Only")
            input("expire", "number", title: "Auto Expire After in seconds:(10800 max)", description: "Applies to Emergency Requests Only")
        }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
    initialize()
}

def updated() {
    initialize()
    
    if (logEnable) {
        log.info "Enabling Debug Logging for 30 minutes" 
        runIn(1800,logsOff)
    } else {
        unschedule(logsoff)
    }

}

def initialize() {
    state.version = version()
}

def getValidated(type){
    if(type=="deviceList"){if (logEnable) log.debug "Generating Device List..."}
    else {if (logEnable) log.debug "Validating Keys..."}
    
    def validated = false
    
    def postBody = [
        token: "$apiKey",
        user: "$userKey",
        device: ""
    ]
    
    def params = [
        uri: "https://api.pushover.net/1/users/validate.json",
        contentType: "application/json",
        requestContentType: "application/x-www-form-urlencoded",
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
                        if (logEnable) log.debug "Device list generated"
                        deviceOptions = response.data.devices
                    }
                    else {
                        if (logEnable) log.debug "Keys validated"
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
    if (logEnable) log.debug "Generating Notification List..."
    def myOptions =[]
    httpGet(uri: "https://api.pushover.net/1/sounds.json?token=${apiKey}"){response ->
        if(response.status != 200) {
            sendPush("ERROR: 'Pushover Me When' received HTTP error ${response.status}. Check your keys!")
            log.error "Received HTTP error ${response.status}. Check your keys!"
        }
        else {
            if (logEnable) log.debug "Notification List Generated"
            mySounds = response.data.sounds
            mySounds.each {eachSound->
            myOptions << ["${eachSound.key}":"${eachSound.value}"]
            }
        }
   	}   
    return myOptions
}

def speak(message, volume = null, voice = null) {
    deviceNotification(message)
}

def deviceNotification(message) {
    if(message.startsWith("[S]")){ 
        customPriority = "-2"
        message = message.minus("[S]")
    }    
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
    if(message.startsWith("[E]")){
        customPriority = "2"
        message = message.minus("[E]")
    }
    if(customPriority){ priority = customPriority}
 
    def html = "0"    
    if(message.contains("[HTML]")){ 
        html = "1"
        message = message.minus("[HTML]")
        if(message.contains("[OPEN]")){
            message = message.replace("[OPEN]","<")
        }
        if(message.contains("[CLOSE]")){
            message = message.replace("[CLOSE]",">")
        }
    }
    
    if((matcher = message =~ /\^(.*?)\^/)){                   
        message = message.minus("^${matcher[0][1]}^")
        message = message.trim() //trim any whitespace
        customTitle = matcher[0][1]
    }
    if(customTitle){ title = customTitle}
        
    if((matcher = message =~ /\#(.*?)\#/)){               
        message = message.minus("#${matcher[0][1]}#")      
        message = message.trim() //trim any whitespace
        customSound = matcher[0][1]
        customSound = customSound.toLowerCase()
    }
    if(customSound){ sound = customSound}

    if((matcher = message =~ /\*(.*?)\*/)){               
        message = message.minus("*${matcher[0][1]}*")      
        message = message.trim() //trim any whitespace
        customDevice = matcher[0][1]
        customDevice = customDevice.toLowerCase()      
    }
    if(customDevice){ deviceName = customDevice}
    
    if((matcher = message =~ /\§(.*?)\§/)){               
        message = message.minus("§${matcher[0][1]}§")      
        message = message.trim() //trim any whitespace
        customUrl = matcher[0][1]
    }
    if(customUrl){ url = customUrl}

    if((matcher = message =~ /\¤(.*?)\¤/)){               
        message = message.minus("¤${matcher[0][1]}¤")      
        message = message.trim() //trim any whitespace
        customUrlTitle = matcher[0][1]   
    }
    if(customUrlTitle){ urlTitle = customUrlTitle}    
    
    if((matcher = message =~ /\¨(.*?)\¨/)){               
        message = message.minus("¨${matcher[0][1]}¨")      
        message = message.trim() //trim any whitespace
        customImageUrl = matcher[0][1]   
    }

    // New Retry and Expire Code
    if((matcher = message =~ /\©(.*?)\©/)){
        message = message.minus("©${matcher[0][1]}©")
        message = message.trim()
        customRetry = matcher[0][1]
    }
    if(customRetry){ 
        retry = customRetry
        if (retry.toInteger() < 30){ retry = 30 }
    }
    
    if((matcher = message =~ /\™(.*?)\™/)){
        message = message.minus("™${matcher[0][1]}™")
        message = message.trim()
        customExpire = matcher[0][1]
    }
    if(customExpire){  
        expire = customExpire
        if (expire.toInteger() < 30){ expire = 30 }
        if (expire.toInteger() > 10800){ expire = 10800 }
    }
 
    // End new code
    
    if (message.indexOf("\\n") > -1) {
        message = message.replace("\\n", "<br>")
        html = "1"
    }
    if(customImageUrl){ imageUrl = customImageUrl }

    if (imageUrl) {
        log.debug "Getting Notification Image"
        httpGet("${imageUrl}")  //modify as needed for authentication header
        { response ->
            imageData = response.data
            log.debug "Notification Image Received (${imageData.available()})"
        }
    }
    
    //Top Part of the POST request Body
    def postBodyTop = """----d29vZHNieQ==\r\nContent-Disposition: form-data; name="user"\r\n\r\n$userKey\r\n----d29vZHNieQ==\r\nContent-Disposition: form-data; name="token"\r\n\r\n$apiKey\r\n----d29vZHNieQ==\r\n"""
    if (title) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="title"\r\n\r\n${title}\r\n----d29vZHNieQ==\r\n"""
    }
    if (url) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="url"\r\n\r\n${url}\r\n----d29vZHNieQ==\r\n"""
    }
    if (urlTitle) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="url_title"\r\n\r\n${urlTitle}\r\n----d29vZHNieQ==\r\n"""
    }
    if (deviceName) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="device"\r\n\r\n${deviceName}\r\n----d29vZHNieQ==\r\n"""
    }
    if (sound) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="sound"\r\n\r\n${sound}\r\n----d29vZHNieQ==\r\n"""
    }
    if (priority) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="priority"\r\n\r\n${priority}\r\n----d29vZHNieQ==\r\n"""
    }
    if (retry) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="retry"\r\n\r\n${retry}\r\n----d29vZHNieQ==\r\n"""
    }
    if (expire) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="expire"\r\n\r\n${expire}\r\n----d29vZHNieQ==\r\n"""
    }
    if (html) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="html"\r\n\r\n${html}\r\n----d29vZHNieQ==\r\n"""
    }
    if (message == ""){ message = Character.toString ((char) 128) }
    postBodyTop = postBodyTop + """Content-Disposition: form-data; name="message"\r\n\r\n${message}\r\n----d29vZHNieQ==\r\n"""
    if (imageData) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="attachment"; filename="image.jpg"\r\nContent-Type: image/jpeg\r\n\r\n"""
    } else {
        postBodyTop = postBodyTop + """\r\n"""
    }

    //Bottom Part of the POST request Body
    def postBodyBottom = """\r\n----d29vZHNieQ==--"""

    byte[] postBodyTopArr = postBodyTop.getBytes("UTF-8")
    byte[] postBodyBottomArr = postBodyBottom.getBytes("UTF-8")

    //Combine different parts of the POST request body
    ByteArrayOutputStream postBodyOutputStream = new ByteArrayOutputStream();

    postBodyOutputStream.write(postBodyTopArr);
    if (imageData) {
        def bSize = imageData.available()
        byte[] imageArr = new byte[bSize]
        imageData.read(imageArr, 0, bSize)
        postBodyOutputStream.write(imageArr);
    }
    postBodyOutputStream.write(postBodyBottomArr);
    byte[] postBody = postBodyOutputStream.toByteArray();

    //Build HTTP Request Parameters
    def params = [
	    requestContentType: "application/octet-stream",
    	headers: ["content-type": "multipart/form-data; boundary=--d29vZHNieQ=="],
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
                if (logEnable) log.debug "Message Received by Pushover Server"
        }
        }
    }
    else {
        // Do not sendPush() here, the user may have intentionally set up bad keys for testing.
        log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
    }
}
