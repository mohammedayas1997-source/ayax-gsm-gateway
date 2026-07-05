import { io } from "socket.io-client";

let socket = null;

export const connectGateway = (token) => {
  socket = io("https://ayax-api-marketplace.onrender.com", {
    transports: ["websocket"],
    auth: {
      token,
    },
  });

  socket.on("connect", () => {
    console.log("Gateway Connected");
  });

  socket.on("disconnect", () => {
    console.log("Gateway Disconnected");
  });

  return socket;
};

export const getSocket = () => socket;