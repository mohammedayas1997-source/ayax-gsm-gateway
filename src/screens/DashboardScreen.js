import React, { useEffect, useState, useRef } from "react";
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
  const isMounted = useRef(true);

  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
    };
  }, []);

  const heartbeat = async () => {
    try {
      const res = await sendHeartbeat();
      if (!isMounted.current) return;
      setStatus("ONLINE");
      setBattery(res?.device?.battery || 0);
    } catch (error) {
      if (!isMounted.current) return;
      setStatus("OFFLINE");
    }
  };

  const loadSimInfo = async () => {
    try {
      const info = await getSimInfo();
      if (!isMounted.current) return;
      setSimInfo(info);
    } catch (error) {
      if (!isMounted.current) return;
      console.log("SIM Error:", error?.message);
    }
  };

  const syncGateway = async () => {
    try {
      await heartbeat();
      await loadSimInfo();
      await syncSimsToBackend();
      await syncLocationToBackend();
    } catch (error) {
      console.log("Sync Gateway error:", error?.message);
    }
  };

  useEffect(() => {
    syncGateway();
    connectGatewaySocket();

    startMotionSecurity().catch((error) => {
      console.log("Motion security error:", error?.message);
    });

    const unsubscribeQueue = subscribeQueueStatus((status) => {
      if (isMounted.current) setQueueStatus(status);
    });

    const unsubscribeLogs = subscribeLogs((newLogs) => {
      if (isMounted.current) setLogs(newLogs);
    });

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
    try {
      disconnectGatewaySocket();
      await clearDevice();
      Alert.alert("Device cleared", "Pair this device again.");
      navigation.replace("Pair");
    } catch (error) {
      console.log("Logout error:", error?.message);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* COMPACT HEADER */}
      <View style={styles.headerContainer}>
        <Text style={styles.title}>Ayax GSM Gateway</Text>
        <Text style={styles.subtitle}>Android Device Gateway Engine</Text>
      </View>

      {/* COMPACT STATUS CARD */}
      <View style={styles.card}>
        <View style={styles.cardHeaderRow}>
          <Text style={styles.cardTitle}>Gateway Status</Text>
          <View style={[styles.statusBadge, status === "ONLINE" ? styles.badgeOnline : styles.badgeOffline]}>
            <Text style={[styles.statusText, status === "ONLINE" ? styles.online : styles.offline]}>
              {status}
            </Text>
          </View>
        </View>

        <View style={styles.statusGrid}>
          <Text style={styles.info}>Battery: <Text style={styles.infoValue}>{battery}%</Text></Text>
          <Text style={styles.info}>SIMs: <Text style={styles.infoValue}>{simInfo?.simCount || 0}</Text></Text>
          <Text style={styles.info}>Queue: <Text style={styles.infoValue}>{queueStatus.processing ? "Processing" : "Idle"}</Text></Text>
          <Text style={styles.info}>Pending: <Text style={styles.infoValue}>{queueStatus.pending}</Text></Text>
        </View>
      </View>

      {/* COMPACT SIM CARDS */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>SIM Cards</Text>

        {simInfo?.sims?.length > 0 ? (
          simInfo.sims.map((sim, index) => (
            <View key={index} style={styles.simCard}>
              <View style={styles.simHeaderRow}>
                <Text style={styles.simTitle}>SIM {(sim.slotIndex ?? index) + 1}</Text>
                <Text style={styles.carrierTag}>{sim.carrierName || "Unknown"}</Text>
              </View>
              <Text style={styles.simText}>Display: {sim.displayName || "Unknown"}</Text>
              <Text style={styles.simText}>Number: {sim.number || sim.phoneNumber || "Hidden by Android"}</Text>
              <Text style={styles.simTextSub}>MCC/MNC: {sim.mcc}/{sim.mnc}</Text>
            </View>
          ))
        ) : (
          <Text style={styles.empty}>No active SIM detected.</Text>
        )}
      </View>

      {/* COMPACT TERMINAL LOGS */}
      <View style={styles.card}>
        <View style={styles.cardHeaderRow}>
          <Text style={styles.cardTitle}>Recent Command Logs</Text>
          <Text style={styles.logCountBadge}>{logs.length} logged</Text>
        </View>

        {logs.length === 0 ? (
          <Text style={styles.empty}>No command received yet.</Text>
        ) : (
          logs.slice(0, 20).map((log, index) => {
            const isSuccess =
              String(log.type || "").includes("SUCCESSFUL") ||
              String(log.status || "").includes("SUCCESSFUL");

            return (
              <View key={log.id || index} style={styles.logRow}>
                <View style={styles.logTopLine}>
                  <Text style={[styles.logTypeTag, isSuccess ? styles.textSuccess : styles.textInfo]}>
                    {log.type || "COMMAND"} • {log.status || "OK"}
                  </Text>
                  <Text style={styles.logTimeText}>{log.time || "Just now"}</Text>
                </View>
                <Text style={styles.logRefText} numberOfLines={1}>{log.reference || "-"}</Text>
                <Text style={styles.logMessageText} numberOfLines={2}>{log.message || "Command executed"}</Text>
              </View>
            );
          })
        )}
      </View>

      {/* ACTION BUTTONS */}
      <View style={styles.actionRow}>
        <TouchableOpacity style={[styles.button, styles.btnHalf]} onPress={syncGateway}>
          <Text style={styles.btnText}>Sync Gateway</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.buttonDark, styles.btnHalf]} onPress={loadSimInfo}>
          <Text style={styles.btnText}>Refresh SIMs</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.logout} onPress={logout}>
        <Text style={styles.btnText}>Clear Pairing</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#060d17",
  },
  content: {
    paddingHorizontal: 14,
    paddingTop: 40,
    paddingBottom: 30,
  },
  headerContainer: {
    marginBottom: 16,
    alignItems: "center",
  },
  title: {
    color: "#fff",
    fontSize: 22,
    fontWeight: "bold",
    textAlign: "center",
  },
  subtitle: {
    color: "#94a3b8",
    textAlign: "center",
    marginTop: 2,
    fontSize: 12,
  },
  card: {
    backgroundColor: "#0f172a",
    borderColor: "#1e293b",
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    marginBottom: 12,
  },
  cardHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 10,
  },
  cardTitle: {
    color: "#f8fafc",
    fontSize: 15,
    fontWeight: "bold",
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  badgeOnline: {
    backgroundColor: "rgba(34, 197, 94, 0.15)",
  },
  badgeOffline: {
    backgroundColor: "rgba(239, 68, 68, 0.15)",
  },
  statusText: {
    fontSize: 12,
    fontWeight: "800",
  },
  online: {
    color: "#22c55e",
  },
  offline: {
    color: "#ef4444",
  },
  statusGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    backgroundColor: "#020617",
    padding: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#1e293b",
  },
  info: {
    color: "#94a3b8",
    fontSize: 12,
    width: "48%",
    marginBottom: 4,
  },
  infoValue: {
    color: "#f1f5f9",
    fontWeight: "bold",
  },
  simCard: {
    backgroundColor: "#020617",
    borderColor: "#1e293b",
    borderWidth: 1,
    borderRadius: 10,
    padding: 10,
    marginBottom: 8,
  },
  simHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  simTitle: {
    color: "#38bdf8",
    fontSize: 14,
    fontWeight: "bold",
  },
  carrierTag: {
    color: "#cbd5e1",
    fontSize: 11,
    backgroundColor: "#1e293b",
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    fontWeight: "600",
  },
  simText: {
    color: "#cbd5e1",
    fontSize: 12,
    marginTop: 2,
  },
  simTextSub: {
    color: "#64748b",
    fontSize: 11,
    marginTop: 2,
  },
  logCountBadge: {
    color: "#64748b",
    fontSize: 11,
  },
  logRow: {
    backgroundColor: "#020617",
    borderColor: "#1e293b",
    borderLeftColor: "#38bdf8",
    borderLeftWidth: 3,
    borderWidth: 1,
    borderRadius: 8,
    paddingVertical: 7,
    paddingHorizontal: 10,
    marginBottom: 6,
  },
  logTopLine: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  logTypeTag: {
    fontSize: 11,
    fontWeight: "bold",
    textTransform: "uppercase",
  },
  textSuccess: {
    color: "#4ade80",
  },
  textInfo: {
    color: "#38bdf8",
  },
  logTimeText: {
    color: "#64748b",
    fontSize: 10,
    fontFamily: "monospace",
  },
  logRefText: {
    color: "#cbd5e1",
    fontSize: 11,
    fontFamily: "monospace",
    marginTop: 2,
  },
  logMessageText: {
    color: "#94a3b8",
    fontSize: 11,
    marginTop: 2,
  },
  empty: {
    color: "#64748b",
    fontSize: 12,
    textAlign: "center",
    paddingVertical: 10,
  },
  actionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 4,
    marginBottom: 10,
  },
  btnHalf: {
    width: "48%",
    marginBottom: 0,
  },
  button: {
    backgroundColor: "#2563eb",
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: "center",
  },
  buttonDark: {
    backgroundColor: "#1e293b",
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: "center",
  },
  logout: {
    backgroundColor: "#991b1b",
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: "center",
    marginBottom: 20,
  },
  btnText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 14,
  },
});