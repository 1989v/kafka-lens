import { ReactNode, createContext, useContext, useEffect, useState } from "react";
import { AppInfo, fetchAppInfo } from "./api";

const Ctx = createContext<AppInfo | null>(null);

export function AppInfoProvider({ children }: { children: ReactNode }) {
  const [info, setInfo] = useState<AppInfo | null>(null);
  useEffect(() => { fetchAppInfo().then(setInfo).catch(() => undefined); }, []);
  return <Ctx.Provider value={info}>{children}</Ctx.Provider>;
}

export function useAppInfo(): AppInfo | null {
  return useContext(Ctx);
}

export function useFeature(name: keyof AppInfo["features"]): boolean {
  const info = useAppInfo();
  return info?.features?.[name] ?? false;
}
