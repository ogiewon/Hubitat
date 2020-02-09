/*
 *
 * Revised for Hubitat by Dan Ogorchock 2020-02-09
 *
 * Based on the SmartThings' "Bob's Thermostat Syncer" (https://github.com/texasbobs/ST-Thermostat-Sync)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

definition(
    name: "Hubitat Thermostat Syncer",
    namespace: "hubitat",
    author: "Tim Slagle Modified by: Duane Helmuth/Bob Snyder/Dan Ogorchock",
    description: "Adjust a thermostat based on the setting of another thermostat",
    category: "Green Living",
    iconUrl: "http://icons.iconarchive.com/icons/icons8/windows-8/512/Science-Temperature-icon.png",
    iconX2Url: "http://icons.iconarchive.com/icons/icons8/windows-8/512/Science-Temperature-icon.png"
)

section
{
    input "thermostat1", "capability.thermostat", title: "Which Master Thermostat?", multi: false, required: true
    input "thermostat2", "capability.thermostat", title: "Which Slave Thermostat?", multi: false, required: true
    input "tempDiff", "decimal", title: "Temperature Difference Between Master and Slave?", required: true, defaultValue: 2
    input "notificationDevice", "capability.notification", title: "Notification Device?",multiple: false, required: false
}

def installed(){
    log.debug "Installed called with ${settings}"
    init()
}

def updated(){
    log.debug "Updated called with ${settings}"
    unsubscribe()
    init()
}

def init(){
    subscribe(thermostat1, "thermostatSetpoint", temperatureHandler)
}

def temperatureHandler(evt) {

log.debug "Temperature Handler Begin"
    //get the latest temp readings and compare
    def MThermostatTemp = thermostat1.latestValue("thermostatSetpoint")
    def SThermostatTemp = thermostat2.latestValue("thermostatSetpoint")
    def mode = thermostat1.latestValue("thermostatMode")
    float difference = (SThermostatTemp.toFloat() - MThermostatTemp.toFloat())

    log.debug "Thermostat(M): ${MThermostatTemp}"
    log.debug "Thermostat(S): ${SThermostatTemp}"
    log.debug "Temp Diff: ${tempDiff}"
    log.debug "Current Temp Difference: ${difference}"
    log.debug "Current Mode: ${mode}"

    def msg = ""
    if( difference != tempDiff ){
        def NewTemp = (MThermostatTemp + tempDiff)
        msg = "${thermostat2} sync'ed with ${thermostat1} with of offset of ${tempDiff} degrees. Now at ${NewTemp}."
        if (mode == "cool"){
            thermostat2.setCoolingSetpoint(NewTemp)
        }
        else {
            thermostat2.setHeatingSetpoint(NewTemp)
        }
        
        runIn(5, refreshThermostat2)
      
        log.debug msg
        if (notificationDevice) {
            notificationDevice.deviceNotification(msg)
        }
    } 
}

def refreshThermostat2 () {
    log.debug "Refreshing Thermostat 2"
    thermostat2.refresh()
}
