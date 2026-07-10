import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ScrollView,
} from "react-native";

import { sendHeartbeat } from "../services/heartbeatService";
import { clearDevice } from "../storage/deviceStorage";
import { getSimInfo } from "../services/gsmService";
import {
  connectGatewaySocket,
  disconnectGatewaySocket,
} from "../socket/gatewaySocket";
import { syncSimsToBackend } from "../services/simSyncService";
import { syncLocationToBackend } from "../services/locationService";
import { subscribeQueueStatus } from "../services/queueService";
import { subscribeLogs } from "../services/logService";
import { startMotionSecurity } from "../services/deviceManagerService";

export default function DashboardScreen({ navigation }) {
  const [status, setStatus] = useState("Connecting...");
  const [battery, setBattery] = useState(0);
  const [simInfo, setSimInfo] = useState(null);

  const [queueStatus, setQueueStatus] = useState({
    processing: false,
    pending: 0,
  });

  const [logs, setLogs] = useState([]);

  const heartbeat = async () => {
    try {
      const res = await sendHeartbeat();
      setStatus("ONLINE");
      setBattery(res?.device?.battery || 0);
    } catch (error) {
      setStatus("OFFLINE");
    }
  };

  const loadSimInfo = async () => {
    try {
      const info = await getSimInfo();
      setSimInfo(info);
    } catch (error) {
      Alert.alert("SIM Error", error.message);
    }
  };

  const syncGateway = async () => {
    await heartbeat();
    await loadSimInfo();
    await syncSimsToBackend();
    await syncLocationToBackend();
  };

  useEffect(() => {
    syncGateway();
    connectGatewaySocket();
    startMotionSecurity().catch((error) => {
  console.log("Motion security error:", error.message);
});
    

    const unsubscribeQueue = subscribeQueueStatus(setQueueStatus);
    const unsubscribeLogs = subscribeLogs(setLogs);

    const timer = setInterval(() => {
      syncGateway();
    }, 30000);

    return () => {
      clearInterval(timer);
      unsubscribeQueue();
      unsubscribeLogs();
      disconnectGatewaySocket();
    };
  }, []);

  const logout = async () => {
    disconnectGatewaySocket();
    await clearDevice();
    Alert.alert("Device cleared", "Pair this device again.");
    navigation.replace("Pair");
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Ayax GSM Gateway</Text>
      <Text style={styles.subtitle}>Android Device Gateway Engine</Text>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Gateway Status</Text>

        <Text
          style={[
            styles.status,
            status === "ONLINE" ? styles.online : styles.offline,
          ]}
        >
          {status}
        </Text>

        <Text style={styles.info}>Battery: {battery}%</Text>
        <Text style={styles.info}>SIM Count: {simInfo?.simCount || 0}</Text>
        <Text style={styles.info}>
          Queue: {queueStatus.processing ? "Processing" : "Idle"}
        </Text>
        <Text style={styles.info}>
          Pending Commands: {queueStatus.pending}
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>SIM Cards</Text>

        {simInfo?.sims?.length > 0 ? (
          simInfo.sims.map((sim, index) => (
            <View key={index} style={styles.simCard}>
              <Text style={styles.simTitle}>SIM {sim.slotIndex + 1}</Text>
              <Text style={styles.simText}>
                Carrier: {sim.carrierName || "Unknown"}
              </Text>
              <Text style={styles.simText}>
                Display: {sim.displayName || "Unknown"}
              </Text>
              <Text style={styles.simText}>
                Number: {sim.number || "Hidden by Android"}
              </Text>
              <Text style={styles.simText}>
                MCC/MNC: {sim.mcc}/{sim.mnc}
              </Text>
            </View>
          ))
        ) : (
          <Text style={styles.empty}>No active SIM detected.</Text>
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Recent Command Logs</Text>

        {logs.length === 0 ? (
          <Text style={styles.empty}>No command received yet.</Text>
        ) : (
          logs.slice(0, 10).map((log) => (
            <View key={log.id} style={styles.logCard}>
              <Text style={styles.logTitle}>
                {log.type} • {log.status}
              </Text>
              <Text style={styles.simText}>{log.reference}</Text>
              <Text style={styles.simText}>{log.message}</Text>
              <Text style={styles.logTime}>{log.time}</Text>
            </View>
          ))
        )}
      </View>

      <TouchableOpacity style={styles.button} onPress={syncGateway}>
        <Text style={styles.btnText}>Sync Gateway</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.buttonDark} onPress={loadSimInfo}>
        <Text style={styles.btnText}>Refresh SIM Info</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.logout} onPress={logout}>
        <Text style={styles.btnText}>Clear Pairing</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#08111f",
  },
  content: {
    padding: 24,
    paddingTop: 60,
  },
  title: {
    color: "#fff",
    fontSize: 30,
    fontWeight: "bold",
    textAlign: "center",
  },
  subtitle: {
    color: "#9ca3af",
    textAlign: "center",
    marginTop: 8,
    marginBottom: 30,
    fontSize: 15,
  },
  card: {
    backgroundColor: "#111827",
    borderColor: "#1f2937",
    borderWidth: 1,
    borderRadius: 20,
    padding: 20,
    marginBottom: 18,
  },
  cardTitle: {
    color: "#fff",
    fontSize: 20,
    fontWeight: "bold",
    marginBottom: 15,
  },
  status: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 10,
  },
  online: {
    color: "#22c55e",
  },
  offline: {
    color: "#ef4444",
  },
  info: {
    color: "#d1d5db",
    fontSize: 16,
    marginTop: 6,
  },
  simCard: {
    backgroundColor: "#020617",
    borderColor: "#1f2937",
    borderWidth: 1,
    borderRadius: 16,
    padding: 15,
    marginBottom: 12,
  },
  simTitle: {
    color: "#60a5fa",
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 8,
  },
  simText: {
    color: "#d1d5db",
    fontSize: 14,
    marginTop: 4,
  },
  empty: {
    color: "#9ca3af",
  },
  logCard: {
    backgroundColor: "#020617",
    borderColor: "#1f2937",
    borderWidth: 1,
    borderRadius: 16,
    padding: 15,
    marginBottom: 12,
  },
  logTitle: {
    color: "#60a5fa",
    fontSize: 15,
    fontWeight: "bold",
  },
  logTime: {
    color: "#6b7280",
    fontSize: 12,
    marginTop: 6,
  },
  button: {
    backgroundColor: "#1565ff",
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: "center",
    marginBottom: 12,
  },
  buttonDark: {
    backgroundColor: "#1f2937",
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: "center",
    marginBottom: 12,
  },
  logout: {
    backgroundColor: "#dc2626",
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: "center",
    marginBottom: 40,
  },
  btnText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 17,
  },
});