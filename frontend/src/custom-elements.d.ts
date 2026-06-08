import type React from "react";

declare global {
  namespace JSX {
    interface IntrinsicElements {
      "twisty-player": React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        puzzle?: string;
        alg?: string;
        "experimental-setup-alg"?: string;
        "control-panel"?: string;
        background?: string;
        "hint-facelets"?: string;
        "camera-latitude"?: string;
        "camera-longitude"?: string;
        "tempo-scale"?: string;
      };
    }
  }
}

export {};
