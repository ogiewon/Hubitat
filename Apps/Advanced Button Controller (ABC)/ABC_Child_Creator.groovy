/*	
 *	Hubitat Import URL: https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/ABC_Child_Creator.groovy
 *
 *
 *
 *	ABC Child Creator for Advanced Button Controller
 *
 *	Authors: SmartThings, modified by Bruce Ravenel, Dale Coffing, Stephan Hackett (@stephack), Dan Ogorchock (@ogiewon)
 *
 *	04/09/26 - Per agreement with Stephan Hackett, ABC is now being maintained by Dan Ogorchock 
 *
 *	04/03/26 - added new 'menu: "Automations"' to the app metadata to properly display this app as an Automation in HE 2.5.x menu
 *
 *	01/09/22 - added support for RMv5 rules. Older versions are considered "legacy". Requires Hub v2.2.9 f/w or higher.
 *           - Thanks to @bertabcd1234 for guidance with the undocumented RMUtils v5 api.
 *
 *	09/03/21 - added new garageDoorControl capability with distinct open/close actions
 *           - original Garage Door section is now Legacy Garage Door
 *
 *	08/12/21 - added optional input to disable log.info statements to reduce Hubitat Log traffic (Dan Ogorchock)
 *
 *	06/17/21 - added support for stopPositionChange() and startPositionChange(direction) to comply with windowShade capability
 *
 *	10/02/20 - split Ramp section in 2. Original is now called Ramp(Auto Stop on Release) and there is also a new option called Ramp (Manual Stop)
 *           - added the ability to unlock locks since this was added as an option in built-in apps.
 *
 *	03/01/20 - added ability to control windowShade devices under "Shades" menu option)
 *           - original shade control (doorControl) moved to "Garage Doors/Legacy Shades" menu option
 *
 *	02/26/20 - Forced initialization on hub restart to avoid delays on "first activation". Thank you @ogiewon for the suggestion.
 *
 *	11/05/19 - Added previousTrack support for speakers
 *
 *	10/06/19 - Added Auto as option under Set Fan Speed
 *
 *	08/14/19 - Send Http Requests (POST or GET - simple form encoded)
 *
 *	05/18/19 - Speech notifications now allow random messages to be sent (Use ; to separate options)
 *			 - cycleFan modified to no longer use numeric setSpeed values as this may be deprecated by HE for future fan devices
 *
 *	04/29/19 - fixed small UI bug handling '0' level values
 *			 - updated adjustFans method
 *
 *	02/19/19 - rules api bug squashed
 *
 *	02/17/19 - updated Button Description for rules to show Rule name instead of Rule number
 * 			 - Button Descriptions will now be surrounded by [] for better visibility
 * 			 - Action details are now stored in a state value to allow for better efficiency
 *
 *	02/10/19 - setColor Level is no longer required (can be left blank)
 *
 *	02/07/19 - fixed Set Color bug (missing level option)
 *
 *	01/14/19 - updated logging output to appropriate type (info vs debug)
 *			 - added input to enable/disable debug logging
 *			 - added url to Raw code at the top of the parent/child apps
 *			   (Thanks for the feedback and suggestions @csteeele)
 *			 - update checking code is now done through json file (Thanks to @Cobra for his guidance)
 *
 *	12/15/18 - updated color scheme to match new HE theme
 *			 - added suppot for Rules API
 *
 *
 *	10/12/18 - adjusted "Set Mode" to comply with mode related updates in firmware 1.1.5
 *
 *
 *	8/01/18 - added Hubitat Safety Monitor Control (created new MODES section for Set Mode and Set HSM)
 * 		added level to setColor()
 *		added new detail parameter "myDetail.mul" (Mode and HSM set to multiple:false)
 *		removed section shallHide for sub inputs .... (section will be visible if primary input has a value...sub value no longer checked)
 *
 *
 *	7/03/18 - code cleanup
 *		Added pictures enhancements and reordered options for better flow
 *		Corrected default child app label (previously defaulted to "ABC Button Mapping" on first save)
 *
 *
 *	7/01/18 - added Released actions for all control sections
 *		Pushed/Held/DoubleTapped/Released hidden from Dimmer Ramp section based on devices capabilities
 *
 *	6/30/18 - adapted fan cycle to be compliant with fanControl capability (removed cycle support for custom driver)
 *		added ability to set specific fan speed
 *		added support for ramping (graceful dimming) - switch/bulb needs changeLevel capability and button device needs releaseableButton capability
 *		
 *
 *	6/02/18 - added ability to cycle custom Hampton Bay Zigbee Fan Controller
 *
 *
 *	4/21/18 - added support for new Sonos Player devices (play/pause, next, previous, mute/unmute, volumeup/down)
 *
 *
 *	3/28/18 - added option to set color and temp
 *		test code for custom commands (not yet working)
 *
 *	2/06/18 - converted code to hubitat format
 * 		removed ability to hide "held options"
 *		removed hwspecifics section as is no longer applicable
 *		adjusted device list to look for "capability.pushableButton"
 *		adjusted buttonDevice subscription (pushed, held, doubleTapped)
 *		adjusted buttonEvent() to swap "name" and "value" as per new rules
 *	2/08/18 - change formatting for Button Config Preview (Blue/Complete color)
 *		Added Double Tap inputs and edited shallHide() getDescription()
 *		added code for showDouble() to only display when controller support DT
 *		removed enableSpec and other Virtual Container Code as this is not supported in Hubitat
 *	2/12/18
 * 		Updated to new detailsMap and modified Button Config/Preview pages
 *		hides secondary values if primary not set. When dispayed they are now "required". 
 *
 *	2/12/18
 *		Switched to parent/child config	
 *		removed button pics and descriptive text (not utilized by hubitat)
 *
 *	10/24/18
 *		added the ability to cycle through Scenes (done using push() command and cycles in alphabetical order only)
 *		minor GUI updates
 */

import hubitat.helper.RMUtils

def version(){"v0.2.260409"}

definition(
    name: "ABC Button Mapping",
    namespace: "ogiewon",
    author: "Dan Ogorchock",
    description: "Assign tasks to your Button Controller Devices",
    category: "My Apps",
    parent: "ogiewon:Advanced Button Controller",
    iconUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png",
    iconX2Url: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png",
    iconX3Url: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png", 
	importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/ABC_Child_Creator.groovy",
    menu: "Automations"
)

preferences {
	page(name: "chooseButton")
	page(name: "configButtonsPage")
	page(name: "timeIntervalInput", title: "Only during a certain time") {
		section {
			input "starting", "time", title: "Starting", required: false
			input "ending", "time", title: "Ending", required: false
		}
	}
}

def chooseButton() {
	state.details=getPrefDetails()
	dynamicPage(name: "chooseButton", install: true, uninstall: true) {
		section(){
				def appHead = "<img src=https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png height=80 width=80> \n${checkForUpdate()}"
				paragraph "<div style='text-align:center'>${appHead}</div>"
		}
		section(getFormat("header", "${getImage("Device", "45")}"+" Step 1: Select Your Button Device")) {
            input "buttonDevice", "capability.pushableButton", title: getFormat("section", "Button Device"), description: "Tap to Select", multiple: false, required: true, submitOnChange: true
		}
        if(buttonDevice){
        	state.buttonType =  buttonDevice.typeName
            if(state.buttonType.contains("Aeon Minimote")) state.buttonType =  "Aeon Minimote"
            if(logEnable) log.debug "Device Type is now set to: "+state.buttonType
            state.buttonCount = manualCount?: buttonDevice.currentValue('numberOfButtons')
            section(getFormat("header", "${getImage("Button", "45")}"+"  Step 2: Configure Your Buttons")) {
            	if(state.buttonCount<1) {
                	paragraph "The selected button device did not report the number of buttons it has. Please specify in the Advanced Config section below."
                }
                else {
                	for(i in 1..state.buttonCount){
                		href "configButtonsPage", title: getFormat("section", "${getImage("Button", "30")}" + " Button ${i}"), state: getDescription(i)!="Tap to configure"? "complete": null, description: getDescription(i), params: [pbutton: i]
                    }
            	}
            }
		}
        section(getFormat("header", "${getImage("Custom", "45")}"+"  Set Custom Name (Optional)")) {
        	label title: "Assign a name:", required: false
            paragraph getFormat("line")
        }
        section("Advanced Config:", hideable: true, hidden: hideOptionsSection()) {
            	input "manualCount", "number", title: "Set/Override # of Buttons?", required: false, description: "Only set if your driver does not report", submitOnChange: true
                input "collapseAll", "bool", title: "Collapse Unconfigured Sections?", defaultValue: true
				input "logEnable", "bool", title: "Enable Debug Logging?", required: false
				input "descTextDisable", "bool", title: "Disable Descriptive Text Logging?", required: false
			}
        section(title: "Only Execute When:", hideable: true, hidden: hideOptionsSection()) {
			def timeLabel = timeIntervalLabel()
			href "timeIntervalInput", title: "Only during a certain time", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null
			input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
					options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
		}
	}
}

def configButtonsPage(params) {
	if (params.pbutton != null) state.currentButton = params.pbutton.toInteger()
	dynamicPage(name: "configButtonsPage", title: "CONFIGURE BUTTON ${state.currentButton}:\n${state.buttonType}", getButtonSections(state.currentButton))
}

def getButtonSections(buttonNumber) {
	return {    	
        def myDetail
        section(getFormat("header", "${getImage("Switches", "45")}"+" SWITCHES")){}
		//state.details=getPrefDetails()
        for(i in 1..34) {//Build 1st 34 Button Config Options
        	myDetail = state.details.find{it.sOrder==i}
        	//
    section(title: myDetail.secLabel, hideable: true, hidden: !(shallHide("${myDetail.id}${buttonNumber}"))) {
				if(showPush(myDetail.desc)) input "${myDetail.id}${buttonNumber}_pushed", myDetail.cap, title: "When Pushed", multiple: myDetail.mul, required: false, submitOnChange: collapseAll, options: myDetail.opt
				if(myDetail.sub && isReq("${myDetail.id}${buttonNumber}_pushed")) input "${myDetail.sub}${buttonNumber}_pushed", myDetail.subType, title: myDetail.sTitle, multiple: false, required: !myDetail.sNotReq, description: myDetail.sDesc, options: myDetail.subOpt
                if(myDetail.sub2 && isReq("${myDetail.id}${buttonNumber}_pushed")) input "${myDetail.sub2}${buttonNumber}_pushed", myDetail.sub2Type, title: myDetail.s2Title, multiple: false, required: !myDetail.s2NotReq, description: myDetail.s2Desc, options: myDetail.sub2Opt
                if(myDetail.sub3 && isReq("${myDetail.id}${buttonNumber}_pushed")) input "${myDetail.sub3}${buttonNumber}_pushed", myDetail.sub3Type, title: myDetail.s3Title, multiple: false, required: !myDetail.s3NotReq, description: myDetail.s3Desc, options: myDetail.sub3Opt
				
        		if(showHeld(myDetail.desc)) input "${myDetail.id}${buttonNumber}_held", myDetail.cap, title: "When Held", multiple: myDetail.mul, required: false, submitOnChange: collapseAll, options: myDetail.opt
                if(myDetail.sub && isReq("${myDetail.id}${buttonNumber}_held")) input "${myDetail.sub}${buttonNumber}_held", myDetail.subType, title: myDetail.sTitle, multiple: false, required: !myDetail.sNotReq, description: myDetail.sDesc, options: myDetail.subOpt
                if(myDetail.sub2 && isReq("${myDetail.id}${buttonNumber}_held")) input "${myDetail.sub2}${buttonNumber}_held", myDetail.sub2Type, title: myDetail.s2Title, multiple: false, required: !myDetail.s2NotReq, description: myDetail.s2Desc, options: myDetail.sub2Opt
                if(myDetail.sub3 && isReq("${myDetail.id}${buttonNumber}_held")) input "${myDetail.sub3}${buttonNumber}_held", myDetail.sub3Type, title: myDetail.s3Title, multiple: false, required: !myDetail.s3NotReq, description: myDetail.s3Desc, options: myDetail.sub3Opt
        		
				if(showDouble(myDetail.desc)) input "${myDetail.id}${buttonNumber}_doubleTapped", myDetail.cap, title: "When Double Tapped", multiple: myDetail.mul, required: false, submitOnChange: collapseAll, options: myDetail.opt
                if(myDetail.sub && isReq("${myDetail.id}${buttonNumber}_doubleTapped")) input "${myDetail.sub}${buttonNumber}_doubleTapped", myDetail.subType, title: myDetail.sTitle, multiple: false, required: !myDetail.sNotReq, description: myDetail.sDesc, options: myDetail.subOpt
                if(myDetail.sub2 && isReq("${myDetail.id}${buttonNumber}_doubleTapped")) input "${myDetail.sub2}${buttonNumber}_doubleTapped", myDetail.sub2Type, title: myDetail.s2Title, multiple: false, required: !myDetail.s2NotReq, description: myDetail.s2Desc, options: myDetail.sub2Opt
                if(myDetail.sub3 && isReq("${myDetail.id}${buttonNumber}_doubleTapped")) input "${myDetail.sub3}${buttonNumber}_doubleTapped", myDetail.sub3Type, title: myDetail.s3Title, multiple: false, required: !myDetail.s3NotReq, description: myDetail.s3Desc, options: myDetail.sub3Opt
		
        		if(showRelease(myDetail.desc)) input "${myDetail.id}${buttonNumber}_released", myDetail.cap, title: "When Released", multiple: myDetail.mul, required: false, submitOnChange: collapseAll, options: myDetail.opt
                if(myDetail.sub && isReq("${myDetail.id}${buttonNumber}_released")) input "${myDetail.sub}${buttonNumber}_released", myDetail.subType, title: myDetail.sTitle, multiple: false, required: !myDetail.sNotReq, description: myDetail.sDesc, options: myDetail.subOpt
                if(myDetail.sub2 && isReq("${myDetail.id}${buttonNumber}_released")) input "${myDetail.sub2}${buttonNumber}_released", myDetail.sub2Type, title: myDetail.s2Title, multiple: false, required: !myDetail.s2NotReq, description: myDetail.s2Desc, options: myDetail.sub2Opt
                if(myDetail.sub3 && isReq("${myDetail.id}${buttonNumber}_released")) input "${myDetail.sub3}${buttonNumber}_released", myDetail.sub3Type, title: myDetail.s3Title, multiple: false, required: !myDetail.s3NotReq, description: myDetail.s3Desc, options: myDetail.sub3Opt
		}
            if(i==3) section("\n"+getFormat("header", "${getImage("Dimmers", "45")}"+" DIMMERS")){}
            if(i==10) section("\n"+getFormat("header", "${getImage("Color", "45")}"+" COLOR LIGHTS")){}
            if(i==12) section("\n"+getFormat("header", "${getImage("Speakers", "45")}"+" SPEAKERS")){}
            if(i==19) section("\n"+getFormat("header", "${getImage("Fans", "45")}"+" FANS")){}
            if(i==22) section("\n"+getFormat("header", "${getImage("Mode", "45")}"+" MODES")){}
			if(i==24) section("\n"+getFormat("header", "${getImage("Rule", "45")}"+" RULE CONTROL")){}
            if(i==26) section("\n"+getFormat("header", "${getImage("Other", "45")}"+" OTHER")){}
        }
		
		section(getFormat("section", "Notifications (SMS):"), hideable:true , hidden: !shallHide("notifications_${buttonNumber}")) {
			input "notifications_${buttonNumber}_pushed", "text", title: "Message To Send When Pushed:", description: "Enter message to send", required: false, submitOnChange: collapseAll
            input "phone_${buttonNumber}_pushed","phone" ,title: "Send Text To:", description: "Enter phone number", required: false, submitOnChange: collapseAll
            if(showHeld()) {
            	paragraph getFormat("line")
				input "notifications_${buttonNumber}_held", "text", title: "Message To Send When Held:", description: "Enter message to send", required: false, submitOnChange: collapseAll
				input "phone_${buttonNumber}_held", "phone", title: "Send Text To:", description: "Enter phone number", required: false, submitOnChange: collapseAll
            }
            if(showDouble()) {
            	paragraph getFormat("line")
				input "notifications_${buttonNumber}_doubleTapped", "text", title: "Message To Send When Double Tapped:", description: "Enter message to send", required: false, submitOnChange: collapseAll
				input "phone_${buttonNumber}_doubleTapped", "phone", title: "Send Text To:", description: "Enter phone number", required: false, submitOnChange: collapseAll
            }
            if(showRelease()) {
            	paragraph getFormat("line")
				input "notifications_${buttonNumber}_released", "text", title: "Message To Send When Released:", description: "Enter message to send", required: false, submitOnChange: collapseAll
				input "phone_${buttonNumber}_released", "phone", title: "Send Text To:", description: "Enter phone number", required: false, submitOnChange: collapseAll
            }
		}
	}
}

def getImage(type, mySize) {
    def loc = "<img src=https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/"
    if(type == "Device") return "${loc}Device.png height=${mySize} width=${mySize}>   "
    if(type == "Button") return "${loc}Button.png height=${mySize} width=${mySize}>   "
    if(type == "Switches") return "${loc}Switches.png height=${mySize} width=${mySize}>   "
    if(type == "Color") return "${loc}Color.png height=${mySize} width=${mySize}>   "
    if(type == "Dimmers") return "${loc}Dimmers.png height=${mySize} width=${mySize}>   "
    if(type == "Speakers") return "${loc}Speakers.png height=${mySize} width=${mySize}>   "
    if(type == "Fans") return "${loc}Fans.png height=${mySize} width=${mySize}>   "
    if(type == "HSM") return "${loc}Mode.png height=${mySize} width=${mySize}>   "
    if(type == "Mode") return "${loc}Mode.png height=${mySize} width=${mySize}>   "
    if(type == "Other") return "${loc}Other.png height=${mySize} width=${mySize}>   "
    if(type == "Custom") return "${loc}Custom.png height=${mySize} width=${mySize}>   "
    if(type == "Locks") return "${loc}Locks.png height=30 width=30>   "
    if(type == "Sirens") return "${loc}Sirens.png height=30 width=30>   "
    if(type == "Scenes") return "${loc}Scenes.png height=30 width=30>   "
    if(type == "Shades") return "${loc}Shades.png height=30 width=30>   "
    if(type == "SMS") return "${loc}SMS.png height=30 width=30>   "
    if(type == "Speech") return "${loc}Audio.png height=30 width=30>   "
	if(type == "Rule") return "${loc}Rule.png height=${mySize} width=${mySize}>   "
}

def getFormat(type, myText=""){
    if(type == "section") return "<div style='color:#78bf35;font-weight: bold'>${myText}</div>"
    if(type == "command") return "<div style='color:red;font-weight: bold'>${myText}</div>"
    if(type == "header") return "<div style='color:#ffffff;background-color:#392F2E;text-align:center'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#78bf35; height: 2px; border: 0;'></hr>"
}

def shallHide(myFeature) {
	if(collapseAll) return (settings["${myFeature}_pushed"]||settings["${myFeature}_held"]||settings["${myFeature}_doubleTapped"]||settings["${myFeature}_released"]||settings["${myFeature}"])
	return true
}

def isReq(myFeature) {
    (settings[myFeature])? true : false
}

def showPush(desc) {
    if(buttonDevice.hasCapability("PushableButton")){ 	//is device pushable?
        if(desc.contains("Ramp(Auto Stop)")){									
            if(buttonDevice.hasCapability("HoldableButton")) return false	//if this is the Ramp section and device is also Holdable, then hide Pushed option
        }
        return true
    }
	return false
}

def showHeld(desc) {
    return buttonDevice.hasCapability("HoldableButton")
}

def showDouble(desc) {
    if(desc && desc.contains("Ramp(Auto Stop)")) return false //remove DoubleTapped option when setting smooth dimming button/devices
    return buttonDevice.hasCapability("DoubleTapableButton")
}

def showRelease(desc) {
    if(desc && desc.contains("Ramp(Auto Stop)")) return false //remove On Release option when setting smooth dimming button/devices
    return buttonDevice.hasCapability("ReleasableButton")
}

def getDescription(dNumber) {
    def descript = ""
    if(!(settings.find{it.key.contains("_${dNumber}_")})) return "Tap to configure"
    if(settings.find{it.key.contains("_${dNumber}_pushed")}) descript = "\nPUSHED:"+getDescDetails(dNumber,"_pushed")+"\n"
    if(settings.find{it.key.contains("_${dNumber}_held")}) descript = descript+"\nHELD:"+getDescDetails(dNumber,"_held")+"\n"
    if(settings.find{it.key.contains("_${dNumber}_doubleTapped")}) descript = descript+"\nTAPx2:"+getDescDetails(dNumber,"_doubleTapped")+"\n"
    if(settings.find{it.key.contains("_${dNumber}_released")}) descript = descript+"\nRELEASED:"+getDescDetails(dNumber,"_released")+"\n"
	return descript
}

def getDescDetails(bNum, type){
	def numType=bNum+type
	def preferenceNames = settings.findAll{it.key.contains("_${numType}")}.sort()		//get all configured settings that: match button# and type, AND are not false
    if(!preferenceNames){
    	return "  **Not Configured** "
    }
    else {
    	def formattedPage =""
    	preferenceNames.each {eachPref->
        	def prefDetail = state.details.find{eachPref.key.contains(it.id)}	//gets decription of action being performed(eg Turn On)
        						
			def prefDevice		//name of device the action is being performed on (eg Bedroom Fan)
			if(prefDetail.sub == "valRule"){
				prefDevice = " : " + getRuleName(eachPref.value)	//extracts rules name (instead if number) for button description
			}
			else if(prefDetail.sub == "valv5Rule"){
				prefDevice = " : " + getRuleName5(eachPref.value)	//extracts rules name (instead if number) for button description
			}
			else {
				prefDevice = " : ${eachPref.value}"
			}
			def thisSub = settings[prefDetail.sub + numType]
			def prefSubValue = thisSub != null? thisSub:"(!Missing!)"
            def sub2Value = settings[prefDetail.sub2 + numType]
            def sub3Value = settings[prefDetail.sub3 + numType]
            def sub2Initial = prefDetail.s2Initial
            def sub3Initial = prefDetail.s3Initial
            if(sub2Initial) prefSubValue += "${sub2Initial}"
            if(sub2Value) prefSubValue += "${sub2Value}"
            if(sub3Initial) prefSubValue += "${sub3Initial}"
            if(sub3Value) prefSubValue += "${sub3Value}"
            if(prefDetail.type=="normal") formattedPage += "\n- ${prefDetail.desc}${prefDevice}"
            if(prefDetail.type=="hasSub") formattedPage += "\n- ${prefDetail.desc}${prefSubValue}${prefDevice}"
            if(prefDetail.type=="bool") formattedPage += "\n- ${prefDetail.desc}"
    	}
		return formattedPage
    }
}

def getRules(){
	rules = RMUtils.getRuleList()
	//converts rules list to easily parsed format and stores in state.rules for easy access
	state.rules=[:] 
	rules.each{rule->
		rule.each{pair->
			state.rules[pair.key]=pair.value 
		}
	}
	////////////////////////////////////////////////////
	return rules
}

def getRules5(){
	rules = RMUtils.getRuleList('5.0')
	//converts rules list to easily parsed format and stores in state.rules for easy access
	state.rules5=[:] 
	rules.each{rule->
		rule.each{pair->
			state.rules5[pair.key]=pair.value 
		}
	}
	////////////////////////////////////////////////////
	return rules
}

def getRuleName(num){	//allows button descriptions for RuleAPI controls to show Rule Name instead of Rule Number
	getRules()
	def holder=[]
	num.each{ruleNum->
		holder << state.rules.find{it.key==ruleNum.toInteger()}.value
	}
	return holder
}

def getRuleName5(num){	//allows button descriptions for RuleAPI controls to show Rule Name instead of Rule Number
    getRules5()
	def holder=[]
	num.each{ruleNum->
		holder << state.rules5.find{it.key==ruleNum.toInteger()}.value
	}
	return holder
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
    if(logEnable) log.debug "INITIALIZED with settings: ${settings}"
    if(logEnable) log.debug app.label
    if(!app.label || app.label == "default")app.updateLabel(defaultLabel())
    subscribe(location, "systemStart", hubRestartHandler)
	subscribe(buttonDevice, "pushed", buttonEvent)
	subscribe(buttonDevice, "held", buttonEvent)
	subscribe(buttonDevice, "doubleTapped", buttonEvent)
    subscribe(buttonDevice, "released", buttonEvent)
    state.lastshadesUp = true
	state.details=getPrefDetails()
}

def defaultLabel() {
	return "${buttonDevice} Mapping"
}

def getPrefDetails(){
	def detailMappings =
    	[[id:'lightOn_', sOrder:1, desc:'Turn On ', comm:turnOn, type:"normal", secLabel: getFormat("section", "Turn On"), cap: "capability.switch", mul: true],
     	 [id:"lightOff_", sOrder:2, desc:'Turn Off', comm:turnOff, type:"normal", secLabel: getFormat("section", "Turn Off"), cap: "capability.switch", mul: true],
         [id:'lights_', sOrder:3, desc:'Toggle On/Off', comm:toggle, type:"normal", secLabel: getFormat("section", "Toggle On/Off"), cap: "capability.switch", mul: true],
         
         [id:"lightDim_", sOrder:4, desc:'Dim to ', comm:turnDim, sub:"valLight", subType:"number", type:"hasSub", secLabel: getFormat("section", "On to Level - Group 1"), cap: "capability.switchLevel", sTitle: "Bright Level", sDesc:"0 to 100%", mul: true],
     	 [id:"lightD2m_", sOrder:5, desc:'Dim to ', comm:turnDim, sub:"valLight2", subType:"number", type:"hasSub", secLabel: getFormat("section", "On to Level - Group 2"), cap: "capability.switchLevel", sTitle: "Bright Level", sDesc:"0 to 100%", mul: true],
         [id:'dimPlus_', sOrder:6, desc:'Brightness +', comm:levelUp, sub:"valDimP", subType:"number", type:"hasSub", secLabel: getFormat("section", "Increase Level By"), cap: "capability.switchLevel", sTitle: "Increase by", sDesc:"0 to 15", mul: true],
     	 [id:'dimMinus_', sOrder:7, desc:'Brightness -', comm:levelDown, sub:"valDimM", subType:"number", type:"hasSub", secLabel: getFormat("section", "Decrease Level By"), cap: "capability.switchLevel", sTitle: "Decrease by", sDesc:"0 to 15", mul: true],
         [id:'lightsDT_', sOrder:8, desc:'Toggle Off/Dim to ', comm:dimToggle, sub:"valDT", subType:"number", type:"hasSub", secLabel: getFormat("section", "Toggle OnToLevel/Off"), cap: "capability.switchLevel", sTitle: "Bright Level", sDesc:"0 to 100%", mul: true],
         [id:'lightsRamp_', sOrder:9, desc:'Ramp(Auto Stop) ', comm:rampUp, sub:"valDir", subType:"enum", subOpt:['up','down'], type:"hasSub", secLabel: getFormat("section", "Ramp Up/Down (Auto Stop on Release)"), cap: "capability.changeLevel", sTitle: "Ramp Direction (Up/Down)", sDesc:"Up or Down", mul: true],
         [id:'lightsRampMan_', sOrder:10, desc:'Ramp(Manual Stop) ', comm:rampMan, sub:"valRman", subType:"enum", subOpt:['up','down','stop'], type:"hasSub", secLabel: getFormat("section", "Ramp Up/Down/Stop (Manual Stop)"), cap: "capability.changeLevel", sTitle: "Ramp Command (Up/Down/Stop)", sDesc:"Up, Down or Stop", mul: true],
         
         [id:'lightColorTemp_', sOrder:11, desc:'Set Light Color Temp to ', comm:colorSetT, sub:"valColorTemp", subType:"number", type:"hasSub", secLabel: getFormat("section", "Set Temperature"), cap: "capability.colorTemperature", sTitle: "Color Temp", sDesc:"2000 to 9000", mul: true],
         [id:'lightColor_', sOrder:12, desc:'Set Light Color H:', comm:colorSet, sub:"valHue", sub2:"valSat", sub3:"valLvl", type:"hasSub", subType:"number", sub2Type:"number", sub3Type:"number", secLabel: getFormat("section", "Set Color"), cap: "capability.colorControl", sTitle: "Hue", s2Title: "Saturation", s3Title: "Lvl", sDesc:"0 to 100", s2Desc:"0 to 100", s3Desc:"0 to 100", s2Initial: ", S:", s3Initial: ", L:", mul: true, s3NotReq:true],
     	          
         [id:"speakerpp_", sOrder:13, desc:'Toggle Play/Pause', comm:speakerplaystate, type:"normal", secLabel: getFormat("section", "Toggle Play/Pause"), cap: "capability.musicPlayer", mul: true],
     	 [id:'speakervu_', sOrder:14, desc:'Volume +', comm:levelUp, sub:"valSpeakU", subType:"number", type:"hasSub", secLabel: getFormat("section", "Increase Volume By"), cap: "capability.musicPlayer", sTitle: "Increase by", sDesc:"0 to 15", mul: true],
     	 [id:"speakervd_", sOrder:15, desc:'Volume -', comm:levelDown, sub:"valSpeakD", subType:"number", type:"hasSub", secLabel: getFormat("section", "Decrease Volume By"), cap: "capability.musicPlayer", sTitle: "Decrease by", sDesc:"0 to 15", mul: true],
         [id:'speakernt_', sOrder:16, desc:'Next Track', comm:speakernexttrack, type:"normal", secLabel: getFormat("section", "Go to Next Track"), cap: "capability.musicPlayer", mul: true],
         [id:'speakerpt_', sOrder:17, desc:'Previous Track', comm:speakerprevioustrack, type:"normal", secLabel: getFormat("section", "Go to Previous Track"), cap: "capability.musicPlayer", mul: true],
         [id:'speakermu_', sOrder:18, desc:'Mute', comm:speakermute, type:"normal", secLabel: getFormat("section", "Speakers Toggle Mute"), cap: "capability.musicPlayer", mul: true],
         [id:"musicPreset_", sOrder:19, desc:'Cycle Preset', comm:cyclePlaylist, type:"normal", secLabel: getFormat("section", "Preset to Cycle"), cap: "device.VirtualContainer", mul: true],         
         
         [id:'fanSet_', sOrder:20, desc:'Set Fan to ', comm:setFan, sub:"valSpeed", subType:"enum", subOpt:['off','low','medium-low','medium','high','auto'], type:"hasSub", secLabel: getFormat("section", "Set Speed"), cap: "capability.fanControl", sTitle: "Set Speed to", sDesc:"L/ML/M/H/A", mul: true],
         [id:"fanCycle_", sOrder:21, desc:'Cycle Fan Speed', comm:cycleFan, type:"normal", secLabel: getFormat("section", "Cycle Speed"), cap: "capability.fanControl", mul: true],         
         [id:"fanAdjust_", sOrder:22,desc:'Adjust', comm:adjustFan, type:"normal", secLabel: getFormat("section", "Cycle Speed (Legacy)"), cap: "capability.switchLevel", mul: true],
         
         [id:"mode_", sOrder:23, desc:'Set Mode', comm:changeMode, type:"normal", secLabel: getFormat("section", "Set Mode"), cap: "mode", mul: false],
     	 [id:"hsm_", sOrder:24, desc:'Set HSM', comm:setHSM, type:"normal", secLabel: getFormat("section", "Set HSM"), cap: "enum", opt:['armAway','armHome','disarm','armRules','disarmRules','disarmAll','armAll','cancelAlerts'], mul: false],

         [id:'rule_', sOrder:25, desc:'Rule To ', comm:ruleExec, sub:"valRule", subType:"enum", subOpt:['Run','Stop','Pause','Resume','Evaluate','Set Boolean True','Set Boolean False'], type:"hasSub", secLabel: getFormat("section", "Legacy Rule and Actions"), cap: "enum", opt: getRules(), sTitle: "Select Action Type", sDesc:"Choose Action", mul: true],
         [id:'rule5_', sOrder:26, desc:'Rule v5 To ', comm:ruleExec5, sub:"valv5Rule", subType:"enum", subOpt:['Run','Stop','Pause','Resume','Evaluate','Set Boolean True','Set Boolean False'], type:"hasSub", secLabel: getFormat("section", "Rule (v5) and Actions"), cap: "enum", opt: getRules5(), sTitle: "Select Action Type", sDesc:"Choose Action", mul: true],
		 
         [id:"locks_", sOrder:27, desc:'Set Lock: ', comm:setUnlock, sub:"valLock", subType:"enum", subOpt:['lock','unlock'], type:"hasSub", secLabel: getFormat("section", "Locks"), cap: "capability.lock", sTitle: "Select Action Type", sDesc:"Choose Action", mul: true],
		 [id:'cycleScenes_', sOrder:28, desc:'Cycle', comm:cycle, type:"normal", secLabel: getFormat("section", "Scenes (Cycle)"), cap: "device.SceneActivator", mul: true, isCycle: true],
         [id:"shadeAdjust_", sOrder:29, desc:'Adjust', comm:adjustShade, type:"normal", secLabel: getFormat("section", "Legacy Garage Doors/Legacy Shades (Up/Down/Stop)"), cap: "capability.doorControl", mul: true],
         [id:'newGarageAdjust_', sOrder:30, desc:'Adjust: ', comm:adjustNewGarage, sub:"valGarageAction", type:"hasSub", subType:"enum", subOpt:['Open','Close'], secLabel: getFormat("section", "Garage Doors (Open/Close)"), cap: "capability.garageDoorControl", sTitle: "Action", sDesc:"Choose Action", mul: true],
         [id:'newShadeAdjust_', sOrder:31, desc:'Adjust: ', comm:adjustNewShade, sub:"valShadeAction", sub2:"valSposition", sub3:"valSDirection", type:"hasSub", subType:"enum", sub2Type:"number", sub3Type:"enum", subOpt:['Open','Close', 'Set Position','Start Position Change','Stop Position Change'], sub3Opt:['Open','Close'], secLabel: getFormat("section", "Shades (Open/Close/Position/Start/Stop)"), cap: "capability.windowShade", sTitle: "Action", s2Title:"Position", s3Title:"Direction", sDesc:"", s2Desc:"(0 to 100) *applies to Set Position Only", s3Desc:"*applies to Start Position Change Only", s2Initial: ", Pos:", s3Initial: ", Dir:", mul: true, s2NotReq: true, s3NotReq: true],
         
         [id:'sirens_', sOrder:32, desc:'Toggle', comm:toggle, type:"normal", secLabel: getFormat("section", "Sirens (Toggle)"), cap: "capability.alarm", mul: true],
         [id:'httpRequest_', sOrder:33, desc:'Send: ', comm:hRequest, sub:"reqUrl", subType:"text", type:"hasSub", secLabel: getFormat("section", "Send Http Request"), cap: "enum", opt:['POST', 'GET'], sTitle:"HTTP URL", sDesc:"Enter complete url to send", mul: false],
         [id:"speechDevice_", sOrder:34, desc:'Send Msg To', comm:speechHandle, type:"normal", secLabel: getFormat("section", "Notifications (Speech):"), sub:"speechTxt", cap: "capability.speechSynthesis", subType:"text", sTitle: "Message To Speak:", sDesc:"Enter message to speak (Random messages: Use ; to separate choices)", mul: true],///set type to normal instead of sub so message text is not displayed
		 
		 [id:"notifications_", desc:'Send Push Notification', comm:messageHandle, sub:"valNotify", type:"bool"],
     	 [id:"phone_", desc:'Send SMS', comm:smsHandle, sub:"notifications_", type:"normal"],
        ]
    return detailMappings
}

def checkForUpdate(){
	def params = [uri: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/child.json",
				   	contentType: "application/json"]
       	try {
			httpGet(params) { response ->
				def results = response.data
				def appStatus
				if(version() == results.currVersion) {
					appStatus = "<small>Child ${version()}</small><br>${results.noUpdateImg}"
				}
				else {
					appStatus = "<small>Child ${version()}</small><br>${results.updateImg}${results.changeLog}"
					log.warn "ABC Child App does not appear to be the latest version: Please update."
				}
				return appStatus
			}
		} 
        catch (e) {
        	log.error "Error:  $e"
    	}
}

def hubRestartHandler(evt) {
    if(logEnable) log.debug "ABC [$app.label]Initialized: Hub Restart"
}

def buttonEvent(evt) {
	if(allOk) {
    	def buttonNumber = evt.value
		def pressType = evt.name
		if(logEnable) log.debug "$buttonDevice: Button $buttonNumber was $pressType"
        
        //detects if button is used for graceful hold to dim function then calls stopLevelChange()
        if(pressType == "released" && settings["lightsRamp_${buttonNumber}_pushed"]){
        	rampEnd(settings["lightsRamp_${buttonNumber}_pushed"])
        }
        if(pressType == "released" && settings["lightsRamp_${buttonNumber}_held"]){
        	rampEnd(settings["lightsRamp_${buttonNumber}_held"])
        }        
        
    	def preferenceNames = settings.findAll{it.key.contains("_${buttonNumber}_${pressType}")}
    	preferenceNames.each{eachPref->
        	def prefDetail = state.details?.find{eachPref.key.contains(it.id)}		//returns the detail map of id,desc,comm,sub
        	def PrefSubValue = settings["${prefDetail.sub}${buttonNumber}_${pressType}"] //value of subsetting (eg 100)
            def PrefSub2Value = settings["${prefDetail.sub2}${buttonNumber}_${pressType}"] //value of subsetting (eg 100)
            def PrefSub3Value = settings["${prefDetail.sub3}${buttonNumber}_${pressType}"]	//value of subsetting (eg 100)
            if(prefDetail.sub3) "$prefDetail.comm"(eachPref.value,PrefSubValue, PrefSub2Value, PrefSub3Value)
            	else if(prefDetail.sub2) "$prefDetail.comm"(eachPref.value,PrefSubValue, PrefSub2Value)
        		else if(prefDetail.sub) "$prefDetail.comm"(eachPref.value,PrefSubValue)
                else if(prefDetail.isCycle) "$prefDetail.comm"(eachPref.value, "${eachPref.key}")
        	else "$prefDetail.comm"(eachPref.value)
    	}
	}
}

def speechHandle(devices, msg){
    if (!descTextDisable) log.info "Sending ${msg} to ${devices}"
	if(msg.contains(";")) {
		def myPool = msg.split(";")
		def poolSize = myPool.size()
		def randomItem = Math.abs(new Random().nextInt() % poolSize)
		devices.speak(myPool[randomItem-1])
	}
	else{
		devices.speak(msg)
	}
}

def turnOn(devices) {
	if (!descTextDisable) log.info "Turning On: $devices"
	devices.on()
}

def turnOff(devices) {
	if (!descTextDisable) log.info "Turning Off: $devices"
	devices.off()
}

def turnDim(devices, level) {
	if (!descTextDisable) log.info "Dimming (to $level): $devices"
	devices.setLevel(level)
}

def colorSet(devices,hueVal,satVal,lvlVal) {
    if (!descTextDisable) log.info "Setting Color (to H:$hueVal, S:$satVal, L:$lvlVal): $devices"
    def myColor = [:]
    myColor.hue = hueVal.toInteger()
    myColor.saturation = satVal.toInteger()
    if(lvlVal) myColor.level = lvlVal.toInteger()
    devices.setColor(myColor)//([hue:hueVal,saturation:satVal,level:50]) 
}

def colorSetT(devices, temp) {
    if (!descTextDisable) log.info "Setting Color Temp (to $temp): $devices"
    devices.setColorTemperature(temp)    
}

def adjustFan(device) {
	if (!descTextDisable) log.info "Adjusting: $device"
	def currentLevel = device.currentLevel[0]
	if(device.currentSwitch[0] == 'off') device.setLevel(15)
	else if (currentLevel < 34) device.setLevel(50)
  	else if (currentLevel < 67) device.setLevel(90)
	else device.off()
}

def adjustShade(device) {
	if (!descTextDisable) log.info "Shades: $device = ${device.currentMotor} state.lastUP = $state.lastshadesUp"
	if(device.currentMotor in ["up","down"]) {
    	state.lastshadesUp = device.currentMotor == "up"
    	device.stop()
    } else {
    	state.lastshadesUp ? device.down() : device.up()
        state.lastshadesUp = !state.lastshadesUp
    }
}

def adjustNewGarage(devices, action){
    if (!descTextDisable) log.info "Sending ${action} to: $devices"
    if(action=='Open'){devices.open()}
    if(action=='Close'){devices.close()}
}

def adjustNewShade(devices, action, position, direction){
    if (!descTextDisable) log.info "Sending ${action} to: $devices"
    if(action=='Open'){devices.open()}
    if(action=='Close'){devices.close()}
    if(action=='Set Position'){devices.setPosition(position)}
    if(action=='Start Position Change'){devices.startPositionChange(direction)}
    if(action=='Stop Position Change'){devices.stopPositionChange()}
}

def setFan(devices, speed){
	if (!descTextDisable) log.info "Setting Speed to $speed: $devices"
    devices.setSpeed(speed)
}

def speakerplaystate(device) {
	if (!descTextDisable) log.info "Toggling Play/Pause: $device"
	device.currentStatus.contains('playing')? device.pause() : device.play()
}
   
def speakernexttrack(device) {
	if (!descTextDisable) log.info "Next Track Sent to: $device"
	device.nextTrack()
}   

def speakerprevioustrack(device) {
	if (!descTextDisable) log.info "Previous Track Sent to: $device"
	device.previousTrack()
} 

def speakermute(device) {
	if (!descTextDisable) log.info "Toggling Mute/Unmute: $device"
	device.currentMute.contains('unmuted')? device.mute() : device.unmute()
} 

def levelUp(device, inclevel) {
	if (!descTextDisable) log.info "Incrementing Level (by +$inclevel): $device"
	def currentVol = device.currentLevel[0]//device.currentValue('level')[0]	//currentlevel return a list...[0] is first item in list ie volume level
    def newVol = currentVol + inclevel
  	device.setLevel(newVol)
    if(logEnable) log.debug "Level increased by $inclevel to $newVol"
}

def levelDown(device, declevel) {
	if (!descTextDisable) log.info "Decrementing Level (by -$declevel): $device"
	def currentVol = device.currentLevel[0]//device.currentValue('level')[0]
    def newVol = currentVol - declevel
  	device.setLevel(newVol)
    if(logEnable) log.debug "Level decreased by $declevel to $newVol"
}

def rampUp(devices, dir){
    if (!descTextDisable) log.info "Ramping ${dir}: $devices"
    devices.startLevelChange(dir)
}

def rampEnd(device){
	if (!descTextDisable) log.info "Ending Ramp: $device"
    device.stopLevelChange()    
}

def rampMan(devices, rCommand){
    if(rCommand=='stop'){
        if (!descTextDisable) log.info "Ending Ramp: $devices"
        devices.stopLevelChange() 
    }
    else{
        if (!descTextDisable) log.info "Ramping ${rCommand}: $devices"
        devices.startLevelChange(rCommand)
    }
}

def setUnlock(devices,action) {
    if(action=='unlock'){
       	if (!descTextDisable) log.info "Unlocking: $devices"
	    devices.unlock() 
    }
    if(action=='lock'){
       	if (!descTextDisable) log.info "Locking: $devices"
	    devices.lock() 
    }
}

def toggle(devices) {
	if (!descTextDisable) log.info "Toggling: $devices"
	if (devices*.currentValue('switch').contains('on')) {
		devices.off()
	}
	else if (devices*.currentValue('switch').contains('off')) {
		devices.on()
	}
	else if (devices*.currentValue('alarm').contains('off')) {
        devices.siren()
    }
	else {
		devices.on()
	}
}

def dimToggle(devices, dimLevel) {
	if (!descTextDisable) log.info "Toggling On/Off | Dimming (to $dimLevel): $devices"
	if (devices*.currentValue('switch').contains('on')) devices.off()
	else devices.setLevel(dimLevel)
}

def runRout(rout){
	if (!descTextDisable) log.info "Running: $rout"
	location.helloHome.execute(rout)
}

def ruleExec(rules, action){
	if (!descTextDisable) log.info "Performing ${action} Action on Rules: ${rules}"
	def ruleAction
	if(action == "Run") ruleAction =  "runRuleAct"
	if(action == "Stop") ruleAction =  "stopRuleAct"
	if(action == "Pause") ruleAction =  "pauseRule"
	if(action == "Resume") ruleAction =  "resumeRule"
	if(action == "Evaluate") ruleAction =  "runRule"
	if(action == "Set Boolean True") ruleAction =  "setRuleBooleanTrue"
	if(action == "Set Boolean False") ruleAction =  "setRuleBooleanFalse"
	RMUtils.sendAction(rules, ruleAction, app.label)
}

def ruleExec5(rules, action){
	if (!descTextDisable) log.info "Performing ${action} Action on Rules: ${rules}"
	def ruleAction
	if(action == "Run") ruleAction =  "runRuleAct"
	if(action == "Stop") ruleAction =  "stopRuleAct"
	if(action == "Pause") ruleAction =  "pauseRule"
	if(action == "Resume") ruleAction =  "resumeRule"
	if(action == "Evaluate") ruleAction =  "runRule"
	if(action == "Set Boolean True") ruleAction =  "setRuleBooleanTrue"
	if(action == "Set Boolean False") ruleAction =  "setRuleBooleanFalse"
	log.info "RM5 test running"
	RMUtils.sendAction(rules, ruleAction, app.label,'5.0')
}

def messageHandle(msg, inApp) {
	if(inApp==true) {
    	if (!descTextDisable) log.info "Push notification sent"
    	sendPush(msg)
	}
}

def smsHandle(phone, msg){
    if (!descTextDisable) log.info "SMS sent"
    sendSms(phone, msg ?:"No custom text entered on: $app.label")
}

def setHSM(hsmMode) {
    sendLocationEvent(name: "hsmSetArm", value: hsmMode)
}

def changeMode(mode) {
	if (!descTextDisable) log.info "Changing Mode to: $mode"
	if (location.mode != mode && location.modes?.find { it.name == mode}) setLocationMode(mode)
}

def cycleFan(devices) { //all fans will sync speeds with first fan in the list
    if (!descTextDisable) log.info "Cycling: $devices"
    def mySpeed = devices[0].currentSpeed
    if(mySpeed == "off") devices.setSpeed("low") 
    if(mySpeed == "low") devices.setSpeed("medium-low") 
    if(mySpeed == "medium-low") devices.setSpeed("medium") 
    if(mySpeed == "medium") devices.setSpeed("high")
    if(mySpeed == "high") devices.setSpeed("off") 
}

def cycle(devices, devIndex) {
    if (!descTextDisable) log.info "Cycling: $devices"
    def mySize = devices.size() -1
    if(!state."${devIndex}" || state."${devIndex}" > mySize) state."${devIndex}" = 0
    devices[state."${devIndex}"].push()
    state."${devIndex}" ++
}

def cyclePlaylist(devices){
    devices.cycle()
}

def hRequest(reqType, myUrl){
    def params = [
        uri: myUrl,
		contentType: 'application/x-www-form-urlencoded'
    ]
	
    if(logEnable) log.debug "${reqType} - ${params}"
	if(reqType == "POST") asynchttpPost('myPostResponse', params, [type: reqType])
    if(reqType == "GET") asynchttpGet('myPostResponse', params, [type: reqType])
  	  	
}

def myPostResponse(response,data){
	if(response.status != 200) {
		log.error "HTTP ${data["type"]} Request returned error ${response.status}. Check your URL!"
	}
    else {
        if(logEnable) log.debug "HTTP ${data["type"]} Request sent successfully"
    }
}

// execution filter methods
private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	if(logEnable) log.debug "modeOk = $result"
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	if(logEnable) log.debug "daysOk = $result"
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	if(logEnable) log.debug "timeOk = $result"
	result
}

private hhmm(time, fmt = "h:mm a") {
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private hideOptionsSection() {
	(starting || ending || days || modes || manualCount) ? false : true
}

private timeIntervalLabel() {
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}
