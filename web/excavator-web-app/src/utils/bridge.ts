export type BridgePayload = unknown;

export interface BridgeMessage<TPayload = BridgePayload> {
  id?: string;
  type: string;
  payload?: TPayload;
  error?: string;
  source?: "web" | "android";
}

export type BridgeHandler<TPayload = BridgePayload> = (
  payload: TPayload | undefined,
  message: BridgeMessage<TPayload>,
) => void;

export interface BridgeCallOptions {
  timeout?: number;
}

interface NativeBridgeHost {
  postMessage?: (message: string) => void;
  sendMessage?: (message: string) => void;
}

interface PendingCall {
  timer: number;
  resolve: (payload: BridgePayload) => void;
  reject: (error: Error) => void;
}

interface WebBridgeHost {
  receiveMessage: (message: string | BridgeMessage) => void;
}

declare global {
  interface Window {
    Android?: NativeBridgeHost;
    AndroidWebViewBridge?: NativeBridgeHost;
    ReactNativeWebView?: NativeBridgeHost;
    WebViewBridge?: WebBridgeHost;
    __receiveAndroidMessage?: (message: string | BridgeMessage) => void;
    receiveMessage?: (message: string | BridgeMessage) => void;
    onNativeMessage?: (message: string | BridgeMessage) => void;
  }
}

const DEFAULT_TIMEOUT = 8000;

class WebViewBridge {
  private handlers = new Map<string, Set<BridgeHandler>>();

  private pendingCalls = new Map<string, PendingCall>();

  private lastMessages = new Map<string, BridgeMessage>();

  private messageIndex = 0;

  private lastReceivedSignature = "";

  private lastReceivedAt = 0;

  constructor() {
    this.installReceiver();
  }

  send<TPayload = BridgePayload>(type: string, payload?: TPayload) {
    return this.postToNative({
      type,
      payload,
      source: "web",
    });
  }

  call<TResponse = BridgePayload, TPayload = BridgePayload>(
    type: string,
    payload?: TPayload,
    options: BridgeCallOptions = {},
  ) {
    const id = this.createMessageId();
    const timeout = options.timeout ?? DEFAULT_TIMEOUT;

    return new Promise<TResponse>((resolve, reject) => {
      const timer = window.setTimeout(() => {
        this.pendingCalls.delete(id);
        reject(new Error(`Android bridge call timeout: ${type}`));
      }, timeout);

      this.pendingCalls.set(id, {
        timer,
        resolve: (responsePayload) => resolve(responsePayload as TResponse),
        reject,
      });

      const posted = this.postToNative({
        id,
        type,
        payload,
        source: "web",
      });

      if (!posted) {
        window.clearTimeout(timer);
        this.pendingCalls.delete(id);
        reject(new Error("Android bridge is not available"));
      }
    });
  }

  on<TPayload = BridgePayload>(type: string, handler: BridgeHandler<TPayload>) {
    const currentHandlers = this.handlers.get(type) ?? new Set<BridgeHandler>();
    currentHandlers.add(handler as BridgeHandler);
    this.handlers.set(type, currentHandlers);

    const lastMessage = this.lastMessages.get(type);

    if (lastMessage) {
      handler(lastMessage.payload as TPayload | undefined, lastMessage as BridgeMessage<TPayload>);
    }

    return () => {
      this.off(type, handler);
    };
  }

  off<TPayload = BridgePayload>(type: string, handler: BridgeHandler<TPayload>) {
    const currentHandlers = this.handlers.get(type);

    if (!currentHandlers) {
      return;
    }

    currentHandlers.delete(handler as BridgeHandler);

    if (currentHandlers.size === 0) {
      this.handlers.delete(type);
    }
  }

  receiveMessage(message: string | BridgeMessage) {
    const parsedMessage = this.parseMessage(message);

    if (!parsedMessage) {
      return;
    }

    if (this.isDuplicateMessage(parsedMessage)) {
      return;
    }

    if (parsedMessage.id && this.pendingCalls.has(parsedMessage.id)) {
      this.handleCallResponse(parsedMessage);
      return;
    }

    this.lastMessages.set(parsedMessage.type, parsedMessage);

    const currentHandlers = this.handlers.get(parsedMessage.type);

    if (!currentHandlers) {
      return;
    }

    currentHandlers.forEach((handler) => {
      handler(parsedMessage.payload, parsedMessage);
    });
  }

  private installReceiver() {
    if (typeof window === "undefined") {
      return;
    }

    const receiveMessage = (message: string | BridgeMessage) => this.receiveMessage(message);

    window.WebViewBridge = {
      receiveMessage,
    };
    window.__receiveAndroidMessage = receiveMessage;
    window.receiveMessage = receiveMessage;
    window.onNativeMessage = receiveMessage;
    window.addEventListener("message", (event) => receiveMessage(event.data));
    window.addEventListener("nativeMessage", (event) => {
      receiveMessage((event as CustomEvent).detail);
    });
  }

  private postToNative(message: BridgeMessage) {
    if (typeof window === "undefined") {
      return false;
    }

    const nativeBridge = window.AndroidWebViewBridge ?? window.Android ?? window.ReactNativeWebView;
    const serializedMessage = JSON.stringify(message);

    if (typeof nativeBridge?.postMessage === "function") {
      nativeBridge.postMessage(serializedMessage);
      return true;
    }

    if (typeof nativeBridge?.sendMessage === "function") {
      nativeBridge.sendMessage(serializedMessage);
      return true;
    }

    return false;
  }

  private parseMessage(message: string | BridgeMessage): BridgeMessage | null {
    if (typeof message !== "string") {
      return message;
    }

    try {
      return JSON.parse(message) as BridgeMessage;
    } catch {
      return null;
    }
  }

  private isDuplicateMessage(message: BridgeMessage) {
    const signature = JSON.stringify(message);
    const now = Date.now();
    const isDuplicate = signature === this.lastReceivedSignature && now - this.lastReceivedAt < 100;

    this.lastReceivedSignature = signature;
    this.lastReceivedAt = now;

    return isDuplicate;
  }

  private handleCallResponse(message: BridgeMessage) {
    if (!message.id) {
      return;
    }

    const pendingCall = this.pendingCalls.get(message.id);

    if (!pendingCall) {
      return;
    }

    window.clearTimeout(pendingCall.timer);
    this.pendingCalls.delete(message.id);

    if (message.error) {
      pendingCall.reject(new Error(message.error));
      return;
    }

    pendingCall.resolve(message.payload);
  }

  private createMessageId() {
    this.messageIndex += 1;
    return `${Date.now()}-${this.messageIndex}`;
  }
}

export const bridge = new WebViewBridge();

export const sendToAndroid = bridge.send.bind(bridge);
export const callAndroid = bridge.call.bind(bridge);
export const onAndroidMessage = bridge.on.bind(bridge);
export const offAndroidMessage = bridge.off.bind(bridge);
