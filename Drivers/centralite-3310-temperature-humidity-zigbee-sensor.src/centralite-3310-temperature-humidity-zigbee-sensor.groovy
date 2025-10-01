/*
 *  Centralite 3310 Temperature/Humidity Sensor
 *
 *  Copyright 2025 Dan Ogorchock (Original Copyright 2014 SmartThings)
 *
 *  2014-12-17: Modified by Barry A. Burke to maintain decimal precision for both Celsius and Fahrenheit value (was rounding down prematurely)
 *  2025-09-30: Modified by Dan Ogorchock to work on Hubitat Elevation Platform
 *  2025-10-01: Modified by Dan Ogorchock to allow for decimal temperature and humidity offsets + minor tweaks
 *
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
 */
metadata {
	definition (name: "Centralite 3310 Temp/Humidity Sensor",namespace: "ogiewon", author: "ogiewon", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Drivers/centralite-3310-temperature-humidity-zigbee-sensor.src/centralite-3310-temperature-humidity-zigbee-sensor.groovy") {
		capability "Configuration"
		capability "Battery"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0B05,FC45", outClusters:"0003,0019", model:"3310-G", manufacturer:"CentraLite", controllerType: "ZGB", deviceJoinName: "Centralite 3310-G"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0B05,FC45", outClusters:"0003,0019", model:"3310-S", manufacturer:"CentraLite", controllerType: "ZGB", deviceJoinName: "Centralite 3310-S"
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0B05,FC45", outClusters:"0003,0019", model:"3310", manufacturer:"CentraLite", controllerType: "ZGB", deviceJoinName: "Centralite 3310"
	}
 
	preferences {
		input name: "tempOffset", type: "number", title: "Temperature Offset", description: "Adjust temperature by this many degrees", range: "*..*", defaultValue: 0
		input name: "humidOffset", type: "number", title: "Humidity Offset", description: "Adjust humidity by this percentage", range: "*..*", defaultValue: 0
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {

	if (description?.startsWith('temperature: ')) {
    	if (logEnable) log.debug "description temperature: $description"
    } else {
		if (logEnable) log.debug "PARSE(): description = $description"
    }

	Map map = [:]
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
	else if (description?.startsWith('temperature: ') || description?.startsWith('humidity: ')) {
		map = parseCustomMessage(description)
	}
 
	if (logEnable) log.debug "PARSE RETURNED $map"
	return map ? map : null
}
 
private Map parseCatchAllMessage(String description) {
    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    if (shouldProcessMessage(cluster)) {
        switch(cluster.clusterId) {
            case 0x0001:
            	resultMap = getBatteryResult(cluster.data.last())
                break
            case 0x0402:
                if (logEnable) log.debug "parseCatchAll: temperature cluster.data = ${cluster.data}"
                // temp is last 2 data values. reverse to swap endian
                String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
                if (logEnable) log.debug "***parseCatchAll: temperature = ${temp}"
                def value = getTemperature(temp)
                resultMap = getTemperatureResult(value)
                break
			case 0xFC45:
                if (logEnable) log.debug "parseCatchAll: humidity cluster.data = ${cluster.data}"
//                String pctStr = cluster.data[-1..-2].collect { Integer.toHexString(it) }.join('')
//                String humid = Math.round(Integer.valueOf(pctStr, 16) / 100)
//                log.debug "***parseCatchAll: humid = ${humid}"
//                resultMap = getHumidityResult(humid)
                break
        }
    }
    
    return resultMap
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    boolean ignoredMessage = cluster.profileId != 0x0104 || 
        cluster.command == 0x0B ||
        cluster.command == 0x07 ||
        (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	if (logEnable) log.debug "parseReportAttributeMessage: descMap: $descMap"
 
	Map resultMap = [:]
	if (descMap.cluster == "0402" && descMap.attrId == "0000") {
        if (logEnable) log.debug "parseReportAttributeMessage: TEMP descMap.value = ${descMap.value}"
        String temp = swapEndianHex(descMap.value)
        if (logEnable) log.debug "parseReportAttributeMessage: TEMP temp = ${temp}"
        descMap.value = temp
		def value = getTemperature(descMap.value)
		resultMap = getTemperatureResult(value)
	}
	else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
	else if (descMap.cluster == "FC45" && descMap.attrId == "0000") {
        if (logEnable) log.debug "parseReportAttributeMessage: HUMID descMap.value = ${descMap.value}"
        String humid = swapEndianHex(descMap.value)
        if (logEnable) log.debug "parseReportAttributeMessage: HUMID humid = ${humid}"
        descMap.value = humid
		
        def value = getReportAttributeHumidity(descMap.value)

        if ( value != null ) {
			resultMap = getHumidityResult(value)
        } else {
        	log.warn "Invalid humidity: null"
        }
	}
 
	return resultMap
}
 
private Map parseCustomMessage(String description) {
	Map resultMap = [:]
	if (description?.startsWith('temperature: ')) {
		def value = (description - "temperature: ").trim()
        if (logEnable) log.debug "parseCustomMessage: temperature value = ${value}"
        if (value.isNumber() && (value.toString() != "0.00"))  {
        	if (getTemperatureScale() == "F") {
            	value = celsiusToFahrenheit(value.toFloat()) as Float
			}
			resultMap = getTemperatureResult(value)
            return resultMap
        } else {
        	log.warn "invalid temperature: ${value}"
        }
	}
	else if (description?.startsWith('humidity: ')) {
		def pct = (description - "humidity: " - "%").trim()
		if (pct.isNumber()) {
			def value = Math.round(new BigDecimal(pct)).toString()
            if (logEnable) log.debug "parseCustomMessage: humidity value = ${value}"
			resultMap = getHumidityResult(value)
		} else {
			log.warn "invalid humidity: ${pct}"
		}
	}
    
	return resultMap
}

private Map getBatteryResult(rawValue) {
    if (logEnable) log.debug "Battery rawValue = ${rawValue}"
	float volts = rawValue / 10
    if (logEnable) log.debug "Battery volts = ${volts}"
	float minVolts = 2.1
    float maxVolts = 3.0
	float pct = (volts - minVolts) / (maxVolts - minVolts)
	def value = Math.min(100, (int) (pct * 100))
    if (logEnable) log.debug "Battery value = ${value}%"
	def descriptionText = "${device.displayName} Battery is ${value}%"
    return [
		name: 'battery',
		value: value,
        unit: 'V',
		descriptionText: descriptionText
	]
}

def getTemperature(value) {
    if (logEnable) log.debug "getTemperature value = ${value}"
	float celsius = (Integer.parseInt(value, 16).shortValue()) / 100
    if (logEnable) log.debug "getTemperature celsius = ${celsius}"
	if(getTemperatureScale() == "C"){
		return celsius
	} else {
        if (logEnable) log.debug "getTemperature Fahrenheit = ${celsiusToFahrenheit(celsius) as float}"
		return celsiusToFahrenheit(celsius) as float				// keep the decimals for now
	}
}

private Map getTemperatureResult(value) {
	if (logEnable) log.debug "getTemperatureResult: original value = ${value}°"
    float tmpValue = value.toFloat()
    def temperatureScale = getTemperatureScale()
    if (tempOffset) {
        tmpValue = tmpValue + tempOffset.toFloat()
    }
    tmpValue = tmpValue.round(2)
    if (logEnable) log.debug "getTemperatureResult: adjusted temperature = ${tmpValue}°"
	def descriptionText = "${device.displayName} Temperature is ${value}°${temperatureScale}"
	return [
		name: 'temperature',
		value: tmpValue,
        unit: "°" + temperatureScale,
		descriptionText: descriptionText
	]
}

def getReportAttributeHumidity(String value) {
    def humidity = null
    if (value?.trim()) {
        try {
        	// value is hex with no decimal
            def pct = Integer.parseInt(value.trim(), 16) / 100
            humidity = String.format('%3.0f', pct).trim()
            if (logEnable) log.debug "getReportAttributeHumidity: value = ${value}, pct = ${pct}, humidity = ${humidity}"
        } catch(NumberFormatException nfe) {
            log.error "Error converting value = ${value} to humidity"
        }
    }
    return humidity
}

private Map getHumidityResult(value) {
	if (logEnable) log.debug "getHumidityResult: original value = ${value}%"
    float tmpValue = value.toFloat()
	if (humidOffset) {
		tmpValue = tmpValue + humidOffset.toFloat()
	}
    tmpValue = tmpValue.round(0)
    def descriptionText = "${device.displayName} Humidity is ${tmpValue}%"
	return [
		name: 'humidity',
		value: tmpValue.toInteger(),
		unit: '%RH',
        descriptionText: descriptionText
	]
}

def refresh() {
	if (logEnable) log.debug "refresh temperature, humidity, and battery"

	return zigbee.readAttribute(0xFC45, 0x0000, ["mfgCode": 0x104E]) +   // New firmware
		zigbee.readAttribute(0xFC45, 0x0000, ["mfgCode": 0xC2DF]) +   // Original firmware
		zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)
}

def configure() {
	if (logEnable) log.debug "Configuring Reporting and Bindings."
    
    // humidity minReportTime 30s, maxReportTime 60min (i.e. 3600s). Reporting interval if no activity
	// temperature minReportTime 30s, maxReportTime 60min (i.e. 3600s). Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	
	return refresh() +
		zigbee.configureReporting(0xFC45, 0x0000, DataType.UINT16, 30, 3600, 100, ["mfgCode": 0x104E]) +   // New firmware
		zigbee.configureReporting(0xFC45, 0x0000, DataType.UINT16, 30, 3600, 100, ["mfgCode": 0xC2DF]) +   // Original firmware
		zigbee.batteryConfig() +
		zigbee.temperatureConfig(30, 3600)
}

def installed() {
    updated()
}

def updated() {
    if (logEnable) runIn(1800,logsOff)
    configure()
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}

/*
def refresh()
{
	log.debug "refresh temperature, humidity, and battery"
	[
		
		"zcl mfg-code 0xC2DF", "delay 1000",
		"zcl global read 0xFC45 0", "delay 1000",
		"send 0x${device.deviceNetworkId} 1 1", "delay 1000",
        "he rattr 0x${device.deviceNetworkId} 1 0x402 0", "delay 200",
        "he rattr 0x${device.deviceNetworkId} 1 1 0x20"

	]
}

def configure() {

	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	log.debug "Configuring Reporting and Bindings."
	def configCmds = [	
  
        
        "zcl global send-me-a-report 1 0x20 0x20 600 3600 {0100}", "delay 500",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1000",
        
        "zcl global send-me-a-report 0x402 0 0x29 300 3600 {6400}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",
        
        "zcl global send-me-a-report 0xFC45 0 0x29 300 3600 {6400}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",
        
        "zdo bind 0x${device.deviceNetworkId} 1 1 0xFC45 {${device.zigbeeId}} {}", "delay 1000",
		"zdo bind 0x${device.deviceNetworkId} 1 1 0x402 {${device.zigbeeId}} {}", "delay 500",
		"zdo bind 0x${device.deviceNetworkId} 1 1 1 {${device.zigbeeId}} {}"
	]
    return configCmds + refresh() // send refresh cmds as part of config
}
*/
