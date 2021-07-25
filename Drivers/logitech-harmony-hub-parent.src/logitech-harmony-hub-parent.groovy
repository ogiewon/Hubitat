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
 *    2019-01-07  Dan Ogorchock  Changed log.warn to log.info for unhandled data from harmony hub
 *    2019-02-20  @corerootedxb  Fixed routine to obtain the remoteId due to firmware 4.15.250 changes by Logitech
 *    2019-06-09  Dan Ogorchock  Added importURL to definition
 *    2019-07-14  Dan Ogorchock  Added Harmony Volume and Channel control (for activities that support it) (with help from @aaron!)
 *    2019-07-15  Dan Ogorchock  Added setLevel and setVolume commands for greater compatability with Hubitat Dashboard and other Apps
 *    2019-07-23  Dan Ogorchock  Added Actuator Capability to allow RM Custom Actions to select this device
 *    2019-12-31  Dan Ogorchock  Changed volume control logic to be more robust and clear to users
 *    2020-01-14  Dan Ogorchock  Added Switch Capability and Default Activity user preference.  If the Parent switch is turned on, 
 *                               the default activity is turned on.  If the Parent switch is turned off, the current Activity is turned off.
 *    2020-01-21  Dan Ogorchock  Fixed bug in the Parent Switch's Status not updating when controlled via the physical remote control
 *    2020-01-28  Dan Ogorchock  Exposed "deviceCommand" as a custom command per idea from @Geoff_T
 *    2020-03-01  Rebecca Ellenby Added Left, Right, Up, Down, and Ok custom commands.
 *    2020-07-10  Dan Ogorchock  Fixed minor bug in setLevel() command
 *    2020-09-18  abuttino       Added Home Control Buttons for Harmony Elite/950
 *    2020-09-18  Dan Ogorchock  Minor code cleanup
 *    2020-09-20  Dan Ogorchock  Use pushed and held events for Home Control Buttons.
 *    2020-11-23  Dan Ogorchock  Added custom level attributes for Home Control Buttons.  Thanks @fabien.giuliano and @abuttino.
 *    2020-12-28  Dan Ogorchock  Fixed Null division issue caused in the 11-23-2020 release
 *    2021-01-04  Dan Ogorchock  Added Play, Pause, and Stop custom commands for the current Activity - valid for only Activities that support TransportBasic commands
 *    2021-03-29  @chirpy        Added Channel Number selection for Activities that support NumericBasic numbers
 *    2021-04-25  Dan Ogorchock  Corrected data type of custom attributes
 *    2021-07-02  Dan Ogorchock  Added Presence Capability to indicate whether or not the connection to the Harmony Hub is 'present' or 'not present'
 *    2021-07-25  Dan Ogorchock  Improved log.debug handling
 */

def version() {"v0.1.20210725"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "Logitech Harmony Hub Parent", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/logitech-harmony-hub-parent.src/logitech-harmony-hub-parent.groovy") {
        capability "Initialize"
        capability "Refresh"
        capability "Switch Level"
        capability "Pushable Button"
        capability "Holdable Button"
        capability "Audio Volume"
        capability "Actuator"
        capability "Switch"
        capability "Presence Sensor"  //used to determine if the Harmony Hub is connected or not

        //command "sendMsg", ["String"]
        //command "getConfig"
        //command "startActivity", ["String"]
        //command "stopActivity"
        command "getCurrentActivity"
        command "deviceCommand", [[name:"Command", type: "STRING", description: "Harmony Hub Command", constraints: ["STRING"]], [name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        command "channelUp"
        command "channelDown"
        command "channelPrev"
        command "channelNumber", ["INTEGER"]
        command "play"
        command "pause"
        command "stop"

        // Labeled Actuator
        command "leftPress", [[name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        command "rightPress", [[name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        command "upPress", [[name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        command "downPress", [[name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        command "okPress", [[name:"DeviceID", type: "STRING", description: "Harmony Hub Device ID", constraints: ["STRING"]]]
        
        attribute "Activity","String"
        attribute "bulb1Level","Number"
        attribute "bulb2Level","Number"
        attribute "socket1Level","Number"
        attribute "socket2Level","Number"
    }
}

preferences {
    input("ip", "text", title: "Harmony Hub", description: "IP Address (in form of 192.168.1.45)", required: true)
    input name: "VolumeRepeat", type: "number", title: "Volume Control", description: "Increase/Decrease by this number of 'presses'",required: true, defaultValue: "1", range: "1..100"
    input("deviceName", "enum", title: "Default Activity:", description: "used for parent device's switch capability", multiple: false, required: false, options: getActivities())
    input("hcBulbOne", "text", title: "Home Control Light 1", description: "Enable Debug Logging and get Device ID for this button", required: false)
    input("hcBulbTwo", "text", title: "Home Control Light 2", description: "Enable Debug Logging and get Device ID for this button", required: false)
    input("hcSocketOne", "text", title: "Home Control Socket 1", description: "Enable Debug Logging and get Device ID for this button", required: false)
    input("hcSocketTwo", "text", title: "Home Control Socket 2", description: "Enable Debug Logging and get Device ID for this button", required: false)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true   
}


def parse(String description) {
    if (logEnable) log.debug "parsed: $description"
    //state.description = []
    //state.description = description

    def json = null;
    try{
        json = new groovy.json.JsonSlurper().parseText(description)
        if (logEnable) log.debug "${json}"
        if(json == null){
            log.warn "String description not parsed"
            return
        }
    }  catch(e) {
        log.error("Failed to parse json e = ${e}")
        return
    }
    

    //Retrieves the Harmony device configuration, including all Activity Names and IDs
    if (json?.cmd == "vnd.logitech.harmony/vnd.logitech.harmony.engine?config") {
        if (json?.msg == "OK") {

            state.HarmonyConfig =[]
            
            json?.data?.activity?.each { it ->
                def tempID = (it.id == "-1") ? "PowerOff" : "${it.id}"                    
                if (logEnable) log.debug "Activity Label: ${it.label}, ID: ${tempID}"
                
                //store portion of config results in state variable (needed for volume/channel/play/pause/stop controls) 
                //find the deviceId for volume controls
                def volume = "null"
                if (it.roles?.VolumeActivityRole) volume = it.roles?.VolumeActivityRole
                //find the deviceId for channel controls
                def channel = "null"
                def channelNumbers = "null"
                if (it.roles?.ChannelChangingActivityRole) channel = it.roles?.ChannelChangingActivityRole
                //find the deviceId for the TransportBasic Controls (Play/Pause/Stop)
                //find the deviceId for the NumericBasic Controls (0/1/2/3/4/5/6/7/8/9)
                def transportBasic = "null"
                it.controlGroup?.each { it2 ->
                    if (it2.name == "TransportBasic") {
                        it2.function?.each { it3 ->
                            if (it3.name == "Play") {
                                def temp = new groovy.json.JsonSlurper().parseText(it3.action)
                                transportBasic = "${temp.deviceId}"
                            }
                        }
                    }
                    else if (it2.name == "NumericBasic") {
                        it2.function?.each { it3 ->
                            if (it3.name == "Number9") {
                                def temp = new groovy.json.JsonSlurper().parseText(it3.action)
                                channelNumbers = "${temp.deviceId}"
                            }
                        }
                    }
                }
                //store deviceId's in state variable
                state.HarmonyConfig << ["id":"${it.id}", "label":"${it.label}", "VolumeActivityRole":"${volume}", "ChannelChangingActivityRole":"${channel}", "TransportBasic":"${transportBasic}", "ChannelNumbers":"${channelNumbers}"]
                
                //Create a Child Switch Device for each Activity if needed, default all of them to 'off' for now
                updateChild(tempID, "unknown", it.label)
            }
           
            //if (logEnable) { 
            //    def temp = new groovy.json.JsonBuilder(state.HarmonyConfig).toString()
            //    log.debug state.HarmonyConfig
            //    log.debug temp
            //}

            
            //2020-01-28 DGO - Add new State Variable to display list of Device ID's on Device Details web page.
            state.HarmonyDevices =[]
            json?.data?.device?.each { it ->                    
                if (logEnable) log.debug "Device Label: ${it.label}, ID: ${it.id}"
                state.HarmonyDevices << ["id":"${it.id}", "label":"${it.label}"]
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
    else if ((json?.type == "automation.state?notify")  && (description.contains('"status":1'))) {
        // AJB Added On/Off Functions for Home Control Buttons to generate pushed and held events
        if (hcBulbOne) {
            if (description.contains(hcBulbOne)) {
                def tempbrightness = json?.data[hcBulbOne].brightness
                if (tempbrightness) {
                    tempbrightness = Math.round(tempbrightness/254*100/10)*10
                    if (logEnable) log.debug "Bulb 1 Changed to $tempbrightness"
                    sendEvent(name:"bulb1Level", value: tempbrightness, descriptionText: "Bulb 1 Dimmer Level changed", isStateChange: true)
                }
            }
		    if ((description.contains(hcBulbOne)) && (description.contains('"on":true'))) {
			    if (logEnable) log.debug "Bulb Button 1 was 'pushed'"
			    sendEvent(name:"pushed", value: 1, descriptionText: "Bulb Button 1 was pushed", isStateChange: true)
	        }   
		    if ((description.contains(hcBulbOne)) && (description.contains('"on":false'))) {
			    if (logEnable) log.debug "Bulb Button 1 was 'held'"
			    sendEvent(name:"held", value: 1, descriptionText: "Bulb Button 1 was held", isStateChange: true)
	        }
        }
        if (hcBulbTwo) {
            if (description.contains(hcBulbTwo)) {
                def tempbrightness = json?.data[hcBulbTwo].brightness
                if (tempbrightness) {
                    tempbrightness = Math.round(tempbrightness/254*100/10)*10
                    if (logEnable) log.debug "Bulb 2 Changed to $tempbrightness"
                    sendEvent(name:"bulb2Level", value: tempbrightness, descriptionText: "Bulb 2 Dimmer Level Changed", isStateChange: true)
                }
            }
		    if ((description.contains(hcBulbTwo)) && (description.contains('"on":true'))) {
			    if (logEnable) log.debug "Bulb Button 2 was 'pushed'"
			    sendEvent(name:"pushed", value: 2, descriptionText: "Bulb Button 2 was pushed", isStateChange: true)
	        }
		    if ((description.contains(hcBulbTwo)) && (description.contains('"on":false'))) {
			    if (logEnable) log.debug "Bulb Button 2 was 'held'"
			    sendEvent(name:"held", value: 2, descriptionText: "Bulb Button 2 was held", isStateChange: true)
	        }   
        }   
	    if (hcSocketOne) {
            if (description.contains(hcSocketOne)) {
                def tempbrightness = json?.data[hcSocketOne].brightness
                if (tempbrightness) {
                    tempbrightness = Math.round(tempbrightness/254*100/10)*10
                    if (logEnable) log.debug "Socket 1 Changed to $tempbrightness"
                    sendEvent(name:"socket1Level", value: tempbrightness, descriptionText: "Socket 1 Dimmer Level changed", isStateChange: true)
                }
            }            
		    if ((description.contains(hcSocketOne)) && (description.contains('"on":true'))) {
			    if (logEnable) log.debug "Socket Button 1 was 'pushed'"
			    sendEvent(name:"pushed", value: 3, descriptionText: "Socket Button 1 was pushed", isStateChange: true)
		    }
		    if ((description.contains(hcSocketOne)) && (description.contains('"on":false'))) {
			    if (logEnable) log.debug "Socket Button 1 was 'held'"
			    sendEvent(name:"held", value: 3, descriptionText: "Socket Button 1 was held", isStateChange: true)
		    }
	    }
	    if (hcSocketTwo) {
            if (description.contains(hcSocketTwo)) {
                def tempbrightness = json?.data[hcSocketTwo].brightness
                if (tempbrightness) {
                    tempbrightness = Math.round(tempbrightness/254*100/10)*10
                    if (logEnable) log.debug "Socket 2 Changed to $tempbrightness"
                    sendEvent(name:"socket2Level", value: tempbrightness, descriptionText: "Socket Button 2 Dimmer Level changed", isStateChange: true)
                }
            }  
		    if ((description.contains(hcSocketTwo)) && (description.contains('"on":true'))) {
			    if (logEnable) log.debug "Socket Button 2 was 'pushed'"
			    sendEvent(name:"pushed", value: 4, descriptionText: "Socket Button 2 was pushed", isStateChange: true)
		    }
		    if ((description.contains(hcSocketTwo) && (description.contains('"on":false')))) {
			    if (logEnable) log.debug "Socket Button 2 was 'held'"
			    sendEvent(name:"held", value: 4, descriptionText: "Socket Button 2 was held", isStateChange: true)
		    }
	    }
    }
    else {
        if ((json?.cmd != "harmony.engine?startActivity") && (json?.cmd != "harmony.activityengine?runactivity")) {
            if (logEnable) log.info "Unhandled data from Harmony Hub. json = ${description}"
        }
    }


}



def updateChildren(String ActivityID) {
    //Make sure the parent's "Switch" status is correct
    if (ActivityID != "-1") {
        sendEvent(name: "switch", value: "on")
    }
    else {
        sendEvent(name: "switch", value: "off")
    }	
	
    //Switch Child Device States based on the return value.  If "-1" turn off all child activity devices
    def tempID = (ActivityID == "-1") ? "PowerOff" : ActivityID
    try {
        childDevices.each{ it ->
            def childDNI = it.deviceNetworkId.split("-")[-1]
            if(childDNI == "${tempID}")
            {
                updateChild(childDNI, "on")
                sendEvent(name: "Activity", value: "${it.name}", isStateChange: true)
                state.currentActivity = ActivityID
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
    sendEvent(name: "numberOfButtons", value: 0)
    updated()
}

def updated() {
    log.info "updated() called"
    
    state.version = version()
    
    //Unschedule any existing schedules
    unschedule()
    
    //Create a 30 minute timer for debug logging
    if (logEnable) runIn(1800,logsOff)
    
    //initialize the numberOfButtons attributes based on whether or not the user has enabled this feature in the preferences
    if ((hcBulbOne) || (hcBulbTwo) || (hcSocketOne) || (hcSocketTwo)) {
        sendEvent(name: "numberOfButtons", value: 4)
    } 
    else {
        sendEvent(name: "numberOfButtons", value: 0)
    }
    
    //Connect the webSocket
    initialize()
    
    //Retrieve Harmony Hub Configuration Data to create Activities
    getConfig()
    
    //Get the current activity to make sure the child devices are synched with the harmony hub
    getCurrentActivity()
    
    sendEvent(name: "level", value: 50, unit: "%")
}

def initialize() {
    state.version = version()
    log.info "initialize() called"
    sendEvent(name: "level", value: 50, unit: "%")
    
    if (!ip) {
        log.warn "Harmony Hub IP Address not configured yet"
        return
    }
    
    if (state.remoteId == null) {
        httpPost(uri: "http://${ip}:8088",
                 path: '/',
                 contentType: 'application/json',
                 requestContentType: 'application/json',
                 //Logitech changed webSockets interface in 4.15.250.  Updated Origin and body cmd.
                 headers: ['Origin': 'http://sl.dhg.myharmony.com'],
                 body: '{"id": 1, "cmd": "setup.account?getProvisionInfo", "params": {}}'
                ) { response ->
             //activeRemoteId is the new property name instead of just remoteId.
             if (logEnable) log.debug "hub remote id: ${response.data.data.activeRemoteId}"
             state.remoteId = response.data.data.activeRemoteId
         }
    }

/*    Original code for users on older firmware - need to make more elegant DGO 2019-02-20
    //Make sure we know the remoteId of the Harmony Hub
    if (state.remoteId == null) {
        httpPost(uri: "http://${ip}:8088",
                 path: '/',
                 contentType: 'application/json',
                 requestContentType: 'application/json',
                 headers: ['Origin': 'http//:localhost.nebula.myharmony.com'],
                 body: '{"id": 124, "cmd": "connect.discoveryinfo?get", "params": {}}'
                ) { response ->
            if (logEnable) log.debug "hub remote id: ${response.data.data.remoteId}"
            state.remoteId = response.data.data.remoteId
        }
    }
  */  
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
    
    //Not sure what this is used for, but not needed for our purposes... ;)
    //sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.connect/vnd.logitech.deviceinfo?get","id":"0","params":{"verb":"get"}}}')   
    //Refresh current status - Using getCurrentActivity instead
    //sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.connect/vnd.logitech.statedigest?get","id":"0","params":{"verb":"get","format":"json"}}}')
    
    //Get Activities and a whole lot more data from the Harmony Hub
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":60,"hbus":{"cmd":"vnd.logitech.harmony/vnd.logitech.harmony.engine?config","id":"0","params":{"verb":"get"}}}')
}

def on() {
    if (deviceName) {
        if (logEnable) log.debug "Default Activity = ${deviceName}"
        startActivity(deviceName)
    }
    else {
        log.info "Default Activity not selected yet"
    }
}

def off() {
        stopActivity()
}

def startActivity(String activityID) {
    if(!state.remoteId) return
    if (activityID != "-1") {
        sendEvent(name: "switch", value: "on")
    }
    else {
        sendEvent(name: "switch", value: "off")
    }
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"harmony.activityengine?runactivity","id":"0","params":{"async": "true","timestamp": 0,"args": {"rule": "start"},"activityId": "' + activityID + '"}}}')
}

def stopActivity() {
    if(!state.remoteId) return
    if (logEnable) log.debug "stopActivity() called..."
    startActivity("-1")
}

def getCurrentActivity() {
    if(!state.remoteId) return
    
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.harmony/vnd.logitech.harmony.engine?getCurrentActivity","id":"0","params":{"verb": "get","format": "json"}}}')
}

def sendMsg(String s) {
    InterfaceUtils.sendWebSocketMessage(device, s)
}

def deviceCommand(command, deviceID) {
    sendMsg('{"hubId":"' + state.remoteId + '","timeout":30,"hbus":{"cmd":"vnd.logitech.harmony/vnd.logitech.harmony.engine?holdAction","id": "0", "params":{"status": "pressrelease","timestamp": "0","verb": "render", "action": "{\\"command\\": \\"' + command + '\\", \\"type\\":\\"IRCommand\\", \\"deviceId\\": \\"' + deviceID + '\\"}"}}}')
}

def mute() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.VolumeActivityRole != "null") {
                deviceCommand("Mute", it.VolumeActivityRole)
            } else {
                log.info "Activity ${it.label} does not support volume control"
            }
        }
    }
}

def unmute() {
    mute()
}

def volumeUp() {
    adjustVolume("VolumeUp")
}

def volumeDown() {
    adjustVolume("VolumeDown")
}

def adjustVolume(String direction) {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.VolumeActivityRole != "null") {
                int numPresses = (VolumeRepeat == null) ? 1 : VolumeRepeat.toInteger()
                for(int x = 1; x <= numPresses; x++) {
                    deviceCommand(direction, it.VolumeActivityRole)
                    if (logEnable) log.debug "${direction}"
                    pauseExecution(500)
                }                
            } else {
                log.info "Activity ${it.label} does not support volume control"
            }
        }
    }

}

def setLevel(value,duration=null) {
    if (logEnable) log.debug "setLevel >> value: $value"
    //def valueaux = value as Integer
    def level = Math.max(Math.min(value.toInteger(), 100), 0)

    if (level > 50) {
        volumeUp()
        runIn(1, setLevel, [data: 50])
    } else if (level < 50) {
        volumeDown()
        runIn(1, setLevel, [data: 50])
    }
    sendEvent(name: "level", value: level, unit: "%") 
}

def setVolume(volumelevel) {
    setLevel(volumelevel)
}

def channelUp() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.ChannelChangingActivityRole != "null") {
                deviceCommand("ChannelUp", it.ChannelChangingActivityRole)
            } else {
                log.info "Activity ${it.label} does not support channel control"
            }
        }
    }
}

def channelDown() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.ChannelChangingActivityRole != "null") {
                deviceCommand("ChannelDown", it.ChannelChangingActivityRole)
            } else {
                log.info "Activity ${it.label} does not support channel control"
            }
        }
    }
}

def channelPrev() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.ChannelChangingActivityRole != "null") {
                deviceCommand("PrevChannel", it.ChannelChangingActivityRole)
            } else {
                log.info "Activity ${it.label} does not support channel control"
            }
        }
    }
}

def channelNumber(channel) {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.ChannelNumbers != "null") {
                for (channelNum in channel.toString()){
                    deviceCommand(channelNum, it.ChannelNumbers)
                }
            } else {
                log.info "Activity ${it.label} does not support channel numbers"
            }
        }
    }
}

def play() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.TransportBasic != "null") {
                deviceCommand("Play", it.TransportBasic)
            } else {
                log.info "Activity ${it.label} does not support TransportBasic 'Play' command"
            }
        }
    }
}

def pause() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.TransportBasic != "null") {
                deviceCommand("Pause", it.TransportBasic)
            } else {
                log.info "Activity ${it.label} does not support TransportBasic 'Pause' command"
            }
        }
    }
}

def stop() {
    state.HarmonyConfig.each { it ->
        if (it.id == state.currentActivity) {
            if (it.TransportBasic != "null") {
                deviceCommand("Stop", it.TransportBasic)
            } else {
                log.info "Activity ${it.label} does not support TransportBasic 'Stop' command"
            }
        }
    }
}

// Sends a custom command to a chosen device, not reliant on whether it is the default volume/channel changing device

def customCommand(String command, String device) {
    deviceCommand(command, device)
}

def leftPress(String device) {
    deviceCommand("DirectionLeft", device)
}

def rightPress(String device) {
    deviceCommand("DirectionRight", device)
}

def upPress(String device) {
    deviceCommand("DirectionUp", device)
}

def downPress(String device) {
    deviceCommand("DirectionDown", device)
}

def okPress(String device) {
    deviceCommand("OK", device)
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
    if (logEnable) log.debug "webSocketStatus- ${status}"

    if(status.startsWith('failure: ')) {
        log.warn("failure message from web socket ${status}")
        sendEvent(name: "presence", value: "not present", descriptionText: "webSocket connection to Harmony Hub is closed")
        reconnectWebSocket()
    } 
    else if(status == 'status: open') {
        log.info "websocket is open"
        // success! reset reconnect delay
        sendEvent(name: "presence", value: "present", descriptionText: "webSocket connection to Harmony Hub is open")
        pauseExecution(1000)
        state.reconnectDelay = 1
    } 
    else if (status == "status: closing"){
        log.warn "WebSocket connection closing."
        sendEvent(name: "presence", value: "not present", descriptionText: "webSocket connection to Harmony Hub is closed")
    } 
    else {
        log.warn "WebSocket error, reconnecting."
        sendEvent(name: "presence", value: "not present", descriptionText: "webSocket connection to Harmony Hub is closed")
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

def getActivities() {
    def result = [:]
    def activity
    def name
    
    try {
        childDevices.each{ it ->
            activity = it.deviceNetworkId.minus("${device.deviceNetworkId}-")
            name = it.name
            //log.debug "child: ${activity}:${name}"
            if (name != "PowerOff") {
                result << ["${activity}":"${name}"]
            }
        }
        //result = ["${activity}":"${name}"]
        //log.debug result
        return result;
    } 
    catch(e) {
        //log.error "Failed to find child without exception: ${e}";
        //return null;
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
