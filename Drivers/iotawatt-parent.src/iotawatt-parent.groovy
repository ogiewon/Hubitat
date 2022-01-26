/**
 *
 *  File: IoTaWatt-Parent.groovy
 *  Platform: Hubitat
 *
 *  Requirements:
 *     1) IoTaWatt Home Energy Meter from https://iotawatt.com/ using a reserved/static TCP/IP address
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
 *    2020-05-06  Dan Ogorchock  Added cleanup functionality to the uninstalled() routine 
 *    2020-05-08  Dan ogorchock  Ensure scheduling works properly after a hub reboot
 *    2020-05-16  Dan Ogorchock  Improved error handling
 *    2020-11-02  Dan Ogorchock  Added timeout to http calls
 *    2022-01-03  Dan Ogorchock  Convert child devices to use Hubitat's built-in 'Generic Component' drivers.
 *                               Note:  THIS IS A BREAKING CHANGE!
 *
 *
 */

 def version() {"v1.0.20220103"}

metadata {
    definition (name: "IoTaWatt Parent", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/iotawatt-parent.src/iotawatt-parent.groovy") {
        capability "Initialize"
        capability "Refresh"
        capability "Presence Sensor"  //used to determine is the IoTaWatt microcontroller is still reporting data or not
        
        //command "deleteAllChildDevices"
    }
}

preferences {
    input "deviceIP", "text", title: "IoTaWatt IP Address", description: "in form of 192.168.1.138", required: true, displayDuringSetup: true
    input "pollingInterval", "number", title: "Polling Interval", description: "in seconds", range: "10..300", defaultValue: 30, displayDuringSetup: true
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
    log.info "updated() called"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    initialize()
}

def initialize() {
    state.version = version()
    log.info "initialize() called"
    if (deviceIP) {
        handleUpdates()
    }
    else
    {
        log.warn "Please enter the IoTaWatt IP Address and then click SAVE"
    }

}

def handleUpdates() {
    runIn(pollingInterval, handleUpdates)
    
    String type
    String name
    String units
    def child
    float tmpValue
    
    def IoTaWattData = getData()
    IoTaWattData?.each{ it -> //iterate over all the updates in array
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
        
        if (it.units == "Watts") {
            type = "Power Meter"
            name = "power"
            units = "W"
        }
        else if (it.units == "Vrms") {
            type = "Voltage Sensor"
            name = "voltage"
            units = "V"
        }
        else if (it.units == "Hz") {
            type = "Voltage Sensor"
            name = "frequency"
            units = "Hz"
        }
        
        child = fetchChild(type, it.name)
        tmpValue = Float.parseFloat(it.value)
        tmpValue = tmpValue.round(1)
        child.parse([[name: name, value: tmpValue, unit: units, descriptionText:"${name} is now ${tmpValue} ${units}"]])
    }
    
    //cleanup
    IoTaWattData = null
    child = null
    tmpValue = null    
    type = null
    name = null
    units = null    
}


def getData(){
   
    def params = [
        uri: "http://${deviceIP}/status?inputs=yes&outputs=yes",
        contentType: "application/json",
        requestContentType: "application/json",
        timeout: 10
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
                        String tmpName = "Input_${input.channel.toString().padLeft(2,'0')}"
                        if (input.Watts) {
                            //log.debug "name: ${input.channel}, units: Watts, value: ${input.Watts}, units: Pf, value: ${input.Pf}"
                            IoTaWattData << [name:tmpName, units:'Watts', value:input.Watts.toString().trim()]
                        } else if (input.Vrms) {
                            //log.debug "name: ${input.channel}, units: Vrms, value: ${input.Vrms}, units: Hz, value: ${input.Hz}"
                            IoTaWattData << [name:tmpName, units:'Vrms', value:input.Vrms.toString().trim()]
                            IoTaWattData << [name:tmpName, units:'Hz', value:input.Hz.toString().trim()]
                        } else {
                            log.error "Unhandled case for ${input}"
                        }
                    }

                    response?.data?.outputs?.each { output ->
                        String tmpName = "Output_${output.name}"
                        IoTaWattData << [name:tmpName, units:output.units, value:output.value.toString().trim()]
                    }
                    
                    return IoTaWattData
                }
            }
        } catch (Exception e) {
            if (device.currentValue("presence") != "not present") {
                sendEvent(name: "presence", value: "not present", isStateChange: true, descriptionText: "Error trying to communicate with IoTaWatt device")
            }
            log.warn "IoTaWatt Server Returned: ${e}"
            if (e == "java.net.NoRouteToHostException: No route to host (Host unreachable)") {
                //Give the IoTaWatt extra time to recover
                runIn((pollingInterval * 2), handleUpdates)
            }
        } 
    } else {
        log.error "IP Address '${deviceIP}' is not properly formatted!"
    }
}

def fetchChild(String type, String name){
    def cd = getChildDevice("${device.id}-${type}_${name}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component ${type}", "${device.id}-${type}_${name}", [name: "${name}", isComponent: false])
    }
    return cd 
}

def uninstalled() {
    log.info "Executing 'uninstalled()'"
    unschedule()
    deleteAllChildDevices()
}

def deleteAllChildDevices() {
    log.info "Uninstalling all Child Devices"
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
       }
}

//child device methods
void componentRefresh(cd) {
    log.info "received refresh request from ${cd.displayName}"
    runIn(2, refresh)
}