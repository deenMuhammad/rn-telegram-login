# rn-telegram-login

React Native wrapper for the [official Telegram Login Native SDKs](https://core.telegram.org/bots/telegram-login#native-sdks) — supports Android (≥ API 23) and iOS (≥ 15).

Both SDKs are vendored directly in the package — no GitHub credentials, no SPM setup required.

- **Android**: [`TelegramMessenger/telegram-login-android`](https://github.com/TelegramMessenger/telegram-login-android)
- **iOS**: [`TelegramMessenger/telegram-login-ios`](https://github.com/TelegramMessenger/telegram-login-ios)

---

## Prerequisites — BotFather setup

1. Create a Telegram bot via [@BotFather](https://t.me/botfather).
2. Go to **Bot Settings → Web Login**.
3. Register your redirect URI (e.g. `https://app{ID}-login.tg.dev/tglogin`).
4. For **Android**: provide your app's package name and SHA-256 signing fingerprint (`./gradlew signingReport`).
5. For **iOS**: provide your app's Bundle ID and Apple Developer Team ID.
6. BotFather gives you a **Client ID** and **Client Secret** — store both securely.

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

No additional Android setup required — everything is bundled in the package.

---

## Platform setup

### Android — App Links

Add the intent-filter below to your **main activity** in `android/app/src/main/AndroidManifest.xml`, replacing the host with your redirect URI domain from BotFather:

```xml
<activity
  android:name=".MainActivity"
  android:launchMode="singleTask"
  ...>

  <intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
      android:scheme="https"
      android:host="app{YOUR_APP_ID}-login.tg.dev"
      android:pathPrefix="/tglogin" />
  </intent-filter>
</activity>
```

> `autoVerify="true"` enables App Links so the callback URL opens your app directly without a chooser dialog.

### iOS — Associated Domains

1. In Xcode select your target → **Signing & Capabilities → + Capability → Associated Domains**.
2. Add: `applinks:app{YOUR_APP_ID}-login.tg.dev`

#### AppDelegate URL forwarding

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
```

**Swift (AppDelegate.swift)**
```swift
func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    RCTLinkingManager.application(
        UIApplication.shared,
        continue: userActivity,
        restorationHandler: { _ in }
    )
}
```

---

## Usage

```ts
import { configure, login, handleUrl } from 'rn-telegram-login';
import { Linking } from 'react-native';
import { useEffect } from 'react';

// 1. Configure once at app startup
configure({
  clientId: 'YOUR_CLIENT_ID',
  redirectUri: 'https://app{YOUR_APP_ID}-login.tg.dev/tglogin',
  scopes: ['openid', 'profile'],
});

// 2. iOS only — forward incoming URLs for custom scheme fallback.
//    Not needed when using Universal Links (the default).
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
  } catch (err) {
    if (err.code === 'CANCELLED') return;
    console.error('Telegram login failed', err);
  }
}
```

---

## API

### `configure(options)`

Must be called before `login()`.

| Option | Type | Required | Description |
|---|---|---|---|
| `clientId` | `string` | ✅ | Client ID from BotFather → Bot Settings → Web Login |
| `redirectUri` | `string` | ✅ | Redirect URI registered with BotFather |
| `scopes` | `string[]` | — | Defaults to `['openid', 'profile']`. `openid` is always required. |
| `fallbackScheme` | `string` | — | iOS < 17.4 custom URL scheme fallback |

### `login(): Promise<{ idToken: string }>`

Opens the Telegram app (or falls back to an in-app web session on iOS) and resolves with an OpenID Connect JWT on success.

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

iOS only. Passes a URL received via `Linking` to the SDK. Not needed when using Universal Links.

---

## Scopes

| Scope | Returns | Notes |
|---|---|---|
| `openid` | `sub`, `iss`, `iat`, `exp` | **Required** |
| `profile` | `name`, `preferred_username`, `picture` | |
| `phone` | `phone_number` | Requires explicit user consent |
| `telegram:bot_access` | — | Permission to message the user via your bot |

---

## Token validation (backend)

> **Never trust the `idToken` on the client alone** — always verify it server-side.

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

---

## Support

For Telegram Login issues contact [@BotSupport](https://t.me/botsupport) with the hashtag `#oidc`.

---

## License

MIT
