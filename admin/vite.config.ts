import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = env.VITE_ADMIN_API_PROXY_TARGET || "http://127.0.0.1:3000";

  return {
    server: {
      host: "127.0.0.1",
      proxy: {
        "/admin-api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  };
});
