# T3 Peer

A small native Android app that talks to a T3 Code server **directly**, over your
own network. There is no cloud service in the path: no T3 Connect tunnel, no
account, no relay. The phone and the server talk to each other.

This is a companion to [`apps/mobile`](../mobile), the full React Native app that
ships to Google Play and the App Store. This one is deliberately small: it holds
one connection, lists the server's threads, opens one, and sends messages.

## What it does

- Pair with a server using the one-time code from `t3 pair`, or open a pairing
  link with the app.
- List the threads on that server, with their project and state.
- Open a thread, read it, and watch it fill while the agent works.
- Send a message and let the server run the turn.
- Stay connected: the session is kept, a dropped socket reconnects on its own,
  and a host that goes away is noticed.

## What it does not do

Attachments, plan editing, pull requests, thread creation, approvals, and the
settings screens all live in the full app. This one is a reader and a way to
start a turn.

## Pairing with a host

The host has to accept connections from your network. On a command-line host:

```bash
t3 up --lan          # or: t3 serve --lan
```

On a desktop host, open **Settings → Sharing & access** and turn on **Network
access**.

Then, on the host:

```bash
t3 pair
```

It prints a link such as `http://192.168.1.20:3923/#token=abc123`. Scan it, or
paste it into the app's **Server address** field — the code comes with it.

The host also prints an app link, `t3code://pair?pairingUrl=…`. This app
registers that scheme, so opening the link on the phone fills the form in and
connects.

The code works once. What the phone keeps afterwards is the session it granted,
which is good for a month, so the app comes back on its own the next morning.

If the host speaks HTTPS with a certificate from your own certificate
authority, install that CA on the phone under **Security → Encryption &
credentials → Install a certificate → CA certificate**.

## Building

Needs a JDK 17 or newer and an Android SDK with platform 35.

```bash
cd apps/android
ANDROID_HOME=/path/to/android-sdk ./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy it to the
phone and open it there.

## How the connection works

Three steps, all of them between the phone and the server:

1. `POST /oauth/token` trades the pairing code for an access token.
2. `POST /api/auth/websocket-ticket/rpc` trades that for a WebSocket ticket.
3. `GET /ws?wsTicket=…&orchestrationProtocol=2` opens the socket, which then
   carries T3 Code's RPC: JSON frames with a `_tag`, a request id, and a
   payload.

The app never sends a DPoP proof and never asks for a scope it does not need;
the scopes it gets are the ones the pairing code was created with. Narrow them
on the host if you like:

```bash
t3 pair --scope orchestration:read orchestration:operate
```

After the first pairing the app reuses the session, so the code is not needed
again. The socket is checked every ten seconds, and a host that stops
answering is treated as a dropped connection: the app retries with a backoff
of up to a minute.

## License

MIT, like the rest of T3 Code.
