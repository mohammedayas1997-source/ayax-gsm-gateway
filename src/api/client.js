import axios from "axios";

const api = axios.create({
  baseURL: "https://ayax-api-marketplace.onrender.com/api/v1",
  timeout: 30000,
});

export default api;