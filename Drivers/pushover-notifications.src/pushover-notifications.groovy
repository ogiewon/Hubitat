/*
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
*       2025-01-12 Dan Ogorchock         Changed embeeded character as follows - Emergency RETRY Interval (©...©) and EXPIRE timeout (™...™) - to prevent conflicts in Rule Machine
*       2025-03-17 @hubitrep             Rearchitected use of Pushover API to prevent throttling - implemented caching of Pushover Devices and Sounds lists
*       2025-08-05 @neerav.modi          Fix Emergency retry/expire and TTL, added new style of embedding options
*       2026-01-21 Dan Ogorchock         Minor code cleanup, update version number, minor bug fixes, added usage to the comments section
*       2026-01-26 Dan Ogorchock         Added "ALL" to list of Pushover Devices, since there is no way to unselect a device once selected
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
*  Usage:
*      Priority still works the same [E] , [H] , [N] , [L] , [S] -- Must be at the beginning
*      [HTML] -- flags the message as an HTML message
*      [TITLE=messagetitle] -- message title (equivalent to ^messagetitle^)
*      [SOUND=soundname] -- custom notification sound (equivalent to #soundname#)
*      [DEVICE=devicename] -- send to specific named Pushover device (equivalent to *devicename*)
*      [URL=http://google.com] -- clickable URL (equivalent to §http://google.com§)
*      [URLTITLE=Google] -- short display name for the URL (equivalent to ¤Google¤)
*      [IMAGE=imageurl] -- url and path to replacement notification icon (equivalent to ¨imageurl¨)
*      [EM.RETRY=x] -- for emergency priority, how often does it get "sent" in x seconds (equivalent to ©retryinterval©)
*      [EM.EXPIRE=y] -- for emergency priority, when should repeating stop in y seconds, even if not acknowledged (equivalent to ™expirelength™)
*      [SELFDESTRUCT=z] -- auto delete message in z seconds (no equivalent)
*      \n -- line breaks in HTML messages. can also use ≤br≥ using the custom HTML characters feature below.
*      
*      Set the custom HTML open and close characters to use additional HTML formatting. The default character is ≤ and ≥ (equivalent to [OPEN] and [CLOSE])
*      
*      ≤b≥ and ≤/b≥ -- for bold
*      ≤i≥ and ≤/i≥ -- for italics
*      ≤u≥ and ≤/u≥ -- for underline
*      ≤font color="#FF0000"≥ and ≤/font≥ -- for red colored text
*
*      There is a preference called Testing. Turning this on does all of the formatting above, but does not send the message as HTML. Useful for troubleshooting, testing, or code examples.
*      
*      There is a command to get messaging limits and when the limit resets. Can be used in a Rule. The results are stored in custom attributes and are also accessible in Rules.
*/

import java.text.SimpleDateFormat

def version() {return "v1.0.20260126"}

metadata {
    definition (name: "Pushover", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/pushover-notifications.src/pushover-notifications.groovy", singleThreaded:true) {
        capability "Notification"
        capability "Actuator"
        capability "Speech Synthesis"

		command "getMsgLimits", [
            [name: "Get Messaging Limits", description: "Update the message limits information"]
        ]
        
        attribute "messageLimit","Number"
        attribute "messagesRemaining","Number"
        attribute "limitReset","Number"
        attribute "limitResetDate","String"  
        attribute "limitLastUpdated","String"  
             
    }

    preferences {
        input name: "apiKey", type: "text", title: "API Key:", description: "Pushover API Key", required: true
        input name: "userKey", type: "text", title: "User Key:", description: "Pushover User Key", required: true
        input name: "cacheRefreshInterval", type: "number", title: "Set cache refresh interval (seconds)", description: "Must be high enough to avoid throttling by Pushover API server", required: true, range: "30..10800", defaultValue: 30
       	if (keyFormatIsValid()) {
            def deviceOptions = getCachedDeviceOptions()
            if (deviceOptions) {
                input name: "deviceName", type: "enum", title: "Device Name:", description: "Select ALL to send to all Pushover devices", defaultValue: "ALL", multiple: true, required: false, options: deviceOptions
                input name: "priority", type: "enum", title: "Default Message Priority (Blank = NORMAL):", description: "", defaultValue: "0", options:[["-1":"LOW"], ["0":"NORMAL"], ["1":"HIGH"]]
                def soundOptions = getCachedSoundOptions()
                input name: "sound", type: "enum", title: "Notification Sound (Blank = App Default):", description: "", options: soundOptions
                input name: "url", type: "text", title: "Supplementary URL:", description: ""
                input name: "urlTitle", type: "text", title: "URL Title:", description: ""
                input name: "ttl", type: "number", title: "Message Auto Delete After, in seconds", description: "Number of seconds message will live, before being deleted automatically.  Applies ONLY to Non-Emergency messages."
                input name: "retry", type: "number", title: "Emergency Retry Interval in seconds:(minimum: 30)", description: "Applies to Emergency Requests Only"
                input name: "expire", type: "number", title: "Emergency Auto Expire After in seconds:(maximum: 10800)", description: "Applies to Emergency Requests Only"
            }
    	}
		input name: "htmlOpen", type: "text", title: "HTML tag < character: ", description: "HE cleanses < and > characters from text input boxes.  Use this character or sequence as a substitute. (default: ≤) Ensure this is different from what is defined as >", defaultValue: "≤"
		input name: "htmlClose", type: "text", title: "HTML tag > character: ", description: "HE cleanses < and > characters from text input boxes.  Use this character or sequence as a substitute. (default: ≥) Ensure this is different from what is defined as <", defaultValue: "≥"
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "testingEnable", type: "bool", title: "Testing mode?  Sends messages with HTML markup as plain text.", defaultValue: false

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
        unschedule(logsOff)
    }

    if (testingEnable) {
        log.info "Testing mode enabled.  Messages will be sent as plain text instead of HTML."
    }
}

def initialize() {
    state.version = version()

    // key tracking
    atomicState.lastApiKey = ""
    atomicState.lastUserKey = ""

    // Separate timestamps and caches for each method
    atomicState.lastDeviceOptionsFetch = 0
    atomicState.lastSoundOptionsFetch = 0
    atomicState.cachedDeviceOptions = null
    atomicState.cachedSoundOptions = null
    
    //  Needs more input cleansing. 
    if (htmlOpen == null || htmlClose == null 
        || htmlOpen == '' || htmlClose == '' 
        || htmlOpen =~ /[\s\[\]\\]/ || htmlClose =~ /[\s\[\]\\]/ 
    ) {
    	htmlOpen = "≤"
    	htmlClose = "≥"
    }
}

private boolean keyFormatIsValid() {
    if (apiKey?.matches('[A-Za-z0-9]{30}') && userKey?.matches('[A-Za-z0-9]{30}')) {
        return true
    }
    else {
        log.warn "keyFormatIsValid() - API key '${apiKey}' or USER key '${userKey}' is not properly formatted!"
        return false
    }
}

// Check if keys have changed and handle invalidation of all caches if they have
private boolean checkAndHandleKeyChanges() {
    if (atomicState.lastApiKey != apiKey || atomicState.lastUserKey != userKey) {
        if (logEnable) log.debug "API keys have changed, invalidating all caches"

        // Update the stored keys
        atomicState.lastApiKey = apiKey
        atomicState.lastUserKey = userKey

        // Explicitly invalidate all caches
        atomicState.cachedDeviceOptions = null
        atomicState.cachedSoundOptions = null
        atomicState.lastDeviceOptionsFetch = 0
        atomicState.lastSoundOptionsFetch = 0

        return true
    }
    return false
}

def getDeviceOptions(){
    if (logEnable) log.debug "Validating Keys and Generating Device List..."

    def deviceOptions = []

    if (keyFormatIsValid()) {
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

        try{
            httpPost(params){response ->
                if(response.status != 200) {
                    log.error "Received HTTP error ${response.status}. Check your keys!"
                }
                else {
                    if (logEnable) log.debug "Device list generated: ${response.data.devices}"
                    //deviceOptions = response.data.devices
                    deviceOptions.add("ALL")
                    response.data.devices.each {eachDevice->
                        deviceOptions.add("${eachDevice}")
                    }
                    if (logEnable) log.debug "deviceOptions = ${deviceOptions}"
                }
            }
        }
        catch (Exception e) {
            log.error "PushOver Server Returned: ${e}"
        }
    }
    else {
        log.error "GetDeviceOptions() - API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
    }

    return deviceOptions
}

// Create a cached wrapper for getDeviceOptions()
def getCachedDeviceOptions() {
    // First check if keys have changed (will invalidate all caches if they have)
    checkAndHandleKeyChanges()

    def currentTime = now()
    def cacheTimeout = 30 * 1000   //default needed for initial device creation execution of this code
    if (cacheRefreshInterval != null) cacheTimeout = cacheRefreshInterval * 1000 // in milliseconds

    // Now check if our specific cache needs refreshing
    if (atomicState.cachedDeviceOptions == null ||
        (currentTime - atomicState.lastDeviceOptionsFetch) > cacheTimeout) {

        if (logEnable) log.debug "Device options cache expired, fetching fresh data..."

        // Update our cache
        atomicState.cachedDeviceOptions = getDeviceOptions()
        atomicState.lastDeviceOptionsFetch = currentTime

        if (logEnable) log.debug "Cache updated with new device options"
	} else {
        if (logEnable) log.debug "Using cached device options (age=${currentTime - atomicState.lastDeviceOptionsFetch})"
    }

    return atomicState.cachedDeviceOptions
}

def getSoundOptions() {
    if (logEnable) log.debug "Generating Notification List..."
    def myOptions =[]
    if (keyFormatIsValid()) {
        try{
            httpGet(uri: "https://api.pushover.net/1/sounds.json?token=${apiKey}"){response ->
                if(response.status != 200) {
                    log.error "Received HTTP error ${response.status}. Check your keys!"
                }
                else {
                    if (logEnable) log.debug "Notification List Generated: ${response.data.sounds}"
                    mySounds = response.data.sounds
                    mySounds.each {eachSound->
                    myOptions << ["${eachSound.key}":"${eachSound.value}"]
                    }
                }
            }
        }
        catch (Exception e) {
            log.error "Error retrieving sound options - PushOver Server Returned: ${e}"
        }
    }
    else {
        log.error "GetSoundsOptions() - API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
    }

    return myOptions
}

def getCachedSoundOptions() {
    // First check if keys have changed (will invalidate all caches if they have)
    checkAndHandleKeyChanges()

    def currentTime = now()
    def cacheTimeout = 30 * 1000   //default needed for initial device creation execution of this code
    if (cacheRefreshInterval != null) cacheTimeout = cacheRefreshInterval * 1000 // in milliseconds

    // Now check if our specific cache needs refreshing
    if (atomicState.cachedSoundOptions == null ||
        (currentTime - atomicState.lastSoundOptionsFetch) > cacheTimeout) {

        if (logEnable) log.debug "Sound options cache expired, fetching fresh data..."

        // Get fresh sound options
        def soundOptions = getSoundOptions()

        // Update our cache atomically
        atomicState.cachedSoundOptions = soundOptions
        atomicState.lastSoundOptionsFetch = currentTime

        if (logEnable) log.debug "Cache updated with new sound options"
    } else {
        if (logEnable) log.debug "Using cached sound options (age=${currentTime - atomicState.lastSoundOptionsFetch})"
    }

    return atomicState.cachedSoundOptions
}

def speak(message, volume = null, voice = null) {
    deviceNotification(message)
}

def deviceNotification(message) {

    if (logEnable){
        log.debug "Pushover driver raw message: " + message
    }

    // Message priority
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
    if(customPriority){ 
    	priority = customPriority
    	if (logEnable) log.debug "Pushover processed priority (${priority}): " + message
    }

    def html = "0"
    // Uncomment both of the following lines to override Preferences
    //def htmlOpen = "«"
    //def htmlClose = "»"
    if(message.contains("[HTML]")){
        html = "1"
        message = message.minus("[HTML]")
        if(message.contains("[OPEN]")){
            message = message.replace("[OPEN]","<")
        }
        if(message.contains("[CLOSE]")){
            message = message.replace("[CLOSE]",">")
        }
        if(message.contains("${htmlOpen}")){
            message = message.replace(htmlOpen,"<")
        }
        if(message.contains("${htmlClose}")){
            message = message.replace(htmlClose,">")
        }
        if (logEnable) log.debug "Pushover processed HTML: " + message
    }

    // Title
    if(( matcher = message =~ /((\^|\[TITLE=)(.*?)(\^|\]))/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
    	customTitle = matcher[0][3]
    }
    if(customTitle){ title = customTitle}
	if (logEnable && title != null) log.debug "Pushover processed title (${title}): " + message

    // Sound
    // Needs to be separated into two regexes to protect against greedy matches when using
    // a font color AND a sound, resulting in the cutting off a message
    //if((matcher = message =~ /((\#|\[SOUND=)(.*?)(\#|\]))/ )){
    if((matcher = message =~ /(\#([A-Za-z0-9_\-]{1,20})\#)/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customSound = matcher[0][2]
        customSound = customSound.toLowerCase()    
    } else if ((matcher = message =~ /(\[SOUND=(.*?)\])/ )) {
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customSound = matcher[0][2]
        customSound = customSound.toLowerCase()
    } 
    if(customSound){ sound = customSound}
	if (logEnable && sound != null) log.debug "Pushover processed sound (${sound}): " + message

    // Device
    if((matcher = message =~ /((\*|\[DEVICE=)(.*?)(\*|\]))/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customDevice = matcher[0][3]
        customDevice = customDevice.toLowerCase()
    }
    if(customDevice){ deviceName = customDevice}
    if (logEnable && deviceName != null) log.debug "Pushover processed device (${deviceName}): " + message

    // URL
    if((matcher = message =~ /((\§|\[URL=)(.*?)(\§|\]))/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customUrl = matcher[0][3]
    }
    if(customUrl){ url = customUrl}
    if (logEnable && url != null) log.debug "Pushover processed URL (${url}): " + message

    // URL title
    if((matcher = message =~ /((\¤|\[URLTITLE=)(.*?)(\¤|\]))/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customUrlTitle = matcher[0][3]
    }
    if(customUrlTitle){ urlTitle = customUrlTitle}
    if (logEnable && urlTitle != null) log.debug "Pushover processed URL Title (${urlTitle}): " + message

    // Image
    if((matcher = message =~ /((\¨|\[IMAGE=)(.*?)(\¨|\]))/ )){
        message = message.minus("${matcher[0][1]}")
        message = message.trim() //trim any whitespace
        customImageUrl = matcher[0][3]
    }
    if(customImageUrl){ imageUrl = customImageUrl }
    if (logEnable && imageUrl != null) log.debug "Pushover processed image (${imageUrl}): " + message

    // Retrieve image
    if (imageUrl) {
        log.debug "Getting Notification Image"
        try {
            httpGet("${imageUrl}")  //modify as needed for authentication header
            { response ->
                imageData = response.data
                log.debug "Notification Image Received (${imageData.available()})"
            }
        } catch (Exception e) {
            log.warn "Error retrieving notification image: ${e.message}"
        }
    }

    // New Retry and Expire Code
    if (priority == "2") {
        // Emergency retry interval
        if((matcher = message =~ /((\©|\[EM.RETRY=)(.*?)(\©|\]))/ )){
            message = message.minus("${matcher[0][1]}")
            message = message.trim()
            customRetry = matcher[0][3]
        }
        if(customRetry){
            retry = customRetry
            if (retry.toInteger() < 30){ retry = 30 }
            if (logEnable) log.debug "Pushover processed emergency return (${retry}): " + message
        }

        // Emergency message expiration
        if((matcher = message =~ /((\™|\[EM.EXPIRE=)(.*?)(\™|\]))/ )){
            message = message.minus("${matcher[0][1]}")
            message = message.trim()
            customExpire = matcher[0][3]
        }
        if(customExpire){
            expire = customExpire
            if (expire.toInteger() < 30){ expire = 30 }
            if (expire.toInteger() > 10800){ expire = 10800 }
            if (logEnable) log.debug "Pushover processed emergency expire (${expire}): " + message
        }
	
    }
    // End new code

    // TTL Time to Live
    if (priority != "2") {
        if((matcher = message =~ /(\[SELFDESTRUCT=(\d+)\])/ )){
            message = message.minus("${matcher[0][1]}")
            message = message.trim()
            customTtl = matcher[0][2]
        }
        if(customTtl){
            ttl = customTtl
            if (ttl.toInteger() < 0){ ttl = 0 }
            if (logEnable) log.debug "Pushover processed TTL (${ttl}): " + message
        }
    }

    // Newline -> HTML line break
    if (message.indexOf("\\n") > -1) {
        message = message.replace("\\n", "<br>")
        html = "1"
        if (logEnable) log.debug "Pushover processed newlines: " + message
    }

    // Send message as plain text instead of HTML
    if (testingEnable) { html = "0" 
        if (logEnable) log.debug "Testing mode is ON.  Message and any HTML tags will be sent in plain text."
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
    if (deviceName == "ALL") { deviceName = null }
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
    if (ttl) {
        postBodyTop = postBodyTop + """Content-Disposition: form-data; name="ttl"\r\n\r\n${ttl}\r\n----d29vZHNieQ==\r\n"""
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

    if (logEnable) {
        //log.debug "Pushover message top: " + message
        log.debug "Pushover final message: " + message
        //log.debug "Pushover message bottom: " + message
    }			

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

    if (keyFormatIsValid()) {
        try {
            httpPost(params) { response ->
                if(response.status != 200) {
                    log.error "Received HTTP error ${response.status}. Check your keys!"
                }
                else {
                    if (logEnable) log.debug "Message Received by Pushover Server"
                }
            }
        }
        catch (Exception e) {
            log.error "PushOver Server Returned: ${e}"
	    }
    }
    else {
        log.error "deviceNotification() - API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
		return
    }
}

def getMsgLimits() {
    if (logEnable) log.debug "Sending GET request: https://api.pushover.net/1/apps/limits.json?token=...${state.lastApiKey?.substring(25,30)}"
   
	uri = "https://api.pushover.net/1/apps/limits.json?token=${state.lastApiKey}"
	
    try {
        httpGet(uri) { response ->
            if(response.status != 200) {
                log.error "Received HTTP error ${response.status}. Check your keys!"
            }
            else {
            	if (logEnable) log.debug "${response.data}"
            	sendEvent(name:"messageLimit", value: "${response.data.limit}")
            	sendEvent(name:"messagesRemaining", value: "${response.data.remaining}")
            	sendEvent(name:"limitReset", value: "${response.data.reset}")
            	SimpleDateFormat sdf = new SimpleDateFormat("dd MMM YYYY, HH:mm a")
                epoch = (long) response.data.reset*1000
                rDate = new Date(epoch)
                sendEvent(name:"limitResetDate", value: sdf.format(rDate))
                sendEvent(name:"limitLastUpdated", value: sdf.format(now()))
            }
        }
    } catch (Exception e) {
        log.warn "Error retrieving message limits failed: ${e.message}"
    }
}
