# Hubitat Amazon Alexa Text To Speech  v0.5.0
(USA, Canada, UK, Italy currently supported)

History
-------
- v0.1.0  Initial Release
- v0.1.1  Error handling
- v0.2.0  Added support for additonal device types
- v0.3.0  Added support for country selection (USA, Canada, & UK) + ability to rename the app
- v0.4.0  Added Virtual Container Support - Thanks @stephack!
- v0.4.1  Added notification support for errors (like cookie expiration) and some code clean-up
- v0.4.2  Improved notification for expired cookie
- v0.4.3  Prevent sending empty messages to Amazon
- v0.4.4  Added Notification for Message Rate Exceeded
- v0.4.5  Added support for entering raw cookie (no parsing required, just copy from browser and paste in app)
- v0.4.6  Added support for Italy.  Thank you @gabriele!
- v0.5.0  Added support for automatic cookie refresh with external NodeJS webserver

WARNING: v0.4.x will delete and create new child devices if you choose to enable the virtual container support feature.  To use the Virtual Container feature, you will need @stephack's Virtual Container Driver from his repo at https://github.com/stephack/Hubitat/blob/master/drivers/Virtual%20Container/Virtual%20Container.groovy.


Have you ever wanted to be able to issue Text to Speech (TTS) calls to your individual Amazon Echo, Dot, Spot, or Show devices from your Hubitat Elevation Hub?  If yes, read on!  If not, why wouldn't you want to do this?  Read on!

First things first...  Credit where credit is due...  Thank you to the authors of the folowing shell scripts, whose work was the basis for this integration.

  https://github.com/walthowd/ha-alexa-tts

  https://blog.loetzimmer.de/2017/10/amazon-alexa-hort-auf-die-shell-echo.html

Also, much thanks to Hubitat's Chuck Schwer, for his tips, tricks, nudging, and advice!


I have created a Hubitat App (Alexa TTS Manager) and Driver (Child Alexa TTS) which implement basic TTS capability directly on the Hubitat Elevation hub, with no need for another computer or hub.  The "Alexa TTS Manager" app communicates directly to the alexa.amazon.com website to list your devices.  You then select which devices you want to be able to issue TTS commands to.  The app then creates a "Child Alexa TTS" device for each selected Amazon device.  These child devices can be used by any Hubitat App that can utilize the "Speech Synthesis" Capability. 

Now, the tricky part...  (there's always a 'gotcha', right?)

In order for your Hubitat hub to communicate to https://alexa.amazon.com, it needs to be authenticated.  Unfortunately, Amazon has not yet seen fit to issue OAUTH keys for this type of integration, nor can you easily sign into your Amazon account from within the Hubitat Elevation Hub's Web App.  The solution comes in the form of a cookie. 
There are two ways to obtain it:
1) Manually obtain the contents of your browser's cookie after you sign into alexa.amazon.com (this should be manually done every 14 days)
2) Automatically obtain and refresh cookie with [AlexaCookieNodeJs](https://github.com/gabriele-v/hubitat/tree/master/AlexaCookieNodeJs/AlexaCookieNodeJs)

Method 1A - Get the cookie directly from Chrome.  (Thanks @krlaframboise and @ritchierich !)
-----------------------------------------------------------------------------------------------------
1) Open Chrome and Login to alexa.amazon.com
2) Open the Chrome menu at the top-right of your browser window, then select Tools (More Tools) > Developer Tools (or click F12)
3) On top row of developer tools you will see several tabs: Elements, Console, Sources, Network, etc. Click Network.
4) Just below the tabs you will see a red circle, below that you will see a filter box, and below that you will see sub tabs: All, XHR, JS, etc. Click XHR
5) Click Smart Home in the left navigation
6) The developer tools XHR list should populate with several items, one called "featureaccess-v3", right-click on it and choose Copy, Copy Request Headers. This will place it into your clipboard.
7) Paste the cookie into the app

Method 1B - Get the cookie from Firefox (Thanks @NoWon)
-------------------------------------------------------------------------------------------------------
1) In FireFox goto to https://alexa.amazon.com/ then right click on the web page
2) Select "inspect element (Q)"
3) Select network and then XHR
4) Right click on the "post" that has "alexa.amazon.com" and select "copy request headers"
5) Then paste that into a notepad and your cookie will be clearly labeled there.
6) Ignore the first "Cookie: " part and just add a semi-colon to the end
7) Paste the cookie into the app

Method 2 - Automatically obtain and refresh cookie with AlexaCookieNodeJs (Thanks @gabriele)
-------------------------------------------------------------------------------------------------------
If you are able to install a NodeJS service on a machine in your network (or deploy a cloud one), this will be the easiest and automated method, that will take care of refreshing the cookie every 6 days
1) Deploy and configure AlexaCookieNodeJS as described => https://github.com/gabriele-v/hubitat/tree/master/AlexaCookieNodeJs/AlexaCookieNodeJs
2) Follow the instructions copying RefreshURL and RefreshOptions into Hubitat app

Okay, so you've got your cookie string.  Congratulations as I know that was a bit of manual work!  Now for the easy part!
-----------------------------------------------------------------------------------------------------
1) Add the "Alexa TTS Manager" source code to your Hubitat 'Apps Code" as a new app and save it. https://raw.githubusercontent.com/ogiewon/Hubitat/master/Alexa%20TTS/Apps/alexa-tts-manager.src/alexa-tts-manager.groovy
2) Add the "Child Alexa TTS" source code to your Hubitat 'Drivers Code" as a new app and save it. https://raw.githubusercontent.com/ogiewon/Hubitat/master/Alexa%20TTS/Drivers/child-alexa-tts.src/child-alexa-tts.groovy
3) Go to your 'Apps', and click 'Load New App' and select the "Alexa TTS Manager"
4) Now copy and paste that huge cookie string you created earlier and paste it into the text box.  
5) Select your country from the drop down list.
6) Optionally, rename the App as you see fit.
7) Click Next.
8) If everything went well with your cookie, you should be able to select one or more of your Alexa devices on the page that comes up.
9) Click Done and you should be ready to give it a try!
10) Go to your 'Devices', and select the 'AlexaTTS yourdevicename' device from the list.  
11) On the device's detail page, type in any string your want and click 'Speak'
12) Optionally, change the Label of the device to whatever you'd like.  Do NOT change the Device Network ID!

If everything went well, you should be up and running!

Remember, this will only continue to work as long as the cookie from your browser session is valid.  Also, Amazon could change things behind the scenes at any time which could break this integration.  Enjoy it while it lasts!

If anyone wants to help improve this, or add additonal feature, please feel free to submit pull requests or reach out to me directly via the Hubitat Community Forums (@ogiewon).
