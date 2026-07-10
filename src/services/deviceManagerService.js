import { NativeModules } from "react-native";

const { DeviceManagerModule } = NativeModules;

export const startAlarm = async () => {
  if (!DeviceManagerModule) throw new Error("DeviceManagerModule not linked");
  return DeviceManagerModule.startAlarm();
};

export const stopAlarm = async () => {
  if (!DeviceManagerModule) throw new Error("DeviceManagerModule not linked");
  return DeviceManagerModule.stopAlarm();
};

export const startMotionSecurity = async () => {
  if (!DeviceManagerModule) throw new Error("DeviceManagerModule not linked");

  if (!DeviceManagerModule.startMotionSecurity) {
    throw new Error("startMotionSecurity not available");
  }

  return DeviceManagerModule.startMotionSecurity();
};