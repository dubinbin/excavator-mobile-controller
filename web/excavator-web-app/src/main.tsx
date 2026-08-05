import { StrictMode, Suspense } from "react";
import { createRoot } from "react-dom/client";
import { HashRouter, Routes, Route } from "react-router-dom";
import "./index.css";
import App from "./App.tsx";
import { PopoverHost } from "./components/Popover.tsx";
import { CommonModalHost } from "./components/CommonModalHost.tsx";
import { CommonToastHost } from "./components/CommonToastHost.tsx";
import { DigTask, LevelingTask, RepairSlope, Settings } from "./routes/LazyRoutes.tsx";

const preventDefaultBrowserAction = (event: Event) => {
  event.preventDefault();
};

window.addEventListener("contextmenu", preventDefaultBrowserAction);
window.addEventListener("selectstart", preventDefaultBrowserAction);
window.addEventListener("copy", preventDefaultBrowserAction);
window.addEventListener("cut", preventDefaultBrowserAction);
window.addEventListener("paste", preventDefaultBrowserAction);

const removeInitialLoading = () => {
  const loadingElement = document.getElementById("app-loading");

  if (!loadingElement) {
    return;
  }

  loadingElement.style.opacity = "0";
  loadingElement.style.transition = "opacity 180ms ease";
  window.setTimeout(() => {
    loadingElement.remove();
  }, 180);
};

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HashRouter>
      <Suspense fallback={<App />}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/settings/*" element={<Settings />} />
          <Route path="/dig-task/*" element={<DigTask />} />
          <Route path="/leveling-task/*" element={<LevelingTask />} />
          <Route path="/repair-slope/*" element={<RepairSlope />} />
        </Routes>
      </Suspense>
    </HashRouter>
    <PopoverHost />
    <CommonModalHost />
    <CommonToastHost />
  </StrictMode>,
);

requestAnimationFrame(() => {
  requestAnimationFrame(removeInitialLoading);
});
