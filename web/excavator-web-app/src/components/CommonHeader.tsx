import { type MouseEvent } from "react";
import { useNavigate } from "react-router-dom"
import backIcon from "@/assets/common/back.png";
import AnswerIcon from "@/assets/common/answer.png";
import { Popover } from "../stores/modules/PopoverStore";
import { IEventCode } from "@/types";
import { sendToAndroid } from "@/utils/bridge";

interface CommonHeaderProps {
    title: string;
    goBackAction?: () => void;
    clickHelpAction?: (event: MouseEvent<HTMLButtonElement>) => void;
}

export const CommonHeader = ({ title, goBackAction, clickHelpAction }: CommonHeaderProps) => {
    const navigation = useNavigate();

    const handleBack = () => {
        if (goBackAction) {
            goBackAction();
        } else {
            const posted = sendToAndroid(IEventCode.CLOSE_WEBVIEW_SIGNAL, {
                reason: "header_back",
            });

            if (!posted) {
                // 非 WebView 环境下回到应用首页，避免造成路由逻辑混乱
                navigation('/', { replace: true });
            }
        }
    }

    const handleClickHelp = (event: MouseEvent<HTMLButtonElement>) => {
        Popover.setTrigger(event.currentTarget);

        if (clickHelpAction) {
            clickHelpAction(event);
        }
    }

    return (
        <div className="header-bar grid grid-cols-[1fr_auto_1fr] items-center">
            <button
            className="flex w-fit items-center text-[#004DFF]"
            onClick={handleBack}
            type="button"
            >
            <img
                className="size-6 md:size-7 xl:size-8"
                src={backIcon}
                alt=""
            />
            <span className="ml-3 leading-none text-2xl">返回</span>
            </button>

            <div className="title  leading-none text-[32px]">
                {title}
            </div>

            <button className="ml-auto" type="button" aria-label="帮助" onClick={handleClickHelp}>
            <img
                className="size-7 opacity-70 md:size-8 xl:size-9"
                src={AnswerIcon}
                alt=""
            />
            </button>
        </div>
    )
}
