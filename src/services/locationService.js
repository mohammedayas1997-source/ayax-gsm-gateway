import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

const { LocationModule } = NativeModules;

export const requestLocationPermissions = async () => {
  if (Platform.OS !== "android") return true;

  const result = await PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
  ]);

  return Object.values(result).every(
    (status) => status === PermissionsAndroid.RESULTS.GRANTED
  );
};

export const getCurrentLocation = async () => {
  const granted = await requestLocationPermissions();

  if (!granted) {
    throw new Error("Location permission denied");
  }

  if (!LocationModule) {
    throw new Error("LocationModule not linked");
  }

  return LocationModule.getCurrentLocation();
};

export const syncLocationToBackend = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) return null;

  const location = await getCurrentLocation();

  const res = await api.post("/gateway/location", {
    deviceId,
    secretKey,
    ...location,
  });

  return res.data;
};