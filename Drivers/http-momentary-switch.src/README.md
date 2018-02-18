# Hubitat HTTP Momentary Switch Driver

This driver implements the "Switch" and "Momentary" capabilities.  It is used to create an HTTP POST, PUT, or GET to a user defined IP Address and Port.  The use-case that this was created for specifically is to allow Hubitat to issue commands to a Logitech Harmony Hub via the maddox/harmony-api server (see https://github.com/maddox/harmony-api for details.)


Instructions for use

**Create Hubitat Driver**
- Open up the "http-momentary-switch.groovy" driver from this repository (https://github.com/ogiewon/Hubitat/blob/master/Drivers/http-momentary-switch.src/http-momentary-switch.groovy).  Make sure you hit the "RAW" button, then select/highlight all of the source code, and COPY everything (Ctrl-C on Windows, or right click->Copy). 
- In your Hubitat Elevation Hub's web page, select the "Drivers Code" section and then click the "+ New Driver" button in the top right corner.  This will open a editor window for manipulating source code.
- Click in the editor window.  Then PASTE all of the code you copied in the first step.
- Click the SAVE button in the editor window.

**Create Hubitat Device**
- In your Hubitat Elevation Hub's web page, select the "Devices" section, and then click the "+ Add Virtual Device" button in the top right corner.
- In the window that appears, please fill in the "Device Name", "Device Label", and "Device Network Id" fields.  Make sure the Device Network Id field is UNIQUE!  For example:
  - "Device Name" = Harmony Watch Apple TV
  - "Device Label" = Harmony Watch Apple TV
  - "Device Network Id" = HarmonyWatchAppleTV
- In the "Type" field, scroll to the bottom of the list and select "HTTP Momentary Switch"
- Click Save
- In the next window that appears, fill in the "Device IP Address", "Device Port", "URL Path", and "POST, GET, PUT" with values appropriate to your apllication.  For example:
  - "Device IP Address" = 192.168.1.145   (this is the IP Address of my Raspberry Pi running the harmony-api server)
  - "Device Port" = 8282 (this is the PORT number of my Raspberry Pi running the harmony-api server)
  - "URL Path" = /hubs/family-room-harmony-hub/activities/watch-apple-tv  (this is the URL to activate my Harmony Hub's Watch Apple TV Activity)
  - "POST, GET, PUT" = POST  (**NOTE: the harmony-api needs a HTTP POST to turn on a Harmony Activity**) 
- Click Save
- Test your new device by clicking on "On", "Off", or "Push" 
  - **NOTE: ALL OF THESE DO THE EXACT SAME THING!**
  - Since this is really just a Momentary Pushbutton device, all of the commads in it currently execute the exact same code.  They all cause the HTTP request to be sent, as well as creating a "switch on" and "switch off" event.  This allows other Apps to know this device has been activated.  For example, in Rule Machine you could create a Triggered action to dim the lights when the "Watch TV Activity" is activated.  I implemented the "switch" capability to allow these devices to be discovered by the Amazon Alexa integration.  
  
**Tips for using "HTTP Momentary Switch" with the Harmony-API**
- The harmony-api needs to run constantly on another system on your LAN.  I use a Raspberry Pi with a STATIC IP address for this. I did not make any customizations whatsoever to the harmony-api.  It automatically discovers your Harmony Hub(s) and exposes a very helpful website you can use to help create the URLs needed for each of your Harmony Activities.  Simply go to http://192.168.1.145:8282 (substitute your RPI's IP address of course) and you will see some very useful information as shown below.

![screenshot](https://user-images.githubusercontent.com/5206084/36354113-5aac8f14-149d-11e8-85fb-578b0861d7aa.png)

*Harmony Activities*
- In the "Your Hubs" section, you will find the URLs needed to be inserted into your new Hubitat "HTTP Momentary Switch" devices to be able to turn on an Activity.  
- Just strip off the last "/commands" string from any of your Harmony Hub Activities that you'd like to use as a "HTTP Momentary Switch" in Hubitat.  In the image above, you can see an entry for "/hubs/family-room-harmony-hub/activities/watch-apple-tv/commands".  Just drop off the "/commands" and you'll get "/hubs/family-room-harmony-hub/activities/watch-apple-tv" which is exactly the URL needed to be added to your new Hubitat device.
  - "Device IP Address" = **192.168.1.145**            (this is the IP Address of my Raspberry Pi running the harmony-api server)
  - "Device Port" = **8282**                           (this is the PORT number of my Raspberry Pi running the harmony-api server)
  - "URL Path" = **/hubs/family-room-harmony-hub/activities/watch-apple-tv**  (this is the URL to activate my Harmony Hub's Watch Apple TV Activity)
  - "POST, GET, PUT" = **POST**
  - **IMPORTANT - To turn on an Activiy, you must select the "POST" method in your Hubitat "HTTP Momentary Switch" device**

*Turning Off Harmony Hub Current Activity*
- To shut down your AV system, a special "off" command must be issued to the Harmony Hub.  To do this, simply create one more Hubitat Device using the "HTTP Momentary Switch" driver.  I call mine "Harmony All Off", as it will shut down whatever the current Activity is running. Here are the settings for my system:
  - "Device IP Address" = **192.168.1.145**             (this is the IP Address of my Raspberry Pi running the harmony-api server)
  - "Device Port" = **8282**                            (this is the PORT number of my Raspberry Pi running the harmony-api server)
  - "URL Path" = **/hubs/family-room-harmony-hub/off**  (make sure your put the name of your hub here!)
  - "POST, GET, PUT" = **PUT**
  - **IMPORTANT - To turn off the AV system, you must select the "PUT" method in your Hubitat "HTTP Momentary Switch" device**
  
  
