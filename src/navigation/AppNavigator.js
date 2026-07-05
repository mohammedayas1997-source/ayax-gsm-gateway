import React, { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";

import PairDeviceScreen from "../screens/PairDeviceScreen";
import DashboardScreen from "../screens/DashboardScreen";
import { getDeviceToken } from "../storage/deviceStorage";

const Stack = createNativeStackNavigator();

export default function AppNavigator() {
  const [loading, setLoading] = useState(true);
  const [paired, setPaired] = useState(false);

  useEffect(() => {
    const checkDevice = async () => {
      const token = await getDeviceToken();
      setPaired(!!token);
      setLoading(false);
    };

    checkDevice();
  }, []);

  if (loading) {
    return (
      <View
        style={{
          flex: 1,
          backgroundColor: "#08111f",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <ActivityIndicator size="large" color="#1565ff" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {paired ? (
          <Stack.Screen name="Dashboard" component={DashboardScreen} />
        ) : (
          <Stack.Screen name="Pair" component={PairDeviceScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}