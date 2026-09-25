import {createContext, useContext} from "react";

export type CubePreviewMode = "2d" | "3d";

export const CubePreviewModeContext = createContext<CubePreviewMode>("3d");
export const CubePlaybackModeContext = createContext<CubePreviewMode>("3d");

export function useCubePreviewMode(): CubePreviewMode {
    return useContext(CubePreviewModeContext);
}

export function useCubePlaybackMode(): CubePreviewMode {
    return useContext(CubePlaybackModeContext);
}
