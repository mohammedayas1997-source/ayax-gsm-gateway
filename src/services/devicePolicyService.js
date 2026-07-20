import { NativeModules } from "react-native";

const { DevicePolicyModule } = NativeModules;

const requireModule = () => {
  if (!DevicePolicyModule) {
    throw new Error("DevicePolicyModule not linked");
  }

  return DevicePolicyModule;
};

export const isAdminEnabled = async () => {
  return requireModule().isAdminEnabled();
};

export const isDeviceOwner = async () => {
  return requireModule().isDeviceOwner();
};

export const applyOwnerPolicies = async () => {
  return requireModule().applyOwnerPolicies();
};

export const lockDevice = async () => {
  return requireModule().lockDevice();
};

export const allowAppRemoval = async () => {
  return requireModule().allowAppRemoval();
};