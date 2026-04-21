# rn-telegram-login

React Native wrapper for the official [Telegram Login SDK](https://core.telegram.org/api/login-widget) — supports Android (≥ API 23) and iOS (≥ 15).

The package wraps:
- **Android**: vendored source from [`TelegramMessenger/telegram-login-android`](https://github.com/TelegramMessenger/telegram-login-android)
- **iOS**: vendored source from [`TelegramMessenger/telegram-login-ios`](https://github.com/TelegramMessenger/telegram-login-ios)

---

## Prerequisites — BotFather setup

Before integrating, register your app with [@BotFather](https://t.me/BotFather):

1. Create a bot and open **Bot Settings → Login Widget**.
2. **Android**: provide your app's `packageName` and SHA-256 fingerprint (`./gradlew signingReport`).
3. **iOS**: provide your app's Bundle ID and Apple Developer Team ID.
4. BotFather gives you a `clientId` and a redirect URI (`https://app{ID}-login.tg.dev/tglogin`).

---

## Installation

```sh
npm install rn-telegram-login
# or
yarn add rn-telegram-login
```

### iOS — pod install

```sh
cd ios && pod install
```

No additional Android setup needed — the Telegram SDK source is bundled directly in the package.

---

## Platform setup

### Android

Add the App Links intent-filter to your **main activity** in `android/app/src/main/AndroidManifest.xml`.  
Replace the host with the domain from BotFather:

```xml
<activity
  android:name=".MainActivity"
  android:launchMode="singleTask"
  ...>

  <!-- existing intent-filters -->

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

> `autoVerify="true"` enables App Links verification so that the callback URL opens your app directly without a chooser dialog.

### iOS — Associated Domains

1. In Xcode, select your target → **Signing & Capabilities** → **+ Capability** → **Associated Domains**.
2. Add: `applinks:app{YOUR_APP_ID}-login.tg.dev`

#### URL handling in AppDelegate

The SDK handles the OAuth callback via Universal Links. Add the following to your `AppDelegate`:

**Swift (AppDelegate.swift)**
```swift
import rn_telegram_login   // only if you need to call handleUrl manually

// For scenes (RN 0.71+):
func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    if let url = userActivity.webpageURL {
        RCTLinkingManager.application(
            UIApplication.shared,
            continue: userActivity,
            restorationHandler: { _ in }
        )
    }
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
```

Then in your JS code, forward the URL to the SDK (see Usage below).

---

## Usage

```ts
import { configure, login, handleUrl } from 'rn-telegram-login';
import { Linking } from 'react-native';
import { useEffect } from 'react';

// 1. Configure once at app startup (e.g. in App.tsx or index.js)
configure({
  clientId: 'YOUR_CLIENT_ID',
  redirectUri: 'https://app{YOUR_APP_ID}-login.tg.dev/tglogin',
  scopes: ['openid', 'profile'],
});

// 2. (iOS) Forward incoming URLs so the SDK can exchange the auth code.
//    Not required if the redirect URI is a Universal Link — the native
//    AppDelegate integration handles it. Needed only for custom URL schemes.
useEffect(() => {
  const sub = Linking.addEventListener('url', ({ url }) => handleUrl(url));
  return () => sub.remove();
}, []);

// 3. Trigger login
async function signInWithTelegram() {
  try {
    const { idToken } = await login();
    // ⚠️  Send idToken to YOUR backend for validation before trusting user data.
    await myBackend.verifyTelegramToken(idToken);
  } catch (err) {
    if (err.code === 'CANCELLED') return; // user closed the dialog
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
| `clientId` | `string` | ✅ | Bot client ID from BotFather |
| `redirectUri` | `string` | ✅ | Redirect URI from BotFather |
| `scopes` | `string[]` | — | Defaults to `['openid', 'profile']` |
| `fallbackScheme` | `string` | — | iOS < 17.4 fallback URL scheme |

### `login(): Promise<{ idToken: string }>`

Opens Telegram (or falls back to an in-app web session on iOS) and resolves with an OpenID Connect JWT on success. Rejects with a native error code:

| Code | Meaning |
|---|---|
| `CANCELLED` | User dismissed the dialog |
| `NOT_CONFIGURED` | `configure()` was not called |
| `NO_AUTH_CODE` | Callback URL missing `code` parameter |
| `SERVER_ERROR` | Telegram server returned non-200 |
| `REQUEST_FAILED` | Network or parsing failure |
| `TELEGRAM_LOGIN_ERROR` | Android generic error |

### `handleUrl(url: string)`

iOS only. Pass a URL received via `Linking` to the SDK. Not needed when using Universal Links (the default).

---

## Security

> **Always validate `idToken` on your backend.**

1. Fetch public keys: `GET https://oauth.telegram.org/.well-known/jwks.json`
2. Verify the JWT signature using the matching key.
3. Check claims: `iss === "https://oauth.telegram.org"`, `aud === YOUR_BOT_ID`, `exp > now`.

Never trust a client-supplied token without cryptographic verification.

---

## Available scopes

| Scope | Data granted |
|---|---|
| `openid` | User ID, auth timestamp |
| `profile` | Name, username, profile photo URL |
| `phone` | Verified phone number |
| `telegram:bot_access` | Permission to message user via bot |

---

## License

MIT
