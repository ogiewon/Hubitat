/*
 *	Hubitat Import URL: https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/Advanced_Button_Controller.groovy
 *
 *
 *	Advanced Button Controller (Parent/Child Version)
 *
 *	Original Author: Stephan Hackett
 * 
 *	04/09/26 - Per agreement with Stephan Hackett, ABC is now being maintained by Dan Ogorchock 
 * 
 *	04/03/26 - added new 'menu: "Automations"' to the app metadata to properly display this app as an Automation in HE 2.5.x menu
 *	
 *	01/14/19 - added url to Raw code at the top of the parent/child apps
 *			 - adjusted logging
 *			 - update checking code is now done through json file (Thanks to @Cobra for his guidance)
 *
 *	07/03/18 - added pictures and Update check
 * 
 */
def version(){"v0.2.260409"}

definition(
    name: "Advanced Button Controller",
    namespace: "ogiewon",
    singleInstance: true,
    author: "Dan Ogorchock",
    description: "Configure devices with buttons like the Aeon Labs Minimote and Lutron Pico Remotes.",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png",
    iconX2Url: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png",
    iconX3Url: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png",
	importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/Advanced_Button_Controller.groovy",
    menu: "Automations"
)

preferences {
	page(name: "mainPage")
    page(name: "aboutPage", nextPage: "mainPage")
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if(!state.abcInstalled) {
            section("Hit Done to install ABC App!") {
        	}
        }
        else {
    		def childApps = getAllChildApps()
        	def childVer = "Initial Setup - Version Unknown"
        	if(childApps.size() > 0) {
        		childVer = childApps.first().version()
        	}
			section(){
				def appHead = "<img src=https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png height=80 width=80> \n${checkForUpdate()}"
				paragraph "<div style='text-align:center'>${appHead}</div>"
			}
        	section("Create a new button device mapping.") {
            	app(name: "childApps", appName: "ABC Button Mapping", namespace: "stephack", title: "<img src=https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/New.png height=50 width=50>      New Button Device Mapping", multiple: true)
        	}
			section("") {
				href (name: "aboutPage", title: "User's Guide", 
       				description: "",
       				page: "aboutPage"
				)		
   			}
        }
    }
}

def installed() {
    state.abcInstalled = true
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
}

def checkForUpdate(){
	def params = [uri: "https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/parent.json",
				   	contentType: "application/json"]
       	try {
			httpGet(params) { response ->
				def results = response.data
				def appStatus
				if(version() == results.currVersion){
					appStatus = "<small>Parent ${version()}</small><br>${results.noUpdateImg}"
				}
				else {
					appStatus = "<small>Parent ${version()}</small><br>${results.updateImg}${results.changeLog}"
					log.warn "ABC Parent App does not appear to be the latest version: Please update."
				}
				return appStatus
			}
		} 
        catch (e) {
        	log.error "Error:  $e"
    	}
}

def aboutPage() {
	dynamicPage(name: "aboutPage", title: none){
        textHelp()
	}
}

private def textHelp() {
	def text =
	section("<img src=https://raw.githubusercontent.com/ogiewon/Hubitat/refs/heads/master/Apps/Advanced%20Button%20Controller%20(ABC)/resources/images/abc2.png height=36 width=36> User's Guide - Advanced Button Controller") {
    	paragraph "This smartapp allows you to use a device with buttons including, but not limited to:\n\n  Aeon Labs Minimotes\n"+
    	"  HomeSeer HS-WD100+ switches**\n  HomeSeer HS-WS100+ switches\n  Lutron Picos***\n\n"+
		"It is a heavily modified version of @dalec's 'Button Controller Plus' which is in turn"+
        " a version of @bravenel's 'Button Controller+'."
   	}
	section("Some of the included changes are:"){
        paragraph "A complete revamp of the configuration flow. You can now tell at a glance, what has been configured for each button."+
        "The button configuration page has been collapsed by default for easier navigation."
        paragraph "The original apps were hardcoded to allow configuring 4 or 6 button devices."+
        " This app will automatically detect the number of buttons on your device or allow you to manually"+
        " specify (only needed if device does not report on its own)."
		paragraph "Allows you to give your buton device full speaker control including: Play/Pause, NextTrack, Mute, VolumeUp/Down."+
    	"(***Standard Pico remotes can be converted to Audio Picos)\n\nThe additional control options have been highlighted below."
	}
	section("Available Control Options are:"){
        paragraph "	Switches - Turn On \n"+
        "	Switches - Turn Off \n"+
        "	Switches - Toggle \n\n"+
            
        "	Dimmers - Set Level (Group 1) \n"+
        "	Dimmers - Set Level (Group 2) \n"+
        "	Dimmers - Inc Level \n"+
        "	Dimmers - Dec Level \n"+
        "	Dimmers - Toggle on to Level \n"+
        "	Dimmers - Ramp Up/Down (Smooth Dimming) \n\n"+
            
        "	Color Lights - Set Temperature \n"+
        "	Color Lights - Set Color \n\n"+
        
        "	Speaker - Toggle Play/Pause \n"+
        "	Speaker - Increment Volume \n"+
        "	Speaker - Decrement Volume \n"+
        "	Speaker - Next Track \n"+
        "	Speaker - Mute/Unmute \n"+
        "	Speaker - Cycle Preset \n\n"+
            
         "	Fans - Set Speed \n"+
        "	Fans - Cycle Speed \n"+    
        "	Fans - Legacy Cycle (Low, Medium, High, Off) \n\n"+  
			
		"	Modes - Set Mode \n"+
		"	Modes - Set HSM \n\n"+	
			
		"	Rules - Run, Stop, Pause, Resume, Evaluate, Set Boolean \n\n"+	
            
        "	Locks - Unlock Only \n"+
		"	Scenes - Cycle Scenes \n"+	
        "	Shades - Up, Down, or Stop \n"+
        "	Sirens - Toggle \n"+
        "	Speech Notifications \n"+
        "	SMS Notifications"
	}
	section ("** Quirk for HS-WD100+ on Button 5 & 6:"){
        paragraph "Because a dimmer switch already uses Press&Hold to manually set the dimming level"+
        " please be aware of this operational behavior. If you only want to manually change"+
        " the dim level to the lights that are wired to the switch, you will automatically"+
        " trigger the 5/6 button event as well. And the same is true in reverse. If you"+ 
        " only want to trigger a 5/6 button event action with Press&Hold, you will be manually"+
        " changing the dim level of the switch simultaneously as well.\n"+
        "This quirk doesn't exist of course with the HS-HS100+ since it is not a dimmer."
	}
	section("*** Lutron Pico:"){
        paragraph "There are 2 types of Pico configurations in HE:\n 1. The Standard Picos - with pushed events and held events (followed by released events).\n"+
    	" 2. The Fast Picos - with pushed events followed by released events (no held events)."
	}
}
