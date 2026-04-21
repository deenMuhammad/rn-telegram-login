import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'rn-telegram-login' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({
    ios: "- You have run 'pod install'\n",
    default: '',
  }) +
  '- You rebuilt the app after installing the package\n';

const RNTelegramLogin = NativeModules.RNTelegramLogin
  ? NativeModules.RNTelegramLogin
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export interface TelegramLoginOptions {
  /** Bot client ID obtained from BotFather */
  clientId: string;
  /** Redirect URI registered with BotFather (e.g. "https://app12345-login.tg.dev/tglogin") */
  redirectUri: string;
  /** OAuth scopes to request. Common values: "openid", "profile", "phone" */
  scopes?: string[];
  /**
   * iOS only. Custom URL scheme used as fallback on iOS < 17.4.
   * Required when redirectUri is not a Universal Link.
   */
  fallbackScheme?: string;
}

export interface TelegramLoginResult {
  /** OpenID Connect JWT. Validate this on your backend before creating a session. */
  idToken: string;
}

/**
 * Configure the Telegram Login SDK. Call this once at app startup, before
 * invoking `login()`.
 */
export function configure(options: TelegramLoginOptions): void {
  RNTelegramLogin.configure(
    options.clientId,
    options.redirectUri,
    options.scopes ?? ['openid', 'profile'],
    options.fallbackScheme ?? null
  );
}

/**
 * Start the Telegram login flow. Resolves with an `idToken` JWT on success.
 *
 * **Security**: always validate `idToken` on your backend against
 * https://oauth.telegram.org/.well-known/jwks.json before trusting user data.
 */
export function login(): Promise<TelegramLoginResult> {
  return RNTelegramLogin.login();
}

/**
 * iOS only. Pass a URL received via `Linking.addEventListener('url', ...)` or
 * the `onOpenURL` SwiftUI modifier to the SDK so it can exchange the
 * authorization code for an id_token.
 *
 * Not needed when using the default Universal Links redirect — the native
 * AppDelegate integration handles this automatically.
 */
export function handleUrl(url: string): void {
  if (Platform.OS === 'ios') {
    RNTelegramLogin.handleUrl(url);
  }
}
