/**
 *  Aqara FP300 Presence Multi-Sensor driver for Hubitat
 *  Model: PS-S04D (lumi.sensor_occupy.agl8)
 *
 *  Simplified from the original multi-device driver by @kkossev.
 *  Retains full FP300 functionality; all other device support removed.
 *  Temperature and Humidity child device removed.  
 *
 *  Licensed under the Apache License, Version 2.0
 *
 *  Version  Date          Who              What
 *  1.0.0    2026-02-25    Dan Ogorchock    First release of streamlined driver code
 *  1.0.1    2026-03-01    Dan Ogorchock    Improved Temperature, Humidity, and Illuminance Zigbee reporting for battery life
 *  1.0.2    2026-03-02    Dan Ogorchock    Removed unnecessary configureReporting for Temperature, Humidity, and Illuminace.  The FP300 has special custom handling for these already.
 *
 */

static String version()   { "1.0.2" }
static String timeStamp() { "2026/03/02 20:30" }

import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import java.math.RoundingMode

@Field static final Integer INFO_AUTO_CLEAR_PERIOD    = 60
@Field static final Integer COMMAND_TIMEOUT           = 10
@Field static final Integer PRESENCE_COUNT_THRESHOLD  = 3      //deviceHealthCheck presence count threshold
@Field static final Integer DEFAULT_POLLING_INTERVAL  = 21600  //deviceHealthCheck polling interval

metadata {
    definition( name: "Aqara FP300 Presence Multi-Sensor", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "", singleThreaded: true) {
        capability "Sensor"
        capability "Motion Sensor"
        capability "Illuminance Measurement"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Battery"
        capability "Voltage Measurement"
        capability "Health Check"
        capability "Refresh"
        capability "Initialize"

        attribute "_status_",                "string"
        attribute "healthStatus",            "enum", ["unknown", "offline", "online"]
        attribute "roomState",               "enum", ["unoccupied", "occupied"]       // Presence / room state - mmWave sensor status
        attribute "pirDetection",            "enum", ["active", "inactive"]
        attribute "targetDistance",          "number"

        command "configure", [[name: "Wake the device with one button press.<br>Re-initialize the device and load defaults by clicking Run"]]
        command "ping", [[name: "Wake the device with one button press.<br>Check device online status and measure RTT by clicking Run"]]
        command "restartDevice", [[name: "Wake the device with one button press.<br>Restart Device by clicking Run"]]
        command "startSpatialLearning", [[name: "Wake the device with one button press.<br>Ensure the room is empty, then click Run to start a 30-second calibration"]]
        command "trackTargetDistance", [[name: "Wake the device with one button press.<br>Start tracking by clicking Run (~3 min reporting)"]]
        command "refresh", [[name: "Wake the device with one button press.<br>Read current parameters from device by clicking Run"]]

        fingerprint profileId: "0104", endpointId: "01", inClusters:  "0012,0400,0405,0402,0001,0003,0000,FCC0", outClusters: "000A,0019", model: "lumi.sensor_occupy.agl8", manufacturer: "Aqara", controllerType: "ZGB", deviceJoinName: "Aqara FP300 Presence Sensor PS-S04D"
    }

    preferences {
        input name: "txtEnable",  type: "bool",   title: "<b>Description text logging</b>",  defaultValue: true
        input name: "logEnable",  type: "bool",   title: "<b>Debug logging</b>",              defaultValue: true

        // ── Basic parameters ──────────────────────────────────────────────────
        input name: "presenceDetectionMode", type: "enum", title: "<b>Presence Detection Mode</b>", description: "Both - recommended", options: ["both": "Both mmWave+PIR", "mmwave": "mmWave only", "pir": "PIR only"], defaultValue: "both"
        if (presenceDetectionMode == "pir") {
            input name: "pirDetectionInterval", type: "number", title: "<b>PIR Detection Interval (2-300 s)</b>", description: "The interval duration in seconds for triggering infrared detection.", range: 2..300,  defaultValue: 30
        } else {
            input name: "motionSensitivity", type: "enum", title: "<b>Presence Detection Sensitivity</b>", description: "High - Suitable for bedrooms, small offices, studies, etc..<br>Medium - Sutiable for rooms like bathrooms, small conference rooms, etc..<br>Low - Suitable for complicated rooms with large area, which have plants and curtains.", options: ["1": "low", "2": "medium", "3": "high"], defaultValue: "2"
            input name: "absenceDelayTimer", type: "number", title: "<b>Absence Confirmation Period (10-300 s)</b>", description: "Used for accurate determination of 'no person' status, avoiding false alarms caused by personnel temporarily leaving or slight movements.", range: 10..300, defaultValue: 30
            input name: "detectionRangeZones", type: "string", title: "<b>Detection Range Zones</b>", description: "Comma-separated ranges in 0.25 m steps, e.g. '0.5-2.0' or '0.25-1.5,3.0-5.0'. Leave blank for all zones (0-6 m)."
        } 
        input name: "aiInterferenceIdentification", type: "bool", title: "<b>AI Interference Identification</b>", defaultValue: false
        input name: "aiSensitivityAdaptive", type: "bool",   title: "<b>AI Adaptive Sensitivity</b>", defaultValue: false

        // Temperature & Humidity sampling
        input name: "tempHumiditySamplingFrequency", type: "enum", title: "<b>Temperature and Humidity Detection</b>", description: "Sampling time frequency, increasing affects battery life. Setting to custom allows specifying period, interval & threshold.", options: ["0": "Off", "1": "Low", "2": "Medium", "3": "High", "4": "Custom"], defaultValue: "1"

        if (tempHumiditySamplingFrequency == "4") {
            // Custom Temperature & Humidity Sampling on device
            input name: "tempHumiditySamplingPeriod", type: "number", title: "<b>How often (seconds) temp & humidity readings are taken on the device when in custom mode.</b>", range: 1..3600, defaultValue: 30

            // Custom Temperature reporting
            input name: "temperatureReportingMode", type: "enum", title: "<b>Temperature reporting type when in custom mode.</b>", options: ["1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "temperatureReportingInterval", type: "number", title: "<b>Custom time interval for temperature data reporting. (s)</b>", range: 600..3600, defaultValue: 600
            input name: "temperatureReportingThreshold", type: "decimal", title: "<b>Reporting will trigger as temperature change reaches this value when in custom mode.(°C)</b>", range: 0.2..3.0, defaultValue: 1.0

            // Custom  Humidity reporting
            input name: "humidityReportingMode", type: "enum", title: "<b>Humidity reporting type when in custom mode.</b>", options: ["1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "humidityReportingInterval", type: "number", title: "<b>Custom time interval for humidity data reporting.(s)</b>", range: 600..3600, defaultValue: 600
            input name: "humidityReportingThreshold", type: "decimal", title: "<b>Reporting will trigger as humidity change reaches this value when in custom mode.(%)</b>", range: 2..10, defaultValue: 5.0
        }
        if (tempHumiditySamplingFrequency != "0") {
            input name: "tempOffset", type: "decimal", title: "<b>Temperature Offset (°)</b>", range: "-100..100", defaultValue: 0.0
            input name: "humidityOffset", type: "decimal", title: "<b>Humidity Offset (%)</b>", range: "-100..100", defaultValue: 0.0
        }
        // Illuminance sampling
        input name: "lightSamplingFrequency", type: "enum", title: "<b>Illuminance Sampling Detection</b>", description: "Sampling time frequency, increasing affects battery life. Setting to custom allows specifying period, interval & threshold.", options: ["0": "Off", "1": "Low", "2": "Medium", "3": "High", "4": "Custom"], defaultValue: "1"

        // Custom  Illuminance reporting
        if (lightSamplingFrequency == "4") {        
            input name: "lightSamplingPeriod", type: "number", title: "<b>How often Illuminance readings are taken on the device when in custom mode. (s)</b>", range: 0.5..3600, defaultValue: 30
            input name: "lightReportingMode", type: "enum", title: "<b>Illuminance reporting type when in custom mode.</b>", options: ["0": "No reporting", "1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "lightReportingInterval", type: "number", title: "<b>Custom interval for Illuminance data reporting. (s)</b>", range: 20..3600, defaultValue: 600
            input name: "lightReportingThreshold", type: "decimal", title: "<b>Reporting will trigger as Illuminance percentage change reaches this value when in custom mode. (%)</b>", range: 3..20, defaultValue: 20
        }
        // LED night settings
        input name: "ledDisabledNight", type: "bool", title: "<b>LED Disabled at Night</b>", description: "Enabling allows specifiying custom schedule", defaultValue: false
        if (ledDisabledNight) {
            input name: "ledNightTimeSchedule", type: "string", title: "<b>LED Night Time Schedule (HH:MM-HH:MM)</b>", description: "e.g. '21:00-09:00'. Only active when LED Disabled at Night is enabled."
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
// PARSE
// ════════════════════════════════════════════════════════════════════════════

void parse(String description) {
    checkDriverVersion()
    state.rxCounter = (state.rxCounter ?: 0) + 1
    setHealthStatusOnline()

    // Fast-path: Xiaomi struct in cluster 0000
    if (description.contains("cluster: 0000") && description.contains("attrId: FF01")) {
        parseAqaraAttributeFF01(description); return
    }
    if (description.contains("cluster: 0000") && description.contains("attrId: FF02")) {
        parseAqaraAttributeFF02(description); return
    }

    Map descMap = [:]
    try { descMap = zigbee.parseDescriptionAsMap(description) }
    catch (e) { logWarn "parse exception: ${e}"; return }

    logDebug "parse: ${descMap}"

    if (descMap.attrId != null) {
        List attrData = [[cluster: descMap.cluster, attrId: descMap.attrId,
                          value: descMap.value, status: descMap.status]]
        descMap.additionalAttrs?.each {
            attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
        }
        attrData.each { parseAttribute(description, descMap, it) }
    }
    else if (descMap.profileId == "0000") {
        parseZDOcommand(descMap)
    }
    else if (descMap.clusterId != null && descMap.profileId == "0104") {
        parseZHAcommand(descMap)
    }
    else {
        logDebug "Unprocessed: cluster=${descMap.clusterId} cmd=${descMap.command} data=${descMap.data}"
    }
}

private void parseAttribute(String description, Map descMap, Map it) {
    if (it.status == "86") { logWarn "Unsupported cluster ${it.cluster} attr ${it.attrId}"; return }

    switch (it.cluster) {
        case "0400":    // Illuminance
            if (it.attrId == "0000") illuminanceEvent(Integer.parseInt(it.value, 16))
            break
        case "0402":    // Temperature
            if (it.attrId == "0000") temperatureEvent(Integer.parseInt(it.value, 16) / 100.0)
            break
        case "0405":    // Humidity
            if (it.attrId == "0000") humidityEvent(Integer.parseInt(it.value, 16) / 100.0)
            break
        case "0001":    // Battery
            if (it.attrId == "0020" && it.value != "00") {
                //logDebug "parseAttribute 0x0001 - battery percentage: ${Integer.parseInt(it.value, 16)}%"
                voltageAndBatteryEvents(Integer.parseInt(it.value, 16) / 10.0)
            } else {
                logWarn "parseAttribute FP300 unknown report (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            }
            break
        case "0000":
            if (it.attrId == "0001") sendRttEvent()
            else if (it.attrId == "0005") sendInfoEvent("Button was pressed – device awake for 15 min")
            else if (it.attrId == "FF01") parseAqaraAttributeFF01(description)
            break
        case "FCC0":
            parseAqaraClusterFCC0(description, descMap, it)
            break
        default:
            logDebug "Unprocessed attr: cluster=${it.cluster} attrId=${it.attrId} value=${it.value}"
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AQARA FCC0 CLUSTER PARSING
// ════════════════════════════════════════════════════════════════════════════

private void parseAqaraClusterFCC0(String description, Map descMap, Map it) {
    int value = safeToInt(it.value)
    switch (it.attrId) {
        case "0005":
            //logDebug "Device button pressed"
            logDebug "parseAqaraClusterFCC0 - Device button pressed"
            break
        case "0016" : // FP300 unknown report
            logDebug "<b>Received FP300 unknown report</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0017":    // Battery voltage (mV)
            sendVoltageEvent(Integer.parseInt(it.value, 16) / 1000.0)
            logDebug "FP300 battery voltage: ${Integer.parseInt(it.value, 16) / 1000.0}V (${value} mV)"
            break
        case "0018":    // Battery percentage
            sendBatteryEvent(Integer.parseInt(it.value, 16))
            logDebug "FP300 battery percentage: ${Integer.parseInt(it.value, 16)}%"
            break
        case "00E6" : // FP300 unknown report
            logDebug "<b>Received FP300 unknown report</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "00E8":
            logWarn "FP300 restart response (cluster=FCC0 attrId=00E8 value=${it.value})"
            break
        case "00EE" : // FP300 unknown report
            logDebug "<b>Received FP300 unknown report</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "00F7":
            decodeAqaraStruct(description)
            break
        case "00FC":
            log.warn "LUMI LEAVE report received (cluster=FCC0 attrId=00FC value=${it.value})"
            break
        case "010C":    // Motion sensitivity
            device.updateSetting("motionSensitivity", [value: value.toString(), type: "enum"])
            storeParamValue("motionSensitivity", value.toString(), "enum", false)
            logDebug "Motion sensitivity: ${value}"
            break    
        case "0142":    // Room state / presence
            state.mmwaveState = value
            updateMotionState("mmwave")
            roomStateEvent(value)
            logDebug "(attr. 0x0142) roomState (mmWave 'presence') is ${value ? 'occupied' : 'unoccupied'} (${value})"
            break
        case "014D":    // PIR detection state
            state.pirState = value
            updateMotionState("pir")
            pirDetectionEvent(value)
            logDebug "(attr. 0x014D) pirDetection is ${value ? 'active' : 'inactive'} (${value})"
            break
        case "014F":    // PIR detection interval
            value = Integer.parseInt(it.value, 16)
            device.updateSetting("pirDetectionInterval", [value: value.toString(), type: "number"])
            storeParamValue("pirDetectionInterval", value, "number", false)
            logDebug "PIR detection interval: ${value} seconds"
            break
        case "015D":    // AI adaptive sensitivity
            device.updateSetting("aiSensitivityAdaptive", [value: value ? true : false, type: "bool"])
            storeParamValue("aiSensitivityAdaptive", value ? true : false, "bool", false)
            logDebug "AI adaptive sensitivity: ${value ? 'on' : 'off'}"
            break
        case "015E":    // AI interference identification
            device.updateSetting("aiInterferenceIdentification", [value: value ? true : false, type: "bool"])
            storeParamValue("aiInterferenceIdentification", value ? true : false, "bool", false)
            logDebug "AI interference identification: ${value ? 'on' : 'off'}"
            break
        case "015F":    // Target distance (cm)
            value = Integer.parseInt(it.value, 16)
            targetDistanceEvent(value)
            logDebug "(0x015F) received FP300 target_distance report: ${value} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0162":    // Temp/humidity sampling period (ms)
            storeParamValue("tempHumiditySamplingPeriod", (Integer.parseInt(it.value, 16) / 1000) as Integer, "number", false)
            logDebug "FP300 temp/humidity sampling period: ${(Integer.parseInt(it.value, 16) / 1000) as Integer} seconds"
            break
        case "0163":    // Temperature reporting interval (ms)
            storeParamValue("temperatureReportingInterval", (Integer.parseInt(it.value, 16) / 1000) as int, "number", false)
            logDebug "FP300 temperature reporting interval: ${(Integer.parseInt(it.value, 16) / 1000) as int} seconds"
            break
        case "0164":    // Temperature reporting threshold (centidegrees)
            storeParamValue("temperatureReportingThreshold", Integer.parseInt(it.value, 16) / 100.0, "decimal", false)
            logDebug "FP300 temperature reporting threshold: ${String.format('%.1f', Integer.parseInt(it.value, 16) / 100.0)}°C"
            break
        case "0165":    // Temperature reporting mode
            storeParamValue("temperatureReportingMode", Integer.parseInt(it.value, 16).toString(), "enum", false)
            def modes = ["unknown", "threshold", "reporting interval", "threshold and interval"]
            logDebug "FP300 temperature reporting mode: ${modes[value] ?: 'unknown'} (${value})"
            break
        case "016A":    // Humidity reporting interval (ms)
            storeParamValue("humidityReportingInterval", (Integer.parseInt(it.value, 16) / 1000) as int, "number", false)
            logDebug "FP300 humidity reporting interval: ${(Integer.parseInt(it.value, 16) / 1000)} seconds"
            break
        case "016B":    // Humidity reporting threshold (%*100)
            storeParamValue("humidityReportingThreshold", Integer.parseInt(it.value, 16) / 100.0, "decimal", false)
            logDebug "FP300 humidity reporting threshold: ${String.format('%.1f', Integer.parseInt(it.value, 16) / 100.0)}%"
            break
        case "016C":    // Humidity reporting mode
            storeParamValue("humidityReportingMode", Integer.parseInt(it.value, 16).toString(), "enum", false)
            def modes = ["unknown", "threshold", "reporting interval", "threshold and interval"]
            logDebug "FP300 humidity reporting mode: ${modes[value] ?: 'unknown'} (${value})"
            break
        case "0170":    // Temp/humidity sampling frequency
            storeParamValue("tempHumiditySamplingFrequency", Integer.parseInt(it.value, 16).toString(), "enum", false)
            def frequencies = ["off", "low", "medium", "high", "custom"]
            logDebug "FP300 temp/humidity sampling frequency: ${frequencies[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0192":    // Light sampling frequency
            storeParamValue("lightSamplingFrequency", Integer.parseInt(it.value, 16).toString(), "enum", false)
            def frequencies = ["off", "low", "medium", "high", "custom"]
            logDebug "FP300 light sampling frequency: ${frequencies[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0193":    // Light sampling period (ms)
            storeParamValue("lightSamplingPeriod", (Integer.parseInt(it.value, 16) / 1000) as Integer, "number", false)
            logDebug "FP300 light sampling period: ${(Integer.parseInt(it.value, 16) / 1000) as Integer} seconds"
            break
        case "0194":    // Light reporting interval (ms)
            storeParamValue("lightReportingInterval", (Integer.parseInt(it.value, 16) / 1000) as int, "number", false)
            logDebug "FP300 light reporting interval: ${(Integer.parseInt(it.value, 16) / 1000)} seconds"
            break
        case "0195":    // Light reporting threshold (%*100)
            storeParamValue("lightReportingThreshold", Integer.parseInt(it.value, 16) / 100.0, "decimal", false)
            logDebug "FP300 light reporting threshold: ${String.format('%.1f', Integer.parseInt(it.value, 16) / 100.0)}%"
            break
        case "0196":    // Light reporting mode
            storeParamValue("lightReportingMode", Integer.parseInt(it.value, 16).toString(), "enum", false)
            def modes = ["No reporting", "Threshold only", "Interval only", "Threshold and Interval"]
            logDebug "FP300 light reporting mode: ${modes[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0197":    // Absence delay timer
            value = Integer.parseInt(it.value, 16)
            device.updateSetting("absenceDelayTimer", [value: value.toString(), type: "number"])
            storeParamValue("absenceDelayTimer", value, "number", false)
            logDebug "FP300 absence delay timer: ${value} seconds"
            break
        case "0198":    // Track target distance status
            value = Integer.parseInt(it.value, 16)
            if (value == 1) sendInfoEvent("Target distance tracking enabled for ~3 minutes")
            if (value == 0 && device.currentValue("targetDistance") != null) {
                device.deleteCurrentState("targetDistance")
                logInfo "Target distance tracking disabled – attribute removed"
            }
            break
        case "0199":    // Presence detection mode
            def modes = ["both", "mmwave", "pir"]
            def modeName = modes[value] ?: "both"
            device.updateSetting("presenceDetectionMode", [value: modeName, type: "enum"])
            storeParamValue("presenceDetectionMode", modeName, "enum", false)
            logDebug "FP300 presence detection mode: ${modes[value] ?: 'both'}"
            break
        case "019A":    // Detection range zones (bitmap)
            parseDetectionRangeZonesReport(it.value)
            break
        case "0203":    // LED disabled at night
            def ledState = value ? "on" : "off"
            device.updateSetting("ledDisabledNight", [value: value ? true : false, type: "bool"])
            storeParamValue("ledDisabledNight", value ? true : false, "bool", false)
            logDebug "FP300 LED disabled at night: ${ledState} (value=${value})"
            break
        case "023E":    // LED night time schedule (UINT32)
            long sched = Long.parseLong(it.value, 16)
            def schedStr = String.format("%02d:%02d-%02d:%02d",
                sched & 0xFF, (sched >> 8) & 0xFF, (sched >> 16) & 0xFF, (sched >> 24) & 0xFF)
            device.updateSetting("ledNightTimeSchedule", [value: schedStr, type: "string"])
            storeParamValue("ledNightTimeSchedule", schedStr, "string", false)
            logDebug "FP300 LED night time schedule: ${schedStr} (raw: 0x${it.value})"
            break
        default:
            logDebug "Unprocessed FCC0 attr: attrId=${it.attrId} value=${it.value}"
    }
}

private void parseDetectionRangeZonesReport(String rawHex) {
    if (!rawHex || rawHex.isEmpty() || rawHex == "00") {
        return
    }
    def bitmapHex = rawHex
    if (rawHex.startsWith("0300"))     bitmapHex = rawHex.substring(4)
    else if (rawHex.startsWith("00")) bitmapHex = rawHex.substring(2)

    def rangeValue = 0
    if (bitmapHex.length() >= 6) {
        rangeValue = Integer.parseInt(bitmapHex[0..1], 16) | (Integer.parseInt(bitmapHex[2..3], 16) << 8) | (Integer.parseInt(bitmapHex[4..5], 16) << 16)
    } else if (bitmapHex.length() >= 4) {
        rangeValue = Integer.parseInt(bitmapHex[0..1], 16) | (Integer.parseInt(bitmapHex[2..3], 16) << 8)
    } else if (bitmapHex.length() >= 2) {
        rangeValue = Integer.parseInt(bitmapHex[0..1], 16)
    }

    storeParamValue("detectionRangeZones", String.format("%06X", rangeValue), "string", false)

}

// ════════════════════════════════════════════════════════════════════════════
// XIAOMI STRUCT  (attrId FF01 / FF02)
// ════════════════════════════════════════════════════════════════════════════

void parseAqaraAttributeFF01(String description) {
    def valueHex = description.split(",").find { it.split(":")[0].trim() == "value" }?.split(":")[1]?.trim()
    parseBatteryTLV(valueHex)
}

void parseAqaraAttributeFF02(String description) {
    def valueHex = description.split(",").find { it.split(":")[0].trim() == "value" }?.split(":")[1]?.trim()
    parseBatteryTLV(valueHex)
}

private void parseBatteryTLV(String valueHex) {
    if (!valueHex) return
    if (!(valueHex ==~ /(?i)^[0-9a-f]+$/)) {
        StringBuilder sb = new StringBuilder()
        for (char ch : valueHex.toCharArray()) sb.append(String.format("%02X", ((int) ch) & 0xFF))
        valueHex = sb.toString()
    }
    int L = valueHex.length()
    for (int i = 0; i <= L - 8; i += 2) {
        if (valueHex.substring(i, i+2).equalsIgnoreCase("01") && valueHex.substring(i+2, i+4).equalsIgnoreCase("21")) {
            int rawmV = Integer.parseInt(valueHex.substring(i+6, i+8) + valueHex.substring(i+4, i+6), 16)
            if (rawmV > 0) voltageAndBatteryEvents(rawmV / 1000.0G)
            return
        }
    }
}

void decodeAqaraStruct(String description) {
    def valueHex = description.split(",").find { it.split(":")[0].trim() == "value" }?.split(":")[1]?.trim()
    if (!valueHex) return
    int MsgLength = valueHex.size()
    for (int i = 2; i < (MsgLength - 3); ) {
        int dataType = Integer.parseInt(valueHex[(i+2)..(i+3)], 16)
        int tag      = Integer.parseInt(valueHex[(i+0)..(i+1)], 16)
        int rawValue = 0
        switch (dataType) {
            case 0x08: case 0x10: case 0x18: case 0x20: case 0x28: case 0x30:
                rawValue = Integer.parseInt(valueHex[(i+4)..(i+5)], 16)
                switch (tag) {
                    case 0x18: sendBatteryEvent(rawValue); break
                    case 0x64: logDebug "FP300 presence tag 0x64: ${rawValue}"; break
                    default:   logDebug "decodeAqaraStruct 1B unhandled tag=0x${valueHex[(i+0)..(i+1)]} type=0x${valueHex[(i+2)..(i+3)]} val=${rawValue}"
                }
                i += 6; break
            case 0x21:
                rawValue = Integer.parseInt(valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)], 16)
                switch (tag) {
                    case 0x01: voltageAndBatteryEvents(rawValue / 1000.0); break
                    case 0x17: sendVoltageEvent(rawValue / 1000.0); break
                    default:   logDebug "decodeAqaraStruct 2B unhandled tag=0x${valueHex[(i+0)..(i+1)]} val=${rawValue}"
                }
                i += 8; break
            case 0x0B: case 0x1B: case 0x23: case 0x2B:
                i += 12; break
            case 0x24:
                i += 14; break
            default:
                logDebug "decodeAqaraStruct unknown dataType=0x${valueHex[(i+2)..(i+3)]} at i=${i}"
                // WARNING: unknown data type size; incrementing by 2 may mis-parse remaining struct data.
                i += 2; break
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EVENT HELPERS
// ════════════════════════════════════════════════════════════════════════════

void updateMotionState(String source = null) {
    boolean mmwave = state.mmwaveState == 1
    boolean pir    = state.pirState == 1
    
    String mode = settings?.presenceDetectionMode ?: "both"

    boolean active =
        (mode == "mmwave") ? mmwave :             //if mode is "mmwave" then 'active' = current value of mmwave detector 
        (mode == "pir")    ? pir :                //else, if mode is "pir" then 'active' = current value of pir detector
                             (mmwave || pir)      //else, mode must be "both", so if either mmwave OR pir is true then 'active' = true, otherwise false

    String newState = active ? "active" : "inactive"

    if (device.currentValue("motion") != newState) {
        sendEvent(name: "motion", value: newState, descriptionText: "Motion ${newState} (source: ${source})")
        logInfo "motion has changed to ${newState} (source: ${source})"
    }
}

def roomStateEvent(int value) {
    String status = value ? "occupied" : "unoccupied"
    sendEvent(name: "roomState", "value": status)
    logInfo "roomState (mmWave 'presence') is ${status}"
}
                                              
def pirDetectionEvent(int value) {
    String status = value ? "active" : "inactive"
    sendEvent(name: "pirDetection", "value": status)
    logInfo "pirDetection is ${status}"
}

void illuminanceEvent(int rawLux) {
    if (rawLux == 0xFFFF) { logWarn "Ignored rawLux 0xFFFF"; return }
    int lux = rawLux > 0 ? (int) Math.round(Math.pow(10, ((rawLux - 1) / 10000.0))) : 0
    if (lux > 0xFFDC) lux = 0xFFDC
    def corrected = Math.round(lux)
    sendEvent(name: "illuminance", value: corrected, unit: "lx")
    logInfo "illuminance is ${corrected} lx"
}

void temperatureEvent(double temperature) {
    double offset = (settings?.tempOffset ?: 0) as double
    double temp   = temperature + offset
    if (location.temperatureScale == "F") { temp = temp * 1.8 + 32 }
    def rounded = new BigDecimal(temp).setScale(1, BigDecimal.ROUND_HALF_UP)
    sendEvent(name: "temperature", value: rounded, unit: "°${location.temperatureScale}")
    logInfo "temperature is ${rounded}°${location.temperatureScale}"
}

void humidityEvent(double humidity) {
    double offset = (settings?.humidityOffset ?: 0) as double
    int hum = (int) Math.max(0, Math.min(100, Math.round(humidity + offset)))
    sendEvent(name: "humidity", value: hum, unit: "%")
    logInfo "humidity is ${hum}%"
}

void targetDistanceEvent(int distanceCm) {
    def d = new BigDecimal(distanceCm / 100.0).setScale(2, RoundingMode.HALF_UP)
    sendEvent(name: "targetDistance", value: d)
    logInfo "target distance is ${d} m"
}

void voltageAndBatteryEvents(def rawVolts) {
    def minV = 2.85; def maxV = 3.0
    def pct  = Math.min(100, Math.max(0, Math.round(((rawVolts - minV) / (maxV - minV)) * 100)))
    sendEvent(name: "voltage", value: rawVolts, unit: "V")
    sendEvent(name: "battery",        value: pct,      unit: "%")
    logInfo "battery is ${pct}% (${rawVolts}V)"
}

void sendVoltageEvent(def rawVolts) {
    sendEvent(name: "voltage", value: rawVolts, unit: "V")
    logInfo "battery voltage is ${rawVolts}V"
}

void sendBatteryEvent(int pct) {
    sendEvent(name: "battery", value: pct, unit: "%")
    logInfo "battery is ${pct}%"
}

// ════════════════════════════════════════════════════════════════════════════
// ZDO / ZHA COMMAND PARSING
// ════════════════════════════════════════════════════════════════════════════

void parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        case "0013":
            logInfo "Device announcement received"
            fp300BlackMagic()
            break
        case "8021":
            logDebug "Bind response: ${descMap.data[1] == '00' ? 'Success' : 'Failure'}"
            break
        default:
            logDebug "ZDO: clusterId=${descMap.clusterId} data=${descMap.data}"
    }
}

void parseZHAcommand(Map descMap) {
    switch (descMap.command) {
        case "04":
            logDebug "Write Attribute Response: ${descMap.data[0] == '00' ? 'Success' : 'Failure'}"
            break
        case "07":
            logDebug "Configure Reporting Response for cluster ${descMap.clusterId}: ${descMap.data[0] == '00' ? 'Success' : 'Failure'}"
            break
        case "0B":
            if (descMap.data[1] != "00") logDebug "ZCL Default Response cmd=${descMap.data[0]} status=${descMap.data[1]} cluster=${descMap.clusterId}"
            break
        default:
            logDebug "ZHA: clusterId=${descMap.clusterId} cmd=${descMap.command} data=${descMap.data}"
    }
}

// ════════════════════════════════════════════════════════════════════════════
// COMMANDS
// ════════════════════════════════════════════════════════════════════════════

void ping() {
    logInfo "ping..."
    scheduleCommandTimeoutCheck()
    state.pingTime = new Date().getTime()
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0))
}

void sendRttEvent() {
    def rtt = (new Date().getTime()).toInteger() - (state.pingTime?.toInteger() ?: 0)
    logInfo "Round-Trip Time: ${rtt} ms"
    state.rtt = rtt
    sendInfoEvent("Round-Trip Time: ${rtt} ms")
}

void restartDevice() {
    logInfo "Restarting device..."
    sendZigbeeCommands(zigbee.writeAttribute(0xFCC0, 0x00E8, DataType.BOOLEAN, 0x00, [mfgCode: 0x115F], 0))
}

void startSpatialLearning() {
    logInfo "Starting Spatial Learning..."
    sendZigbeeCommands(zigbee.writeAttribute(0xFCC0, 0x0157, DataType.UINT8, 0x01, [mfgCode: 0x115F], 100))  //0x0157 is believed to start spacial learning on the FP300 sensor
    sendInfoEvent("Spatial Learning started – ensure room is empty for 30 seconds")
    runIn(35, "spatialLearningReset", [overwrite: true])
}

void spatialLearningReset() {
    sendInfoEvent("Spatial Learning complete")
    logInfo "Spatial Learning reset to idle"
}

void trackTargetDistance() {
    logInfo "Enabling target distance tracking (~3 min)..."
    sendInfoEvent("Requesting target distance tracking")
    sendZigbeeCommands(zigbee.writeAttribute(0xFCC0, 0x0198, DataType.UINT8, 0x01, [mfgCode: 0x115F], 0))
}

void refresh() {
    logInfo "Refreshing FP300 parameters..."
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, [0x010C, 0x0142, 0x014D, 0x014F, 0x0197, 0x0199, 0x015D, 0x015E], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [0x0162, 0x0170, 0x0192, 0x0193], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [0x0163, 0x0164, 0x0165], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [0x016A, 0x016B, 0x016C], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [0x0194, 0x0195, 0x0196], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x019A, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [0x0203, 0x023E], [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)
    cmds += zigbee.readAttribute(0x0400, 0x0000, [:], delay=200)
    sendZigbeeCommands(cmds)
}

// ════════════════════════════════════════════════════════════════════════════
// UPDATED (preferences saved)
// ════════════════════════════════════════════════════════════════════════════

void updated() {
    logDebug "updated()"
    checkDriverVersion()
    
    if (logEnable) runIn(1800, "logsOff", [overwrite: true, misfire: "ignore"])  //Enable the debug logging for 30 minutes (i.e. 1800 seconds)
    else           unschedule("logsOff")

    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])

    List<String> cmds = []

    if (hasParamChanged("presenceDetectionMode", settings?.presenceDetectionMode)) {
        int val = ["both": 0, "mmwave": 1, "pir": 2][settings.presenceDetectionMode] ?: 0
        cmds += zigbee.writeAttribute(0xFCC0, 0x0199, 0x20, val, [mfgCode: 0x115F], delay=200)
    }
    
    if (presenceDetectionMode == "pir") {
        if (hasParamChanged("pirDetectionInterval", settings?.pirDetectionInterval)) {
            int val = safeToInt(settings.pirDetectionInterval)
            cmds += zigbee.writeAttribute(0xFCC0, 0x014F, 0x21, val, [mfgCode: 0x115F], delay=200)
        }
    } else {
        if (hasParamChanged("motionSensitivity", settings?.motionSensitivity)) {
            int val = safeToInt(settings.motionSensitivity)
            cmds += zigbee.writeAttribute(0xFCC0, 0x010C, 0x20, val, [mfgCode: 0x115F], delay=200)
            cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("absenceDelayTimer", settings?.absenceDelayTimer)) {
            int val = safeToInt(settings.absenceDelayTimer)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0197, 0x23, val, [mfgCode: 0x115F], delay=200)
        }
        // Detection range zones
        def parseResult = parseDetectionRangeInput(settings?.detectionRangeZones ?: "")
        def newBitmapHex = parseResult.success ? String.format("%06X", parseResult.bitmap) : null
        if (newBitmapHex && hasParamChanged("detectionRangeZones", newBitmapHex)) {
            parseResult.errors.each { logWarn "Detection range: ${it}" }
            cmds += zigbee.writeAttribute(0xFCC0, 0x019A, 0x41, detectionRangeBitmapToPayload(parseResult.bitmap), [mfgCode: 0x115F], delay=200)
            cmds += zigbee.readAttribute(0xFCC0, 0x019A, [mfgCode: 0x115F], delay=200)
        }
    }
    
    if (hasParamChanged("aiInterferenceIdentification", settings?.aiInterferenceIdentification)) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x015E, 0x20, settings.aiInterferenceIdentification ? 1 : 0, [mfgCode: 0x115F], delay=200)
    }
    if (hasParamChanged("aiSensitivityAdaptive", settings?.aiSensitivityAdaptive)) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x015D, 0x20, settings.aiSensitivityAdaptive ? 1 : 0, [mfgCode: 0x115F], delay=200)
    }

    // Virtual/offset params – store locally only
    if (hasParamChanged("tempOffset",     settings?.tempOffset))     storeParamValue("tempOffset",     settings.tempOffset,     "decimal", true)
    if (hasParamChanged("humidityOffset", settings?.humidityOffset)) storeParamValue("humidityOffset", settings.humidityOffset, "decimal", true)

    // Temperature and Humidity
    if (hasParamChanged("tempHumiditySamplingFrequency", settings?.tempHumiditySamplingFrequency) && settings?.tempHumiditySamplingFrequency != null) {
        int val = safeToInt(settings.tempHumiditySamplingFrequency)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0170, 0x20, val, [mfgCode: 0x115F], delay=200)
/*        
        if (settings?.tempHumiditySamplingFrequency == "0") {
            // Disable temperature/humidity reporting if sampling frequency is "Off"
            cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 0, 0xFFFF, 0, [:], delay=200)  // Temperature disabled
            cmds += zigbee.configureReporting(0x0405, 0x0000, 0x21, 0, 0xFFFF, 0, [:], delay=200)  // Humidity disabled
        } else {
            // Enable temperature/humidity reporting if sampling frequency is NOT "Off"
            cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, 14400, 10, [:], delay=100) // min 30s, max 14400s, delta 0.1°C
            cmds += zigbee.configureReporting(0x0405, 0x0000, 0x21, 30, 14400, 100, [:], delay=100) // min 30s, max 14400s, delta 1%
        }
*/
    }
        
    if (tempHumiditySamplingFrequency == "4") {
        // Custom Temperature and Humidity sampling & reporting
        if (hasParamChanged("tempHumiditySamplingPeriod", settings?.tempHumiditySamplingPeriod) && settings?.tempHumiditySamplingPeriod) {
            int val = safeToInt(settings.tempHumiditySamplingPeriod)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0162, 0x23, val * 1000, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("temperatureReportingThreshold", settings?.temperatureReportingThreshold)) {
            cmds += zigbee.writeAttribute(0xFCC0, 0x0164, 0x21, ((settings.temperatureReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("temperatureReportingInterval", settings?.temperatureReportingInterval)) {
            int val = safeToInt(settings.temperatureReportingInterval)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0163, 0x23, val * 1000, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("temperatureReportingMode", settings?.temperatureReportingMode)) {
            int val = safeToInt(settings.temperatureReportingMode)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0165, 0x20, val, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("humidityReportingThreshold", settings?.humidityReportingThreshold)) {
            cmds += zigbee.writeAttribute(0xFCC0, 0x016B, 0x21, ((settings.humidityReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("humidityReportingInterval", settings?.humidityReportingInterval)) {
            int val = safeToInt(settings.humidityReportingInterval)
            cmds += zigbee.writeAttribute(0xFCC0, 0x016A, 0x23, val * 1000, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("humidityReportingMode", settings?.humidityReportingMode)) {
            int val = safeToInt(settings.humidityReportingMode)
            cmds += zigbee.writeAttribute(0xFCC0, 0x016C, 0x20, val, [mfgCode: 0x115F], delay=200)
        }
    }

    // Illuminance
    if (hasParamChanged("lightSamplingFrequency", settings?.lightSamplingFrequency) && settings?.lightSamplingFrequency != null) {
        int val = safeToInt(settings.lightSamplingFrequency)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0192, 0x20, val, [mfgCode: 0x115F], delay=200)
/*        
        if (settings?.lightSamplingFrequency == "0") {
            // Disable illuminance reporting if sampling frequency is "Off"
            cmds += zigbee.configureReporting(0x0400, 0x0000, 0x21, 0, 0xFFFF, 0, [:], delay=200)  // Illuminance disabled
        } else {
            // Enable illuminance reporting if sampling frequency is NOT "Off"
            cmds += zigbee.configureReporting(0x0400, 0x0000, 0x21, 30, 14400, 50, [:], delay=100) // min 30s, max 14400s, delta 50 lux
        }
*/
    }
    
    if (lightSamplingFrequency == "4") {
        // Custom Illuminance sampling & reporting
        if (hasParamChanged("lightSamplingPeriod", settings?.lightSamplingPeriod) && settings?.lightSamplingPeriod) {
            int val = safeToInt(settings.lightSamplingPeriod)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0193, 0x23, val * 1000, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("lightReportingInterval", settings?.lightReportingInterval)) {
            int val = safeToInt(settings.lightReportingInterval)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0194, 0x23, val * 1000, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("lightReportingThreshold", settings?.lightReportingThreshold)) {
            cmds += zigbee.writeAttribute(0xFCC0, 0x0195, 0x21, ((settings.lightReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=200)
        }
        if (hasParamChanged("lightReportingMode", settings?.lightReportingMode)) {
            int val = safeToInt(settings.lightReportingMode)
            cmds += zigbee.writeAttribute(0xFCC0, 0x0196, 0x20, val, [mfgCode: 0x115F], delay=200)
        }
    }


    // LED night disable
    if (hasParamChanged("ledDisabledNight", settings?.ledDisabledNight)) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x0203, 0x10, settings.ledDisabledNight ? 1 : 0, [mfgCode: 0x115F], delay=200)
    }
    if (ledDisabledNight) {
        def schedStr = settings?.ledNightTimeSchedule ?: "21:00-09:00"
        if (hasParamChanged("ledNightTimeSchedule", schedStr) ||
            (settings?.ledDisabledNight == true && hasParamChanged("ledDisabledNight", settings?.ledDisabledNight))) {
            def payload = ledNightTimeToPayload(schedStr)
            if (payload != null) cmds += zigbee.writeAttribute(0xFCC0, 0x023E, 0x23, payload.intValue(), [mfgCode: 0x115F], delay=200)
        }
    }

    if (cmds) sendZigbeeCommands(cmds)
    else logInfo "No parameter changes requiring device commands."
    
    runIn(5, "refresh")
}

// ════════════════════════════════════════════════════════════════════════════
// LIFECYCLE
// ════════════════════════════════════════════════════════════════════════════

void installed() {
    log.info "${device.displayName} installed() – FP300 driver v${version()}"
    sendHealthStatusEvent("unknown")
    runIn(4, "fp300BlackMagic")
}

void configure(boolean fullInit = false) {
    log.info "${device.displayName} configure() fullInit=${fullInit}"
    unschedule()
    if (logEnable) runIn(1800, "logsOff", [overwrite: true, misfire: "ignore"])  //Enable the debug logging for 30 minutes (i.e. 1800 seconds)
    initializeVars(fullInit)
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
    runIn(5, "fp300BlackMagic")
    runIn(15, "updated")
    logWarn "If no further logs appear, re-pair the device to Hubitat."
}

void initialize() {
    log.info "${device.displayName} initialize() called"
    //configure(true)
    state.pirState   = device.currentValue("pirDetection") == "active"  ? 1 : 0
    state.mmwaveState = device.currentValue("roomState")   == "occupied" ? 1 : 0
}

void initializeVars(boolean fullInit = false) {
    if (fullInit) state.clear()
    if (state.params == null)          state.params          = []
    if (state.rxCounter == null)       state.rxCounter       = 0
    if (state.txCounter == null)       state.txCounter       = 0
    if (state.notPresentCounter == null) state.notPresentCounter = 0

    if (fullInit || settings?.logEnable  == null) device.updateSetting("logEnable",  true)
    if (fullInit || settings?.txtEnable  == null) device.updateSetting("txtEnable",  true)

    if (fullInit || settings?.presenceDetectionMode == null)    device.updateSetting("presenceDetectionMode", "both")
    if (fullInit || settings?.absenceDelayTimer == null)        device.updateSetting("absenceDelayTimer", [value: 30, type: "number"])
    if (fullInit || settings?.pirDetectionInterval == null)     device.updateSetting("pirDetectionInterval", [value: 10, type: "number"])
    if (fullInit || settings?.aiInterferenceIdentification == null) device.updateSetting("aiInterferenceIdentification", false)
    if (fullInit || settings?.aiSensitivityAdaptive == null)    device.updateSetting("aiSensitivityAdaptive", false)
    if (fullInit || settings?.tempOffset == null)               device.updateSetting("tempOffset", 0)
    if (fullInit || settings?.humidityOffset == null)           device.updateSetting("humidityOffset", 0)

    //ToDo: Add other user preferences to the above list
    
    if (fullInit) {
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

// ════════════════════════════════════════════════════════════════════════════
// HEALTH CHECK
// ════════════════════════════════════════════════════════════════════════════

void setHealthStatusOnline() {
    if ((state.rxCounter ?: 0) <= 2) return
    sendHealthStatusEvent("online")
    state.notPresentCounter = 0
    unschedule("deviceCommandTimeout")
}

void deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter >= PRESENCE_COUNT_THRESHOLD) {
        sendHealthStatusEvent("offline")
    }
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, "deviceCommandTimeout")
}

void deviceCommandTimeout() {
    logWarn "No ping response received"
    // FP300 is battery-powered – don't aggressively flip offline on a missed ping
}

void sendHealthStatusEvent(String value) {
    if (device.currentValue("healthStatus") != value) {
        def msg = "healthStatus changed to ${value}"
        sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} ${msg}")
        value != "online" ? log.warn("${device.displayName} ${msg}") : log.info("${device.displayName} ${msg}")
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ZIGBEE INITIALIZATION (Black Magic for FP300)
// ════════════════════════════════════════════════════════════════════════════

void fp300BlackMagic() {
    List<String> cmds = []
    // Bind temperature, humidity, and illuminance clusters
    
    // Bind temperature cluster (0x0402)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}", "delay 50"]
//    cmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, 14400, 10, [:], delay=100) // min 30s, max 14400s, delta 0.1°C    // Unnecessary for the FP300
    
    // Bind humidity cluster (0x0405)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0405 {${device.zigbeeId}} {}", "delay 50"]
//    cmds += zigbee.configureReporting(0x0405, 0x0000, 0x21, 30, 14400, 100, [:], delay=100) // min 30s, max 14400s, delta 1%      // Unnecessary for the FP300
    
    // Bind  illuminance cluster (0x0400)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}", "delay 50"]
//    cmds += zigbee.configureReporting(0x0400, 0x0000, 0x21, 30, 14400, 50, [:], delay=100) // min 30s, max 14400s, delta 50 lux   // Unnecessary for the FP300
    
    // Bind manufacturer cluster and read initial values
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"]
    
    sendZigbeeCommands(cmds)
    
    // Read initial state shortly after binding
    runIn(10, "refresh")
}

// ════════════════════════════════════════════════════════════════════════════
// PARAMETER CHANGE DETECTION
// ════════════════════════════════════════════════════════════════════════════

void storeParamValue(String name, Object value, String type, Boolean isLocal = false) {
    if (state.params == null) state.params = []
    def existing = state.params.find { it.n == name }
    if (existing?.v == value && existing?.t == type) return   // no change
    state.params.removeAll { it.n == name }
    state.params << [n: name, t: type, v: value, l: isLocal]
}

Object getStoredParamValue(String name) {
    return state.params?.find { it.n == name }?.v
}

Boolean hasParamChanged(String name, Object newValue) {
    def stored = getStoredParamValue(name)
    if (stored == null) return newValue != null
    if (newValue == null) return false
    return normalizeParam(newValue) != normalizeParam(stored)
}

private Object normalizeParam(Object v) {
    if (v instanceof String) {
        if (v.isInteger())                     return v.toInteger()
        if (v.isDouble())                      return v.toDouble()
        if (v.toLowerCase() in ["true","false"]) return v.toLowerCase() == "true"
    }
    return v
}

// ════════════════════════════════════════════════════════════════════════════
// DETECTION RANGE HELPERS
// ════════════════════════════════════════════════════════════════════════════

Map parseDetectionRangeInput(String input) {
    def result = [success: true, bitmap: 0, zones: [], errors: []]
    if (!input || input.trim().isEmpty()) { result.bitmap = 0xFFFFFF; return result }

    input.split(",").each { range ->
        def parts = range.trim().split("-")
        if (parts.size() != 2) { result.errors << "Invalid format '${range}'"; return }
        try {
            def startM = parts[0].trim() as BigDecimal
            def endM   = parts[1].trim() as BigDecimal
            startM = Math.max(0G, Math.min(6.0G, startM))   // clip first
            endM   = Math.max(0G, Math.min(6.0G, endM))
            if (startM >= endM) { result.errors << "Start >= end or out of range in '${range}'"; return }           
            int startZ = (int)(startM / 0.25)
            int endZ   = (int) Math.ceil(endM / 0.25) - 1
            for (int i = Math.max(0, startZ); i <= Math.min(23, endZ); i++) result.bitmap |= (1 << i)
            result.zones << "${startM}m-${endM}m"
        } catch (NumberFormatException e) { result.errors << "Invalid number in '${range}'" }
    }
    if (result.bitmap == 0) { result.success = false; result.errors << "No valid zones" }
    return result
}

String detectionRangeBitmapToPayload(int bitmap) {
    return String.format("050300%02X%02X%02X", bitmap & 0xFF, (bitmap >> 8) & 0xFF, (bitmap >> 16) & 0xFF)
}

Long ledNightTimeToPayload(String timeRange) {
    if (!timeRange || timeRange.trim().isEmpty()) timeRange = "21:00-09:00"
    def parts = timeRange.trim().split("-")
    if (parts.size() != 2) { logWarn "Invalid LED schedule format: ${timeRange}"; return null }
    try {
        def s = parts[0].trim().split(":"); def e = parts[1].trim().split(":")
        if (s.size() != 2 || e.size() != 2) { logWarn "Invalid time in LED schedule"; return null }
        int sH = s[0] as int, sM = s[1] as int, eH = e[0] as int, eM = e[1] as int
        if (sH < 0 || sH > 23 || eH < 0 || eH > 23 || sM < 0 || sM > 59 || eM < 0 || eM > 59) {
            logWarn "LED schedule time out of range"; return null
        }
        return (long)sH | ((long)sM << 8) | ((long)eH << 16) | ((long)eM << 24)
    } catch (Exception ex) { logWarn "Failed to parse LED schedule '${timeRange}': ${ex.message}"; return null }
}

// ════════════════════════════════════════════════════════════════════════════
// INFO / STATUS EVENT
// ════════════════════════════════════════════════════════════════════════════

void sendInfoEvent(String info = null) {
    if (!info || info == "clear") {
        sendEvent(name: "_status_", value: "clear")
    } else {
        logInfo info
        sendEvent(name: "_status_", value: info)
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")
    }
}

void clearInfoEvent() { sendInfoEvent("clear") }

// ════════════════════════════════════════════════════════════════════════════
// DRIVER VERSION
// ════════════════════════════════════════════════════════════════════════════

static String driverVersionAndTimeStamp() { version() + " " + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion != driverVersionAndTimeStamp()) {
        logInfo "Updating driver version from ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        state.driverVersion = driverVersionAndTimeStamp()
        if (state.params == null) state.params = []
    }
}

// ════════════════════════════════════════════════════════════════════════════
// UTILITIES
// ════════════════════════════════════════════════════════════════════════════

Integer safeToInt(val, Integer defaultVal = 0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

void sendZigbeeCommands(List<String> cmds) {
    logDebug "sending Zigbee: ${cmds}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    state.txCounter = (state.txCounter ?: 0) + 1
}

void logsOff() {
    log.info "${device.displayName} debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

void logDebug(String msg) { if (settings?.logEnable)  log.debug "${device.displayName} ${msg}" }
void logInfo(String msg)  { if (settings?.txtEnable)  log.info  "${device.displayName} ${msg}" }
void logWarn(String msg)  { if (settings?.logEnable)  log.warn  "${device.displayName} ${msg}" }
