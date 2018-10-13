/*
TP-Link Connect Service Manager, 2018 Version 2, TP-Link Device Manager, 2018 Version 3

Copyright 2018 Dave Gutheinz, Anthony Ramirez

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at:

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License.

##### Discalimer: This Service Manager and the associated Device
Handlers are in no way sanctioned or supported by TP-Link. All
development is based upon open-source data on the TP-Link Kasa Devices;
primarily various users on GitHub.com.

##### Notes #####
1.	This Service Manager is designed to install and manage TP-Link
	bulbs, plugs, and switches using their respective device handlers.
2.	Please direct comments to the SmartThings community thread
	'TP-Link Device Manager SmartThings Integration'.
*/

definition(
	name: "${appLabel()}",
	namespace: "${appNamespace()}",
	author: "${appAuthor()}",
	description: "${textDesc()}",
	category: "Convenience",
	iconUrl: "${getAppImg("kasa.png", on)}",
	iconX2Url: "${getAppImg("kasa.png", on)}",
	iconX3Url: "${getAppImg("kasa.png", on)}",
	singleInstance: true
)

def appVersion() { return "3.5.0" }
def appVerDate() { return "10-13-2018" }
def driverVersionsMin() { return "3.2.0" }

preferences {
	page(name: "startPage")
	page(name: "authPage")
	page(name: "mainPage")
	page(name: "selectDevices")
	page(name: "tokenPage")
	page(name: "devMode")
	page(name: "devModeTestingPage")
	page(name: "hiddenPage")
	page(name: "aboutPage")
	page(name: "changeLogPage")
	page(name: "uninstallPage")
}

def setInitialStates() {
	if (!state.TpLinkToken) {state.TpLinkToken = null}
	if (!state.devices) {state.devices = [:]}
	if (!state.currentError) {state.currentError = null}
	if (!state.errorCount) {state.errorCount = 0}
	settingUpdate("userSelectedReload", "false", "bool")
	settingUpdate("userSelectedRemoveMode", "false", "bool")
	settingUpdate("userSelectedDevicesRemove", "", "enum")
	settingUpdate("userSelectedDevicesAdd", "", "enum")
}

def setRecommendedOptions() {
	if (userSelectedAssistant){
		getDevices()
		def devices = state.devices
		def newDevices = [:]
		def oldDevices = [:]
		devices.each {
			def isChild = getChildDevice(it.value.deviceMac)
			if (isChild) {
				oldDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
			}
			if (!isChild) {
				newDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
			}
		}
		if ("${userName}" =~ null || "${userPassword}" =~ null){
			settingUpdate("userSelectedOptionTwo", "Activate Account", "enum")
		} else {
			settingUpdate("userSelectedOptionTwo", "Update Account", "enum")
		}
		if (state.TpLinkToken != null){
			settingUpdate("userSelectedOptionOne", "Add/Remove Devices", "enum")
		} else {
			if ("${userName}" =~ null || "${userPassword}" =~ null){
				settingUpdate("userSelectedOptionOne", "Initial Install", "enum")
			} else {
				settingUpdate("userSelectedOptionOne", "Update Token", "enum")
			}
		}
		if (newDevices != [:]){
			settingUpdate("userSelectedRemoveMode", "false", "bool")
		} else {
			if (oldDevices != [:]){
				settingUpdate("userSelectedRemoveMode", "true", "bool")
			}
		}
		if (state.currentError != null){
			settingUpdate("userSelectedOptionThree", "Update Credentials", "enum")
		} else {
			if (state.TpLinkToken != null){
				if (userSelectedOptionTwo =~ "Update Account") {
					settingUpdate("userSelectedOptionThree", "Update Token", "enum")
				} else {
					settingUpdate("userSelectedOptionThree", "Delete Token", "enum")
				}
			} else {
				settingUpdate("userSelectedOptionThree", "Update Token", "enum")
			}
		}
	}
}

//	----- START PAGE -----
def startPage() {
	setInitialStates()
	if ("${userName}" =~ null || "${userPassword}" =~ null){
		return authPage()
	} else {
		setRecommendedOptions()
		return mainPage()
	}
}

//	----- AUTH PAGE -----
def authPage() {
	def authPageText = "If possible, open the IDE and select Live Logging. Then, " +
		"enter your Username and Password for TP-Link (same as Kasa app) and the "+
		"action you want to complete. " + "\n\rAvailable actions:\n\r" +
		"Activate Account: Login into TP-Link Account and obtains token and adds devices.\n\r" +
		"Update Account: Updates or removes the token and credentials."
	def driverVersionText = "TP-Link Kasa Drivers for SmartThings:" + "${driverVersionsMin()}" + "\n" + "Note: Drivers from the old the original repository will not work with this version of the application."
	return dynamicPage(
		name: "authPage",
		title: "Login Page",
		install: false,
		uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Information and Driver Version:", hideable: true, hidden: true) {
			if (state.TpLinkToken != null){
				paragraph tokenInfoOnline(), image: getAppImg("success.png")
			} else {
				paragraph tokenInfoOffline(), image: getAppImg("error.png")
			}
			paragraph title: "Information:", authPageText, image: getAppImg("information.png")
			paragraph title: "Driver Version:", driverVersionText, image: getAppImg("devices.png")
		}
		section("Account Configuration:") {
			input(
				"userName", "email",
				title: "TP-Link Kasa Email Address",
				required: true,
				submitOnChange: true,
				image: getAppImg("email.png")
			)
			input(
				"userPassword", "password",
				title: "TP-Link Kasa Account Password",
				required: true,
				submitOnChange: true,
				image: getAppImg("password.png")
			)
		}
		section("User Configuration:") {
			input(
				"userSelectedOptionTwo", "enum",
				title: "What do you want to do?",
				required: true,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Activate Account", "Update Account", "About Application"]],
				image: getAppImg("settings.png")
			)
			if (userSelectedOptionTwo =~ "Activate Account") {
				getToken()
			}
		}
		section("Page Selector:") {
			if (userSelectedOptionTwo != null) {
				if (state.currentError != null){
					paragraph pageSelectorErrorText(), image: getAppImg("error.png")
				} else {
					paragraph pageSelectorText(), image: getAppImg("pageselected.png")
				}
			} else {
				paragraph pageSelectorNullText(), image: getAppImg("pickapage.png")
			}
			if (userSelectedOptionTwo =~ "Activate Account") {
				href "selectDevices", title: "Device Manager Page", description: "Tap to view", image: getAppImg("selectdevices.png")
			}
			if (userSelectedOptionTwo =~ "Update Account") {
				href "tokenPage", title: "Token Manager Page", description: "Tap to view", image: getAppImg("tokenpage.png")
			}
			if (userSelectedOptionTwo =~ "About Application") {
				href "aboutPage", title: "About Page", description: "Tap to view", image: getAppImg("aboutpage.png")
			}
		}
		section("Extra Configuration:") {
			input (name: "userSelectedAssistant", type: "bool", title: "Do you want to enable recommended options?", required: false, submitOnChange: true, image: getAppImg("ease.png"))
			input(
				"userSelectedDevMode", "bool",
				title: "Do you want to enable developer mode?",
				submitOnChange: true,
				image: getAppImg("developer.png")
			)
		}
	}
}

//	----- MAIN PAGE -----
def mainPage() {
	if (userSelectedAssistant){
		
	}
	def mainPageText = "Available actions:\n\r" +
		"Initial Install: Login into TP-Link Account and obtains token and adds devices.\n\r" +
		"Add/Remove Devices: Only Add/Remove Devices.\n\r" +
		"Update Token: Updates the token or you can remove the token."
	def errorMsgCom = "None"
	if (state.currentError != null){
		errorMsgCom = "Error communicating with cloud:\n\r" + "${state.currentError}" +
			"\n\rPlease resolve the error and try again."
	}
	return dynamicPage(
		name: "mainPage",
		title: "Settings Page",
		install: false,
		uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
        section("Information and Diagnostics:", hideable: true, hidden: true) {
			if (state.TpLinkToken != null){
				paragraph tokenInfoOnline(), image: getAppImg("success.png")
			} else {
				paragraph tokenInfoOffline(), image: getAppImg("error.png")
			}
			paragraph title: "Information:", mainPageText, image: getAppImg("information.png")
			paragraph title: "Communication Error:", errorMsgCom, image: getAppImg("error.png")
		}
		section("User Configuration:") {
			input(
				"userSelectedOptionOne", "enum",
				title: "What do you want to do?",
				required: true,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Initial Install", "Add/Remove Devices", "Update Token"]],
				image: getAppImg("settings.png")
			)
		}
		section("Page Selector:") {
			if (userSelectedOptionOne != null) {
				if (state.currentError != null){
					paragraph pageSelectorErrorText(), image: getAppImg("error.png")
				} else {
					paragraph pageSelectorText(), image: getAppImg("pageselected.png")
				}
			} else {
				paragraph pageSelectorNullText(), image: getAppImg("pickapage.png")
			}
			if (userSelectedOptionOne =~ "Initial Install") {
				href "authPage", title: "Login Page", description: "Tap to view", image: getAppImg("authpage.png")
			}
			if (userSelectedOptionOne =~ "Add/Remove Devices") {
				href "selectDevices", title: "Device Manager Page", description: "Tap to view", image: getAppImg("selectdevices.png")
			}
			if (userSelectedOptionOne =~ "Update Token") {
				href "tokenPage", title: "Token Manager Page", description: "Tap to view", image: getAppImg("tokenpage.png")
			}
		}
		section("Extra Configuration:") {
			input ("appIcons", "bool", title: "Disable App Icons?", required: false, submitOnChange: true, image: getAppImg("noicon.png"))
			input (name: "userSelectedReload", type: "bool", title: "Do you want to refresh your current state?", required: false, submitOnChange: true, image: getAppImg("sync.png"))
			if (userSelectedReload){
				setInitialStates()
			}
		}
		section("Help and Feedback:") {
			if (userSelectedDevMode){
				href "devMode", title: "Developer Page", description: "Tap to view", image: getAppImg("developer.png")
			}
			href url: getWikiPageUrl(), style:"embedded", required:false, title:"View the Projects Wiki", description:"Tap to open in browser", state: "complete", image: getAppImg("help.png")
			href url: getIssuePageUrl(), style:"embedded", required:false, title:"Report | View Issues", description:"Tap to open in browser", state: "complete", image: getAppImg("issue.png")
		}
		section("About and Changelog:") {
			href "aboutPage", title: "About Page", description: "Tap to view", image: getAppImg("aboutpage.png")
			href "changeLogPage", title: "Changelog Page", description: "Tap to view", image: getAppImg("changelogpage.png")
		}
		section("Uninstall:") {
			href "uninstallPage", title: "Uninstall Page", description: "Tap to view", image: getAppImg("uninstall.png")
		}
	}
}

//	----- SELECT DEVICES PAGE -----
def selectDevices() {
	getDevices()
	def devices = state.devices
	def errorMsgDev = "None"
	def newDevices = [:]
	def oldDevices = [:]
	devices.each {
		def isChild = getChildDevice(it.value.deviceMac)
		if (isChild) {
			oldDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
		if (!isChild) {
			newDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
	}
	if (devices == [:]) {
		errorMsgDev = "We were unable to find any TP-Link Kasa devices on your account. This usually means "+
		"that all devices are in 'Local Control Only'. Correct them then " + "rerun the application."
	}
	if (newDevices == [:]) {
		errorMsgDev = "No new devices to add. Are you sure they are in Remote " + "Control Mode?"
	}
	if (oldDevices == [:] && userSelectedRemoveMode) {
		errorMsgDev = "There are no devices to remove from the smart things app at this time."
	}
	def TPLinkDevicesMsg = "Devices that have not been previously installed and are not in 'Local " +
		"WiFi control only' will appear below. Tap below to see the list of " +
		"TP-Link Kasa Devices available select the ones you want to connect to " +
		"SmartThings.\n\r" + "Press Done when you have selected the devices you " +
		"wish to add, then press Done again to install the devices. Press < " +
		"to return to the previous page."
	return dynamicPage(
		name: "selectDevices",
		title: "Device Manager Page",
		install: true,
		uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Information and Diagnostics:", hideable: true, hidden: true) {
			if (state.TpLinkToken != null){
				paragraph tokenInfoOnline(), image: getAppImg("success.png")
			} else {
				paragraph tokenInfoOffline(), image: getAppImg("error.png")
			}
			paragraph title: "Information:", TPLinkDevicesMsg, image: getAppImg("information.png")
			paragraph title: "Device Error:", errorMsgDev, image: getAppImg("error.png")
		}
		section("Device Controller:") {
			input(
				"userSelectedRemoveMode", "bool",
				title: "Do you want to enable device removal mode?",
				defaultValue: false,
				submitOnChange: true,
				image: getAppImg("deviceremover.png")
			)
			if (userSelectedRemoveMode) {
				input(
					"userSelectedDevicesRemove", "enum",
					required: true,
					multiple: true,
					submitOnChange: true,
					title: "Select Devices (${oldDevices.size() ?: 0} found)",
					metadata: [values:oldDevices],
					image: getAppImg("removedevices.png")
				)
			} else {
				input(
					"userSelectedDevicesAdd", "enum",
					required: true,
					multiple: true,
					submitOnChange: true,
					title: "Select Devices (${newDevices.size() ?: 0} found)",
					metadata: [values:newDevices],
					image: getAppImg("adddevices.png")
				)
			}
		}
		section("Saving Settings:") {
			if (userLightTransTime != null && userRefreshRate != null){
				sendEvent(name: "lightingTransitionTime", value: userLightTransTime)
				sendEvent(name: "deviceRefreshRate", value: userRefreshRate)
				paragraph sendingDataSuccess(), image: getAppImg("send.png")
			} else {
				paragraph sendingDataFailed(), image: getAppImg("issue.png")
			}
		}
		section("Device Configuration:") {
			input(
				"userLightTransTime", "number",
				required: true,
				multiple: false,
				submitOnChange: true,
				title: "Lighting Transition Time",
				description: "0 to 60 seconds",
				image: getAppImg("transition.png")
			)
			input(
				"userRefreshRate", "enum",
				required: true,
				multiple: false,
				submitOnChange: true,
				title: "Device Refresh Rate",
				metadata: [values:["5" : "Refresh every 5 minutes", "10" : "Refresh every 10 minutes", "15" : "Refresh every 15 minutes", "30" : "Refresh every 30 minutes"]],
				image: getAppImg("refresh.png")
			)
		}
	}
}

//	----- TOKEN MANAGER PAGE -----
def tokenPage () {
	def tokenPageText = "Your current token:\n\r\n\r${state.TpLinkToken}" + 
		"\n\rAvailable actions:\n\r" +
		"Update Token: Updates the token.\n\r" +
		"Remove Token: Removes the token.\n\r" +
		"Update Credentials: Updates your out of date credentials so you can get a new token."
		def errorMsgTok = "None"
		if (state.TpLinkToken == null){
			errorMsgTok = "You will be unable to control your devices until you get a new token."
		}
		if (state.currentError != null){
			errorMsgTok = "You may not be able to control your devices until you update your credentials."
		}
	dynamicPage(name: "tokenPage", title: "Token Manager Page", install: false, uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Information and Diagnostics:", hideable: true, hidden: true) {
			paragraph title: "Information:", tokenPageText, image: getAppImg("information.png")
			paragraph title: "Account Error:", errorMsgTok, image: getAppImg("error.png")
		}
		section("Account Status:") {
			if (state.TpLinkToken != null){
				paragraph tokenInfoOnline(), image: getAppImg("success.png")
			} else {
				paragraph tokenInfoOffline(), image: getAppImg("error.png")
			}
		}
		section("User Configuration:") {
			input(
				"userSelectedOptionThree", "enum",
				title: "What do you want to do?",
				required: true,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Update Token", "Delete Token", "Update Credentials"]],
				image: getAppImg("token.png")
			)
			if (userSelectedOptionThree =~ "Update Token") {
				getToken()
			}
			if (userSelectedOptionThree =~ "Delete Token") {
				state.TpLinkToken = null
			}
		}
		section("Extra Configuration:") {
			input (name: "userSelectedReload", type: "bool", title: "Do you want to refresh your current state?", required: false, submitOnChange: true, image: getAppImg("sync.png"))
			if (userSelectedReload){
				setInitialStates()
			}
		}
	}
}

//	----- DEVELOPER PAGE -----
def devMode() {
	getDevices()
	def hiddenInput = 0
	def devices = state.devices
	def newDevices = [:]
	def oldDevices = [:]
	devices.each {
		def isChild = getChildDevice(it.value.deviceMac)
		if (isChild) {
			oldDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
		if (!isChild) {
			newDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
	}
	def hub = location.hubs[0]
	def hubId = hub.id
	return dynamicPage(
		name: "devMode",
		title: "Developer Page",
		install: false,
		uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Application Information:", hideable: true, hidden: true) {
			paragraph title: "TP-Link Token:", "${state.TpLinkToken}", image: getAppImg("token.png")
			paragraph title: "Hub:", "${hub}", image: getAppImg("samsunghub.png")
			paragraph title: "Hub ID:", "${hubId}", image: getAppImg("samsunghub.png")
			paragraph title: "Username:", "${userName}", image: getAppImg("email.png")
			paragraph title: "Password:", "${userPassword}", image: getAppImg("password.png")
			paragraph title: "Managed Devices:", "${oldDevices}", image: getAppImg("devices.png")
			paragraph title: "New Devices:", "${newDevices}", image: getAppImg("devices.png")
		}
		section("Page Selector:") {
			href "startPage", title: "Start Page", description: "Tap to view", image: getAppImg("startpage.png")
			href "authPage", title: "Login Page", description: "Tap to view", image: getAppImg("authpage.png")
			href "mainPage", title: "Settings Page", description: "Tap to view", image: getAppImg("mainpage.png")
			href "selectDevices", title: "Device Manager Page", description: "Tap to view", image: getAppImg("selectdevices.png")
			href "tokenPage", title: "Token Manager Page", description: "Tap to view", image: getAppImg("tokenpage.png")
			if ("${restrictedRecordPasswordPrompt}" =~ "Mac5089"){
				href "hiddenPage", title: "xKiller Clan Page", description: "Tap to view", image: getAppImg("xkillerclan.png")
			}
			if (devModeLoaded){
				href "devModeTestingPage", title: "Developer Testing Page", description: "Tap to view", image: getAppImg("testing.png")
			}
			href "aboutPage", title: "About Page", description: "Tap to view", image: getAppImg("aboutpage.png")
			href "changeLogPage", title: "Changelog Page", description: "Tap to view", image: getAppImg("changelogpage.png")
			href "uninstallPage", title: "Uninstall Page", description: "Tap to view", image: getAppImg("uninstallpage.png")
		}
		section("Extra Configuration:") {
			input (name: "userSelectedReload", type: "bool", title: "Do you want to refresh your current state?", required: false, submitOnChange: true, image: getAppImg("sync.png"))
			if (userSelectedReload){
				setInitialStates()
			}
			input(
				"devModeLoaded", "bool",
				title: "Do you want to enable developer testing page?",
				submitOnChange: true,
				required: false,
				image: getAppImg("developer.png")
			)
			if (devModeLoaded && userSelectedReload || hiddenInput == 1){
				hiddenInput = 1
				input (name: "restrictedRecordPasswordPrompt", type: "password", title: "This is a restricted record, Please input your password", required: false, submitOnChange: true, image: getAppImg("passwordverification.png"))
			}
		}
	}
}

//	----- DEVELOPER TESTING PAGE -----
def devModeTestingPage() {
	getDevices()
	def devices = state.devices
	def newDevices = [:]
	def oldDevices = [:]
	def errorMsgCom = "None"
	def errorMsgDev = "None"
	def errorMsgNew = "None"
	def errorMsgOld = "None"
	def errorMsgTok = "None"
		if (state.TpLinkToken == null){
			errorMsgTok = "You will be unable to control your devices until you get a new token."
		}
	devices.each {
		def isChild = getChildDevice(it.value.deviceMac)
		if (isChild) {
			oldDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
		if (!isChild) {
			newDevices["${it.value.deviceMac}"] = "${it.value.alias} model ${it.value.deviceModel}"
		}
	}
	if (state.currentError != null){
		errorMsgCom = "Error communicating with cloud:\n\r" + "${state.currentError}" +
			"\n\rPlease resolve the error and try again."
	}
	if (devices == [:]) {
		errorMsgDev = "We were unable to find any TP-Link Kasa devices on your account. This usually means "+
		"that all devices are in 'Local Control Only'. Correct them then " +
		"rerun the application."
	}
	if (newDevices == [:] && oldDevices == [:]) {
		errorMsgNew = "No new devices to add. Are you sure they are in Remote " +
		"Control Mode?"
	}
	if (oldDevices == [:] && userSelectedRemoveMode) {
		errorMsgOld = "No current devices to remove from smart things."
	}
	return dynamicPage(
		name: "devModeTestingPage",
		title: "Developer Testing Page",
		install: false,
		uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Application Information:", hideable: true, hidden: true) {
			paragraph title: "Communication Error:", errorMsgCom, image: getAppImg("error.png")
			paragraph title: "Finding Devices Error:", errorMsgDev, image: getAppImg("error.png")
			paragraph title: "New Devices Error:", errorMsgNew, image: getAppImg("error.png")
			paragraph title: "Current Devices Error:", errorMsgOld, image: getAppImg("error.png")
			paragraph title: "Account Error:", errorMsgTok, image: getAppImg("error.png")
			paragraph title: "Error Count:", "${state.errorCount}", image: getAppImg("error.png")
			paragraph title: "Current Error:", "${state.currentError}", image: getAppImg("error.png")
			paragraph title: "Error Messages:", "${errMsg}", image: getAppImg("error.png")
		}
		section("User Configuration:") {
			input(
				"userSelectedOptionTwo", "enum",
				title: "What do you want to do?",
				required: false,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Activate Account", "Update Account"]],
				image: getAppImg("settings.png")
			)
			input(
				"userSelectedOptionOne", "enum",
				title: "What do you want to do?",
				required: false,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Initial Install", "Add/Remove Devices", "Update Token"]],
				image: getAppImg("settings.png")
			)
			input(
				"userSelectedOptionThree", "enum",
				title: "What do you want to do?",
				required: true,
				multiple: false,
				submitOnChange: true,
				metadata: [values:["Update Token", "Delete Token", "Update Credentials"]],
				image: getAppImg("token.png")
			)
		}
		section("Extra Configuration:") {
			input ("appIcons", "bool", title: "Disable App Icons?", required: false, submitOnChange: true, image: getAppImg("noicon.png"))
			input (name: "userSelectedReload", type: "bool", title: "Do you want to refresh your current state?", required: false, submitOnChange: true, image: getAppImg("sync.png"))
			input (name: "userSelectedAssistant", type: "bool", title: "Do you want enable recommended options?", required: false, submitOnChange: true, image: getAppImg("ease.png"))
			input(
				"userSelectedDevMode", "bool",
				title: "Do you want to enable developer mode?",
				submitOnChange: true,
				image: getAppImg("developer.png")
			)
		}
		section("Page Selector:") {
			paragraph pageSelectorErrorText(), image: getAppImg("error.png")
			paragraph pageSelectorText(), image: getAppImg("pageselected.png")
			paragraph pageSelectorNullText(), image: getAppImg("pickapage.png")
		}
		section("Account Configuration:") {
			input(
				"userName", "email",
				title: "TP-Link Kasa Email Address",
				required: false,
				image: getAppImg("email.png")
			)
			input(
				"userPassword", "password",
				title: "TP-Link Kasa Account Password",
				required: false,
				image: getAppImg("password.png")
			)
		}
		section("Device Configuration:") {
			input(
				"userSelectedRemoveMode", "bool",
				title: "Do you want to enable device removal mode?",
				submitOnChange: true,
				image: getAppImg("deviceremover.png")
			)
			input(
				"userSelectedDevicesRemove", "enum",
				required: false,
				multiple: true,
				submitOnChange: true,
				title: "Select Devices (${oldDevices.size() ?: 0} found)",
				metadata: [values:oldDevices],
				image: getAppImg("devices.png")
			)
			input(
				"userSelectedDevicesAdd", "enum",
				required: false,
				multiple: true,
				submitOnChange: true,
				title: "Select Devices (${newDevices.size() ?: 0} found)",
				metadata: [values:newDevices],
				image: getAppImg("devices.png")
			)
			input(
				"userLightTransTime", "number",
				required: true,
				multiple: false,
				submitOnChange: true,
				title: "Lighting Transition Time",
				description: "0 to 60 seconds",
				image: getAppImg("transition.png")
			)
			input(
				"userRefreshRate", "enum",
				required: true,
				multiple: false,
				submitOnChange: true,
				title: "Device Refresh Rate",
				metadata: [values:["5" : "Refresh every 5 minutes", "10" : "Refresh every 10 minutes", "15" : "Refresh every 15 minutes", "30" : "Refresh every 30 minutes"]],
				image: getAppImg("refresh.png")
			)
		}
		section("Saving Settings:") {
			paragraph sendingDataSuccess(), image: getAppImg("send.png")
			paragraph sendingDataFailed(), image: getAppImg("issue.png")
		}
		section("Account Status:") {
			paragraph tokenInfoOnline(), image: getAppImg("success.png")
			paragraph tokenInfoOffline(), image: getAppImg("error.png")
		}
	}
}

//	----- HIDDEN PAGE -----
def hiddenPage () {
	def xkMembersInfo = "Although most of these members have left here is a complete list of all the members we had" 
	def xkMembers = "xKllerBOSSXXX, xKillerDDigital, xKillerIntense, xKillerMaverick, xKillerKittyKat, xKillerPP, xKillerBrute, xKillerBSOD, xKillerFoxy, xKillerTricky, xKillerReaper, xKillerPain, xKillerRobot, xKillerSasha, XKillerAwesomer, xKillerSonic, xKillerChakra, xKillerDoobage, xKillerSeki, xKillerEvo, xKillerSubXero, xKillerCali, xKillerAsh, xKillerTruKillah, Weirdowack"
	def xkGameInfo = "Although we may not play most of these games anymore but as a bunch of friends and some family had fun along the way but i guess some things just don't last"
	dynamicPage(name: "hiddenPage", title: "xKiller Clan Page", install: false, uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("xkillerclan.png")
		}
		section("Members:") {
			paragraph xkMembersInfo, image: getAppImg("information.png")
			paragraph xkMembers, image: getAppImg("family.png")
		}
		section("Games:") {
			paragraph xkGameInfo, image: getAppImg("information.png")
			paragraph "Halo 2 For Windows Vista - RIP late 2015", image: getAppImg("halo2.png")
			paragraph "Battlefield 3", image: getAppImg("battlefield3.png")
			paragraph "Garrys Mod", image: getAppImg("garrysmod.png")
			paragraph "Portal 2", image: getAppImg("portal2.png")
			paragraph "Dead Speace 3", image: getAppImg("deadspace3.png")
			paragraph "Clash of Clans - Clan Tag: #YYCLJ2YR", image: getAppImg("clashofclans.png")
			paragraph "Halo The Master Chief Collection", image: getAppImg("halomcc.png")
			paragraph "Clash Royale - Clan Tag: #209G8L9", image: getAppImg("clashroyale.png")
			paragraph "Saints Row 3", image: getAppImg("saintsrow3.png")
			paragraph "Boom Beach - Clan Tag: #92V92QCC", image: getAppImg("boombeach.png")
			paragraph "Call of Duty Black Ops 2", image: getAppImg("callofdutyblackops2.png")
			paragraph "Halo 5", image: getAppImg("halo5.png")
			paragraph "Vainglory", image: getAppImg("vainglory.png")
			paragraph "Minecraft Bedrock Edition", image: getAppImg("minecraft.png")
		}
		section("Easter Eggs:") {
			href url: linkYoutubeEE1(), style:"external", required: false, title:"Youtube Link #1", description:"Tap to open in browser", state: "complete", image: getAppImg("youtube.png")
			href url: linkYoutubeEE2(), style:"external", required: false, title:"Youtube Link #2", description:"Tap to open in browser", state: "complete", image: getAppImg("youtube.png")
			href url: linkYoutubeEE3(), style:"external", required: false, title:"Youtube Link #3", description:"Tap to open in browser", state: "complete", image: getAppImg("youtube.png")
		}
		section("Contact:") {
			href url: linkDiscord(), style:"external", required: false, title:"Discord", description:"Tap to open in browser", state: "complete", image: getAppImg("discord.png")
			href url: linkWaypoint(), style:"external", required: false, title:"Halo Waypoint", description:"Tap to open in browser", state: "complete", image: getAppImg("waypoint.ico")
			href url: linkXbox(), style:"external", required: false, title:"Xbox", description:"Tap to open in browser", state: "complete", image: getAppImg("xbox.png")
			href url: linkSteam(), style:"external", required: false, title:"Steam", description:"Tap to open in browser", state: "complete", image: getAppImg("steam.png")
			href url: linkFacebook(), style:"external", required: false, title:"Facebook", description:"Tap to open in browser", state: "complete", image: getAppImg("facebook.png")
		}
	}
}

//	----- ABOUT PAGE -----
def aboutPage() {
	dynamicPage(name: "aboutPage", title: "About Page", install: false, uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png", true)
		}
		section("Donations:") {
			href url: textDonateLinkAntR(), style:"external", required: false, title:"Donations (@ramiran2)", description:"Tap to open in browser", state: "complete", image: getAppImg("paypal.png")
		}
		section("Credits:") {
			paragraph title: "Creator:", "Dave G. (@DaveGut)", state: "complete"
			paragraph title: "Co-Author:", "Anthony R. (@ramiran2)", state: "complete"
			paragraph title: "Collaborator:", "Anthony S. (@tonesto7)", state: "complete"
		}
		section("Application Changes Details:") {
			href "changeLogPage", title: "View App Revision History", description: "Tap to view", image: getAppImg("changelogpage.png")
		}
		section("GitHub:") {
			href url: linkGitHubDavG(), style:"external", required: false, title:"Dave G. (@DaveGut)", description:"Tap to open in browser", state: "complete", image: getAppImg("github.png")
			href url: linkGitHubAntR(), style:"external", required: false, title:"Anthony R. (@ramiran2)", description:"Tap to open in browser", state: "complete", image: getAppImg("github.png")
			href url: linkGitHubAntS(), style:"external", required: false, title:"Anthony S. (@tonesto7)", description:"Tap to open in browser", state: "complete", image: getAppImg("github.png")
		}
		section("Licensing Information:") {
			paragraph "${textCopyright()}\n${textLicense()}"
		}
	}
}

//	----- CHANGELOG PAGE -----
def changeLogPage () {
	dynamicPage(name: "changeLogPage", title: "Changelog Page", install: false, uninstall: false) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Changelog:") {
			paragraph title: "What's New in this Release...", "", state: "complete", image: getAppImg("new.png")
			paragraph appVerInfo()
		}
	}
}

//	----- UNINSTALL PAGE -----
def uninstallPage() {
	def uninstallPageText = "This will uninstall the App, All Child Devices.\nPlease make sure that any devices created by this app are removed from any routines/rules/smartapps before tapping Remove."
	dynamicPage(name: "uninstallPage", title: "Uninstall Page", install: false, uninstall: true) {
		section("") {
			paragraph appInfoDesc(), image: getAppImg("kasa.png")
		}
		section("Information:") {
			paragraph title: "", uninstallPageText, image: getAppImg("information.png")
		}
		remove("Remove ${appLabel()} and Devices!", "WARNING!!!", "Last Chance to Stop!\nThis action is not reversible\n\nThis App, All Devices, and Automations will be removed")
	}
}

void settingUpdate(name, value, type=null) {
	log.trace "settingUpdate($name, $value, $type)..."
	if(name) {
		if(value == "" || value == null || value == []) {
			settingRemove(name)
			return
		}
	}
	if(name && type) {
		app?.updateSetting("$name", [type: "$type", value: value])
	}
	else if (name && type == null){ app?.updateSetting(name.toString(), value) }
}

void settingRemove(name) {
	log.trace "settingRemove($name)..."
	if(name) { app?.deleteSetting("$name") }
}

def getDevices() {
	def currentDevices = getDeviceData()
	state.devices = [:]
	def devices = state.devices
	currentDevices.each {
		def device = [:]
		device["deviceMac"] = it.deviceMac
		device["alias"] = it.alias
		device["deviceModel"] = it.deviceModel
		device["deviceId"] = it.deviceId
		device["appServerUrl"] = it.appServerUrl
		devices << ["${it.deviceMac}": device]
		def isChild = getChildDevice(it.deviceMac)
		if (isChild) {
			isChild.syncAppServerUrl(it.appServerUrl)
		}
		log.info "Device ${it.alias} added to devices array"
	}
}

def removeDevices() {
	userSelectedDevicesRemove.each { dni ->
		try{
			def isChild = getChildDevice(dni)
			if (isChild) {
				def delete = isChild
				delete.each { deleteChildDevice(it.deviceNetworkId, true) }
			}
		} catch (e) {
			log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
		}
	}
}

def getWebData(params, desc, text=true) {
	try {
		log.info "getWebData: ${desc} data"
		httpGet(params) { resp ->
			if(resp.data) {
				if(text) {
					return resp?.data?.text.toString()
				} else { return resp?.data }
			}
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {
			log.warn "${desc} file not found"
		} else {
			log.error "getWebData(params: $params, desc: $desc, text: $text) Exception:", ex
		}
		return "${label} info not found"
	}
}

def addDevices() {
	def tpLinkModel = [:]
	//	Plug-Switch Devices (no energy monitor capability)
	tpLinkModel << ["HS100" : "TP-Link Smart Plug - Kasa Account"]						//	HS100
	tpLinkModel << ["HS103" : "TP-Link Smart Plug - Kasa Account"]						//	HS103
	tpLinkModel << ["HS105" : "TP-Link Smart Plug - Kasa Account"]						//	HS105
	tpLinkModel << ["HS200" : "TP-Link Smart Switch - Kasa Account"]					//	HS200
	tpLinkModel << ["HS210" : "TP-Link Smart Switch - Kasa Account"]					//	HS210
	tpLinkModel << ["KP100" : "TP-Link Smart Plug - Kasa Account"]						//	KP100
	//	Dimming Switch Devices
	tpLinkModel << ["HS220" : "TP-Link Smart Dimming Switch - Kasa Account"]			//	HS220
	//	Energy Monitor Plugs
	tpLinkModel << ["HS110" : "TP-Link Smart Energy Monitor Plug - Kasa Account"]		//	HS110
	tpLinkModel << ["HS115" : "TP-Link Smart Energy Monitor Plug - Kasa Account"]		//	HS110
	//	Soft White Bulbs
	tpLinkModel << ["KB100" : "TP-Link Smart Soft White Bulb - Kasa Account"]			//	KB100
	tpLinkModel << ["LB100" : "TP-Link Smart Soft White Bulb - Kasa Account"]			//	LB100
	tpLinkModel << ["LB110" : "TP-Link Smart Soft White Bulb - Kasa Account"]			//	LB110
	tpLinkModel << ["KL110" : "TP-Link Smart Soft White Bulb - Kasa Account"]			//	KL110
	tpLinkModel << ["LB200" : "TP-Link Smart Soft White Bulb - Kasa Account"]			//	LB200
	//	Tunable White Bulbs
	tpLinkModel << ["LB120" : "TP-Link Smart Tunable White Bulb - Kasa Account"]		//	LB120
	tpLinkModel << ["KL120" : "TP-Link Smart Tunable White Bulb - Kasa Account"]		//	KL120
	//	Color Bulbs
	tpLinkModel << ["KB130" : "TP-Link Smart Color Bulb - Kasa Account"]				//	KB130
	tpLinkModel << ["LB130" : "TP-Link Smart Color Bulb - Kasa Account"]				//	LB130
	tpLinkModel << ["KL130" : "TP-Link Smart Color Bulb - Kasa Account"]				//	KL130
	tpLinkModel << ["LB230" : "TP-Link Smart Color Bulb - Kasa Account"]				//	LB230

	def hub = location.hubs[0]
	def hubId = hub.id
	userSelectedDevicesAdd.each { dni ->
		try {
			def isChild = getChildDevice(dni)
			if (!isChild) {
				def device = state.devices.find { it.value.deviceMac == dni }
				def deviceModel = device.value.deviceModel.substring(0,5)
				addChildDevice(
					"ramiran2",
					tpLinkModel["${deviceModel}"],
					device.value.deviceMac,
					hubId, [
						"label": device.value.alias,
							"name": device.value.deviceModel,
						"data": [
							"deviceId" : device.value.deviceId,
							"appServerUrl": device.value.appServerUrl,
						]
					]
				)
				log.info "Installed TP-Link $deviceModel with alias ${device.value.alias}"
			}
		} catch (e) {
			log.debug "Error Adding ${deviceModel}: ${e}"
		}
	}
}

//	----- GET A NEW TOKEN FROM CLOUD -----
def getToken() {
	def hub = location.hubs[0]
	def cmdBody = [
		method: "login",
		params: [
			appType: "Kasa_Android",
			cloudUserName: "${userName}",
			cloudPassword: "${userPassword}",
			terminalUUID: "${hub.id}"
		]
	]
	def getTokenParams = [
		uri: "https://wap.tplinkcloud.com",
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	httpPostJson(getTokenParams) {resp ->
		if (resp.status == 200 && resp.data.error_code == 0) {
			state.TpLinkToken = resp.data.result.token
			log.info "TpLinkToken updated to ${state.TpLinkToken}"
			sendEvent(name: "TokenUpdate", value: "tokenUpdate Successful.")
			if (state.currentError != null) {
				state.currentError = null
			}
		} else if (resp.status != 200) {
			state.currentError = resp.statusLine
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in getToken: ${state.currentError}"
			sendEvent(name: "TokenUpdate", value: state.currentError)
		} else if (resp.data.error_code != 0) {
			state.currentError = resp.data
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in getToken: ${state.currentError}"
			sendEvent(name: "TokenUpdate", value: state.currentError)
		}
	}
}

//	----- GET DEVICE DATA FROM THE CLOUD -----
def getDeviceData() {
	def currentDevices = ""
	def cmdBody = [method: "getDeviceList"]
	def getDevicesParams = [
		uri: "https://wap.tplinkcloud.com?token=${state.TpLinkToken}",
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	httpPostJson(getDevicesParams) {resp ->
		if (resp.status == 200 && resp.data.error_code == 0) {
			currentDevices = resp.data.result.deviceList
			if (state.currentError != null) {
				state.currentError = null
			}
			return currentDevices
		} else if (resp.status != 200) {
			state.currentError = resp.statusLine
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in getDeviceData: ${state.currentError}"
		} else if (resp.data.error_code != 0) {
			state.currentError = resp.data
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in getDeviceData: ${state.currentError}"
		}
	}
}

//	----- SEND DEVICE COMMAND TO CLOUD FOR DH -----
def sendDeviceCmd(appServerUrl, deviceId, command) {
	def cmdResponse = ""
	def cmdBody = [
		method: "passthrough",
		params: [
			deviceId: deviceId,
			requestData: "${command}"
		]
	]
	def sendCmdParams = [
		uri: "${appServerUrl}/?token=${state.TpLinkToken}",
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	httpPostJson(sendCmdParams) {resp ->
		if (resp.status == 200 && resp.data.error_code == 0) {
			def jsonSlurper = new groovy.json.JsonSlurper()
			cmdResponse = jsonSlurper.parseText(resp.data.result.responseData)
			if (state.errorCount != 0) {
				state.errorCount = 0
			}
			if (state.currentError != null) {
				state.currentError = null
				sendEvent(name: "currentError", value: null)
			log.debug "state.errorCount = ${state.errorCount} //	state.currentError = ${state.currentError}"
			}
//			log.debug "state.errorCount = ${state.errorCount} //	state.currentError = ${state.currentError}"
		} else if (resp.status != 200) {
			state.currentError = resp.statusLine
			cmdResponse = "ERROR: ${resp.statusLine}"
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in sendDeviceCmd: ${state.currentError}"
		} else if (resp.data.error_code != 0) {
			state.currentError = resp.data
			cmdResponse = "ERROR: ${resp.data.msg}"
			sendEvent(name: "currentError", value: resp.data)
			log.error "Error in sendDeviceCmd: ${state.currentError}"
		}
	}
	return cmdResponse
}


def uninstManagerApp() {
	try {
		//Revokes Smartthings endpoint token
		revokeAccessToken()
		//Revokes TP-Link Auth Token
		state.TpLinkToken = null
	} catch (ex) {
		log.error "uninstManagerApp Exception:", ex
	}
}

//	----- INSTALL, UPDATE, INITIALIZE, UNINSTALLED -----
def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	unsubscribe()
	unschedule()
	runEvery5Minutes(checkError)
	schedule("0 30 2 ? * WED", getToken)
	if (userSelectedDevicesAdd || userSelectedDevicesRemove) {
		if (userSelectedRemoveMode){
			removeDevices()
		} else {
			addDevices()
		}
	}
}

def uninstalled() {
	uninstManagerApp()
}

//	----- PERIODIC CLOUD MX TASKS -----
def checkError() {
	if (state.currentError == null || state.currentError == "none") {
		log.info "TP-Link Connect did not have any set errors."
		if (state.currentError == "none") {
			state.currentError = null
		}
		return
	}
	def errMsg = state.currentError.msg
	log.info "Attempting to solve error: ${errMsg}"
	state.errorCount = state.errorCount +1
	if (errMsg == "Token expired" && state.errorCount < 6) {
		sendEvent (name: "ErrHandling", value: "Handle comms error attempt ${state.errorCount}")
		getDevices()
		if (state.currentError == null) {
			log.info "getDevices successful. apiServerUrl updated and token is good."
			return
		}
		log.error "${errMsg} error while attempting getDevices. Will attempt getToken"
		getToken()
		if (state.currentError == null) {
			log.info "getToken successful. Token has been updated."
			getDevices()
			return
		}
	} else {
		log.error "checkError: No auto-correctable errors or exceeded Token request count."
	}
	log.error "checkError residual: ${state.currentError}"
}

//	----- CHILD CALLED TASKS -----
def removeChildDevice(alias, deviceNetworkId) {
	try {
		deleteChildDevice(it.deviceNetworkId)
		sendEvent(name: "DeviceDelete", value: "${alias} deleted")
	} catch (Exception e) {
		sendEvent(name: "DeviceDelete", value: "Failed to delete ${alias}")
	}
}

def gitBranch() { return betaMarker() ? "beta" : "master"  }
def getAppImg(imgName, on = null)	{ return (!appIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/images/$imgName" : "" }
def getWikiPageUrl() { return "https://github.com/${gitRepo()}/wiki" }
def getIssuePageUrl() { return "https://github.com/${gitRepo()}/issues" }
def appLabel() { return "TP-Link Device Manager" }
def appNamespace() { return "ramiran2" }
def gitRepo()		{ return "ramiran2/TP-Link-SmartThings"}
def gitPath()		{ return "${gitRepo()}/${gitBranch()}"}
def betaMarker() { return false }
def sendingDataSuccess()	{ return "Data Sent to All Devices" }
def sendingDataFailed()	{ return "Ready to Send Data to All Devices" }
def tokenInfoOnline()	{ return "Online and Ready to Control Devices" }
def tokenInfoOffline()	{ return "Offline, Please Fix to Restore Control on Devices" }
def pageSelectorText()	{ return "Please tap below to continue" }
def pageSelectorNullText()	{ return "Please select a option to continue" }
def pageSelectorErrorText()	{ return "Please continue with caution, we have detected a error" }
def appInfoDesc()	{
	def str = ""
	str += "${appLabel()}"
	str += "\n" + "• ${textVersion()}"
	str += "\n" + "• ${textModified()}"
	return str
}
def appAuthor() { return "Dave Gutheinz, Anthony Ramirez" }
def textVersion()	{ return "Version: ${appVersion()}" }
def textModified()	{ return "Updated: ${appVerDate()}" }
def textVerInfo()	{ return "${appVerInfo()}" }
def appVerInfo()	{ return getWebData([uri: "https://raw.githubusercontent.com/${gitPath()}/data/changelog.txt", contentType: "text/plain; charset=UTF-8"], "changelog") }
def textLicense()	{ return getWebData([uri: "https://raw.githubusercontent.com/${gitPath()}/data/license.txt", contentType: "text/plain; charset=UTF-8"], "license") }
def textDonateLinkAntR(){ return "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=S2CJBWCJEGVJA" }
def linkGitHubDavG(){ return "https://github.com/DaveGut/SmartThings_Cloud-Based_TP-Link-Plugs-Switches-Bulbs" }
def linkGitHubAntR(){ return "https://github.com/${gitRepo()}/" }
def linkGitHubAntS(){ return "https://github.com/tonesto7/nest-manager" }
def linkYoutubeEE1(){ return "https://www.youtube.com/watch?v=87JPlNk5ves&list=PL0S-Da7zGmE9PRn_YIitvUZEHYQglJw" }
def linkYoutubeEE2(){ return "https://www.youtube.com/watch?v=0eYTZrucx_o" }
def linkYoutubeEE3(){ return "https://www.youtube.com/watch?v=4_5kpOeiZyg&index=3&list=PL0S-Da7zGmE-i5MQdHORm6a" }
def linkDiscord(){ return "https://discord.gg/JDXeV23" }
def linkXbox(){ return "https://account.xbox.com/en-us/clubs/profile?clubid=3379843591790358" }
def linkWaypoint(){ return "https://www.halowaypoint.com/en-us/spartan-companies/xkiller%20clan" }
def linkSteam(){ return "https://steamcommunity.com/groups/xKillerClan" }
def linkFacebook(){ return "https://www.facebook.com/groups/xKillerClan/" }
def textCopyright()	{ return "Copyright© 2018 - Dave Gutheinz, Anthony Ramirez" }
def textDesc() { return "A Service Manager for the TP-Link Kasa Devices connecting through the TP-Link Servers to SmartThings." }