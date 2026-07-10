import { NativeModules } from "react-native";

const { DevicePolicyModule } = NativeModules;

export const isAdminEnabled = async () => {
  if (!DevicePolicyModule) {
    throw new Error("DevicePolicyModule not linked");
  }

  return DevicePolicyModule.isAdminEnabled();
};

export const isDeviceOwner = async () => {
  if (!DevicePolicyModule) {
    throw new Error("DevicePolicyModule not linked");
  }

  return DevicePolicyModule.isDeviceOwner();
};

export const lockDevice = async () => {
  if (!DevicePolicyModule) {
    throw new Error("DevicePolicyModule not linked");
  }

  return DevicePolicyModule.lockDevice();
};