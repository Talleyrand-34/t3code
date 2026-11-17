# Android notifications

The Android app has no push transport. It carries no Google Play Services or Firebase code, so it installs and runs on devices without them. Agent activity arrives over the app's own connection and is rendered by the native module, which posts the notification and the ongoing activity card itself.

## Android compatibility and automated checks

The app's minimum is Android 7.0 (API 24), declared in `app.config.ts` and enforced by the relay's device-registration schema. Compile/target SDK versions follow the locked Expo/React Native toolchain (currently API 36). Notification channels begin at API 26; the notification permission prompt begins at API 33. Live Update promotion requires API 36 and remains subject to system settings and device support. Alerts and ordinary activity cards work below API 36.

On Android 16+, open T3 Code Settings → Live Update Settings to allow status bar chips. Android controls this separately from notification permission. The chip reads `Working` during work, `Approve` for approvals, and `Answer` for input requests; completed work returns to a normal notification. Use an Android 16 QPR2 or newer emulator image to verify the shipped promotion behavior.

API 24–25 use a single inexact system alarm to expire cards after process exit, with no exact-alarm permission. Android can delay that alarm in power-saving modes. API 26+ use notification timeouts. Disabling activity, dismissal, account changes and sign-out cancel the legacy alarm. A stale expiry broadcast cannot remove a newer run's card.

The native notification tests cover API 24, 26, 33 and 36 (plus API 25 for legacy expiry) using Robolectric. To compile the module, run these tests and run Android lint, use the following command from a generated `apps/mobile/android` project with JDK 21 available. No signing or relay secrets are required:

```sh
./gradlew :t3-agent-notifications:testDebugUnitTest :t3-agent-notifications:lintRelease -Pandroid.lint.useK2Uast=false
```

Robolectric's API 36 runtime requires JDK 21; module compilation still uses Expo's Java 17 toolchain. The existing Mobile Native Static Analysis job separately runs ktlint and detekt. The native fingerprint check marks this change as requiring a new binary; the production workflow cannot deliver it to an older binary by OTA. Settings disable Android notifications if the installed native module is missing required methods.

The lint command uses the K1 frontend because AGP's K2 frontend crashes while analyzing Worklets 0.10's Gradle Kotlin scripts. This does not disable lint checks. Live Update eligibility must be verified on a device: Robolectric's API 36 image implements older promotion rules that require colorization, while shipped Live Updates require uncolorized notifications.

## App build

There is no Firebase project, no `google-services.json`, and no service account to configure. The Android build excludes the Firebase messaging transport, the Play Services code and document scanners, the Play Services sign-in and passkey providers, and Firebase's installation and measurement clients.

The QR scanner reads codes with `com.google.zxing:core`, a plain Java library, so it works on a device without Play Services and adds no Google code to the APK. The scanner lives in a patched `expo-camera`; `patches/expo-camera@58.0.10.patch` carries the decoder and its tests. Run those tests from a generated `apps/mobile/android` project:

```sh
./gradlew :expo-camera:testDebugUnitTest
```

A JavaScript-only update cannot install the native notification handler. Build a new Android binary and, for hosted delivery, deploy the matching relay.

For a local development build, from `apps/mobile`:

```sh
APP_VARIANT=development vp run android:dev
```

If deploying a separate hosted relay, configure the build's T3 Connect public settings for that relay and Clerk application as described in [T3 Connect](../internals/t3-connect.md).

Set `T3CODE_MOBILE_UPDATES_ENABLED=0` before prebuild and bundling a private binary to disable the repository's configured Expo OTA update source. A debug development-client APK requires Metro; a bundled release build is needed to verify cold-start notification taps without Expo's development launcher.

## Clerk sign-in for private builds

Clerk's native Android sign-in uses `clerk://<applicationId>.callback`. In the Clerk instance selected by the build's publishable key, its administrator must allow the exact callback under **Native applications > Allowlist for mobile SSO redirect**. For the development package, add:

```text
clerk://com.t3tools.t3code.dev.callback
```

The app already declares the matching callback receiver. A "redirect url ... does not match an authorized redirect URI" error requires a Clerk configuration change; rebuilding the same APK does not fix it. Reopen sign-in after the administrator saves the entry. See [Android native sign-in redirects](./connect-setup.md#android-native-sign-in-redirects) for the other variants.

Using T3's existing production publishable key selects the maintainers' Clerk instance. It grants no access to change that instance's allowlist. The chosen package's callback must already be allowed or be added by that instance's administrator. Android device registration and hosted delivery separately require the relay deployment below. A successful direct-pairing does not verify hosted sign-in or device registration.

Building with `APP_VARIANT=production` selects `com.t3tools.t3code` and its corresponding Clerk callback. Set the same variant during prebuild and bundling. Keep OTA updates disabled for a private binary. A locally signed build with this package cannot update an official installation signed by the maintainer or coexist with it; removing that installation also removes its app-local data. The development package remains a separate app.

## Focused delivery check

The relay has no push transport for Android. It answers the app's agent-activity snapshot with the payload the device renders, and the app hands that to the native module.

The checks below describe what the native module does with a delivered payload: the notification, the ongoing activity card, dismissal, and tap routing. Run them against a payload delivered to the running app.

With the app in the foreground, Android suppresses an alert only for the thread currently on screen, matching iOS notification presentation; alerts for other threads still show. Activity cards still update in the foreground and retain finished results silently. Check that completion stays quiet while its thread is open, alerts while another screen is open, alerts after backgrounding, and that retrying a suppressed alert does not show it later. This uses the app lifecycle and route on the receiving phone, not thread visibility on other clients.

With ongoing activity enabled, verify two threads entering approval/input together produce one `2 agents need attention` alert, and two observed active threads completing/failing together produce one `2 agents finished` alert. The body lists their titles. The relay shares iOS transition selection and retains its delivered baseline when work finishes; publishing the same states again must not produce another alert. Grouped alerts open the aggregate’s priority thread; individual alerts retain their thread link.

Verify an expanded card with five threads, attention/failure priority, project names and statuses. When all work finishes, the card should show **Agent work completed** or **Agent work failed**, lose its ongoing/promotion flag, and expire 15 minutes after the newest displayed result. Replays must not extend that deadline. Quiet running work uses the relay’s two-hour state lifetime; approval/input states use 24 hours. Reopen the same signed-in app and confirm existing alerts and dismissal survive, with a silent aggregate replay on cold start or a foreground after at least 60 seconds. Also check empty replays remove an orphaned card and completions older than two minutes never alert, even with ongoing activity disabled.

After Android prebuild, run the native presentation regression tests from `apps/mobile/android`:

```sh
./gradlew :t3-agent-notifications:testDebugUnitTest --tests expo.modules.t3agentnotifications.AgentNotificationsTest
```

## Relay deployment

The relay has no push transport for Android. It answers the app's agent-activity
snapshot with the payload the device renders, and the app polls it while it is in front
of the user. Nothing in the relay or the app talks to Google.

### What the relay needs

Nothing beyond its usual configuration. The Firebase service account, the FCM delivery
queue and its dead-letter queue are gone; the APNs path for iOS is unchanged.

The snapshot endpoint answers with the aggregate every client already reads, and, when
the request names a device, with that device's payload:

```
GET /v1/mobile/agent-activity?deviceId=<device id>
```

The payload is the same shape the native handler has always read — `t3_kind`,
`device_id`, `user_id`, `updated_at`, the `activity_*` fields for the card, and the
`alert_*` fields when something needs attention. The relay builds it from the device's own
preferences and its delivery baseline, so the alert fires once per transition.

### What the app does with it

The app polls the endpoint while it is in the foreground, and hands each payload to the
native module, which renders the ongoing activity card and raises the alert. The wait is
short while agents are working and long when they are not.

### Hosted delivery

The existing Alchemy deployment provisions Cloudflare Workers, the APNs delivery queue,
Hyperdrive, tunnel/DNS resources, PlanetScale Postgres, and Axiom observability by default.
It requires credentials for the enabled services and private Clerk configuration; the
repository's public app settings do not grant deployment access. Set `APNS_ENABLED=false`
in an Android-only development relay to skip Apple delivery and its credential
requirements. APNs remains enabled by default.

A maintainer with access to the existing Alchemy state and deployment credentials can
deploy to a personal stage. Non-production stages reference the retained database and DNS
zones owned by the `prod` stage, create a separate PlanetScale branch, and apply migrations
to that branch. A personal stage is therefore not a standalone deployment into an unrelated
account.

1. Apply the changes to a checkout with the existing deployment credentials. Create a
   private `infra/relay/.env.android-dev` using the existing Cloudflare, PlanetScale,
   Axiom, domain, and Clerk configuration described in the
   [relay README](../../infra/relay/README.md#deployment-ci). Keep
   `CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`, and `CLERK_JWT_AUDIENCE` on the same Clerk
   instance used by the test clients.
2. Leave `RELAY_DOMAIN` unset so the deployment derives a hostname for the personal stage
   instead of using the production hostname.
3. From the repository root, inspect the deployment plan, then deploy the same stage:

   ```sh
   vp run --filter t3code-relay deploy --stage dev_ryan_android --env-file .env.android-dev --dry-run
   vp run --filter t3code-relay deploy --stage dev_ryan_android --env-file .env.android-dev
   ```

4. Give the tester the deployed relay URL and matching public Clerk configuration. The
   deploy wrapper also writes the relay URL and public tracing configuration into that
   checkout's root `.env`. Rebuild the private APK with this `T3CODE_RELAY_URL` and OTA
   updates disabled. If using the separate development package, authorize its Clerk
   callback as described above.
5. Configure one isolated T3 server with the same relay URL and link that test environment
   through the new relay. Existing production relay links do not automatically move to a
   personal stage. Enable activity publishing for the test environment, enable
   notifications on the phone, and verify a real agent turn produces a running update and a
   completion alert while the app is in the foreground.

The maintainer can perform deployment themselves and return only the public client
configuration; the tester does not need copies of their hosting or Clerk server credentials.
A fully independent deployment needs its own initial Cloudflare stack, PostgreSQL database,
and a Clerk instance the operator can configure. Its Alchemy deployment needs PlanetScale
and Axiom credentials.

Build the host client and mobile app with the same relay URL and Clerk public
configuration. A source server or desktop development build can host the test environment;
keep its T3 home separate from an existing installation. Signing into the phone alone does
not link a host environment. Use the host client's T3 Connect settings to link it and
enable activity publishing. A private Clerk instance also needs its own CLI OAuth
application before using `t3 connect login`; the repository's production CLI client ID
belongs to the maintainers' instance.
