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
 *  1.0.2    2026-03-02    Dan Ogorchock    Removed unnecessary configureReporting for Temperature, Humidity, and Illuminance.  The FP300 has special custom handling for these already.
 *  1.0.3    2026-03-02    Dan Ogorchock    Added Import URL
 *  1.0.4    2026-03-02    Dan Ogorchock    Additional code cleanup
 *  1.0.5    2026-03-06    Dan Ogorchock    Improved efficiency
 *  1.0.6    2026-03-12    Dan Ogorchock    Simplified User Preferences logic & improved User Preferences titles and descriptions
 *  1.0.7    2026-03-12    Dan Ogorchock    Automatically remove state.params variable (thanks @hubitrep!)
 *  1.0.8    2026-03-12    Dan Ogorchock    Only send Presence Detection Mode setting to the FP300 if the value has changed.  If it is changed, Spatial Learning must be run to calibrate the mmWave sensor.
 *                                          Removed aiInterferenceIdentification & aiSensitivityAdaptive user preferences, as setting these also messes up the mmWave calibration.  Will revisit if they are actually useful.
 *  1.0.9    2026-03-12    Dan Ogorchock    Replace @Field variables with traditional state variables.  @Field variables are reset each time the driver source code is saved, which can lead to unexpected behaviors.
 *                                          Added aiInterferenceIdentification & aiSensitivityAdaptive back as Advanced/Experimental user preferences. Only send these 2 setting to the FP300 if the value has changed. 
 *  1.0.10   2026-03-17    Dan Ogorchock    Added Firmware Version info thanks to @hubitrep!
 *  1.0.11   2026-03-17    Dan Ogorchock    Minor changes to refresh() to reduce the chance of overwhelming the FP300 sensor, clean up firmware version number reporting, fix dateCode no being updated reliably
 *
 */

static String version()   { "1.0.11" }
static String timeStamp() { "2026/03/17 20:30" }

import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

@Field static final Integer INFO_AUTO_CLEAR_PERIOD    = 60
@Field static final Integer COMMAND_TIMEOUT           = 10
@Field static final Integer PRESENCE_COUNT_THRESHOLD  = 3      //deviceHealthCheck presence count threshold
@Field static final Integer DEFAULT_POLLING_INTERVAL  = 21600  //deviceHealthCheck polling interval

metadata {
    definition( name: "Aqara FP300 Presence Multi-Sensor", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Drivers/aqara-fp300.src/aqara-fp300.groovy", singleThreaded: true) {
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
        capability "Configuration"
        capability "Presence Sensor"
        
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
        input name: "txtEnable",  type: "bool",   title: "<b>Description text logging</b>", description: "Provides informative log data during communications with the FP300 sensor.",  defaultValue: true
        input name: "logEnable",  type: "bool",   title: "<b>Debug logging</b>", description: "Provides detailed log data to help debug the driver code.<br>Will automatically disable itself after 30 minutes.",              defaultValue: true

        // ── Basic parameters ──────────────────────────────────────────────────
        input name: "presenceDetectionMode", type: "enum", title: "<b>Presence Detection Mode</b>", description: "<b>WARNING: Changing this setting will result in losing the mmWave sensor's current calibration.  You should re-run Spatial Learning if this setting is changed!</b><br> * 'Both mmWave+PIR' is the recommended setting.<br> * 'PIR only' reveals PIR Detection Interval preference and hides mmWave prefereces.<br> * 'Both mmWave+PIR' or 'mmWave only' reveals mmWave specific preferences and hides unsued PIR Detection Interval preference.", options: ["both": "Both mmWave+PIR", "mmwave": "mmWave only", "pir": "PIR only"], defaultValue: "both"
        if (presenceDetectionMode == "pir") {
            input name: "pirDetectionInterval", type: "number", title: "<b>PIR Detection Interval (2-300s)</b>", description: "The interval duration in seconds for triggering infrared detection.", range: 2..300,  defaultValue: 10
        } else {
            input name: "motionSensitivity", type: "enum", title: "<b>Presence Detection Sensitivity</b>", description: "High - Suitable for bedrooms, small offices, studies, etc..<br>Medium - Sutiable for rooms like bathrooms, small conference rooms, etc..<br>Low - Suitable for complicated rooms with large area, which have plants and curtains.", options: ["1": "low", "2": "medium", "3": "high"], defaultValue: "2"
            input name: "absenceDelayTimer", type: "number", title: "<b>Absence Confirmation Period (10-300s)</b>", description: "Used for accurate determination of 'no person' status, avoiding false alarms caused by personnel temporarily leaving or slight movements.", range: 10..300, defaultValue: 10
            input name: "detectionRangeZones", type: "string", title: "<b>Detection Range Zones</b>", description: "Comma-separated ranges in 0.25 m steps, e.g. '0.5-2.0' or '0.25-1.5,3.0-5.0'. Leave blank for all zones (0-6 m)."
        } 

        // Temperature & Humidity sampling
        input name: "tempHumiditySamplingFrequency", type: "enum", title: "<b>Temperature and Humidity Sampling Frequency</b>", description: "Sampling time frequency, increasing lowers battery life.<br>Setting to 'Custom' allows specifying period, interval & threshold via additional preferences.", options: ["0": "Off", "1": "Low", "2": "Medium", "3": "High", "4": "Custom"], defaultValue: "1"

        if (tempHumiditySamplingFrequency == "4") {
            // Custom Temperature & Humidity Sampling on device
            input name: "tempHumiditySamplingPeriod", type: "number", title: "<b>Temperature and Humidity Sampling Period (s)</b>", description: "How often in seconds temp & humidity readings are taken on the device when in custom mode.", range: 1..3600, defaultValue: 30

            // Custom Temperature reporting
            input name: "temperatureReportingMode", type: "enum", title: "<b>Temperature Reporting Mode</b>", description: "Temperature reporting type when in custom mode.", options: ["1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "temperatureReportingInterval", type: "number", title: "<b>Temperature Reporting Interval (s)</b>", description: "Custom time interval for temperature data reporting.", range: 600..3600, defaultValue: 600
            input name: "temperatureReportingThreshold", type: "decimal", title: "<b>Temperature Reporting Threshold (°C)</b>", description: "Reporting will trigger as temperature change reaches this value when in custom mode.", range: 0.2..3.0, defaultValue: 1.0

            // Custom  Humidity reporting
            input name: "humidityReportingMode", type: "enum", title: "<b>Humidity Reporting Mode</b>", description: "Humidity reporting type when in custom mode.", options: ["1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "humidityReportingInterval", type: "number", title: "<b>Humidity Reporting Interval (s)</b>", description: "Custom time interval for humidity data reporting.", range: 600..3600, defaultValue: 600
            input name: "humidityReportingThreshold", type: "decimal", title: "<b>Humidity Reporting Threshold (%)</b>", description: "Reporting will trigger as humidity change reaches this value when in custom mode.", range: 2..10, defaultValue: 5.0
        }
        if (tempHumiditySamplingFrequency != "0") {
            input name: "tempOffset", type: "decimal", title: "<b>Temperature Offset (°)</b>", description: "Change reflected on next temperature data update from the sensor.", range: "-100..100", defaultValue: 0.0
            input name: "humidityOffset", type: "decimal", title: "<b>Humidity Offset (%)</b>", description: "Change reflected on next humidity data update from the sensor.", range: "-100..100", defaultValue: 0.0
        }
        // Illuminance sampling
        input name: "lightSamplingFrequency", type: "enum", title: "<b>Illuminance Sampling Frequency</b>", description: "Sampling time frequency, increasing lowers battery life.<br>Setting to 'Custom' allows specifying period, interval & threshold via additional preferences.", options: ["0": "Off", "1": "Low", "2": "Medium", "3": "High", "4": "Custom"], defaultValue: "1"

        // Custom  Illuminance reporting
        if (lightSamplingFrequency == "4") {        
            input name: "lightSamplingPeriod", type: "number", title: "<b>Illuminance Sampling Period</b>", description: "How often Illuminance readings are taken on the device when in custom mode. (s)", range: 0.5..3600, defaultValue: 30
            input name: "lightReportingMode", type: "enum", title: "<b>Illuminance Reporting Mode</b>", description: "Illuminance reporting type when in custom mode.", options: ["1": "Threshold only", "2": "Interval only", "3": "Threshold and Interval"], defaultValue: "1"
            input name: "lightReportingInterval", type: "number", title: "<b>Illuminance Reporting Interval (s)</b>", description: "Custom interval for Illuminance data reporting.", range: 20..3600, defaultValue: 600
            input name: "lightReportingThreshold", type: "decimal", title: "<b>Illuminance Reporting Threshold (%)</b>", description: "Reporting will trigger as Illuminance percentage change reaches this value when in custom mode.", range: 3..20, defaultValue: 20
        }
        // LED night settings
        input name: "ledDisabledNight", type: "bool", title: "<b>LED Disabled at Night</b>", description: "Enabling allows specifiying custom schedule by revelaling an additional preference.", defaultValue: false
        if (ledDisabledNight) {
            input name: "ledNightTimeSchedule", type: "string", title: "<b>LED Night Time Schedule (HH:MM-HH:MM)</b>", description: "e.g. '21:00-09:00'. Only active when LED Disabled at Night is enabled."
        }

        // Advanced/Experimental options
        input (name: "advancedOptions", type: "bool", title: "<b>Advanced/Experimental Options</b>", description: "<b>WARNING: Enabling these settings may result in losing the mmWave sensor's current calibration.</b><br>Show advanced/experimental configuration options (refresh page to see options)", defaultValue: false, submitOnChange: true)
        if (advancedOptions == true) {
            input name: "aiInterferenceIdentification", type: "bool", title: "<b>AI Interference Identification</b>", description: "<b>WARNING: Changing this setting may result in losing the mmWave sensor's current calibration.  You may want to re-run Spatial Learning if this setting is changed!</b><br>Designed to enhance detection accuracy by distinguishing between human presence and moving, non-human objects. It enables the sensor to learn the environment and ignore false triggers caused by common household items, thereby reducing ghosting and false alarms.", defaultValue: false
            input name: "aiSensitivityAdaptive", type: "bool",   title: "<b>AI Adaptive Sensitivity</b>", description: "<b>WARNING: Changing this setting may result in losing the mmWave sensor's current calibration.  You may want to re-run Spatial Learning if this setting is changed!</b><br>Uses machine learning to automatically adjust motion detection sensitivity based on the environment.", defaultValue: false
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
            if (it.attrId == "0001") {
                sendRttEvent()
            }
            else if (it.attrId == "0004") { 
                device.updateDataValue("manufacturer", it.value ?: "")
                logDebug "Manufacturer: ${it.value}" 
            }
            else if (it.attrId == "0005") {
                device.updateDataValue("model", it.value ?: ""); logDebug "Model: ${it.value}"
                if (descMap.command == "0A") sendInfoEvent("Button was pressed – device awake for 15 min")
            }
            else if (it.attrId == "0006") { 
                device.updateDataValue("dateCode", it.value ?: "")
                logDebug "Date code: ${it.value}" 
            }
            else if (it.attrId == "FF01") {
                parseAqaraAttributeFF01(description)
            }
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
            logDebug "Motion sensitivity: ${value}"
            break    
        case "0142":    // mmWave detection state (i.e. Room state / presence)
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
            logDebug "PIR detection interval: ${value} seconds"
            break
        case "015D":    // AI adaptive sensitivity
            state.aiSensitivityAdaptiveCache = value ? true : false
            device.updateSetting("aiSensitivityAdaptive", [value: value ? true : false, type: "bool"])
            logDebug "AI adaptive sensitivity: ${value ? 'on' : 'off'}"
            break
        case "015E":    // AI interference identification
            state.aiInterferenceIdentificationCache = value ? true : false
            device.updateSetting("aiInterferenceIdentification", [value: value ? true : false, type: "bool"])
            logDebug "AI interference identification: ${value ? 'on' : 'off'}"
            break
        case "015F":    // Target distance (cm)
            value = Integer.parseInt(it.value, 16)
            targetDistanceEvent(value)
            logDebug "(0x015F) received FP300 target_distance report: ${value} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0162":    // Temp/humidity sampling period (ms)
            value = Integer.parseInt(it.value, 16) / 1000
            device.updateSetting("tempHumiditySamplingPeriod", [value: value.toString(), type: "number"])
            logDebug "FP300 temp/humidity sampling period: ${value} seconds"
            break
        case "0163":    // Temperature reporting interval (ms)
            value = Integer.parseInt(it.value, 16) / 1000
            device.updateSetting("temperatureReportingInterval", [value: value.toString(), type: "number"])
            logDebug "FP300 temperature reporting interval: ${value} seconds"
            break
        case "0164":    // Temperature reporting threshold (degrees Celsius)
            float f_value = Integer.parseInt(it.value, 16) / 100.0
            device.updateSetting("temperatureReportingThreshold", [value: f_value.toString(), type: "decimal"])
            logDebug "FP300 temperature reporting threshold: ${f_value}°C"
            break
        case "0165":    // Temperature reporting mode
            device.updateSetting("temperatureReportingMode", [value: value.toString(), type: "enum"])
            def modes = ["unknown", "threshold", "reporting interval", "threshold and interval"]
            logDebug "FP300 temperature reporting mode: ${modes[value] ?: 'unknown'} (${value})"
            break
        case "016A":    // Humidity reporting interval (ms)
            value = Integer.parseInt(it.value, 16) / 1000
            device.updateSetting("humidityReportingInterval", [value: value.toString(), type: "number"])
            logDebug "FP300 humidity reporting interval: ${value} seconds"
            break
        case "016B":    // Humidity reporting threshold (%*100)
            float f_value = Integer.parseInt(it.value, 16) / 100.0
            device.updateSetting("humidityReportingThreshold", [value: f_value.toString(), type: "decimal"])
            logDebug "FP300 humidity reporting threshold: ${f_value}%"
            break
        case "016C":    // Humidity reporting mode
            device.updateSetting("humidityReportingMode", [value: value.toString(), type: "enum"])
            def modes = ["unknown", "threshold", "reporting interval", "threshold and interval"]
            logDebug "FP300 humidity reporting mode: ${modes[value] ?: 'unknown'} (${value})"
            break
        case "0170":    // Temp/humidity sampling frequency
            device.updateSetting("tempHumiditySamplingFrequency", [value: value.toString(), type: "enum"])
            def frequencies = ["off", "low", "medium", "high", "custom"]
            logDebug "FP300 temp/humidity sampling frequency: ${frequencies[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0192":    // Light sampling frequency
            device.updateSetting("lightSamplingFrequency", [value: value.toString(), type: "enum"])
            def frequencies = ["off", "low", "medium", "high", "custom"]
            logDebug "FP300 light sampling frequency: ${frequencies[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0193":    // Light sampling period (ms)
            value = Integer.parseInt(it.value, 16) / 1000
            device.updateSetting("lightSamplingPeriod", [value: value.toString(), type: "number"])
            logDebug "FP300 light sampling period: ${value} seconds"
            break
        case "0194":    // Light reporting interval (ms)
            value = Integer.parseInt(it.value, 16) / 1000
            device.updateSetting("lightReportingInterval", [value: value.toString(), type: "number"])
            logDebug "FP300 light reporting interval: ${value} seconds"
            break
        case "0195":    // Light reporting threshold (%*100)
            float f_value = Integer.parseInt(it.value, 16) / 100.0
            device.updateSetting("lightReportingThreshold", [value: f_value.toString(), type: "decimal"])
            logDebug "FP300 light reporting threshold: ${f_value}%"
            break
        case "0196":    // Light reporting mode
            device.updateSetting("lightReportingMode", [value: value.toString(), type: "enum"])
            def modes = ["No reporting", "Threshold only", "Interval only", "Threshold and Interval"]
            logDebug "FP300 light reporting mode: ${modes[Integer.parseInt(it.value, 16)] ?: 'unknown'} (${value})"
            break
        case "0197":    // Absence delay timer
            value = Integer.parseInt(it.value, 16)
            device.updateSetting("absenceDelayTimer", [value: value.toString(), type: "number"])
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
        case "0199":    // Presence Detection Mode
            def modes = ["both", "mmwave", "pir"]
            def modeName = modes[value] ?: "both"
            device.updateSetting("presenceDetectionMode", [value: modeName, type: "enum"])
            state.presenceDetectionModeCache = modeName
            logDebug "FP300 presence detection mode: ${modes[value] ?: 'both'}"
            break
        case "019A":    // Detection range zones (bitmap)
            parseDetectionRangeZonesReport(it.value)
            logDebug "FP300 DetectionRangeZones value = ${value})"
            break
        case "0203":    // LED disabled at night
            def ledState = value ? "on" : "off"
            device.updateSetting("ledDisabledNight", [value: value ? true : false, type: "bool"])
            logDebug "FP300 LED disabled at night: ${ledState} (value=${value})"
            break
        case "023E":    // LED night time schedule (UINT32)
            long sched = Long.parseLong(it.value, 16)
            def schedStr = String.format("%02d:%02d-%02d:%02d",
                sched & 0xFF, (sched >> 8) & 0xFF, (sched >> 16) & 0xFF, (sched >> 24) & 0xFF)
            device.updateSetting("ledNightTimeSchedule", [value: schedStr, type: "string"])
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
            case 0x42:  // String type – Aqara often encodes firmware version here
                int strLen = Integer.parseInt(valueHex[(i+4)..(i+5)], 16)
                if (i + 6 + strLen * 2 <= MsgLength) {
                    String strVal = new String(valueHex[(i+6)..(i+5+strLen*2)].decodeHex())
                    switch (tag) {
                        case 0x03: device.updateDataValue("aqaraFirmware", strVal); logDebug "Aqara firmware (tag 0x03): ${strVal}"; break
                        case 0x05: device.updateDataValue("aqaraModel", strVal); logDebug "Aqara model (tag 0x05): ${strVal}"; break
                        default:   logDebug "decodeAqaraStruct string tag=0x${valueHex[(i+0)..(i+1)]} val='${strVal}'"
                    }
                }
                i += 6 + strLen * 2; break
            case 0x0B: case 0x1B: case 0x23: case 0x2B:
                if (dataType == 0x23) {
                    rawValue = Integer.parseInt(valueHex[(i+10)..(i+11)] + valueHex[(i+8)..(i+9)] + valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)], 16)
                    if (tag == 0x0D) {
                        // Matches fw version format reported in some reddit posts.  Might need to be tweaked later as the ".0_" is hardcoded below per @hubitrep
                        String fwVer = "${(rawValue >> 24) & 0xFF}.${(rawValue >> 16) & 0xFF}.0_${String.format("%02d%02d", (rawValue >> 8) & 0xFF, rawValue & 0xFF)}"
                        device.updateDataValue("aqaraVersion", fwVer)
                        logDebug "Aqara firmware version (tag 0x0D): ${fwVer}"
                        if (device.getDataValue("aqaraVersionInt")) device.removeDataValue("aqaraVersionInt")  // ToDo: remove this line in a later version after users' hubs are cleaned up
                    } else {
                        logDebug "decodeAqaraStruct 4B tag=0x${valueHex[(i+0)..(i+1)]} val=${rawValue}"
                    }
                }
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
    
    boolean mmwave = state.mmwaveState == 1 ? true : false
    boolean pir    = state.pirState == 1 ? true : false
    String newState
    
    String mode = settings?.presenceDetectionMode ?: "both"
    
    switch (mode) {
        case "both":
            newState = mmwave || pir ? "active" : "inactive"
            break
        case "pir":
            newState = pir ? "active" : "inactive"
            break
        case "mmwave":
            newState = mmwave ? "active" : "inactive"
            break
        default:
            log.warn "Unknown Presence Detection Mode = ${mode}"
    }   

    if (device.currentValue("motion") != newState) {
        sendEvent(name: "motion", value: newState, descriptionText: "Motion ${newState} (source: ${source})")
        logInfo "motion has changed to ${newState} (source: ${source})"
    }
    
    logDebug "updateMotionState() - source = ${source}, mmwave = ${mmwave}, pir = ${pir}, mode = ${mode}, newState = ${newState}, current Motion = ${device.currentValue("motion")}"
}

def roomStateEvent(int value) {
    String status = value ? "occupied" : "unoccupied"
    String presence = value ? "present" : "not present"
    sendEvent(name: "roomState", "value": status)
    sendEvent(name: "presence", "value": presence)
    logInfo "roomState (mmWave 'presence') is ${status} | presence: ${presence}"
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
    sendEvent(name: "battery", value: pct,      unit: "%")
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
    cmds += zigbee.readAttribute(0xFCC0, [0x010C, 0x0142, 0x014D, 0x014F, 0x0197], [mfgCode: 0x115F], delay=1000)
    cmds += zigbee.readAttribute(0xFCC0, [0x0199, 0x015D, 0x015E, 0x0162, 0x0170], [mfgCode: 0x115F], delay=1000)
    cmds += zigbee.readAttribute(0xFCC0, [0x0192, 0x0193, 0x0163, 0x0164, 0x0165], [mfgCode: 0x115F], delay=1000)
    cmds += zigbee.readAttribute(0xFCC0, [0x016A, 0x016B, 0x016C, 0x0194, 0x0195], [mfgCode: 0x115F], delay=1000)
    cmds += zigbee.readAttribute(0xFCC0, [0x0196, 0x019A, 0x0203, 0x023E, 0x00F7], [mfgCode: 0x115F], delay=1000)
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=1000)
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=1000)
    cmds += zigbee.readAttribute(0x0400, 0x0000, [:], delay=1000)
    // Read device details from Basic cluster
    cmds += zigbee.readAttribute(zigbee.BASIC_CLUSTER, [0x0004, 0x0005, 0x0006], [:], delay=1000)

    sendZigbeeCommands(cmds)
}

// ════════════════════════════════════════════════════════════════════════════
// UPDATED (preferences saved)
// ════════════════════════════════════════════════════════════════════════════

void updated() {
    log.info "${device.displayName} updated() called."
    checkDriverVersion()
	
    if (state.params != null)state.remove("params")  //remove legacy state.params variable
	
    if (logEnable) runIn(1800, "logsOff", [overwrite: true, misfire: "ignore"])  //Enable the debug logging for 30 minutes (i.e. 1800 seconds)
    else           unschedule("logsOff")

    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])

    List<String> cmds = []
    int val = 0
    
    // Only update Presence Detection Mode on the FP300 sensor if the value has changed.  Otherwise, the mmWave sensor calibration will get messed up!
    logDebug "updated() - presenceDetectionModeCache = ${state.presenceDetectionModeCache}, settings.presenceDetectionMode = ${settings.presenceDetectionMode}"
    if (state.presenceDetectionModeCache != settings.presenceDetectionMode) {
        val = ["both": 0, "mmwave": 1, "pir": 2][settings.presenceDetectionMode] ?: 0
        cmds += zigbee.writeAttribute(0xFCC0, 0x0199, 0x20, val, [mfgCode: 0x115F], delay=1000)
        //state.presenceDetectionModeCache = settings.presenceDetectionMode   // Will be stored after reply is parsed
        log.warn "updated() - presenceDetectionMode setting changed, ${val} sent to FP300 sensor.  <b>Please run Spatial Learning with the room empty!</b>"
    } else {
        logDebug "updated() - presenceDetectionMode setting unchanged, not sent to FP300 sensor"
    }
    
    if (presenceDetectionMode == "pir") {
        val = safeToInt(settings.pirDetectionInterval)
        cmds += zigbee.writeAttribute(0xFCC0, 0x014F, 0x21, val, [mfgCode: 0x115F], delay=1000)
    } else {
        val = safeToInt(settings.motionSensitivity)
        cmds += zigbee.writeAttribute(0xFCC0, 0x010C, 0x20, val, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.absenceDelayTimer)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0197, 0x23, val, [mfgCode: 0x115F], delay=1000)
        def parseResult = parseDetectionRangeInput(settings?.detectionRangeZones ?: "")
        def newBitmapHex = parseResult.success ? String.format("%06X", parseResult.bitmap) : null
        if (newBitmapHex) {
            parseResult.errors.each { logWarn "Detection range: ${it}" }
            cmds += zigbee.writeAttribute(0xFCC0, 0x019A, 0x41, detectionRangeBitmapToPayload(parseResult.bitmap), [mfgCode: 0x115F], delay=1000)
        }
    }

    // Adcanced/Experimental AI Features
    if (advancedOptions == true) {
        // Only update aiInterferenceIdentification on the FP300 sensor if the value has changed.  Otherwise, the mmWave sensor calibration will get messed up!
        logDebug "updated() - aiInterferenceIdentificationCache = ${state.aiInterferenceIdentificationCache}, settings.presenceDetectionMode = ${settings.aiInterferenceIdentification}"
        if (state.aiInterferenceIdentificationCache != settings.aiInterferenceIdentification) {
            cmds += zigbee.writeAttribute(0xFCC0, 0x015E, 0x20, settings.aiInterferenceIdentification ? 1 : 0, [mfgCode: 0x115F], delay=1000)
            //state.aiInterferenceIdentificationCache = settings.aiInterferenceIdentification   // Will be stored after reply is parsed
            log.warn "updated() - aiInterferenceIdentification setting changed, ${settings.aiInterferenceIdentification ? 1 : 0} sent to FP300 sensor.  <b>May need to run Spatial Learning with the room empty!</b>"
        } else {
            logDebug "updated() - aiInterferenceIdentification setting unchanged, not sent to FP300 sensor"
        }
        // Only update aiSensitivityAdaptive on the FP300 sensor if the value has changed.  Otherwise, the mmWave sensor calibration will get messed up!
        logDebug "updated() - aiSensitivityAdaptiveCache = ${state.aiSensitivityAdaptiveCache}, settings.presenceDetectionMode = ${settings.aiSensitivityAdaptive}"
        if (state.aiSensitivityAdaptiveCache != settings.aiSensitivityAdaptive) {
            cmds += zigbee.writeAttribute(0xFCC0, 0x015D, 0x20, settings.aiSensitivityAdaptive ? 1 : 0, [mfgCode: 0x115F], delay=1000)
            //state.aiSensitivityAdaptiveCache = settings.aiSensitivityAdaptive   // Will be stored after reply is parsed
             log.warn "updated() - aiSensitivityAdaptive setting changed, ${settings.aiSensitivityAdaptive ? 1 : 0} sent to FP300 sensor.  <b>May need to run Spatial Learning with the room empty!</b>"
        } else {
            logDebug "updated() - aiSensitivityAdaptive setting unchanged, not sent to FP300 sensor"
        }
    }
    
    // Temperature and Humidity
    if (tempHumiditySamplingFrequency != "4") {    // If Low, Med, or High
        val = 3                                    // set Temperature and Humiity Sampling modes to "Threshold and Interval" to ensure data is transmitted from the sensor
        cmds += zigbee.writeAttribute(0xFCC0, 0x0165, 0x20, val, [mfgCode: 0x115F], delay=1000)
        cmds += zigbee.writeAttribute(0xFCC0, 0x016C, 0x20, val, [mfgCode: 0x115F], delay=1000)
    } 
    else {
        // Custom Temperature and Humidity sampling & reporting
        val = safeToInt(settings.tempHumiditySamplingPeriod)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0162, 0x23, val * 1000, [mfgCode: 0x115F], delay=1000)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0164, 0x21, ((settings.temperatureReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.temperatureReportingInterval)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0163, 0x23, val * 1000, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.temperatureReportingMode)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0165, 0x20, val, [mfgCode: 0x115F], delay=1000)
        cmds += zigbee.writeAttribute(0xFCC0, 0x016B, 0x21, ((settings.humidityReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.humidityReportingInterval)
        cmds += zigbee.writeAttribute(0xFCC0, 0x016A, 0x23, val * 1000, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.humidityReportingMode)
        cmds += zigbee.writeAttribute(0xFCC0, 0x016C, 0x20, val, [mfgCode: 0x115F], delay=1000)
    }
    
    val = safeToInt(settings.tempHumiditySamplingFrequency)
    cmds += zigbee.writeAttribute(0xFCC0, 0x0170, 0x20, val, [mfgCode: 0x115F], delay=1000)
    
    // Illuminance
    if (lightSamplingFrequency != "4") {     // If Low, Med, or High
            val = 3                          // set LightSamplingFrequency to "Threshold and Interval" to ensure data is transmitted from the sensor
            cmds += zigbee.writeAttribute(0xFCC0, 0x0196, 0x20, val, [mfgCode: 0x115F], delay=1000)
    }
    else {
        // Custom Illuminance sampling & reporting
        val = safeToInt(settings.lightSamplingPeriod)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0193, 0x23, val * 1000, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.lightReportingInterval)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0194, 0x23, val * 1000, [mfgCode: 0x115F], delay=1000)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0195, 0x21, ((settings.lightReportingThreshold as BigDecimal) * 100) as Integer, [mfgCode: 0x115F], delay=1000)
        val = safeToInt(settings.lightReportingMode)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0196, 0x20, val, [mfgCode: 0x115F], delay=1000)
    }
    
    val = safeToInt(settings.lightSamplingFrequency)
    cmds += zigbee.writeAttribute(0xFCC0, 0x0192, 0x20, val, [mfgCode: 0x115F], delay=1000)
    

    // LED Disable at Night
    cmds += zigbee.writeAttribute(0xFCC0, 0x0203, 0x10, settings.ledDisabledNight ? 1 : 0, [mfgCode: 0x115F], delay=1000)

    if (ledDisabledNight && ledNightTimeSchedule != null) {
        def schedStr = settings?.ledNightTimeSchedule ?: "21:00-09:00"
        def payload = ledNightTimeToPayload(schedStr)
        if (payload != null) cmds += zigbee.writeAttribute(0xFCC0, 0x023E, 0x23, payload.intValue(), [mfgCode: 0x115F], delay=1000)
    }
   
    sendZigbeeCommands(cmds)
    runIn(30, "refresh")
}

// ════════════════════════════════════════════════════════════════════════════
// LIFECYCLE
// ════════════════════════════════════════════════════════════════════════════

void installed() {
    log.info "${device.displayName} installed() called. FP300 driver v${version()}"
    sendHealthStatusEvent("unknown")
    initializeVars(true)
    initialize()
}

void configure() {
    log.info "${device.displayName} configure() called"
    unschedule()
    if (logEnable) runIn(1800, "logsOff", [overwrite: true, misfire: "ignore"])  //Enable the debug logging for 30 minutes (i.e. 1800 seconds)
    initializeVars(false)
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
    runIn(5, "fp300BlackMagic")
    runIn(15, "updated")
    logWarn "configure() - If no further logs appear, make sure you have woken the FP300 by pressing the button on it."
}

void initialize() {
    log.info "${device.displayName} initialize() called"
    runIn(5, "refresh")
    state.pirState    = device.currentValue("pirDetection") == "active"  ? 1 : 0
    state.mmwaveState = device.currentValue("roomState")   == "occupied" ? 1 : 0
}

void initializeVars(boolean fullInit = false) {
    if (fullInit) state.clear()
	
    if (state.params != null)state.remove("params")  //remove legacy state.params variable
	
    if (state.rxCounter == null)         state.rxCounter         = 0
    if (state.txCounter == null)         state.txCounter         = 0
    if (state.notPresentCounter == null) state.notPresentCounter = 0

    if (settings?.logEnable  == null) device.updateSetting("logEnable",  true)
    if (settings?.txtEnable  == null) device.updateSetting("txtEnable",  true)

    if (settings?.presenceDetectionMode == null)         device.updateSetting("presenceDetectionMode", [value: "both", type: "enum"])
    if (settings?.absenceDelayTimer == null)             device.updateSetting("absenceDelayTimer", [value: 10, type: "number"])
    if (settings?.pirDetectionInterval == null)          device.updateSetting("pirDetectionInterval", [value: 10, type: "number"])
    if (settings?.aiInterferenceIdentification == null)  device.updateSetting("aiInterferenceIdentification", false)
    if (settings?.aiSensitivityAdaptive == null)         device.updateSetting("aiSensitivityAdaptive", false)
    if (settings?.tempOffset == null)                    device.updateSetting("tempOffset", [value: 0.0, type: "decimal"])
    if (settings?.humidityOffset == null)                device.updateSetting("humidityOffset", [value: 0.0, type: "decimal"])
    if (settings?.motionSensitivity == null)             device.updateSetting("motionSensitivity", [value: "2", type: "enum"])
    if (settings?.tempHumiditySamplingFrequency == null) device.updateSetting("tempHumiditySamplingFrequency", [value: "1", type: "enum"])
    if (settings?.lightSamplingFrequency == null)        device.updateSetting("lightSamplingFrequency", [value: "1", type: "enum"])
    if (settings?.ledDisabledNight == null)              device.updateSetting("ledDisabledNight", false)
    if (settings?.tempHumiditySamplingPeriod == null)    device.updateSetting("tempHumiditySamplingPeriod", [value: 600, type: "number"])
    if (settings?.lightSamplingPeriod == null)           device.updateSetting("lightSamplingPeriod", [value: 30, type: "number"])
    if (settings?.temperatureReportingInterval == null)  device.updateSetting("temperatureReportingInterval", [value: 600, type: "number"])
    if (settings?.temperatureReportingThreshold == null) device.updateSetting("temperatureReportingThreshold", [value: 1.0, type: "decimal"])
    if (settings?.temperatureReportingMode == null)      device.updateSetting("temperatureReportingMode", [value: "1", type: "enum"])
    if (settings?.humidityReportingInterval == null)     device.updateSetting("humidityReportingInterval", [value: 600, type: "number"])
    if (settings?.humidityReportingThreshold == null)    device.updateSetting("humidityReportingThreshold", [value: 5.0, type: "decimal"])
    if (settings?.humidityReportingMode == null)         device.updateSetting("humidityReportingMode", [value: "1", type: "enum"])
    if (settings?.lightReportingInterval == null)        device.updateSetting("lightReportingInterval", [value: 600, type: "number"])
    if (settings?.lightReportingThreshold == null)       device.updateSetting("lightReportingThreshold", [value: 20.0, type: "decimal"])    
    if (settings?.lightReportingMode == null)            device.updateSetting("lightReportingMode", [value: "1", type: "enum"])
//    if (settings?.ledNightTimeSchedule == null)          device.updateSetting("ledNightTimeSchedule", [value: "21:00-09:00", type: "string"])
    
    state.driverVersion = driverVersionAndTimeStamp()
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
    
    // Bind temperature cluster (0x0402)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}", "delay 50"]
    
    // Bind humidity cluster (0x0405)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0405 {${device.zigbeeId}} {}", "delay 50"]
    
    // Bind  illuminance cluster (0x0400)
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}", "delay 50"]
    
    // Bind manufacturer cluster and read initial values
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"]
    
    // Call routine to send the Zigbee commands
    sendZigbeeCommands(cmds)
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
