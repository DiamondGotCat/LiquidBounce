import { CSSProperties } from "react";

import AlignmentGrid, {
  AlignmentGridProvider,
} from "~/components/alignment-grid";

import { useModules } from "~/features/clickgui/use-modules";
import Panel from "~/features/clickgui/components/panel";

import styles from "./clickgui.module.css";

export default function ClickGUI() {
  const { modulesByCategory } = useModules();

  // let modulesColor = kotlin.colorToHex(clickGuiModule.instance.getModuleColor())
  //     let headerColor = kotlin.colorToHex(clickGuiModule.instance.getHeaderColor())
  //     let accentColor = kotlin.colorToHex(clickGuiModule.instance.getAccentColor())
  //     let accendDimmed = kotlin.colorToHex(clickGuiModule.instance.getAccentColor())
  //     let textColor = kotlin.colorToHex(clickGuiModule.instance.getTextColor())
  //     let textDimmedColor = kotlin.colorToHex(clickGuiModule.instance.getDimmedTextColor())
  const style = {
    "--module": "rgba(0, 0, 0, 0.5)",
    "--header": "rgba(0, 0, 0, 0.5)",
    "--accent": "var(--brand)",
    "--accent-dimmed": "#121212",
    "--text": "#fff",
    "--text-dimmed": "#aaa",
  } as CSSProperties;

  return (
    <div className={styles.clickgui} style={style}>
      <AlignmentGridProvider
        value={{
          enabled: true,
          horizontal: 16,
          vertical: 16,
        }}
      >
        <AlignmentGrid />
        <img
          src="./background.png"
          className="absolute inset-0 w-full h-full -z-10"
        />
        {Object.entries(modulesByCategory).map(([category, modules], idx) => (
          <Panel
            category={category}
            modules={modules}
            key={category}
            startPosition={[30, 30 + idx * 45]}
          />
        ))}
      </AlignmentGridProvider>
    </div>
  );
}