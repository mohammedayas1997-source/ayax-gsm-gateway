import { NativeModules } from "react-native";

const { DeviceManagerModule } = NativeModules;

const checkModule = () => {
  if (!DeviceManagerModule) {
    throw new Error("DeviceManagerModule is not linked or available in NativeModules");
  }
};

export const startAlarm = async () => {
  try {
    checkModule();
    if (typeof DeviceManagerModule.startAlarm !== "function") {
      throw new Error("startAlarm method is not available on DeviceManagerModule");
    }
    return await DeviceManagerModule.startAlarm();
  } catch (error) {
    console.log("Error starting alarm via native module:", error.message);
    throw error;
  }
};

export const stopAlarm = async () => {
  try {
    checkModule();
    if (typeof DeviceManagerModule.stopAlarm !== "function") {
      throw new Error("stopAlarm method is not available on DeviceManagerModule");
    }
    return await DeviceManagerModule.stopAlarm();
  } catch (error) {
    console.log("Error stopping alarm via native module:", error.message);
    throw error;
  }
};

export const startMotionSecurity = async () => {
  try {
    checkModule();
    if (typeof DeviceManagerModule.startMotionSecurity !== "function") {
      throw new Error("startMotionSecurity method is not available on DeviceManagerModule");
    }
    return await DeviceManagerModule.startMotionSecurity();
  } catch (error) {
    console.log("Error starting motion security via native module:", error.message);
    throw error;
  }
};