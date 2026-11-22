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
- List the threads on that server, with their project and state. The list is
  grouped the way the desktop app groups it: the threads that wait for you,
  then the ones that are working, then the ones that finished, then the idle
  ones, and every project wears a colour and a monogram of its own.
- Narrow the list to one project, and search it: a thread whose own title
  matches comes first, and the ones a message inside them matched follow.
- Answer a question the agent asked on the thread, or pick one of the options
  it offered.
- Approve what the agent asks for, on the thread: allow it once, allow it for
  the session, or decline it.
- Attach a photo or a file to a message. The file is scaled or sent to the
  server, stored by it, and shown in the thread.
- Open a thread, read it, and watch it fill while the agent works.
- See the messages the server took but has not run yet, and the subagents of
  the thread you are in.
- Send a message and let the server run the turn.
- Send a message while the agent is still working, and say what it should do:
  wait for the turn to finish, or reach the agent right away. The setting says
  which one you usually want; holding the send button picks the other one for
  a single message.
- Stop the agent. Stopping holds the messages that wait, so the queue says it
  is paused and offers to let it go again.
- Change a message that waits, take it out of the queue, or send it on its own
  while nothing else is working.
- Stay connected: the session is kept, a dropped socket reconnects on its own,
  and a host that goes away is noticed.
- Get a notification when the agent needs you, as long as the app is running.
- Start a new thread from the phone, on the model and mode the project uses.
- Paint itself light, dark, or follow the system, from the settings sheet.
- Move the thread to another branch, from the branch in its header.

## What it does not do

Plan editing, pull requests, thread diffs, checkpoints, the terminal, file
browsing, browser previews, MCP apps, and the settings screens all live in the
full app. This one is a reader and a way to run a turn.

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

The host also prints an app link for each client. This app registers
`t3peer://pair?pairingUrl=…`; the full mobile app registers `t3code://`, so a
phone with both installed still opens the right one. Opening the link fills the
form in and connects.

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

The `-r` matters: it replaces the app instead of removing it first, so the
pairing survives the update. The one thing that does clear the pairing is
removing the app, which Android also demands when the new APK is signed with a
different key.

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

## What the app asks the server to do

Every command goes through the server's `orchestration.dispatchCommand`, so the
pairing scopes above are enough for all of it.

| What the user does                                          | What the app sends                                                                                  |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Sends a message while the agent works, and wants it to wait | `message.dispatch` with `dispatchMode: queue_after_active`                                          |
| Sends a message while the agent works, and wants it now     | `message.dispatch` with `deliveryIntent: auto`, so a provider that cannot steer takes it as a queue |
| Stops the agent                                             | `run.interrupt` with `holdQueue`, so the messages that wait stay waiting                            |
| Lets the queue go again                                     | `queue.resume`                                                                                      |
| Sends a queued message on its own                           | `queued-run.start`, or `queued-message.promote-to-steer` into the turn that is working              |
| Changes or removes a queued message                         | `queued-run.edit`, `queued-run.cancel`                                                              |
| Approves something                                          | `runtime-request.respond` with a decision                                                           |
| Answers a question                                          | `runtime-request.respond` with answers                                                              |
| Starts a new thread                                         | `orchestration.launchThread`                                                                        |

## License

MIT, like the rest of T3 Code.
