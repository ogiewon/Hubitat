# Hubitat Logitech Harmony Hub Driver 

It is used to communicate via webSockets to a Logitech Harmony Hub and it creates child devices for each Harmony Activity.  Instant status updates are included as well!  No polling necessary to keep Hubitat updated when a user changes the Activity via a Harmony Remote Control or the Harmony Mobile Phone App.

- v0.1.20181225 - Initial beta release
- v0.1.20181227 - Fixed Hub Restart Issue
- v0.1.20190104 - Faster Activity Switch Response Times
- v0.1.20190107 - Live Logging Tweak
- v0.1.20190220 - Fixed routine to obtain the remoteId due to firmware 4.15.250 changes by Logitech - Thanks @corerootedxb!
- v0.1.20190609 - Added importURL to definition
- v0.1.20190714 - Added Volume and Channel controls (for activities that support these features)- Thanks @aaron for the help!
- v0.1.20190715 - Added setLevel and setVolume commands to enable limited Dashboard support
- v0.1.20190723 - Added Actuator Capability to allow device to be used in RM's 'Custom Actions' 
- v0.1.20191231 - Improved volume control logic, fixed small bug preventing volume updates if number of repeats was 0 
- v0.1.20200114 - Added Switch Capability and Default Activity user preference.  If the Parent switch is turned on, the default activity is turned on.  If the Parent switch is turned off, the current Activity is turned off.
- v0.1.20200301 - Added Left, Right, Up, Down and OK commands along with custom command option - Requires the device ID - @rebecca (Rebecca Ellenby)
- v0.1.20200918 - Added Home Control Buttons for Harmony remotes that support these buttons - Thanks @abuttino!
**Instructions For Use**

NOTE: You must be running Hubitat Elevation firmware version v2.0.3.114 or newer! 

**Create Hubitat Driver**
- Open up the "logitech-harmony-hub-parent.groovy" driver from this repository.  Make sure you hit the "RAW" button, then select/highlight all of the source code, and COPY everything (Ctrl-C on Windows, or right click->Copy). 
- In your Hubitat Elevation Hub's admin web page, select the "Drivers Code" section and then click the "+ New Driver" button in the top right corner.  This will open a editor window for manipulating source code.
- Click in the editor window.  Then PASTE all of the code you copied in the first step.
- Click the SAVE button in the editor window.
- Repeat the above setps to install the **required** HubDuino "Child Switch" Driver.  This is avilable at https://raw.githubusercontent.com/DanielOgorchock/ST_Anything/master/HubDuino/Drivers/child-switch.groovy

**Create the Logitech Harmony Hub Hubitat Device**
- In your Hubitat Elevation Hub's web page, select the "Devices" section, and then click the "+ Add Virtual Device" button in the top right corner.
- In the window that appears, please fill in the "Device Name", "Device Label", and "Device Network Id" fields.  Make sure the Device Network Id field is UNIQUE!  For example:
  - "Device Name" = "Family Room"
  - "Device Label" = {optional}
  - "Device Network Id" = FamilyRoomHarmonyHub  (Note:  make sure this is unique! Update: Hubitat now defaults to a unique value.)
- In the "Type" field, scroll to the bottom of the list and select "Logitech Harmony Hub Parent"
- Click Save
- In the next window that appears, fill in the "Device IP Address" with values appropriate to your application.  For example:
  - "Device IP Address" = 192.168.1.137   (this is the IP Address of my Logitich Harmony Hub device)
- Click Save
- Look at Live Logs (or Past Logs if you didn't have a tab open when you clicked SAVE)
- You should see all of the Child Devices created for your Logitech Harmony Hub
- You can now add these devices to the Hubitat Dashboard, use them in Rules, etc...
- After the initial connection to the HArmony Hub is verified, Select a "Default Activity" that you can use via the Parent's Switch Capability and click SAVE.
- Clicking "Refresh" in the parent device will simply cause a synchronization between your Harmony Hub and your Hubitat Hub's Chile Activity Switches.  This should not be necessary very often as this driver supports INSTANT STATUS UPDATES! :) 

**Very basic VolumeUP, VolumeDown, and Mute Support**
- If the current Activity supports volume control, then you can use the Parent's volumeUp, volumeDown, and mute commands
- You can also use the setVolume() and setLevel() commands, to increase or decrease the volume BUT NOT ACTUALLY SET A SPECIFIC VOLUME PERCENTAGE.  The Harmony Hub can only increase or decrease the volume relative to whatever the real device's volume currently is.  The Harmony Hub does no know what that percentage is, and therefore neither can Hubitat.
- mute() and unmute() both do the exact same thing, simply toggle the status of mute on the real audio device.  again, there is no way for Hubitat to know the accurate state of the audio device's current mute status.
- Hubitat Dashboard Support - you can now assign the Harmony Hub Parent device a Volume Tile in the Dashboard.  This tile will always revert back to 50%.  If you slide it UP, a volumeUp() call will be made to increase the volume.  If you slide it DOWN, a volumeDown() call will be made.  This was the only solution I could figure out to control volume from the Dashboard.  If anyone has a better idea, I am all ears. 

**Home Control Button Support**
- If your remote control has the home control buttons on it, you may be able to utilize these buttons and Hubitat Pushable Buttons. To do so, you must already have these buttons mapped to devices on another platform, such as SmartThings using virtual devices.  Doing so will allow these buttons to transmit webSocket data to the Hubitat hub which can be used to create button pushed events.  In order to use this feature, you must determine the Harmony Device ID for each button by watching the Live Logs (with debug logging enabled in the parent device) to find the Harmony device IDs when each button on the remote is pushed.  Then, copy and paste this ID into the parent device's user preference for each button respectively.  Doing so cause the Parent Device to create buton pushed events which can be used by any App that supports button devices.  For more details, contact @abuttino in the Hubitat Community as he added this functionality.  Nice work!
