import { lazy } from "react";

export const Settings = lazy(() => import("@/pages/Settings/index.tsx"));
export const DigTask = lazy(() => import("@/pages/DigTask/index.tsx").then(({ DigTask }) => ({ default: DigTask })));
export const RepairSlope = lazy(() => import("@/pages/RepairSlope/index.tsx").then(({ RepairSlope }) => ({ default: RepairSlope })));
export const LevelingTask = lazy(() => import("@/pages/Leveling/index.tsx").then(({ LevelingTask }) => ({ default: LevelingTask })));
