/**
 *
 *  File: Logitech-Harmony-Hub-Parent.groovy
 *  Platform: Hubitat
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/logitech-harmony-hub-parent.src/logitech-harmony-hub-parent.groovy
 *
 *  Requirements:
 *     1) Logitech Harmony Home Hub connected to same LAN as your Hubitat Hub.  Use router
 *        DHCP Reservation to prevent IP address from changing.
 *     2) HubDuino "Child Switch" Driver is also necessary.  This is available
 *        at https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino/Drivers
 *
 *  Copyright 2018 Dan G Ogorchock 
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
 *    2018-12-25  Dan Ogorchock  Original Creation
 *    2018-12-27  Dan Ogorchock  Fixes to correct hub reboot issue
 *    2019-01-04  Dan Ogorchock  Faster updates to Child Switch Devices to prevent Alexa "Device is not repsonding" message
 *
 *
 */

def version() {"v0.1.20190104"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "Logitech Harmony Hub Parent", namespace: "ogiewon", author: "Dan Ogorchock") {
        capability "Initialize"
        capability "Refresh"

        //command "sendMsg", ["String"]
        //command "getConfig"
        //command "startActivity", ["String"]
        //command "stopActivity"
        command "getCurrentActivity"
        
        attribute "Activity","String"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "IP Address", required: true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}


def parse(String description) {
    //log.debug "parsed $description"
    def json = null;
    try{
        json = new groovy.json.JsonSlurper().parseText(description)
        //log.debug "${json}"    
        if(json == null){
            log.warn "String description not parsed"
            return
        }
    }  catch(e) {
        log.error("Failed to parse json e = ${e}")
        return
    }
    
    //def results = []
    //Retrieves the Harmony device configuration, including all Activity Names and IDs
    if (json?.cmd == "vnd.logitech.harmony/vnd.logitech.harmony.engine?config") {
        if (json?.msg == "OK") {
            json?.data?.activity?.each { it ->
                def tempID = (it.id == "-1") ? "PowerOff" : "${it.id}"                    
                if (logEnable) log.debug "Activity Label: ${it.label}, ID: ${tempID}"
                
                //Create a Child Switch Device for each Activity if needed, default all of them to 'off' for now
                updateChild(tempID, "unknown", it.label)
            }
        } else {
            log.error "Received msg = '${json?.msg}' and code = '${json?.code}' from Harmony Hub"
        }
    } 
    //Retrieves the Harmony Start Activity Finished event
    else if (json?.type == "harmony.engine?startActivityFinished") {
        if ((json?.data?.errorString == "OK") && (json?.data?.errorCode == "200")) {
            if (logEnable) log.debug "Harmony Activity Started - activityID: ${json?.data?.activityId}"
            
            updateChildren(json?.data?.activityId)
            
        } else {
            log.error "Received errorString = '${json?.data?.errorString}' and errorCode = '${json?.data?.errorCode}' from Harmony Hub"
        }
    }
    //Retrieves changes to activities as they happen.  Status = 1 is the start of a change.  Status = 2 is the end of the change.  
    else if (json?.type == "connect.stateDigest?notify") {
        if (logEnable) log.debug "Harmony Activity Digest - activityID: ${json?.data?.activityId}, runningActivityList: ${json?.data?.runningActivityList}, activityStatus: ${json?.data?.activityStatus}"

        if (json?.data?.activityStatus == 1) {
        	updateChildren(json?.data?.activityId)
        }
    }
    // ????
    else if (json?.cmd == "vnd.logitech.connect/vnd.logitech.statedigest?get") {
        if ((json?.msg == "OK") && (json?.code == 200)) {
            if (logEnable) log.debug "Harmony Activity Stated Digest - activityID: ${json?.data?.activityId}, runningActivityList: ${json?.data?.runningActivityList}, activityStatus: ${json?.data?.activityStatus}"   

            //TODO - ????

        } else {
            log.error "Received msg = '${json?.msg}' and code = '${json?.code}' from Harmony Hub"
        }
    } 
    //Retrieves the current activity on demand
    else if (json?.cmd == "vnd.logitech.harmony/vnd.logitech.harmony.engine?getCurrentActivity") {
        if ((json?.msg == "OK") && (json?.code == 200)) {
            if (logEnable) log.debug "Current Harmony Activity result: ${json?.data?.result}"
            
            updateChildren(json?.data?.result)

        } else {
            log.error "Received msg = '${json?.msg}' and code = '${json?.code}' from Harmony Hub"
        }
    }
    else {
        if ((json?.cmd != "harmony.engine?startActivity") && (json?.cmd != "harmony.activityengine?runactivity")) {
            log.info "Unhandled data from Harmony Hub. json = ${description}"
        }
    }
    
    //return results
    
}


def updateChildren(String ActivityID) {
    //Switch Child Device States based on the return value.  If "-1" turn off all child activity devices
    def tempID = (ActivityID == "-1") ? "PowerOff" : ActivityID
    try {
        childDevices.each{ it ->
            def childDNI = it.deviceNetworkId.split("-")[-1]
            if(childDNI == "${tempID}")
            {
                updateChild(childDNI, "on")
                sendEvent(name: "Activity", value: "${it.name}", isStateChange: true)
            }
            else {
                updateChild(childDNI, "off")    
            }
        }
    }
    catch(e) {
        log.error "Failed to find child without exception: ${e}";
    }    
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    log.info "refresh() called"
    
    //Retrieve Harmony Hub Configuration Data to create Activities
    getConfig()
    
    //Get the current activity to make sure the child devices are synched with the harmony hub
    getCurrentActivity()    
}

def installed() {
    log.info "installed() called"
    updated()
}

def updated() {
    log.info "updated() called"
    //Unschedule any existing schedules
    unschedule()
    
    //Create a 30 minute timer for debug logging
    if (logEnable) runIn(1800,logsOff)
    
    //Connect the webSocket
    initialize()
    
    //Retrieve Harmony Hub Configuration Data to create Activities
    getConfig()
    
    //Get the current activity to make sure the child devices are synched with the harmony hub
    getCurrentActivity()
}

def initialize() {
    state.version = version()
    log.info "initialize() called"
    
    if (!ip) {
        log.warn "Harmony Hub IP Address not configured yet"
        return
    }
    
    //Make sure we know the remoteId of the Harmony Hub
    if (state.remoteId == null) {
        httpPost(uri: "http://${ip}:8088",
                 path: '/',
                 contentType: 'application/json',
                 requestContentType: 'application/json',
                 headers: ['Origin': 'http//:localhost.nebula.myharmony.com'],
                 body: '{"id": 124, "cmd": "connect.discoveryinfo?get", "params": {}}'
                ) { response ->
            log.debug "hub remote id: ${response.data.data.remoteId}"
            state.remoteId = response.data.data.remoteId
        }
    }
    
    //Connect the webSocket to the Harmony Hub
    try {
        InterfaceUtils.webSocketConnect(device, "ws://${ip}:8088/?domain=svcs.myharmony.com&hubId=${state.remoteId}")
    } 
    catch(e) {
        if (logEnable) log.debug "initialize error: ${e.message}"
        log.error "WebSocket connect failed"
    }
}

def getConfig() {
    if(!state.remoteId) return
    
    //Not sure what this is used for
    //sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.connect/vnd.logitech.deviceinfo?get","id":"0","params":{"verb":"get"}}}')   
    //Refresh current status - Using getCurrentActivity instead
    //sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.connect/vnd.logitech.statedigest?get","id":"0","params":{"verb":"get","format":"json"}}}')
    
    //Get Activities and a whole lot more data from the Harmony Hub
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":60,"hbus":{"cmd":"vnd.logitech.harmony/vnd.logitech.harmony.engine?config","id":"0","params":{"verb":"get"}}}')
}

def startActivity(String activityID) {
    if(!state.remoteId) return
    
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"harmony.activityengine?runactivity","id":"0","params":{"async": "true","timestamp": 0,"args": {"rule": "start"},"activityId": "' + activityID + '"}}}')
}

def stopActivity() {
    if(!state.remoteId) return
    log.debug "stopActivity() called..."
    startActivity("-1")
}

def getCurrentActivity() {
    if(!state.remoteId) return
    
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.harmony/vnd.logitech.harmony.engine?getCurrentActivity","id":"0","params":{"verb": "get","format": "json"}}}')
}

def sendMsg(String s) {
    InterfaceUtils.sendWebSocketMessage(device, s)
}

//sendData() is called from the Child Devices to start/stop activities
def sendData(message) {
    if (message.contains(" ")) {
        def parts = message.split(" ")
        def name  = parts.length>0?parts[0].trim():null
        def value = parts.length>0?parts[1].trim():null
        
        
        if ((name == "PowerOff") || (value == "off")) {
            
            //If the PowerOff Activity (-1), or any Activity is asked to turn off, call stopActivity
            stopActivity()
        }
        else {
            
            //Otherwise, call startActivity for the activityId of the child device that called us
            def activityId = (name == "PowerOff") ? "-1" : "${name}"
            startActivity(activityId)
        }
    }
}


def webSocketStatus(String status){
    log.debug "webSocketStatus- ${status}"

    if(status.startsWith('failure: ')) {
        log.warn("failure message from web socket ${status}")
        reconnectWebSocket()
    } 
    else if(status == 'status: open') {
        log.info "websocket is open"
        // success! reset reconnect delay
        pauseExecution(1000)
        state.reconnectDelay = 1
    } 
    else if (status == "status: closing"){
        log.warn "WebSocket connection closing."
    } 
    else {
        log.warn "WebSocket error, reconnecting."
        reconnectWebSocket()
    }
}

def reconnectWebSocket() {
    // first delay is 2 seconds, doubles every time
    state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
    // don't let delay get too crazy, max it out at 10 minutes
    if(state.reconnectDelay > 600) state.reconnectDelay = 600

    //If the Harmony Hub is offline, give it some time before trying to reconnect
    runIn(state.reconnectDelay, initialize)
}

def updateChild(String activityId, String value, String activityName = null) {
    //Try to find existing child device
    def child = getChild(activityId)

    //If child does not exist, create it
    if(child == null) {
        if (activityName != null) {
            if (logEnable) log.debug "child with activityId = ${activityId}, activityName = ${activityName}  does not exist."
            def childType = "Child Switch"
            createChildDevice(activityId, activityName, childType)
            child = getChild(activityId)
        } 
        else {
            log.error "Cannot create child device for ${activityId} due to missing 'activityName'"
        }
    } 
    else {
        //log.trace "child with activityId=${activityId} exists already."
    }

    //If we have a valid child device and a valid value, update its attributes
    if((child != null) && (value != "unknown")) {
        try {
            if (logEnable) log.debug "Calling child.parse for Activity '${child.label}' with 'switch ${value}'"
            child.parse("switch ${value}")
        } 
        catch(e) {
            log.error("Child parse call failed: ${e}")
        }
    }
}

private def getChild(String activityId)
{
    //if (logEnable) log.debug "Searching for child device with network id: ${device.deviceNetworkId}-${activityId}"
    def result = null
    try {
        childDevices.each{ it ->
            //log.debug "child: ${it.deviceNetworkId}"
            if(it.deviceNetworkId == "${device.deviceNetworkId}-${activityId}")
            {
                result = it;
            }
        }
        return result;
    } 
    catch(e) {
        log.error "Failed to find child without exception: ${e}";
        return null;
    }
}

private void createChildDevice(String activityId, String activityName, String type) {
    log.trace "Attempting to create child with activityId = ${activityId}, activityName = ${activityName}, type = ${type}"
    
    try {
        addChildDevice("${type}", "${device.deviceNetworkId}-${activityId}",
            [label: "${device.displayName}-${activityName}", 
             isComponent: false, name: "${activityName}"])
        log.trace "Created child device with network id: ${device.deviceNetworkId}-${activityId}"
    } 
    catch(e) {
        log.error "Failed to create child device with error = ${e}"
    }
}
