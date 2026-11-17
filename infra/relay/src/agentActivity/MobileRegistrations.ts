import { makeAggregateState } from "./agentActivityAggregate.ts";
import type {
  RelayAgentActivitySnapshotResponse,
  RelayDeviceRegistrationRequest,
  RelayLiveActivityRegistrationRequest,
} from "@t3tools/contracts/relay";
import * as DateTime from "effect/DateTime";
import * as Context from "effect/Context";
import * as Effect from "effect/Effect";
import * as Layer from "effect/Layer";

import * as AgentActivityRows from "./AgentActivityRows.ts";
import * as Devices from "./Devices.ts";
import * as LiveActivities from "./LiveActivities.ts";
import { buildDevicePayload } from "./agentActivityDelivery.ts";
import * as AgentActivityPublisher from "./AgentActivityPublisher.ts";

export type MobileRegistrationError =
  | Devices.DeviceRegistrationPersistenceError
  | Devices.DeviceUnregistrationPersistenceError
  | LiveActivities.LiveActivityRegistrationPersistenceError
  | AgentActivityRows.AgentActivityRowListPersistenceError
  | LiveActivities.LiveActivityTargetListPersistenceError
  | LiveActivities.LiveActivityDeliveryMarkPersistenceError;

export class MobileRegistrations extends Context.Service<
  MobileRegistrations,
  {
    readonly registerDevice: (input: {
      readonly userId: string;
      readonly payload: RelayDeviceRegistrationRequest;
    }) => Effect.Effect<{ readonly ok: true }, MobileRegistrationError>;
    readonly registerLiveActivity: (input: {
      readonly userId: string;
      readonly payload: RelayLiveActivityRegistrationRequest;
    }) => Effect.Effect<{ readonly ok: true }, MobileRegistrationError>;
    readonly unregisterDevice: (input: {
      readonly userId: string;
      readonly deviceId: string;
    }) => Effect.Effect<{ readonly ok: true }, MobileRegistrationError>;
    readonly getAgentActivitySnapshot: (input: {
      readonly userId: string;
      /** The device the payload is for. Without it the snapshot carries no payload. */
      readonly deviceId?: string | undefined;
    }) => Effect.Effect<RelayAgentActivitySnapshotResponse, MobileRegistrationError>;
  }
>()("t3code-relay/agentActivity/MobileRegistrations") {}

const make = Effect.gen(function* () {
  const rows = yield* AgentActivityRows.AgentActivityRows;
  const devices = yield* Devices.Devices;
  const liveActivities = yield* LiveActivities.LiveActivities;
  const publisher = yield* AgentActivityPublisher.AgentActivityPublisher;

  return MobileRegistrations.of({
    registerDevice: Effect.fn("relay.mobile_registrations.register_device")(function* (input) {
      yield* Effect.annotateCurrentSpan({
        "relay.mobile.device_id": input.payload.deviceId,
        "relay.mobile.platform": input.payload.platform,
      });
      yield* devices.register({ userId: input.userId, registration: input.payload });
      yield* publisher
        .replayForLiveActivityRegistration({
          userId: input.userId,
          deviceId: input.payload.deviceId,
        })
        .pipe(
          Effect.tapError((error) =>
            Effect.logWarning("device registration activity replay failed", {
              errorTag: error._tag,
            }),
          ),
          Effect.ignore,
        );
      return { ok: true as const };
    }),
    registerLiveActivity: Effect.fn("relay.mobile_registrations.register_live_activity")(
      function* (input) {
        yield* Effect.annotateCurrentSpan({
          "relay.mobile.device_id": input.payload.deviceId,
        });
        yield* liveActivities.register({ userId: input.userId, registration: input.payload });
        yield* publisher
          .replayForLiveActivityRegistration({
            userId: input.userId,
            deviceId: input.payload.deviceId,
          })
          .pipe(
            Effect.tapError((error) =>
              Effect.logWarning("live activity registration replay failed", {
                errorTag: error._tag,
              }),
            ),
            Effect.ignore,
          );
        return { ok: true as const };
      },
    ),
    getAgentActivitySnapshot: Effect.fn("relay.mobile_registrations.get_agent_activity_snapshot")(
      function* (input) {
        const activeStates = yield* rows.listForUser({ userId: input.userId });
        const now = yield* DateTime.now;
        const aggregate = makeAggregateState({
          activeStates,
          terminalState: null,
          nowMs: now.epochMilliseconds,
        });
        if (!input.deviceId) return { aggregate };
        // The payload is the device's own view: its preferences and its delivery baseline.
        const targets = yield* liveActivities.listTargets({ userId: input.userId });
        const target = targets.find((row) => row.device_id === input.deviceId);
        if (!target) return { aggregate };
        const payload = buildDevicePayload({
          userId: input.userId,
          deviceId: target.device_id,
          preferencesJson: target.preferences_json,
          previousAggregateJson: target.last_aggregate_json,
          aggregate,
          nowMs: now.epochMilliseconds,
        });
        if (!payload) return { aggregate };
        yield* liveActivities
          .markDelivery({
            userId: input.userId,
            deviceId: target.device_id,
            kind: "live_activity_update",
            aggregate,
            deliveredAt: DateTime.formatIso(now),
          })
          .pipe(Effect.ignore);
        return { aggregate, payload };
      },
    ),
    unregisterDevice: Effect.fn("relay.mobile_registrations.unregister_device")(function* (input) {
      yield* Effect.annotateCurrentSpan({
        "relay.mobile.device_id": input.deviceId,
      });
      yield* devices.unregister(input);
      return { ok: true as const };
    }),
  });
});

export const layer = Layer.effect(MobileRegistrations, make);
