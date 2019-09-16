# Hubitat IoTaWatt Driver 

This driver implements the "Refresh" capability.  It is used to communicate via http to an IoTaWatt Power Monitor system and it creates child devices for Power and Voltage

This driver implements the "Presence" capability.  It is used to indicate good communications as "present" and lack of communications as "not present"

v0.1.20190915 - Added Capability Presence to indicate communications issue with the IoTaWatt device

v0.1.20181220 - Initial beta release

Instructions for use

**Create Hubitat Driver**
- Open up the "iotawatt-parent.groovy" driver from this repository.  Make sure you hit the "RAW" button, then select/highlight all of the source code, and COPY everything (Ctrl-C on Windows, or right click->Copy). 
- In your Hubitat Elevation Hub's admin web page, select the "Drivers Code" section and then click the "+ New Driver" button in the top right corner.  This will open a editor window for manipulating source code.
- Click in the editor window.  Then PASTE all of the code you copied in the first step.
- Click the SAVE button in the editor window.
- Repeate the above setps to install the **required** HubDuino "Child Power Meter" and "Child Voltage Sensor" Drivers.  These are avilable at https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino/Drivers

**Create the IoTaWatt Hubitat Device**
- In your Hubitat Elevation Hub's web page, select the "Devices" section, and then click the "+ Add Virtual Device" button in the top right corner.
- In the window that appears, please fill in the "Device Name", "Device Label", and "Device Network Id" fields.  Make sure the Device Network Id field is UNIQUE!  For example:
  - "Device Name" = IoTaWatt
  - "Device Label" = {optional}
  - "Device Network Id" = (automatically filled in by Hubitat)
  - In the "Type" field, scroll to the bottom of the list and select "IoTaWatt Parent"
- Click Save
- In the next window that appears, fill in the "Device IP Address" and "Polling Interval" with values appropriate to your application.  For example:
  - "Device IP Address" = 192.168.1.138   (this is the IP Address of my IoTaWatt device)
  - "Polling Interval" = 30  (this is how often the IoTaWatt will be polled for new data in seconds) 
- Click Save
- Look at Live Logs (or Past Logs if you didn't have a tab open when you clicked SAVE)
- You should see all of the Child Devices created for your IoTaWatt
- You can now add these devices to the Hubitat Dashboard, use them in Rules, etc...  One nice use is for monitoring laundry cycles to create notifications
