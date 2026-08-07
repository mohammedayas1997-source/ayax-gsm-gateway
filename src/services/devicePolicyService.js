import { NativeModules } from "react-native";

const { DevicePolicyModule } = NativeModules;

const requireModule = () => {
  if (!DevicePolicyModule) {
    throw new Error("DevicePolicyModule is not linked or available in NativeModules");
  }

  return DevicePolicyModule;
};

export const isAdminEnabled = async () => {
  try {
    const module = requireModule();
    if (typeof module.isAdminEnabled !== "function") {
      throw new Error("isAdminEnabled method is not available");
    }
    return await module.isAdminEnabled();
  } catch (error) {
    console.log("Error checking admin status:", error.message);
    throw error;
  }
};

export const isDeviceOwner = async () => {
  try {
    const module = requireModule();
    if (typeof module.isDeviceOwner !== "function") {
      throw new Error("isDeviceOwner method is not available");
    }
    return await module.isDeviceOwner();
  } catch (error) {
    console.log("Error checking device owner status:", error.message);
    throw error;
  }
};

export const applyOwnerPolicies = async () => {
  try {
    const module = requireModule();
    if (typeof module.applyOwnerPolicies !== "function") {
      throw new Error("applyOwnerPolicies method is not available");
    }
    return await module.applyOwnerPolicies();
  } catch (error) {
    console.log("Error applying owner policies:", error.message);
    throw error;
  }
};

export const lockDevice = async () => {
  try {
    const module = requireModule();
    if (typeof module.lockDevice !== "function") {
      throw new Error("lockDevice method is not available");
    }
    return await module.lockDevice();
  } catch (error) {
    console.log("Error locking device:", error.message);
    throw error;
  }
};

export const allowAppRemoval = async () => {
  try {
    const module = requireModule();
    if (typeof module.allowAppRemoval !== "function") {
      throw new Error("allowAppRemoval method is not available");
    }
    return await module.allowAppRemoval();
  } catch (error) {
    console.log("Error allowing app removal:", error.message);
    throw error;
  }
};