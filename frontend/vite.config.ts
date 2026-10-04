import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// During development the React app runs on port 5173 and the Spring Boot API on 8090.
// Any request starting with /api is passed on to the API, so the browser sees one address.
// Requests starting with /notify go to the notification service.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // The notification service is a separate program on port 8091
      "/notify": {
        target: process.env.NOTIFY_URL ?? "http://localhost:8091",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/notify/, ""),
      },
      "/api": {
        target: process.env.API_URL ?? "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
