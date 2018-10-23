# Hubitat Amazon Alexa Text To Speech  v0.3.0 (USA only currently)

History
-------
- v0.1.0  Initial Release
- v0.1.1  Error handling
- v0.2.0  Added support for additonal device types
- v0.3.0  Added support for country selection (USA, Canada, & UK) + ability to rename the app

WARNING: v0.3.0 will create NEW child devices in addition to your existing ones.  This was necessary to support the ability to rename the App.  I had to modify the Device Network ID for the children to not depend on the app name.  You will need to modify any automations that use the old child devices to use the new ones.  Then delete the original child devices.  Sorry for the inconveniece!

Have you ever wanted to be able to issue Text to Speech (TTS) calls to your individual Amazon Echo, Dot, Spot, or Show devices from your Hubitat Elevation Hub?  If yes, read on!  If not, why wouldn't you want to do this?  Read on!

First things first...  Credit where credit is due...  Thank you to the authors of the folowing shell scripts, whose work was the basis for this integration.

  https://github.com/walthowd/ha-alexa-tts

  https://blog.loetzimmer.de/2017/10/amazon-alexa-hort-auf-die-shell-echo.html

Also, much thanks to Hubitat's Chuck Schwer, for his tips, tricks, nudging, and advice!


I have created a Hubitat App (Alexa TTS Manager) and Driver (Child Alexa TTS) which implement basic TTS capability directly on the Hubitat Elevation hub, with no need for another computer or hub.  The "Alexa TTS Manager" app communicates directly to the alexa.amazon.com website to list your devices.  You then select which devices you want to be able to issue TTS commands to.  The app then creates a "Child Alexa TTS" device for each selected Amazon device.  These child devices can be used by any Hubitat App that can utilize the "Speech Synthesis" Capability. 

Now, the tricky part...  (there's always a 'gotcha', right?)

In order for your Hubitat hub to communicate to https://alexa.amazon.com, it needs to be authenticated.  Unfortunately, Amazon has not yet seen fit to issue OAUTH keys for this type of integration, nor can you easily sign into your Amazon account from within the Hubitat Elevation Hub's Web App.  The solution comes in the form of a cookie.  You will have to obtain the contents of your browser's cookie after you sign into alexa.amazon.com, then strip out a bunch of fluff, and format it corectly so that it can be used by the "Alexa TTS Manager" app.  

My main browser of choice on Windows is Chrome.  Since I want this cookie to remain valid for a very long time, I decided that it was safest to download FireFox and install the "Cookies.txt" extension.  This way, I won't accidentally invalidate my cookie for alexa.amazon.com.  The goal is to be able to keep this cookie valid for a very long time.  There may be better solutions for this that I am not aware of, so please feel free to provide some feedback!  Always looking for some help!

# Original method (see below for alternate)
1) Fire up a browser, like FireFox, and install the Cookies.txt Extension by lennonhill (source available at https://github.com/lennonhill/cookies-txt)
2) Sign into https://alexa.amazon.com using your browser
3) Click on the 'Cookies.txt' extension and save the resulting cookie file to you computer
4) Open the cookie file in an editor like NotePad++
5) You should see something that looks similar to the following example

```
# Netscape HTTP Cookie File
# https://curl.haxx.se/rfc/cookie_spec.html
# This is a generated file! Do not edit.

www.mozilla.org	FALSE	/	FALSE	1539823641	moz-stub-attribution-code	c281cmNdPXd3dy5tb3ppbGxhLm9yZyetZWRpdW09KG5vgmUpJmNhbgBhaWduPShub3QgcgV0KSgjb25gZW50gShuw3Qgw2V0WQ..
www.mozilla.org	FALSE	/	FALSE	1539823641	moz-stub-attribution-sig	24f20c1791234567cbb5056789f1f4003fc1cb8cd216bab0cb34f9baa206a5f9
.mozilla.org	TRUE	/	FALSE	1539737302	_gat_UA-36116321-1	1
.mozilla.org	TRUE	/	FALSE	1539737361	_gat	1
.mozilla.org	TRUE	/	FALSE	1602809309	_ga	GA1.2.4567890684.1539456789
.mozilla.org	TRUE	/	FALSE	1539823709	_gid	GA1.2.4567890295.1545678901
.amazon.com	TRUE	/	FALSE	2170457359	session-id	123-4567890-1234567
.amazon.com	TRUE	/	FALSE	2170457359	session-id-time	1234567890l
www.amazon.com	FALSE	/	FALSE	1540342172	csm-hit	tb:7T191234567890DQH5CD+s-9G3HZNZ91234567890|1234567890764&adb:adblk_no
#HttpOnly_.amazon.com	TRUE	/	TRUE	2170457391	sid	"9CumEN1F/rk4s5lagx86rw==|F8rrq1234567890HOzwLFaX12345678903fcDQ="
.amazon.com	TRUE	/	FALSE	2170457390	ubid-main	130-2711001-12345678
.amazon.com	TRUE	/	FALSE	2170457391	session-token	"5H1234567890sg5zn3qe9BE6zniSfsS6iWarF2rjHunCxErxZFoK7HYVqVLJ01234567890kAQgqHm2Z0/ce1234567890iej5IrwENPF1234567890L8mHG1234567890luCpHqoxL7fDtwQrpe7WMwW1QW+ITqsQhuQy501234567890P+XCpci5xsG1234567890HBLRND0arnZifPqndStRf8="
.amazon.com	TRUE	/	FALSE	2170457391	x-main	"Uy@EPvTF@KBnko?V@12345678906QrX"
#HttpOnly_.amazon.com	TRUE	/	TRUE	2170457390	at-main	Atza|IwEBIIX59iE-dR1234567890nt-tflCFWhqH8E1234567890sbE4cHgK1_mz01QQymCzve0iFu4wq7k-t86VkmmvUm123456789069_wjxusrO3v7b-c0XzvsgzYT4PDSmEME0hn1234567890W90k4LZWgRwa541234567890RYCE1EENn_o9hkle1234567890Y8r8IbjumqaH891234567890QX67EdZ6Nrwz1234567890h0iX3iWzSno9vcHZLbQAyw12345678901DDz46_V-L48wDi1234567890AuOqENK1234567890TSh84SJ_XjM_Tiv-6gUdn_11234567890LFXvDtvnYh-J1234567890g6e1nRQa1234567890Fk8QvvRrCY1234567890Jt-mqf1234567890O0jdcn45C-Yg-nB1ZxXoG-84m1234567890Wz-Cw1234567890azXQR_gE777A
#HttpOnly_.amazon.com	TRUE	/	TRUE	2170457391	sess-at-main	"1234567890Q1xRW21234567890G2k1234567890DDCEnk="
.amazon.com	TRUE	/	FALSE	1855356593	csrf	123456789
alexa.amazon.com	FALSE	/api	FALSE	0	x-amzn-dat-gui-client-v	1.24.203945.0
alexa.amazon.com	FALSE	/spa/	FALSE	1234567890	gw-timestamp	Wed%2C%2014%20SEP%202018%2019%3A49%3A54%30GMT
alexa.amazon.com	FALSE	/spa/	FALSE	1234567890	gw-penalty	10000
```

Every cookie that ends in ".amazon.com" but does not start with "www." or "alexa." needs to be used in the following step.  

Ignore the www.amazon.com and alexa.amazon.com lines. The rest need to be combined into a semicolon separated list, with all double quotes removed!  

The above cookie file would end up looking like the following string. Note, it seems like the order of these values might make a difference.  Try to replicate the order of each cookie value as shown below.  Your cookies.txt file may have more or less lines than mine did.  Using FireFox definitely produce fewer lines than Chrome for me!  If you have more or less, just roll with it and create your string that resembles the following.  Again, we're focusing only on lines that end in ".amazon.com" but do not start with "www." or "alexa."

Note:  Be sure to replace the spaces with equals signs and end section with a semicolon.  For example, "csrf 123456789" becomes "csrf=123456789;"

```
at-main=Atza|IwEBIIX59iE-dR1234567890nt-tflCFWhqH8E1234567890sbE4cHgK1_mz01QQymCzve0iFu4wq7k-t86VkmmvUm123456789069_wjxusrO3v7b-c0XzvsgzYT4PDSmEME0hn1234567890W90k4LZWgRwa541234567890RYCE1EENn_o9hkle1234567890Y8r8IbjumqaH891234567890QX67EdZ6Nrwz1234567890h0iX3iWzSno9vcHZLbQAyw12345678901DDz46_V-L48wDi1234567890AuOqENK1234567890TSh84SJ_XjM_Tiv-6gUdn_11234567890LFXvDtvnYh-J1234567890g6e1nRQa1234567890Fk8QvvRrCY1234567890Jt-mqf1234567890O0jdcn45C-Yg-nB1ZxXoG-84m1234567890Wz-Cw1234567890azXQR_gE777A; csrf=123456789; sess-at-main=1234567890Q1xRW21234567890G2k1234567890DDCEnk=; session-id=123-4567890-1234567; session-id-time=1234567890l; x-main=Uy@EPvTF@KBnko?V@12345678906QrX; session-token=5H1234567890sg5zn3qe9BE6zniSfsS6iWarF2rjHunCxErxZFoK7HYVqVLJ01234567890kAQgqHm2Z0/ce1234567890iej5IrwENPF1234567890L8mHG1234567890luCpHqoxL7fDtwQrpe7WMwW1QW+ITqsQhuQy501234567890P+XCpci5xsG1234567890HBLRND0arnZifPqndStRf8=; sid=9CumEN1F/rk4s5lagx86rw==|F8rrq1234567890HOzwLFaX12345678903fcDQ=; ubid-main=130-2711001-12345678;
```
# Alternate method to get the cookie directly from Chrome.  (Thanks @krlaframboise and @ritchierich !)
1) Open Chrome and Login to alexa.amazon.com
2) Open the Chrome menu at the top-right of your browser window, then select Tools (More Tools) > Developer Tools
3) On top row of developer tools you will see several tabs: Elements, Console, Sources, Network, etc. Click Network.
4) Just below the tabs you will see a red circle, below that you will see a filter box, and below that you will see sub tabs: All, XHR, JS, etc. Click XHR
5) Click Smart Home in the left navigation
6) The developer tools XHR list should populate with several items, one called "featureaccess-v3", right-click on it and choose Copy, Copy Request Headers. This will place it into your clipboard.
7) Open your favorite text editor such as Notepad and paste the contents into it.
8) The last item should be the cookie. Remove everything including "Cookie: ", leaving the raw cookie in the text editor.
9) Remove all double quotes. Search for double quotes and replace with an empty string.
10) Paste the cookie into the app and add a semi-colon to the end of it


If someone wants to write a standalone app to parse the cookies.txt file and create the above semicolon delimited string, that would be a great help to users.

Okay, so you've got your cookie string.  Congratulations as I know that was a bit of manual work!  Now for the easy part!
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
