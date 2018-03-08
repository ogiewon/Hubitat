/**
 *  AD2SmartThings Home Alarm Device Type
 *  Adds your Honeywell/Ademco home alarm to your SmartThings physical graph
 *  Honeywell/Ademco alarms are usually installed by ADT and other companies.  Check your alarm panel to verify
 *  This SmartThings Device Type Code Works With Arduino Connected To An AD2Pi Wired To A Honeywell/Ademco Home Alarm
 *
 *	By Stan Dotson (stan@dotson.info) 
 *
 *	Credits:
 *  Lots of good ideas from @craig whose code can be found at https://gist.github.com/e5b30109fdaec805d474.git
 *  Also relied on architecture enabled by github contributor vassilisv 
 *  Also thanks to Sean Matthews for contributing technical approach to setting AD2Pi address
 *  Date: 2014-08-14
 *  
 *  Zones
 *  This device type supports up to 36 zones.  All the coding is in place
 *  To adjust the number of zones, simply trim the number of zones listed in the <details> argument list.
 *  No other code needs to be modified!  
 *  
 */
 
 // for the UI

preferences {
	
    // The Configuration Command preferences allows input of a configuration command to be sent to AD2Pi.  
    // For example, to change the address to 31 the command would be "ADDRESS=31"
    // After entering the command in setup, you MUST press the "Config" tile to send the configuration command to the AD2Pi
    // Note: this will write to the eeprom of the AD2* so caution should be used to not excessively do this task or it would eventually damage the EEPROM. 
    // This should be preformed only during system setup and configuration!
    // To prevent excessive use, the configCommand value can be reset to null after sending to device
    // pressing the "Config" tile and sending null will harmlessly request the alarm panel to report out its Configurtion into the message tile.
    
    input("configCommand", "text", title: "AD2Pi Configuration Command", description: "for example ADDRESS=31", defaultValue: '', required: false, displayDuringSetup: true)
	input("securityCode", "number", title: "Enter your 4 digit homeowner security code", description: "enter 4 digit code", defaultValue:"", required: true, displayDuringSetup: true)
    input("panicKey","enum", title: "Select Panic Key Configured By Your Installer", description: "(A:1&*) or (B:*&#) or (C:3&#)", options: ["A","B","C"], required: true, displayDuringSetup: true)
}


metadata {
	definition (name: "AD2SmartThings v2.0.1", namespace: "ogiewon", author: "stan@dotson.info") 
    {

		capability "Switch"  // <on> will arm alarm in "stay" mode; same as armStay
        capability "Lock"  // <lock> will arm alarm in "away" mode; same as armAway
		capability "Alarm" // enables <both> or <siren> to  immediately alarm the system
        
        command "disarm"
        command "armStay"
        command "armAway"
        command "chime"
        command "config"
        command "siren"
        command "pressPanicOnce"
        command "pressPanicTwice"
        
        attribute "system", "string"
        attribute "msg", "string"

	}
}

/*
	// Simulator metadata
simulator {

}


// UI tile definitions
tiles 
{
		
 	standardTile("main", "device.system", width: 2, height: 2, canChangeIcon: true, canChangeBackground: true)
    {
		state "disarmed", label:"Disarmed", action:"disarm", icon:"st.Home.home2", backgroundColor:"#ffffff"
		state "armedAway", label:"Armed Away", action:"disarm", icon:"st.Home.home3", backgroundColor:"#587498"
		state "armedStay", label:"Armed Stay", action:"disarm", icon:"st.Home.home4", backgroundColor:"#587498"
        state "alarm", label:"Alarm!", action:"disarm", icon:"st.Home.home3", backgroundColor:"#E86850"
        state "armingAway", label:"Arming Away", action:"disarm", icon:"st.Home.home3", backgroundColor:"#FFD800"
		state "armingStay", label:"Arming Stay", action:"disarm", icon:"st.Home.home4", backgroundColor:"#FFD800"
	}
 	standardTile("stay", "device.system", width: 1, height: 1, decoration: "flat")
    {
        state "disarmed", label: "Stay", action: "armStay", icon:"st.Home.home4", nextState: "armingStay"
        state "armingStay", label:"Arming Stay", action: "disarm", icon:"st.Home.home4"
        state "armedStay", label:"Armed Stay", action: "disarm", icon:"st.Home.home4"
  	}
        
  	standardTile("away", "device.system", width: 1, height: 1, decoration: "flat")
    {
       	state "disarmed", label: "Away", action: "armAway", icon:"st.Home.home3", nextState: "armingAway"
        state "armingAway", label:"Arming Away", action: "disarm", icon:"st.Home.home3" 
       	state "armedAway", label:"Armed Away", action: "disarm", icon:"st.Home.home3"   
  	}
        
 	standardTile("disarm", "device.system", width: 1, height: 1, decoration: "flat")
    {
       	state "disarmed", label:'Disarm', action: "disarm", icon:"st.Home.home2"   
 	}
    
    standardTile("panic", "device.panic", width: 1, height: 1, decoration: "flat")
    {
       	state "disarmed", label: "PANIC", action: "pressPanicOnce", icon:"st.alarm.beep.beep", nextState: "pressedOnce"
        state "pressedOnce", label: "Press Twice More", action: "pressPanicTwice", icon:"st.alarm.beep.beep", nextState: "pressedTwice"
        state "pressedTwice", label: "Press Once More", action: "siren", icon:"st.alarm.beep.beep"
        state "alarm", label:"Panic Alarm", action:"disarm", icon:"st.alarm.beep.beep", backgroundColor:"#E86850" 
  	}
    
    standardTile("chime", "device.chime", width: 1, height: 1, canChangeIcon: true, canChangeBackground: true, inactiveLabel: false, decoration: "flat")
    {
       	state "chimeOn", label: 'Chime On',   action: "chime", icon: "st.custom.sonos.unmuted", backgroundColor: "#ffffff", nextState: "sendingChime"
       	state "chimeOff", label: 'Chime Off', action: "chime", icon: "st.custom.sonos.muted", backgroundColor: "#ffffff", nextState: "sendingChime"
       	state "sendingChime", label: 'Sending', action: "chime",icon: "st.custom.sonos.unmuted", backgroundColor: "#ffffff"
  	}

	standardTile("configAD2Pi", "device.config", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true,  decoration:"flat")
    {
    	state "default", label: 'AD2Pi Config', action: "config", icon: "st.secondary.refresh-icon", backgroundColor: "#ffffff"
    }
        
   	valueTile("msg", "device.msg", width: 3, height:1, inactiveLabel: false, decoration: "flat") 
    {
			state "default", label:'${currentValue}'
	}
 
 
 // tiles to report activity in a given zone.  Feel free to customize by changing the label in label: 'custom name' 
 
   	standardTile("zoneOne", "device.zoneOne", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone One', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone One', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
	standardTile("zoneTwo", "device.zoneTwo", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Two', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
        state "active", label: 'Zone Two', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
   	}
	standardTile("zoneThree", "device.zoneThree", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Three', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Three', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
 	standardTile("zoneFour", "device.zoneFour", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Four', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Four', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
   	standardTile("zoneFive", "device.zoneFive", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Five', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Five', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
  	standardTile("zoneSix", "device.zoneSix", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Six', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Six', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}                      
    standardTile("zoneTen", "device.zoneTen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Ten', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Ten', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
   	standardTile("zoneEleven", "device.zoneEleven", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Eleven', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Eleven', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
	standardTile("zoneTwelve", "device.zoneTwelve", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twelve', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
        state "active", label: 'Zone Twelve', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
   	}
	standardTile("zoneThirteen", "device.zoneThirteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirteen', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
 	standardTile("zoneFourteen", "device.zoneFourteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Fourteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Fourteen', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
   	standardTile("zoneFifteen", "device.zoneFifteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Fifteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Fifteen', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
  	standardTile("zoneSixteen", "device.zoneSixteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Sixteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Sixteen', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}                      
  	standardTile("zoneSeventeen", "device.zoneSeventeen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Seventeen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Seven', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneEighteen", "device.zoneEighteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Eighteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Eighteen', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneNineteen", "device.zoneNineteen", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Nineteen', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Nineteen', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
    standardTile("zoneTwenty", "device.zoneTwenty", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twenty', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twenty', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
   	standardTile("zoneTwentyone", "device.zoneTwentyone", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentyone', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentyone', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
	standardTile("zoneTwentytwo", "device.zoneTwentytwo", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentytwo', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
        state "active", label: 'Zone Twentytwo', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
   	}
	standardTile("zoneTwentythree", "device.Twentythree", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentythree', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentythree', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
 	standardTile("zoneTwentyfour", "device.zoneTwentyfour", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentyfour', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentyfour', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
   	standardTile("zoneTwentyfive", "device.zoneTwentyfive", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentyfive', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentyfive', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
  	standardTile("zoneTwentysix", "device.zoneTwentysix", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentysix', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentysix', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}                      
  	standardTile("zoneTwentyseven", "device.zoneTwentyseven", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentyseven', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentyseven', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneTwentyeight", "device.zoneTwentyeight", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentyeight', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentyeight', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneTwentynine", "device.zoneTwentynine", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Twentynine', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Twentynine', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
    standardTile("zoneThirty", "device.zoneThirty", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirty', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirty', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}   
   	standardTile("zoneThirtyone", "device.zoneThirtyone", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtyone', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtyone', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
	standardTile("zoneThirtytwo", "device.zoneThirtytwo", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtytwo', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
        state "active", label: 'Zone Thirtytwo', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
   	}
	standardTile("zoneThirtythree", "device.Thirtythree", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtythree', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtythree', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
 	standardTile("zoneThirtyfour", "device.zoneThirtyfour", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtyfour', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtyfour', icon: "st.security.alarm.alarm", backgroundColor: "#ffa81e"
	}
   	standardTile("zoneThirtyfive", "device.zoneThirtyfive", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtyfive', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtyfive', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}
  	standardTile("zoneThirtysix", "device.zoneThirtysix", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtysix', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtysix', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}                      
  	standardTile("zoneThirtyseven", "device.zoneThirtyseven", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtyseven', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtyseven', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneThirtyeight", "device.zoneThirtyeight", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtyeight', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtyeight', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}  
  	standardTile("zoneThirtynine", "device.zoneThirtynine", width: 1, height:1, inactiveLabel: false, canChangeIcon: true, canChangeBackground: true) 
    {
		state "inactive", label: 'Zone Thirtynine', icon: "st.security.alarm.clear", backgroundColor: "#ffffff"
		state "active", label: 'Zone Thirtynine', icon: "st.security.alarm.alarm", backgroundColor: "#53a7c0"
	}


//*****************************************************************************************************************************************
//To customize your device type to the number of zones in your system, simply trim the argument for the <details> command
//to equal the number of zones. 
//For example, for 1 zone systems:
//  	details(["main", "disarm","chime","stay", "away", "panic", "msg", "zoneOne", "configAD2Pi"])
//For example, for 4 zones:
//  	details(["main", "disarm","chime","stay", "away", "panic", "msg", "zoneOne","zoneTwo", "zoneThree","zoneFour", "configAD2Pi"])
//The maximum number of zones supported by this device type is 36 zones:
// 		details(["main", "disarm","chime","stay", "away", "panic", "msg", "zoneOne","zoneTwo", "zoneThree","zoneFour","zoneFive","zoneSix","zoneTen","zoneEleven","zoneTwelve","zoneThirteen","zoneFourteen","zoneFifteen","zoneSixteen","zoneSeventeen","zoneEighteen","zoneNineteen","zoneTwenty","zoneTwentyone","zoneTwentytwo","zoneTwentythree","zoneTwentyfour","zoneTwentyfive","zoneTwentysix","zoneTwentyseven","zoneTwentyeight","zoneTwentynine","zoneThirty","zoneThirtyone","zoneThirtytwo","zoneThirtythree","zoneThirtyfour","zoneThirtyfive","zoneThirtysix","zoneThirtyseven","zoneThirtyeight","zoneThirtynine", "configAD2Pi"])
//
//
//Note: Zones 7,8,9, 95,96 are typically reserved for Panic, Duress, Tamper, Panic and Panic, respectively.  No tile is provided for them
//*****************************************************************************************************************************************

	main(["main"])
	details(["main", "disarm","chime","stay", "away", "panic", "msg", "zoneOne","zoneTwo", "zoneThree","zoneFour","zoneFive","zoneSix", "zoneTen","zoneEleven","zoneTwelve","zoneThirteen","zoneFourteen","zoneFifteen","zoneSixteen","zoneSeventeen","zoneEighteen","zoneNineteen","zoneTwenty","zoneTwentyone","zoneTwentytwo","zoneTwentythree","zoneTwentyfour","zoneTwentyfive","zoneTwentysix","zoneTwentyseven","zoneTwentyeight","zoneTwentynine","zoneThirty","zoneThirtyone","zoneThirtytwo","zoneThirtythree","zoneThirtyfour","zoneThirtyfive","zoneThirtysix","zoneThirtyseven","zoneThirtyeight","zoneThirtynine", "configAD2Pi"])

}
*/

def fromHexString(String hex) {
    StringBuilder str = new StringBuilder();
    for (int i = 0; i < hex.length(); i+=2) {
        str.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
    }
    return str.toString();
}

def parseThingShield(String description) {
    def resultMap = zigbee.parseDescriptionAsMap(description)
    if (resultMap.attrId) {
    	return fromHexString(resultMap.attrId.substring(0,2)) + fromHexString(resultMap.encoding) + fromHexString(resultMap.value)
    } 
    else {
        return description
    }
}

def sendThingShield(String message) {
    def encodedString = DataType.pack(message, DataType.STRING_CHAR).substring(2)
	return "he raw 0x${device.deviceNetworkId} 1 0x${device.endpointId} 0x0 {00000a0a" + encodedString + "}"
}

// Parse incoming device messages to generate events
def parse(String description) 
{
	
  	def result = []
    
//	log.debug "Parsing: ${description}"
    
	def msg = parseThingShield(description)
	//def msg = zigbee.parse(description)?.text
    log.info "Alarm Message: ${msg} "
    
    if (msg.contains("ping") || msg.equals("")) 
    {
    }
    
    else 
    {
		result << createEvent(name: "msg", value: msg)  //display message to message tile
    	
        if (msg.contains("...."))  {
			result << createEvent(name: "msg", value: "Having Trouble Sending")  

    	}
        
        if (msg.contains("DISARMED"))
    	{
        	result << createEvent(name: "system", value: "disarmed", displayed: true, isStateChange: true, isPhysical: true)
            	result << createEvent(name: "zoneOne", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwo", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThree", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneFour", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneFive", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneSix", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneSeven", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneEight", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneNine", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneEleven", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwelve", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneFourteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneFifteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneSixteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneSeventeen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneEighteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneNineteen", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwenty", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentyone", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentytwo", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentythree", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentyfour", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentyfive", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentysix", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentyseven", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentyeight", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneTwentynine", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirty", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtyone", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtytwo", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtythree", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtyfour", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtyfive", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtysix", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtyseven", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtyeight", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
        	result << createEvent(name: "zoneThirtynine", value: "inactive", displayed: true, isStateChange: true, isPhysical: true)
    	}   
 		
        if (msg.contains("CHIME"))  {
        	result << createEvent(name: "chime", value: "chimeOn", displayed: true, isStateChange: true, isPhysical: true)
    	}
   		else  {
       		result << createEvent(name: "chime", value: "chimeOff", displayed: true, isStateChange: true, isPhysical: true)
    	}
    
    	
        if (msg.contains("ALARM"))
    	{
    		result << createEvent(name: "system", value: "alarm", displayed: true, isStateChange: true, isPhysical: true)
    	}
    	
        if (msg.contains("ARMED"))
    	{
        	if (msg.contains("STAY"))
    		{
        		if (msg.contains("You may exit now"))
    			{
            		result << createEvent(name: "system", value: "armingStay", displayed: true)
        		}	
        		else
            	{
            		result << createEvent(name: "system", value: "armedStay", displayed: true, isStateChange: true, isPhysical: true)
        		}
        	}
        	else if (msg.contains("AWAY"))
        	{
        		if (msg.contains("You may exit now"))
    			{
            		result << createEvent(name: "system", value: "armingAway", displayed: true, isStateChange: true, isPhysical: true)
        		}
        		else
            	{
            		result << createEvent(name: "system", value: "armedAway", displayed: true, isStateChange: true, isPhysical: true)
				}
        	}
    	}
    	
        if (msg.contains("FAULT"))
    	{
        	if (msg.contains("01"))
        	{
        		log.info "Home Alarm Sensor Fault Zone 01"
        		result << createEvent(name: "zoneOne", value: "active", displayed: true, isStateChange: true, isPhysical: true)    
        	}
        	else if (msg.contains("02"))
        	{
        		log.info "Home Alarm Sensor Fault 02"
        		result << createEvent(name: "zoneTwo", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("03"))
        	{
        		log.info "Home Alarm Sensor Fault 03"
        		result << createEvent(name: "zoneThree", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("04"))
        	{
        		log.info "Home Alarm Sensor Fault 04"
        		result << createEvent(name: "zoneFour", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("05"))
        	{
        		log.info "Home Alarm Sensor Fault 05"
        		result << createEvent(name: "zoneFive", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("06"))
        	{
        		log.info "Home Alarm Sensor Fault 06"
        		result << createEvent(name: "zoneSix", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("07"))
        	{
        		log.info "Home Alarm Sensor Fault 07"
        		result << createEvent(name: "zoneSeven", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("08"))
        	{
        		log.info "Home Alarm Sensor Fault 08"
        		result << createEvent(name: "zoneEight", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("09"))
        	{
        		log.info "Home Alarm Sensor Fault 09"
        		result << createEvent(name: "zoneNine", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("10"))
        	{
        		log.info "Home Alarm Sensor Fault 10"
        		result << createEvent(name: "zoneTen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("11"))
        	{
        		log.info "Home Alarm Sensor Fault Zone 11"
        		result << createEvent(name: "zoneEleven", value: "active", displayed: true, isStateChange: true, isPhysical: true)    
        	}
        	else if (msg.contains("12"))
        	{
        		log.info "Home Alarm Sensor Fault 12"
        		result << createEvent(name: "zoneTwelve", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("13"))
        	{
        		log.info "Home Alarm Sensor Fault 13"
        		result << createEvent(name: "zoneThirteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("14"))
        	{
        		log.info "Home Alarm Sensor Fault 14"
        		result << createEvent(name: "zoneFourteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("15"))
        	{
        		log.info "Home Alarm Sensor Fault 15"
        		result << createEvent(name: "zoneFifteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("16"))
        	{
        		log.info "Home Alarm Sensor Fault 16"
        		result << createEvent(name: "zoneSixteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("17"))
        	{
        		log.info "Home Alarm Sensor Fault 17"
        		result << createEvent(name: "zoneSeventeen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("18"))
        	{
        		log.info "Home Alarm Sensor Fault 18"
        		result << createEvent(name: "zoneEighteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("19"))
        	{
        		log.info "Home Alarm Sensor Fault 19"
        		result << createEvent(name: "zoneNineteen", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("20"))
        	{
        		log.info "Home Alarm Sensor Fault 20"
        		result << createEvent(name: "zoneTwenty", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("21"))
        	{
        		log.info "Home Alarm Sensor Fault Zone 21"
        		result << createEvent(name: "zoneTwentyone", value: "active", displayed: true, isStateChange: true, isPhysical: true)    
        	}
        	else if (msg.contains("22"))
        	{
        		log.info "Home Alarm Sensor Fault 22"
        		result << createEvent(name: "zoneTwentytwo", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("23"))
        	{
        		log.info "Home Alarm Sensor Fault 23"
        		result << createEvent(name: "zoneTwentythree", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("24"))
        	{
        		log.info "Home Alarm Sensor Fault 24"
        		result << createEvent(name: "zoneTwentyfour", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("25"))
        	{
        		log.info "Home Alarm Sensor Fault 25"
        		result << createEvent(name: "zoneTwentyfive", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("26"))
        	{
        		log.info "Home Alarm Sensor Fault 26"
        		result << createEvent(name: "zoneTwentysix", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("27"))
        	{
        		log.info "Home Alarm Sensor Fault 27"
        		result << createEvent(name: "zoneTwentyseven", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("28"))
        	{
        		log.info "Home Alarm Sensor Fault 28"
        		result << createEvent(name: "zoneTwentyeight", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("29"))
        	{
        		log.info "Home Alarm Sensor Fault 29"
        		result << createEvent(name: "zoneTwentynine", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("30"))
        	{
        		log.info "Home Alarm Sensor Fault 30"
        		result << createEvent(name: "zoneThirty", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("31"))
        	{
        		log.info "Home Alarm Sensor Fault Zone 31"
        		result << createEvent(name: "zoneThirtyone", value: "active", displayed: true, isStateChange: true, isPhysical: true)    
        	}
        	else if (msg.contains("32"))
        	{
        		log.info "Home Alarm Sensor Fault 32"
        		result << createEvent(name: "zoneThirtytwo", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("33"))
        	{
        		log.info "Home Alarm Sensor Fault 33"
        		result << createEvent(name: "zoneThirtythree", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("34"))
        	{
        		log.info "Home Alarm Sensor Fault 34"
        		result << createEvent(name: "zoneThirtyfour", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("35"))
        	{
        		log.info "Home Alarm Sensor Fault 35"
        		result << createEvent(name: "zoneThirtyfive", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("36"))
        	{
        		log.info "Home Alarm Sensor Fault 36"
        		result << createEvent(name: "zoneThirtysix", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("37"))
        	{
        		log.info "Home Alarm Sensor Fault 37"
        		result << createEvent(name: "zoneThirtyseven", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}	
        	else if (msg.contains("38"))
        	{
        		log.info "Home Alarm Sensor Fault 38"
        		result << createEvent(name: "zoneThirtyeight", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
        	else if (msg.contains("39"))
        	{
        		log.info "Home Alarm Sensor Fault 39"
        		result << createEvent(name: "zoneThirtynine", value: "active", displayed: true, isStateChange: true, isPhysical: true)
        	}
    	}
		log.debug "Parse returned ${result?.descriptionText}"
		return result
	}
}

// Commands sent to the device
def on() //use to turn on alarm while home
{  
	armStay()
}
def off() 
{
	disarm()
}

def lock()//use to turn on alarm in away mode
{
	armAway()
}

def unlock()
{
	disarm()
}

def armAway()
{
    sendThingShield("[CODE]"+ securityCode + "2")
	//zigbee.smartShield(text: "[CODE]"+ securityCode + "2").format()    
}
def disarm() 
{
	sendEvent(name: "panic", value: "disarmed", displayed: true, isStateChange: true, isPhysical: true)
    sendThingShield("[CODE]"+ securityCode + "1***")
    //zigbee.smartShield(text: "[CODE]"+ securityCode + "1***").format()
}

def armStay() 
{
    sendThingShield("[CODE]" + securityCode + "3")
	//zigbee.smartShield(text: "[CODE]" + securityCode + "3").format()
}


def siren()
{
    log.debug "sent panic"
    sendThingShield("[FUNC]"+ panicKey)
    //zigbee.smartShield(text: "[FUNC]"+ panicKey).format()
}

def chime() 
{
	sendThingShield("[CODE]" + securityCode + "9")
	//zigbee.smartShield(text: "[CODE]" + securityCode + "9").format()
}

def config()
	// pressing the "Config" tile on the AD2Pi will normally request the alarm panel to report out its Configurtion into the message tile.
    // If a Configuration Command was provided as input into the Preferences, this method will send the command down to the Arduino
    // which will write to the eeprom of the AD2Pi 
{
    log.debug "sending AD2Pi Config Command: ${configCommand}"
    sendThingShield("[CONF]${configCommand}")
    //zigbee.smartShield(text: "[CONF]${configCommand}").format()
//  TODO: reset the configCommand value to null
}

def pressPanicOnce() 
{
  sendEvent(name: "panic", value: "pressedOnce", displayed: true, isStateChange: true, isPhysical: true)
  log.debug "pressed panic key once"
}


def pressPanicTwice() 
{
  sendEvent(name: "panic", value: "pressedTwice", displayed: true, isStateChange: true, isPhysical: true)
  log.debug "pressed panic key twice"
}





        	
  


