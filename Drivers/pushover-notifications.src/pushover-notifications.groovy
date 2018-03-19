/**
*   
*	File: Pushover_Driver.groovy
*	Platform: Hubitat
*   Modification History:
*       Date       Who            What
*		2018-03-11 Dan Ogorchock  Modified/Simplified for Hubitat
*
*   Inspired by original work for SmartThings by: Zachary Priddy, https://zpriddy.com, me@zpriddy.com
*
*  Copyright 2018 Dan Ogorchock
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*
*/

preferences {
  input("apiKey", "text", title: "API Key:", description: "Pushover API Key")
  input("userKey", "text", title: "User Key:", description: "Pushover User Key")
  input("deviceName", "text", title: "Optional Device Name:", description: "If blank, all devices get notified")
}

metadata {
  definition (name: "Pushover", namespace: "ogiewon", author: "Dan Ogorchock") {
    capability "Notification"
    capability "Actuator"
    capability "Speech Synthesis"
  }
}

def speak(message) {
    deviceNotification(message)
}

def deviceNotification(message) {
  log.debug "Sending Message: ${message} Priority: ${priority}"

  // Define the initial postBody keys and values for all messages
  def postBody = [
    token: "$apiKey",
    user: "$userKey",
    message: "${message}",
    priority: 0
  ]

  // We only have to define the device if we are sending to a single device
  if (deviceName)
  {
    log.debug "Sending Pushover to Device: $deviceName"
    postBody['device'] = "$deviceName"
  }
  else
  {
    log.debug "Sending Pushover to All Devices"
  }

  // Prepare the package to be sent
  def params = [
    uri: "https://api.pushover.net/1/messages.json",
    body: postBody
  ]

  log.debug postBody

  if ((apiKey =~ /[A-Za-z0-9]{30}/) && (userKey =~ /[A-Za-z0-9]{30}/))
  {
    log.debug "Sending Pushover: API key '${apiKey}' | User key '${userKey}'"
    httpPost(params){response ->
      if(response.status != 200)
      {
        sendPush("ERROR: 'Pushover Me When' received HTTP error ${response.status}. Check your keys!")
        log.error "Received HTTP error ${response.status}. Check your keys!"
      }
      else
      {
        log.debug "HTTP response received [$response.status]"
      }
    }
  }
  else {
    // Do not sendPush() here, the user may have intentionally set up bad keys for testing.
    log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
  }
}