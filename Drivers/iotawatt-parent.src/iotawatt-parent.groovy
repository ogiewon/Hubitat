/**
 *
 *  File: IoTaWatt-Parent.groovy
 *  Platform: Hubitat
 *
 *  Requirements:
 *     1) IoTaWatt Home Energy Meter from https://iotawatt.com/
 *     2) HubDuino "Child Power Meter" and "Child Voltage Sensor" Drivers are also necessary.  These
 *        are avilable at https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino/Drivers
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
 *    2018-12-20  Dan Ogorchock  Original Creation
 *    2019-05-24  Dan Ogorchock  Added ImportURL metadata
 *    2019-09-15  Dan Ogorchock  Added Presence Capability to know if the IoTaWatt device is online or offline
 *
 *
 */

 def version() {"v0.1.20190915"}

metadata {
    definition (name: "IoTaWatt Parent", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/iotawatt-parent.src/iotawatt-parent.groovy") {
        capability "Refresh"
        capability "Presence Sensor"  //used to determine is the IoTaWatt microcontroller is still reporting data or not
    }
}

preferences {
    input "deviceIP", "text", title: "IoTaWatt IP Address", description: "in form of 192.168.1.138", required: true, displayDuringSetup: true
    input "pollingInterval", "number", title: "Polling Interval", description: "in seconds", range: "10..300", defaultValue: 30, displayDuringSetup: true
//    input "timeOut", "number", title: "Timeout in Seconds", description: "Max time w/o HubDuino update before setting device to 'not present'", defaultValue: "900", required: true, displayDuringSetup:true
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
   handleUpdates() 
}

def installed() {
    initialize()
}

def updated() {
    log.debug "updated() called"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    initialize()
    schedule("0/${pollingInterval} * * * * ? *", handleUpdates)
}

def initialize() {
    state.version = version()
    log.debug "initialize() called"
    if (deviceIP) {
        if (logEnable) {
            getData()?.each {temp ->
                log.debug "${temp.name}, ${temp.value} ${temp.units}"
            }
        }
        handleUpdates()
    }
}

def handleUpdates() {
        getData()?.each{ it -> //iterate over all the updates in array
            //make sure we were given name and type
            if(!it.containsKey('name'))
            {
                log.error("'name' was not supplied")
                return
            }
            if(!it.containsKey('value'))
            {
                log.error("'value' was not supplied")
                return
            }
            if(!it.containsKey('units'))
            {
                log.error("'units' was not supplied")
                return
            }
            
            def child = getChild(it.name)
            if(child == null)
            {
                if (logEnable) log.debug "child with name=${it.name} does not exist."
                def childType = (it.units == "Watts") ? "Child Power Meter" : "Child Voltage Sensor"
                createChildDevice(it.name, childType)
                child = getChild(it.name)
            }
            else
            {
                if (logEnable) log.debug "child with name=${it.name} exists already."
            }
            
            if(child != null) // update the child
            {
                try{
                    def nameValue = (it.units == "Watts") ? "power ${it.value}" : "voltage ${it.value}"
                    if (logEnable) log.debug "Calling child.parse with '${nameValue}'"
                    child.parse(nameValue)
                }
                catch(e){
                    log.error("Child parse call failed: ${e}")
                }
            }
        }
}


def getData(){
   
    def params = [
        uri: "http://${deviceIP}/status?inputs=yes&outputs=yes",
        contentType: "application/json",
        requestContentType: "application/json",
    ]
    
    if (deviceIP) {
        try{
            httpGet(params){response ->
                if(response.status != 200) {
                    if (device.currentValue("presence") != "not present") {
                        sendEvent(name: "presence", value: "not present", isStateChange: true, descriptionText: "Error trying to communicate with IoTaWatt device")
                    }
                    log.error "Received HTTP error ${response.status}. Check your IP Address and IoTaWatt!"
                } else {
                    if (device.currentValue("presence") != "present") {
                        sendEvent(name: "presence", value: "present", isStateChange: true, descriptionText: "New update received from IoTaWatt device")
                    }
                    //log.debug "Response from IoTaWatt = ${response.data}"
                    def inputs = response.data.inputs
                    def outputs = response.data.outputs             
                    def IoTaWattData = []
                    
                    //Format the IoTaWatt Input and Output data consistently to simplify the child device creation process
                    response?.data?.inputs?.each { input ->
                        def tmpName = "Input_${input.channel.toString().padLeft(2,'0')}"
                        if (input.Watts) {
                            //log.debug "name: ${input.channel}, units: Watts, value: ${input.Watts}, units: Pf, value: ${input.Pf}"
                            IoTaWattData << [name:tmpName, units:'Watts', value:input.Watts.toString().trim()]
                        } else if (input.Vrms) {
                            //log.debug "name: ${input.channel}, units: Vrms, value: ${input.Vrms}, units: Hz, value: ${input.Hz}"
                            IoTaWattData << [name:tmpName, units:'Vrms', value:input.Vrms.toString().trim()]
                        } else {
                            log.error "Unhandled case for ${input}"
                        }
                    }

                    response?.data?.outputs?.each { output ->
                       def tmpName = "Output_${output.name}"
                        IoTaWattData << [name:tmpName, units:output.units, value:output.value.toString().trim()]
                    }
                    
                    return IoTaWattData
                }
            }
        } catch (Exception e) {
            if (device.currentValue("presence") != "not present") {
                sendEvent(name: "presence", value: "not present", isStateChange: true, descriptionText: "Error trying to communicate with IoTaWatt device")
            }
            log.error "IoTaWatt Server Returned: ${e}"
        } 
    } else {
        log.error "IP Address '${deviceIP}' is not properly formatted!"
    }
}

private void createChildDevice(String name, String type) {
    log.trace "Attempting to create child with name=" + name + " type=" + type;
    
    try {
        addChildDevice("${type}", "${device.deviceNetworkId}_${name}",
            [label: "${device.displayName} (${name})", 
             isComponent: false, name: "${name}"])
        log.trace "Created child device with network id: ${device.deviceNetworkId}_${name}"
    } 
    catch(e) {
        log.error "Failed to create child device with error = ${e}";
    }
}

private def getChild(String name)
{
    if (logEnable) log.debug "Searching for child device with network id: ${device.deviceNetworkId}_${name}"
    def result = null
    try {
        childDevices.each{ it ->
            //log.debug "child: ${it.deviceNetworkId}"
            if(it.deviceNetworkId == "${device.deviceNetworkId}_${name}")
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
