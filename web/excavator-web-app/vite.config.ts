import { defineConfig, type PluginOption } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

const buildVersion = process.env.BUILD_VERSION ?? '0.0.0'

const injectBuildVersionMeta = (): PluginOption => ({
  name: 'inject-build-version-meta',
  apply: 'build',
  transformIndexHtml() {
    return [
      {
        tag: 'meta',
        attrs: {
          name: 'version',
          content: buildVersion,
        },
        injectTo: 'head',
      },
    ]
  },
})

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), injectBuildVersionMeta()],
  base: "./",
  build: {
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
    dedupe: ['react', 'react-dom'],
  },
})
