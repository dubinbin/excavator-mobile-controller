import { IEventCode } from "@/types";
import { sendToAndroid } from "./bridge";

export const limitDecimalPlaces = (value: string, maxDecimalPlaces = 2) => {
  const [integerPart, ...decimalParts] = value.split(".");

  if (decimalParts.length === 0) {
    return value;
  }

  return `${integerPart}.${decimalParts.join("").slice(0, maxDecimalPlaces)}`;
};

const decimalToScaledInteger = (value: string | number, maxDecimalPlaces: number) => {
  const normalizedValue = limitDecimalPlaces(String(value), maxDecimalPlaces);

  if (!Number.isFinite(Number(normalizedValue))) {
    return null;
  }

  const sign = normalizedValue.startsWith("-") ? -1 : 1;
  const unsignedValue = sign === -1 ? normalizedValue.slice(1) : normalizedValue;
  const [integerPart = "0", decimalPart = ""] = unsignedValue.split(".");
  const scale = 10 ** maxDecimalPlaces;
  const scaledDecimal = Number(decimalPart.padEnd(maxDecimalPlaces, "0"));

  return sign * (Number(integerPart || "0") * scale + scaledDecimal);
};

const scaledIntegerToDecimal = (value: number, maxDecimalPlaces: number) => {
  const scale = 10 ** maxDecimalPlaces;
  const sign = value < 0 ? "-" : "";
  const absoluteValue = Math.abs(value);
  const integerPart = Math.trunc(absoluteValue / scale);
  const decimalPart = String(absoluteValue % scale)
    .padStart(maxDecimalPlaces, "0")
    .replace(/0+$/, "");

  return `${sign}${integerPart}${decimalPart ? `.${decimalPart}` : ""}`;
};

export const addDecimalByScale = (
  value: string | number,
  delta: number,
  maxDecimalPlaces = 2,
) => {
  const currentValue = decimalToScaledInteger(value, maxDecimalPlaces);
  const deltaValue = decimalToScaledInteger(delta, maxDecimalPlaces);

  if (currentValue === null || deltaValue === null) {
    return "";
  }

  return scaledIntegerToDecimal(currentValue + deltaValue, maxDecimalPlaces);
};

export const closeWebView = () => {
  sendToAndroid(IEventCode.CLOSE_WEBVIEW_SIGNAL)
}