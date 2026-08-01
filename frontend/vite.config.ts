import {defineConfig} from "vitest/config";
import react from "@vitejs/plugin-react";

function workerSafePreloadHelper() {
  return {
    name: "worker-safe-preload-helper",
    generateBundle(
      _options: unknown,
      bundle: {[fileName: string]: {type: string; code?: string}},
    ) {
      for (const fileName of Object.keys(bundle)) {
        const output = bundle[fileName];
        if (output.type !== "chunk") {
          continue;
        }
        const code = output.code ?? "";
        const marker = /(\w+=function\((\w+),(\w+),(\w+)\)\{)(let \w+=Promise\.resolve\(\);)if\(\3&&\3\.length>0\)/;
        if (!marker.test(code)) {
          continue;
        }
        output.code = code.replace(
          marker,
          "$1if(typeof document===`undefined`)return Promise.resolve().then($2);$5if($3&&$3.length>0)",
        );
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), workerSafePreloadHelper()],
  optimizeDeps: {
    exclude: ["cubing"],
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    target: "es2022",
    // cubing's generated module worker must not use Vite's DOM-only preload helper.
    modulePreload: false,
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    restoreMocks: true,
    exclude: ["**/node_modules/**", "**/dist/**", "**/e2e/**"],
  },
});
