import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

const { LocationModule } = NativeModules;

export const requestLocationPermissions = async () => {
  try {
    if (Platform.OS !== "android") return true;

    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
    ]);

    return Object.values(result).every(
      (status) => status === PermissionsAndroid.RESULTS.GRANTED
    );
  } catch (error) {
    console.log("Error requesting location permissions:", error?.message);
    return false;
  }
};

export const getCurrentLocation = async () => {
  try {
    const granted = await requestLocationPermissions();

    if (!granted) {
      throw new Error("Location permission denied");
    }

    if (!LocationModule) {
      throw new Error("LocationModule not linked or available in NativeModules");
    }

    if (typeof LocationModule.getCurrentLocation !== "function") {
      throw new Error("getCurrentLocation method is not available on LocationModule");
    }

    return await LocationModule.getCurrentLocation();
  } catch (error) {
    console.log("Error getting current location:", error?.message);
    throw error;
  }
};

export const syncLocationToBackend = async () => {
  try {
    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) return null;

    const location = await getCurrentLocation();

    if (!location) {
      console.log("Location data returned empty");
      return null;
    }

    const payload = {
      deviceId,
      secretKey,
      ...location,
    };

    const res = await api.post("/gateway/location", payload);

    return res.data;
  } catch (error) {
    console.log(
      "Failed to sync location to backend:",
      error?.response?.data?.message || error?.message || "Unknown error"
    );
    // Ba a fitar da throw error anan ba domin hakan zai hana background service ci gaba da aiki idan GPS ko intanet ta ɗan katse.
    return null;
  }
};