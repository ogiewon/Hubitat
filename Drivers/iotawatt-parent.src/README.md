# Hubitat IoTaWatt Driver 

** v0.1.20220103 is a BREAKING CHANGE!  Please do NOT upgrade to this version as it will end up creating an entire new set of Child Devices from your IoTaWatt.  This is due to the change from custom Child Drivers to Hubitat's built-in 'Generic Component' child drivers.  I have made this change to reduce the amount of custom code required for new installations. **

This driver implements the "Refresh" capability.  It is used to communicate via http to an IoTaWatt Power Monitor system and it creates child devices for Power and Voltage.  Scheduling is handled automatically via a user preference polling interval in seconds.

This driver implements the "Presence" capability.  It is used to indicate good communications as "present" and lack of communications as "not present"

v0.1.20220103 - Convert child devices to use Hubitat's built-in 'Generic Component' drivers. Note:  THIS IS A BREAKING CHANGE!

v0.1.20201102 - Added timeout to http calls

v0.1.20200516 - Improved error handling

v0.1.20200508 - Ensure scheduling works properly after a hub reboot

v0.1.20200508 - Added cleanup functionality to the uninstalled() routine

v0.1.20190915 - Added Capability Presence to indicate communications issue with the IoTaWatt device

v0.1.20181220 - Initial beta release

Instructions for use

**Create Hubitat Driver**
- Open up the "iotawatt-parent.groovy" driver from this repository.  Make sure you hit the "RAW" button, then select/highlight all of the source code, and COPY everything (Ctrl-C on Windows, or right click->Copy). ALTERNATE Method: Use the URL from the RAW code page, and paste it into the new driver's IMPORT dialogue window.
- In your Hubitat Elevation Hub's admin web page, select the "Drivers Code" section and then click the "+ New Driver" button in the top right corner.  This will open a editor window for manipulating source code.
- Click in the editor window.  Then PASTE all of the code you copied in the first step.
- Click the SAVE button in the editor window.

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
