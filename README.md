# rn-telegram-login

React Native wrapper for the [official Telegram Login Native SDKs](https://core.telegram.org/bots/telegram-login#native-sdks) — supports Android (≥ API 23) and iOS (≥ 15).

Both SDKs are vendored directly in the package — no GitHub credentials, no SPM setup required.

- **Android**: [`TelegramMessenger/telegram-login-android`](https://github.com/TelegramMessenger/telegram-login-android)
- **iOS**: [`TelegramMessenger/telegram-login-ios`](https://github.com/TelegramMessenger/telegram-login-ios)

---

## Prerequisites — BotFather setup

1. Create a Telegram bot via [@BotFather](https://t.me/botfather).
2. Go to **Bot Settings → Web Login**.
3. Register your redirect URI. Two options — pick one:
   - **Custom scheme (recommended):** `myapp://telegram-auth` — no domain verification required, works immediately.
   - **HTTPS (Universal Links):** `https://app{ID}-login.tg.dev/tglogin` — requires Associated Domains setup.
4. For **Android**: provide your app's package name and SHA-256 signing fingerprint (`./gradlew signingReport`).
5. For **iOS**: provide your app's Bundle ID and Apple Developer Team ID.
6. BotFather gives you an app URL and a **Client ID** (your bot's numeric ID) — store them.

> **Custom scheme is strongly recommended.** HTTPS redirect URIs require Apple AASA verification and are sensitive to Apple's CDN caching delays. Custom schemes work on first install with no verification step.

---

## Installation

```sh
npm install rn-telegram-login
# or
yarn add rn-telegram-login
```

### iOS

```sh
cd ios && pod install
```

No additional Android linking setup required — everything is bundled in the package.

---

## Platform setup

### iOS — AppDelegate (critical)

Without this, Firebase and other SDKs that swizzle `continueUserActivity` will consume incoming Universal Link callbacks before React Native's `Linking` module sees them, causing the login to hang silently.

**Swift (AppDelegate.swift)**
```swift
import React

// Forward Universal Links (https:// callbacks) to React Native Linking
func application(
  _ application: UIApplication,
  continue userActivity: NSUserActivity,
  restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
) -> Bool {
  return RCTLinkingManager.application(application, continue: userActivity, restorationHandler: restorationHandler)
}

// Forward custom-scheme URLs (myapp://) to React Native Linking
func application(
  _ app: UIApplication,
  open url: URL,
  options: [UIApplication.OpenURLOptionsKey: Any] = [:]
) -> Bool {
  return RCTLinkingManager.application(app, open: url, options: options)
}
```

**Objective-C (AppDelegate.mm)**
```objc
- (BOOL)application:(UIApplication *)application
    continueUserActivity:(NSUserActivity *)userActivity
    restorationHandler:(void (^)(NSArray<id<UIUserActivityRestoring>> *))restorationHandler
{
  return [RCTLinkingManager application:application
                   continueUserActivity:userActivity
                     restorationHandler:restorationHandler];
}

- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options
{
  return [RCTLinkingManager application:app openURL:url options:options];
}
```

### iOS — Info.plist

If using a custom scheme redirect URI, register it:

```xml
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLName</key>
    <string>myapp</string>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>myapp</string>
    </array>
  </dict>
</array>
```

If using the cross-app flow (`preferNativeApp: true`), also add:

```xml
<key>LSApplicationQueriesSchemes</key>
<array>
  <string>tg</string>
</array>
```

### iOS — Associated Domains (HTTPS redirect URI only)

Skip this section if using a custom scheme.

1. In Xcode → target → **Signing & Capabilities → + → Associated Domains**.
2. Add: `applinks:app{YOUR_APP_ID}-login.tg.dev`
3. Uninstall and reinstall the app after adding the entitlement — iOS only verifies Associated Domains on a fresh install.

### Android — App Links (HTTPS redirect URI only)

Skip this section if using a custom scheme.

Add to your main activity in `android/app/src/main/AndroidManifest.xml`:

```xml
<intent-filter android:autoVerify="true">
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data
    android:scheme="https"
    android:host="app{YOUR_APP_ID}-login.tg.dev"
    android:pathPrefix="/tglogin" />
</intent-filter>
```

Serve `https://app{YOUR_APP_ID}-login.tg.dev/.well-known/assetlinks.json` — Telegram hosts this automatically once you register your app in BotFather.

---

## Usage

```ts
import { configure, login, handleUrl } from 'rn-telegram-login';
import { Linking, Platform } from 'react-native';
import { useEffect } from 'react';

// 1. Configure once at app startup (e.g. in App.tsx before NavigationContainer)
configure({
  clientId: 'YOUR_BOT_NUMERIC_ID',  // e.g. '1234567890'
  redirectUri: 'myapp://telegram-auth',
  scopes: ['openid', 'profile'],
});

// 2. Forward all incoming URLs to the SDK (handles both custom scheme and Universal Link callbacks)
useEffect(() => {
  const sub = Linking.addEventListener('url', ({ url }) => handleUrl(url));
  return () => sub.remove();
}, []);

// 3. Trigger login
async function signInWithTelegram() {
  try {
    const { idToken } = await login();
    // ⚠️ Always validate idToken on your backend before creating a session.
    await myBackend.verifyTelegramToken(idToken);
  } catch (err: any) {
    if (err.code === 'CANCELLED') return;
    console.error('Telegram login failed', err);
  }
}
```

---

## API

### `configure(options)`

Must be called before `login()`, typically at app startup.

| Option | Type | Required | Default | Description |
|---|---|---|---|---|
| `clientId` | `string` | ✅ | — | Your bot's numeric ID (from BotFather) |
| `redirectUri` | `string` | ✅ | — | Redirect URI registered with BotFather |
| `scopes` | `string[]` | — | `['openid', 'profile']` | OIDC scopes. `openid` is always required. |
| `fallbackScheme` | `string` | — | — | iOS < 17.4: custom URL scheme for HTTPS redirect URIs |
| `preferNativeApp` | `boolean` | — | `false` | iOS: attempt to open the Telegram app directly instead of a web popup. Enable only after your bot is approved for native cross-app login. |

### `login(): Promise<{ idToken: string }>`

Starts the Telegram login flow. On iOS, opens an in-app web browser (or the Telegram app if `preferNativeApp: true`). Resolves with an OpenID Connect JWT on success.

Rejects with one of these error codes:

| Code | Meaning |
|---|---|
| `CANCELLED` | User dismissed the dialog |
| `NOT_CONFIGURED` | `configure()` was not called |
| `NO_AUTH_CODE` | Callback URL missing `code` parameter |
| `SERVER_ERROR` | Telegram server returned a non-200 response |
| `REQUEST_FAILED` | Network or parsing failure |
| `TELEGRAM_LOGIN_ERROR` | Android generic error |

### `handleUrl(url: string)`

Passes a URL received via `Linking` to the SDK. Call this from your `Linking.addEventListener` listener as shown in the usage example. Handles both custom scheme and Universal Link callbacks.

---

## Scopes

| Scope | JWT claims | Notes |
|---|---|---|
| `openid` | `sub`, `iss`, `iat`, `exp` | **Required** |
| `profile` | `name`, `preferred_username`, `picture` | |
| `phone` | `phone_number` | Requires explicit user consent |
| `telegram:bot_access` | — | Permission to message the user via your bot |

---

## Token validation (backend)

> **Never trust the `idToken` client-side** — always verify it server-side before creating a session.

Telegram exposes standard OpenID Connect endpoints:

| Endpoint | URL |
|---|---|
| Discovery | `https://oauth.telegram.org/.well-known/openid-configuration` |
| Public keys (JWKS) | `https://oauth.telegram.org/.well-known/jwks.json` |
| Authorization | `https://oauth.telegram.org/auth` |
| Token | `https://oauth.telegram.org/token` |

Validation steps:
1. Fetch the public key from the JWKS endpoint matching the token's `kid`.
2. Verify the JWT signature.
3. Check claims: `iss === "https://oauth.telegram.org"`, `aud === YOUR_BOT_ID`, `exp > now`.
4. Extract user info from decoded claims: `sub` (Telegram user ID), `name`, `preferred_username`, `picture`.

---

## Cross-app flow (`preferNativeApp: true`)

When enabled, the SDK attempts to open the Telegram app directly for authentication instead of showing an in-app web popup.

**Requirements:**
- Telegram must be installed on the device
- `tg` must be in `LSApplicationQueriesSchemes` in `Info.plist`
- The HTTPS tg.dev redirect URI and Associated Domains must be set up
- For verified apps: Telegram redirects back to your app automatically after login
- For **unverified apps**: Telegram shows "Login Successful" without auto-redirecting. The user must manually return to your app, after which login completes. Automatic redirect is granted once your bot is verified through Telegram's official process.

> If your bot is not yet verified, leave `preferNativeApp` at its default (`false`) to use the in-app web popup, which completes the full flow without requiring the user to switch apps.

---

## Troubleshooting

**Login hangs silently after returning from Telegram or Safari**
The most common cause is a missing `continueUserActivity` implementation in the AppDelegate. Firebase and other SDKs that use method swizzling will consume the Universal Link callback before React Native sees it. Add both `continueUserActivity` and `openURL` as shown in the iOS AppDelegate section above.

**`SFAuthenticationViewController deallocating` warning in Xcode**
Harmless system log — does not affect functionality.

**`RBSServiceErrorDomain "Client not entitled"` in Xcode**
Normal system-level RunningBoard logs that appear on every physical device debug session. Unrelated to this SDK.

**`com.apple.mobile.usermanagerd.xpc was invalidated`**
Occurs when `prefersEphemeralWebBrowserSession` is `false` on a debug build. The SDK uses `true` by default, which avoids this.

**Universal Links not working after adding Associated Domains**
iOS only verifies Associated Domains on a **fresh install**. Delete the app from the device and reinstall — do not use "Build and Run" over an existing install. Also confirm Apple's CDN has cached your AASA file.

**"Login failed, try again" in Telegram**
The redirect URI sent to the `crossapp` endpoint doesn't match what's registered in BotFather. Verify the `redirectUri` in `configure()` exactly matches one of your registered redirect URIs.

---

## Changelog

### 0.2.1
**Android fixes for React Native New Architecture (TurboModules)**
- `configure()` now accepts `preferNativeApp` as 5th argument — TurboModules enforce strict argument-count matching, so the missing parameter caused a red-screen crash on startup.
- `onNewIntent` / `onActivityResult` signatures updated to non-nullable (`Intent`, `Activity`) to match the `ActivityEventListener` interface in RN 0.73+.
- `scopes` parsing updated to `mapNotNull` to satisfy `List<String>` (non-nullable) expected by the SDK.
- `currentActivity` access moved to `reactContext.currentActivity` for compatibility with TurboModule context.
- Android `build.gradle` JVM target bumped from 1.8 → 17 to match host project and avoid Gradle JVM-target mismatch build error.

### 0.2.0
**iOS fixes**
- `prefersEphemeralWebBrowserSession = true` — prevents `usermanagerd.xpc` errors on debug builds.
- Removed `ios_sdk=1` from auth URL — it caused Telegram's server to issue a `tg://` redirect that `ASWebAuthenticationSession` could not follow.
- Robust `PresentationContextProvider` fallback chain — fixes blank/no-op button when `keyWindow` is nil in React Native's window hierarchy.
- Deferred `_authSession = nil` to next run loop — eliminates `SFAuthenticationViewController deallocating` Xcode warning.
- Cross-app fast path: if the `/crossapp` response already contains a `code`, resolve immediately without opening the Telegram app.
- Added `preferNativeApp` option (default `false`) — controls whether iOS attempts the Telegram app directly instead of the in-app web popup.

---

## Support

For Telegram Login issues contact [@BotSupport](https://t.me/botsupport) with the hashtag `#oidc`.

---

## License

MIT
