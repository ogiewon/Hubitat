/**
 *  Visonic MCT-370 SMA Zigbee Contact Sensor
 *
 *  Hubitat Elevation Device Driver
 *
 *  Author:   Dan Ogorchock
 *  Date:     2026-02-28
 *  Version:  1.0.0
 *
 *  Device:   Visonic MCT-370 / MCT-370 SMA  (ZigBee Door/Window Magnetic Contact Sensor)
 *
 *  Clusters used:
 *    0000  Basic
 *    0001  Power Configuration  (battery voltage / percentage)
 *    0003  Identify
 *    0402  Temperature Measurement
 *    0500  IAS Zone             (open / closed / tamper)
 *    0020  Poll Control
 *    0B05  Diagnostics          (LQI / RSSI)
 *
 *  Copyright 2025 – Licensed under the Apache License, Version 2.0
 *
 *  History:
 *  2026-02-28    Dan Ogorchock    Original release v1.0.0
 *
 *
 */

import hubitat.zigbee.clusters.iaszone.ZoneStatus
import hubitat.zigbee.zcl.DataType

metadata {
    definition( name: "Visonic MCT-370 Zigbee Contact Sensor", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "" ) {
        capability "Battery"
        capability "Configuration"
        capability "Contact Sensor"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Tamper Alert"
        capability "Voltage Measurement"

        // Raw IAS zone status bitmap for diagnostics (stored as decimal string)
        //attribute "iasZoneStatus", "string"

        // ── Fingerprints ──────────────────────────────────────────────
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0500,0B05", outClusters:"0019", model:"MCT-370 SMA", manufacturer:"Visonic"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0500,0B05", outClusters:"0019", model:"MCT-370",     manufacturer:"Visonic"
    }

    preferences {
        input name: "tempOffset",
              type: "decimal",
              title: "Temperature Offset (°${getTemperatureScale()})",
              description: "Adjust reported temperature by this amount (e.g. -1.5 or +2.0)",
              defaultValue: 0,
              range: "-10..10"

        input name: "tempReportMinutes",
              type: "number",
              title: "Temperature Report Interval (minutes)",
              description: "How often to request a temperature report (1-60 min, default 10)",
              defaultValue: 10,
              range: "1..60"

        input name: "batteryReportHours",
              type: "number",
              title: "Battery Report Interval (hours)",
              description: "How often to request a battery report (1-24 hrs, default 12)",
              defaultValue: 12,
              range: "1..24"

        input name: "invertContact",
              type: "bool",
              title: "Invert Contact Logic",
              description: "Enable if open/closed is reported backwards",
              defaultValue: false

        input name: "logEnable",
              type: "bool",
              title: "Enable Debug Logging",
              defaultValue: true

        input name: "txtEnable",
              type: "bool",
              title: "Enable Descriptive Text Logging",
              defaultValue: true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  L I F E C Y C L E
// ─────────────────────────────────────────────────────────────────────────────

def installed() {
    logDebug "installed()"
    runIn(1800, logsOff)
}

def updated() {
    logDebug "updated()"
    unschedule()
    if (logEnable) {runIn(1800, logsOff)}
    configure()
}

// ─────────────────────────────────────────────────────────────────────────────
//  C O N F I G U R A T I O N
// ─────────────────────────────────────────────────────────────────────────────

def configure() {
    logDebug "configure()"

    int tempMinSec = (settings.tempReportMinutes  ?: 10)  * 60
    int battMaxSec = (settings.batteryReportHours ?: 12)  * 3600
    int tempMaxSec = tempMinSec * 10 >= 14400 ? tempMinSec * 10 : 14400

    List<String> cmds = []

    // 1.  IAS Zone – write hub IEEE address as IAS CIE (attribute 0x0010).
    //     Hubitat's zigbeeEui is big-endian; the attribute requires little-endian.
/*
    String euiBE = device.hub.zigbeeEui
    byte[] euiBytes = euiBE.decodeHex()
    List euiList = euiBytes as List
    euiList = euiList.reverse()
    String euiLE = euiList.collect { String.format("%02X", it & 0xFF) }.join()
    cmds += zigbee.writeAttribute(0x0500, 0x0010, DataType.IEEE_ADDRESS, euiLE)
*/
    cmds += zigbee.writeAttribute(0x0500, 0x0010, DataType.IEEE_ADDRESS, getHubEuiLittleEndian())
    
    // 2.  Battery – configure reporting for both voltage (0x0020) and percentage (0x0021).
    //     parseBattery() prefers 0x0021; 0x0020 is a fallback only.
    cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8,  30, battMaxSec, 1)
    cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8,  30, battMaxSec, 1)

    // 3.  Temperature – 0.01 °C units, delta 10 = 0.10 °C
    cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, tempMinSec, tempMaxSec, 10)  //Seems to always report on 1°C changes. Device may just ignore this setting.

    // 4.  IAS Zone status change reporting
//    cmds += zigbee.configureReporting(0x0500, 0x0002, DataType.BITMAP16, 0, 3600, 1)  // ChatGPT says this is not necessary - test to find out - Looks like it is not necessary

    cmds += zigbee.enrollResponse()
    
    sendZigbeeCommands(cmds)
    
    runIn(5, refresh)
}

private String getHubEuiLittleEndian() {
    return (device.hub.zigbeeEui.decodeHex().toList().reverse() as byte[]).encodeHex().toString().toUpperCase()
}

// ─────────────────────────────────────────────────────────────────────────────
//  R E F R E S H
// ─────────────────────────────────────────────────────────────────────────────

def refresh() {                          // From initial testing, not sure that this device supports ad-hoc attribute reads
    logDebug "refresh()"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0402, 0x0000)   // Temperature
    cmds += zigbee.readAttribute(0x0001, 0x0020)   // Battery voltage (fallback)
    cmds += zigbee.readAttribute(0x0500, 0x0002)   // IAS ZoneStatus
    cmds += zigbee.readAttribute(0x0001, 0x0021)   // Battery percentage
    cmds += zigbee.enrollResponse()                // Added by DGO 2026-02-26
    
    sendZigbeeCommands(cmds)
}

// ─────────────────────────────────────────────────────────────────────────────
//  M E S S A G E   P A R S I N G
// ─────────────────────────────────────────────────────────────────────────────

def parse(String description) {
    logDebug "parse() – description: ${description}"

    // ── IAS Zone enroll request ───────────────────────────────────────────────
    if (description?.startsWith("enroll request")) {
        logDebug "IAS Zone enroll request – sending enroll response"
        sendZigbeeCommands(enrollResponse())
        return
    }

    // ── Zone status change notification (Hubitat text form) ───────────────────
    // e.g. "zone status 0x0021 -- extended status 0x00 ..."
    if (description?.startsWith("zone status")) {
        ZoneStatus zs = zigbee.parseZoneStatus(description)
        if (zs != null) {
            handleZoneStatus(zs, description)
        } else {
            logDebug "Could not parse zone status text: ${description}"
        }
        return
    }

    // ── All other messages → parse into a descriptor map ─────────────────────
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (!descMap) return
    logDebug "descMap: ${descMap}"

    // Ignore non-HA-profile (ZDO, etc.) and Basic cluster (0x0000) catchalls —
    // these are normal Zigbee stack traffic with no actionable data for this driver.
    if (descMap.profileId != null && descMap.profileId != "0104") {
        logDebug "Ignoring non-HA message (profileId:${descMap.profileId} clusterId:${descMap.clusterId})"
        return
    }
    if (descMap.clusterInt == 0) {
        logDebug "Ignoring Basic cluster (0x0000) catchall"
        return
    }

    switch (descMap.clusterInt) {
        case 0x0001:
            parseBattery(descMap)
            break
        case 0x0402:
            parseTemperature(descMap)
            break
        case 0x0500:
            parseIasZoneCluster(descMap)
            break
        default:
            logDebug "Unhandled cluster: ${descMap.clusterId} (clusterInt:${descMap.clusterInt})"
    }
}

// ── IAS Zone – cluster 0x0500 attribute/catchall ──────────────────────────────

private void parseIasZoneCluster(Map descMap) {
    if (descMap.attrInt == 0x0002 && descMap.value) {
        // Attribute report – parse the raw bitmap directly; no ZoneStatus object needed
        int raw = Integer.parseInt(descMap.value, 16)
        handleZoneStatusRaw(raw)
    } else {
        // Other 0x0500 commands (enroll response acks, etc.) – nothing to act on
        logDebug "Ignoring IAS Zone cluster non-attribute message: command=${descMap.command}"
    }
}

// ── IAS Zone – handle ZoneStatus object (from text-form notifications) ────────

private void handleZoneStatus(ZoneStatus zs, String description) {
    // Extract the raw bitmap from the description text rather than from the ZoneStatus
    // object, because Hubitat's ZoneStatus class exposes neither .value nor .zoneStatus.
    // Text format: "zone status 0x00XX -- extended status ..."
    int raw = 0
    def m = (description =~ /zone status 0x([0-9A-Fa-f]{4})/)
    if (m) {
        raw = Integer.parseInt(m[0][1], 16)
    }
    handleZoneStatusRaw(raw, zs)
}
/* ChatGPT's Suggestion for simpler routine
private void handleZoneStatus(ZoneStatus zs, String description) {
    int raw = zs?.zoneStatus ?: 0
    handleZoneStatusRaw(raw)
}
*/

// ── IAS Zone – common handler using raw bitmap ────────────────────────────────

private void handleZoneStatusRaw(int raw, ZoneStatus zs = null) {
    // Use ZoneStatus helper methods when available, otherwise decode the bitmap directly.
    // Bit 0 – Alarm1 → contact open
    // Bit 2 – Tamper
    boolean alarm1  = zs ? zs.isAlarm1Set()  : ((raw & 0x0001) != 0)
    boolean tampered = zs ? zs.isTamperSet() : ((raw & 0x0004) != 0)

    boolean contactOpen = settings.invertContact ? !alarm1 : alarm1
    sendContactEvent(contactOpen ? "open" : "closed")
    sendTamperEvent(tampered ? "detected" : "clear")
    //sendEvent(name: "iasZoneStatus", value: raw.toString(), displayed: false)
}

// ── Battery ────────────────────────────────────────────────────────────────────

private void parseBattery(Map descMap) {
    if (!descMap.value) return

    // Treat 0xFF / 0xFFFF as "unsupported attribute" — ignore.
    // Use equalsIgnoreCase because Hubitat may return hex in lower or upper case.
    String val = descMap.value
    if (val.equalsIgnoreCase("FF") || val.equalsIgnoreCase("FFFF")) {
        logDebug "Battery attribute ${descMap.attrInt == 0x0021 ? '0x0021' : '0x0020'} returned unsupported (${val}) – ignoring"
        return
    }

    int raw = Integer.parseInt(val, 16)

    switch (descMap.attrInt) {
        case 0x0021:                          // From initial testing, I do not believe this device supports Battery Percentage Reporting
            // Battery percentage remaining.
            // Per ZCL spec the value is in units of 0.5%, so divide by 2.
            // e.g. raw 200 → 100%, raw 100 → 50%
            int pct = Math.round(raw / 2.0)
            pct = Math.min(100, Math.max(0, pct))
            logDebug "case 0x0021: battery pct = ${pct}"
            sendBatteryEvent(pct)
            break

        case 0x0020:                          // From initial testing, it appears the device only reports battery voltage
            // Battery voltage in 100 mV units (e.g. 0x1E = 30 = 3.0 V).
            BigDecimal volts = raw / 10.0
            sendVoltageEvent(volts)
            // Only fall back to voltage if percentage (0x0021) has never been received.  // The problem with this is that it only works one time!!!
            //if (device.currentValue("battery") == null) {
                // Li primary cell: ~3.0 V full → ~2.0 V empty                
                int pct = Math.round(((volts - 2.0) / (3.0 - 2.0)) * 100)   // Claude's recommendation
                pct = Math.min(100, Math.max(0, pct))                       // Claude's recommendation
                logDebug "case 0x0020: battery pct = ${pct}"
                sendBatteryEvent(pct)
            //}
            break
    }
}

// ── Temperature ────────────────────────────────────────────────────────────────

private void parseTemperature(Map descMap) {
    if (descMap.attrInt != 0x0000) return
    // 0x8000 = invalid/unreported per ZCL spec
    if (!descMap.value || descMap.value.equalsIgnoreCase("8000")) return

    int raw = Integer.parseInt(descMap.value, 16)
    // Two's-complement for sub-zero values
    if (raw > 0x7FFF) raw = raw - 0x10000

    BigDecimal celsius = raw / 100.0
    celsius += ((settings.tempOffset as BigDecimal) ?: 0.0)

    BigDecimal displayTemp = (getTemperatureScale() == "F")
        ? celsiusToFahrenheit(celsius)
        : celsius
    displayTemp = displayTemp.setScale(1, BigDecimal.ROUND_HALF_UP)

    sendEvent(
        name:            "temperature",
        value:           displayTemp,
        unit:            "°${getTemperatureScale()}",
        descriptionText: "${device.displayName} temperature is ${displayTemp}°${getTemperatureScale()}"
    )
    if (txtEnable) log.info "${device.displayName} temperature is ${displayTemp}°${getTemperatureScale()}"
}

// ─────────────────────────────────────────────────────────────────────────────
//  I A S   E N R O L L   R E S P O N S E
// ─────────────────────────────────────────────────────────────────────────────

def enrollResponse() {
    logDebug "enrollResponse()"
    // Zone Enroll Response command: enroll success (0x00), zone ID 0x01
    return [
        "he raw 0x${device.deviceNetworkId} 1 1 0x0500 {01 23 00 00 01}", "delay 200"
    ]
}

// ─────────────────────────────────────────────────────────────────────────────
//  H E L P E R S
// ─────────────────────────────────────────────────────────────────────────────

private void sendContactEvent(String value) {
    String descText = "${device.displayName} contact is ${value}"
    sendEvent(name: "contact", value: value, descriptionText: descText)
    if (txtEnable) log.info descText
}

private void sendTamperEvent(String value) {
    String descText = "${device.displayName} tamper is ${value}"
    sendEvent(name: "tamper", value: value, descriptionText: descText)
    if (txtEnable && value == "detected") log.warn descText
}

private void sendBatteryEvent(int pct) {
    String descText = "${device.displayName} battery is ${pct}%"
    sendEvent(name: "battery", value: pct, unit: "%", descriptionText: descText)
    if (txtEnable) log.info descText
}

private void sendVoltageEvent(BigDecimal volts) {
    String descText = "${device.displayName} voltage is ${volts}V"
    sendEvent(name: "voltage", value: volts, unit: "V", descriptionText: descText)
    if (txtEnable) log.info descText
}

private BigDecimal celsiusToFahrenheit(BigDecimal c) {
    return (c * 9 / 5) + 32
}

private void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) log.debug "${device.displayName} – sendZigbeeCommands: ${cmds}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName} – ${msg}"
}

def logsOff() {
    log.warn "${device.displayName} – debug logging disabled automatically after 30 minutes"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
