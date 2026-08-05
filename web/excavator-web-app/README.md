# Excavator Web App

React + Vite application embedded by the Android app in this repository.

Use `npm run dev` with the Android `devWebDebug` variant for daily development. Use
`npm run build:android` only when checking the exact deterministic build used by Gradle; unlike
the legacy `npm run build` release helper, it never increments `package.json`.

See the repository root `README.md` for Android device, packaged-assets, and release commands.
