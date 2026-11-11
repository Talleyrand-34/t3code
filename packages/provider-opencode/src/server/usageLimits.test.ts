import { assert, describe, it } from "@effect/vitest";
import * as Context from "effect/Context";
import * as Effect from "effect/Effect";
import * as NodeServices from "@effect/platform-node/NodeServices";
import * as FileSystem from "effect/FileSystem";
import { HttpClient, HttpClientResponse } from "effect/http";
import * as Layer from "effect/Layer";
import * as Path from "effect/Path";
import * as SqlClient from "effect/sql/SqlClient";
import * as NodeSqliteClient from "@t3tools/shared/nodeSqliteClient";

import { mergeUsageLimits, readOpenCodeIntegrationUsageLimits } from "./usageLimits.ts";

const openDatabase = (filename: string) =>
  Layer.build(NodeSqliteClient.layer({ filename })).pipe(
    Effect.map(Context.get(SqlClient.SqlClient)),
  );

const MINIMAX_RESPONSE = {
  base_resp: { status_code: 0, status_msg: "success" },
  model_remains: [
    {
      model_name: "general",
      current_interval_remaining_percent: 85,
      current_interval_status: 1,
      end_time: 1794328907302,
      current_weekly_remaining_percent: 91,
      current_weekly_status: 1,
      weekly_end_time: 1794905017270,
    },
  ],
};

// MiniMax reports one bucket per quota: the plan every chat model draws from,
// and a separate video allowance that only video models spend.
const MINIMAX_PLAN_AND_VIDEO_RESPONSE = {
  base_resp: { status_code: 0, status_msg: "success" },
  model_remains: [
    {
      model_name: "general",
      current_interval_remaining_percent: 55,
      current_interval_status: 1,
      start_time: 1794310907302,
      end_time: 1794328907302,
      current_weekly_remaining_percent: 86,
      current_weekly_status: 1,
      weekly_start_time: 1794298617270,
      weekly_end_time: 1794905017270,
    },
    {
      model_name: "video",
      current_interval_remaining_percent: 100,
      current_interval_status: 1,
      start_time: 1794238907302,
      end_time: 1794328907302,
      current_weekly_remaining_percent: 100,
      current_weekly_status: 1,
      weekly_start_time: 1794298617270,
      weekly_end_time: 1794905017270,
    },
  ],
};

const readLimits = (response: unknown) =>
  Effect.gen(function* () {
    const fileSystem = yield* FileSystem.FileSystem;
    const path = yield* Path.Path;
    const dir = yield* fileSystem.makeTempDirectoryScoped({ prefix: "opencode-limits-test-" });
    const dataHome = path.join(dir, ".local", "share");
    yield* fileSystem.makeDirectory(path.join(dataHome, "opencode"), { recursive: true });
    const db = yield* openDatabase(path.join(dataHome, "opencode", "opencode.db"));
    yield* db.unsafe(
      "CREATE TABLE credential (id TEXT, integration_id TEXT, value TEXT, active INTEGER, time_created INTEGER)",
    );
    yield* db.unsafe(
      "INSERT INTO credential (id, integration_id, value, active, time_created) VALUES (?, ?, ?, 1, 0)",
      ["cred-1", "minimax-coding-plan", JSON.stringify({ type: "key", key: "sk-test" })],
    );

    const client = HttpClient.make((request) =>
      Effect.succeed(HttpClientResponse.fromWeb(request, Response.json(response))),
    );
    return yield* readOpenCodeIntegrationUsageLimits({
      enabled: true,
      serverUrl: "",
      environment: {
        HOME: dir,
        XDG_DATA_HOME: dataHome,
      },
    }).pipe(Effect.provideService(HttpClient.HttpClient, client));
  });

describe("readOpenCodeIntegrationUsageLimits", () => {
  it.effect("reads an integration's own quota instead of the OpenCode Console budget", () =>
    Effect.gen(function* () {
      const result = yield* readLimits(MINIMAX_RESPONSE);
      assert.strictEqual(result.windows.length, 2);
      const [interval, weekly] = result.windows;
      assert.strictEqual(interval?.id, "minimax-coding-plan_interval");
      assert.strictEqual(interval?.label, "MiniMax Token Plan · Interval");
      assert.strictEqual(interval?.usedPercent, 15);
      assert.strictEqual(weekly?.id, "minimax-coding-plan_weekly");
      assert.strictEqual(weekly?.usedPercent, 9);
      assert.isDefined(interval?.resetsAt);
    }).pipe(Effect.provide(NodeServices.layer)),
  );

  it.effect("reports the plan's own quota instead of pooling it with another bucket", () =>
    Effect.gen(function* () {
      const result = yield* readLimits(MINIMAX_PLAN_AND_VIDEO_RESPONSE);
      assert.strictEqual(result.windows.length, 2);
      const [interval, weekly] = result.windows;
      assert.strictEqual(interval?.id, "minimax-coding-plan_interval");
      assert.strictEqual(interval?.usedPercent, 45);
      assert.strictEqual(weekly?.id, "minimax-coding-plan_weekly");
      assert.strictEqual(weekly?.usedPercent, 14);
    }).pipe(Effect.provide(NodeServices.layer)),
  );
});

describe("mergeUsageLimits", () => {
  it("keeps the integration windows and drops the unavailable marker", () => {
    const merged = mergeUsageLimits(
      {
        checkedAt: "2026-11-10T00:00:00.000Z",
        windows: [],
        unavailable: { reason: "unsupported" },
      },
      [
        {
          id: "minimax-coding-plan_weekly",
          kind: "weekly",
          label: "MiniMax Token Plan · Weekly",
          usedPercent: 9,
        },
      ],
    );
    assert.strictEqual(merged.windows.length, 1);
    assert.isUndefined(merged.unavailable);
  });

  it("keeps the Console fingerprint so one account counts once across environments", () => {
    const merged = mergeUsageLimits(
      {
        checkedAt: "2026-11-10T00:00:00.000Z",
        windows: [],
        credentialFingerprint: "go-key",
        unavailable: { reason: "unsupported" },
      },
      [
        {
          id: "minimax-coding-plan_weekly",
          kind: "weekly",
          label: "MiniMax Token Plan · Weekly",
          usedPercent: 9,
        },
      ],
    );
    assert.strictEqual(merged.credentialFingerprint, "go-key");
    assert.isUndefined(merged.unavailable);
  });

  it("keeps the Console budget untouched when no integration reports a quota", () => {
    const console = {
      checkedAt: "2026-11-10T00:00:00.000Z",
      windows: [],
      unavailable: { reason: "unsupported" as const },
    };
    assert.deepStrictEqual(mergeUsageLimits(console, []), console);
  });
});
