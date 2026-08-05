
import { useCallback, useEffect, useLayoutEffect, useState } from "react";
import LeftControlImage from "@/assets/setting/left_control.png";
import RightControlImage from "@/assets/setting/right_control.png";
import { useSettingStore } from "@/stores/modules/SettingStore";
import { onAndroidMessage, sendToAndroid } from "@/utils/bridge";
import { CURRENT_STATUS_KEYS, IEventCode } from "@/types";

type ActionGroup = "boom" | "arm" | "bucket" | "swing";
type OptionId =
  | "boom_forward"
  | "boom_reverse"
  | "arm_forward"
  | "arm_reverse"
  | "bucket_forward"
  | "bucket_reverse"
  | "swing_forward"
  | "swing_reverse";
type ChannelId = "ab" | "cd" | "ef" | "gh";
type ActionOption = { id: OptionId; label: string; group: ActionGroup; data_name: string };
type ChannelMapping = { optionId: OptionId; data_name: string };
type ChannelMappings = Record<ChannelId, ChannelMapping | null>;
type NativeActionKey = "boom" | "stick" | "bucket" | "swing";
type NativeAxisKey = "leftAb" | "leftCd" | "rightEf" | "rightGh";
type NativeAxis = {
  label?: string;
  key?: string;
  reverse?: boolean;
  display?: string;
};
type JoystickMappingPayload = {
  axes?: Partial<Record<NativeAxisKey, NativeAxis>>;
};
type SubmitObject = {
  leftAb: string;
  leftAbReverse: boolean;
  leftCd: string;
  leftCdReverse: boolean;
  rightEf: string;
  rightEfReverse: boolean;
  rightGh: string;
  rightGhReverse: boolean;
};
type SubmitNameKey = "leftAb" | "leftCd" | "rightEf" | "rightGh";
type SubmitReverseKey = "leftAbReverse" | "leftCdReverse" | "rightEfReverse" | "rightGhReverse";

const actionOptions: ActionOption[] = [
  { id: "boom_forward", label: "大臂（正向）", group: "boom", data_name: "大臂" },
  { id: "boom_reverse", label: "大臂（反向）", group: "boom", data_name: "大臂" },
  { id: "arm_forward", label: "小臂（正向）", group: "arm", data_name: "小臂" },
  { id: "arm_reverse", label: "小臂（反向）", group: "arm", data_name: "小臂" },
  { id: "bucket_forward", label: "铲斗（正向）", group: "bucket", data_name: "铲斗" },
  { id: "bucket_reverse", label: "铲斗（反向）", group: "bucket", data_name: "铲斗" },
  { id: "swing_forward", label: "回旋（正向）", group: "swing", data_name: "回旋" },
  { id: "swing_reverse", label: "回旋（反向）", group: "swing", data_name: "回旋" },
];

const joystickCards: Array<{
  title: string;
  image: string;
  channels: Array<{ id: ChannelId; label: string }>;
}> = [
  {
    title: "左杆",
    image: LeftControlImage,
    channels: [
      { id: "ab", label: "AB 指令" },
      { id: "cd", label: "CD 指令" },
    ],
  },
  {
    title: "右杆",
    image: RightControlImage,
    channels: [
      { id: "ef", label: "EF 指令" },
      { id: "gh", label: "GH 指令" },
    ],
  },
];

const optionById = actionOptions.reduce(
  (acc, option) => {
    acc[option.id] = option;
    return acc;
  },
  {} as Record<OptionId, ActionOption>,
);

const initialMappings: ChannelMappings = {
  ab: null,
  cd: null,
  ef: null,
  gh: null,
};

const channelIds: ChannelId[] = ["ab", "cd", "ef", "gh"];
const forwardOptionIds: OptionId[] = ["boom_forward", "arm_forward", "bucket_forward", "swing_forward"];
const channelSubmitKeyById: Record<
  ChannelId,
  { nameKey: SubmitNameKey; reverseKey: SubmitReverseKey }
> = {
  ab: { nameKey: "leftAb", reverseKey: "leftAbReverse" },
  cd: { nameKey: "leftCd", reverseKey: "leftCdReverse" },
  ef: { nameKey: "rightEf", reverseKey: "rightEfReverse" },
  gh: { nameKey: "rightGh", reverseKey: "rightGhReverse" },
};
const nativeAxisKeyByChannelId: Record<ChannelId, NativeAxisKey> = {
  ab: "leftAb",
  cd: "leftCd",
  ef: "rightEf",
  gh: "rightGh",
};
const actionGroupByNativeKey: Record<NativeActionKey, ActionGroup> = {
  boom: "boom",
  stick: "arm",
  bucket: "bucket",
  swing: "swing",
};

const createMapping = (optionId: OptionId): ChannelMapping => ({
  optionId,
  data_name: optionById[optionId].data_name,
});

const isNativeActionKey = (key: string | undefined): key is NativeActionKey => {
  return key === "boom" || key === "stick" || key === "bucket" || key === "swing";
};

const getOptionIdFromNativeAxis = (axis: NativeAxis | undefined): OptionId | null => {
  if (!isNativeActionKey(axis?.key)) {
    return null;
  }

  const group = actionGroupByNativeKey[axis.key];
  const direction = axis.reverse ? "reverse" : "forward";
  const option = actionOptions.find((item) => item.group === group && item.id.endsWith(`_${direction}`));

  return option?.id ?? null;
};

const createMappingsFromPayload = (payload: unknown): ChannelMappings | null => {
  const axes = (payload as JoystickMappingPayload | null)?.axes;

  if (!axes) {
    return null;
  }

  return channelIds.reduce((acc, channelId) => {
    const optionId = getOptionIdFromNativeAxis(axes[nativeAxisKeyByChannelId[channelId]]);
    acc[channelId] = optionId ? createMapping(optionId) : null;
    return acc;
  }, { ...initialMappings });
};

const shuffle = <T,>(items: T[]) => {
  const shuffledItems = [...items];

  for (let index = shuffledItems.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1));
    [shuffledItems[index], shuffledItems[randomIndex]] = [shuffledItems[randomIndex], shuffledItems[index]];
  }

  return shuffledItems;
};

const completeMappings = (mappings: ChannelMappings): ChannelMappings => {
  const completedMappings = { ...mappings };
  const selectedGroups = new Set(
    Object.values(completedMappings)
      .filter((mapping): mapping is ChannelMapping => Boolean(mapping))
      .map((mapping) => optionById[mapping.optionId].group),
  );
  const availableForwardOptionIds = shuffle(
    forwardOptionIds.filter((optionId) => !selectedGroups.has(optionById[optionId].group)),
  );

  channelIds.forEach((channelId) => {
    if (completedMappings[channelId]) {
      return;
    }

    const optionId = availableForwardOptionIds.shift();

    if (optionId) {
      completedMappings[channelId] = createMapping(optionId);
    }
  });

  return completedMappings;
};

const buildSubmitObject = (mappings: ChannelMappings): SubmitObject => {
  const submitObj: SubmitObject = {
    leftAb: "",
    leftAbReverse: false,
    leftCd: "",
    leftCdReverse: false,
    rightEf: "",
    rightEfReverse: false,
    rightGh: "",
    rightGhReverse: false,
  };

  channelIds.forEach((channelId) => {
    const mapping = mappings[channelId];

    if (!mapping) {
      return;
    }

    const submitKeys = channelSubmitKeyById[channelId];
    submitObj[submitKeys.nameKey] = mapping.data_name;
    submitObj[submitKeys.reverseKey] = mapping.optionId.endsWith("_reverse");
  });

  return submitObj;
};

const ChannelSelect = ({
  channelId,
  label,
  value,
  open,
  mappings,
  onToggle,
  onSelect,
}: {
  channelId: ChannelId;
  label: string;
  value: ChannelMapping | null;
  open: boolean;
  mappings: ChannelMappings;
  onToggle: (channelId: ChannelId) => void;
  onSelect: (channelId: ChannelId, optionId: OptionId | null) => void;
}) => {
  const selectedGroups = new Set(
    Object.entries(mappings)
      .filter(([id]) => id !== channelId)
      .map(([, selected]) => (selected ? optionById[selected.optionId].group : null))
      .filter(Boolean),
  );
  const availableOptions = actionOptions.filter((option) => !selectedGroups.has(option.group));
  const selectedOption = value ? optionById[value.optionId] : null;


  return (
    <div className="relative min-w-0">
      <div className="mb-3 text-xl font-semibold leading-none text-black">{label}</div>
      <button
        className={[
          "flex h-14 w-full items-center justify-between rounded-lg border bg-[#F7F9FC] px-4 text-left text-[16px] leading-none transition",
          open ? "border-[#006BFF]" : "border-[#E8EEF7]",
        ].join(" ")}
        type="button"
        onClick={() => onToggle(channelId)}
      >
        <span className={selectedOption ? "text-xl text-[#BCBCBC]" : "text-[#BCC2CC] text-xl"}>
          {selectedOption ? selectedOption.label : "选择指令"}
        </span>
        <svg
          className={[
            "h-4 w-4 text-[#101828] transition-transform",
            open ? "rotate-180" : "rotate-0",
          ].join(" ")}
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.2"
          aria-hidden="true"
        >
          <path d="m5 8 5 5 5-5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open && (
        <div className="fixed inset-0 z-[70] flex items-end bg-black/35">
          <button
            className="absolute inset-0 cursor-default"
            type="button"
            aria-label="关闭选择指令"
            onClick={() => onToggle(channelId)}
          />
          <div
            className="relative w-full overflow-hidden rounded-tr-2xl rounded-tl-2xl opacity-85 bg-white shadow-[0_-18px_52px_rgba(15,23,42,0.2)]"
            role="dialog"
            aria-modal="true"
            aria-labelledby={`${channelId}-action-sheet-title`}
          >
            <div className=" px-8 py-6">
              <div
                id={`${channelId}-action-sheet-title`}
                className="text-[28px] font-semibold leading-none text-black"
              >
                {label}
              </div>
              <div className="mt-3 text-xl leading-none text-[#8C8F96]">选择指令</div>
            </div>

            <div className="max-h-[52vh] overflow-y-auto py-2">
              {value && (
                <button
                  className="flex h-20 w-full items-center px-8 text-left text-[26px] font-medium leading-none text-[#ff7b00] transition active:bg-[#F4F8FF]"
                  type="button"
                  onClick={() => onSelect(channelId, null)}
                >
                  清除当前选择
                </button>
              )}
              {availableOptions.map((option) => (
                <button
                  key={option.id}
                  className={[
                    "flex h-20 w-full items-center justify-between px-8 text-left text-[26px] leading-none transition active:bg-[#F4F8FF]",
                    value?.optionId === option.id ? "font-semibold text-[#006BFF]" : "font-medium text-black",
                  ].join(" ")}
                  type="button"
                  onClick={() => onSelect(channelId, option.id)}
                >
                  <span>{option.label}</span>
                  {value?.optionId === option.id && (
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-full text-[#006BFF] text-3xl font-bold leading-none">
                          ✓
                      </span>
                  )}
                </button>
              ))}
            </div>

            <div className="p-5">
              <button
                className="h-16 w-full rounded-xl bg-[#F3F6FA] text-[24px] font-semibold leading-none text-[#344054] transition active:scale-[0.99]"
                type="button"
                onClick={() => onToggle(channelId)}
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export const JoyStickSetting = () => {
  const [mappings, setMappings] = useState<ChannelMappings>(initialMappings);
  const [openChannel, setOpenChannel] = useState<ChannelId | null>(null);
  const [canOperation, setCanOperation] = useState(true);
  const { setSaveAction } = useSettingStore();

  const handleToggle = (channelId: ChannelId) => {
    if (!canOperation) {
      return;
    }

    setOpenChannel((current) => (current === channelId ? null : channelId));
  };

  useEffect(() => {
    sendToAndroid(IEventCode.WEBVIEW_READY_SIGNAL, {});
    const unsubscribe = onAndroidMessage(IEventCode.GET_JOYSTICK_MAPPING_SIGNAL, (payload) => {
      const nextMappings = createMappingsFromPayload(payload);

      if (nextMappings) {

        setMappings(nextMappings);
      }
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    let unsubscribeCurrentStatus;
    sendToAndroid(IEventCode.GET_CURRENT_STATUS_SIGNAL, {});
    setTimeout(() => {
      /** 当前运动模式payload：0 停止，1 底盘，2 铲斗。 */
      unsubscribeCurrentStatus = onAndroidMessage(IEventCode.GET_CURRENT_STATUS, (payload) => {
        if (payload === CURRENT_STATUS_KEYS.BUCKET) {
          setCanOperation(true)
        } else {
          setCanOperation(false);
          setOpenChannel(null);
        }

      });
    }, 50);

    return unsubscribeCurrentStatus;
  }, []);

  const handleSelect = (channelId: ChannelId, optionId: OptionId | null) => {
    setMappings((current) => ({
      ...current,
      [channelId]: optionId ? createMapping(optionId) : null,
    }));
    setOpenChannel(null);
  };

  const sendSavingChannelProxy = useCallback(() => {
    const completedMappings = completeMappings(mappings);
    const submitObj = buildSubmitObject(completedMappings);

    setMappings(completedMappings);

    sendToAndroid(IEventCode.JOYSTICK_MAPPING_SAVED_SIGNAL, submitObj);
  }, [mappings]);

  useLayoutEffect(() => {
    setSaveAction(sendSavingChannelProxy);

    return () => {
      setSaveAction(() => {});
    };
  }, [sendSavingChannelProxy, setSaveAction]);

  return (
    <div>
      <div className="text-[40px] font-semibold leading-tight text-black">摇杆通道映射</div>
      <div className="mt-3 text-[24px] leading-none text-[#8C8F96]">
        <p>设置摇杆通道与设备动作方向的对应关系。</p>
      </div>

      <div className="mt-12" style={{ position: "relative" }}>
        <div className="grid grid-cols-2 gap-12" aria-disabled={!canOperation}>
          {joystickCards.map((card) => (
            <div
              key={card.title}
              className="rounded-2xl bg-white px-7 pb-7 pt-7 shadow-[0_16px_34px_rgba(24,68,145,0.06)]"
            >
              <div className="text-[28px] font-semibold leading-none text-black">{card.title}</div>
              <div className="mt-4 flex justify-center">
                <img className="h-80 w-80 object-contain" src={card.image} alt={card.title} />
              </div>

              <div className="mt-8 grid grid-cols-2 gap-5">
                {card.channels.map((channel) => (
                  <ChannelSelect
                    key={channel.id}
                    channelId={channel.id}
                    label={channel.label}
                    value={mappings[channel.id]}
                    open={openChannel === channel.id}
                    mappings={mappings}
                    onToggle={handleToggle}
                    onSelect={handleSelect}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>

        {!canOperation && (
          <div
            className="z-10 flex items-center justify-center"
            style={{
              position: "absolute",
              top: 0,
              right: 0,
              bottom: 0,
              left: 0,
              backgroundColor: "rgba(0, 0, 0, 0.5)",
            }}
          >
            <p className="px-8 py-5 text-[28px] font-semibold leading-none text-white">
              只有铲斗模式下才允许调节摇杆映射
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
