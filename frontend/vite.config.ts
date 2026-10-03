import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// During development the React app runs on port 5173 and the Spring Boot API on 8090.
// Any request starting with /api is passed on to the API, so the browser sees one address.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.API_URL ?? "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
