import { DEFAULT_CLIENT_SETTINGS, EnvironmentId, ProjectId, ThreadId } from "@t3tools/contracts";
import { describe, expect, it, vi } from "vite-plus/test";
import { userEvent } from "vite-plus/test/browser";

import { render } from "../../test/render";
import { openCommandPalette } from "../commandPaletteBus";
import { useHandleNewThread } from "../hooks/useHandleNewThread";
import { __setClientSettingsForTests, getClientSettings } from "../hooks/useSettings";
import { useProjects, useQuietThreadShells } from "../state/entities";
import { makeThreadFixture } from "../test-fixtures";
import type { Project } from "../types";
import Sidebar from "./Sidebar";
import { SidebarProvider } from "./ui/sidebar";

vi.mock("../state/entities", { spy: true });
vi.mock("../hooks/useHandleNewThread", { spy: true });
vi.mock("../commandPaletteBus", () => ({
  openCommandPalette: vi.fn(),
  isCommandPaletteOpen: () => false,
}));
vi.mock("./sidebar/SidebarChrome", () => ({
  SidebarChromeHeader: () => null,
  SidebarChromeFooter: () => null,
}));

function project(id: string): Project {
  return {
    id: ProjectId.make(id),
    environmentId: EnvironmentId.make("local"),
    title: id,
    workspaceRoot: `/projects/${id}`,
    repositoryIdentity: null,
    defaultModelSelection: null,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    scripts: [],
  };
}

describe("sidebar header new thread", () => {
  it.each([
    { mode: "normal", count: 2, shift: false, picker: true },
    { mode: "boring", count: 2, shift: false, picker: true },
    { mode: "boring", count: 2, shift: true, picker: false },
    { mode: "boring", count: 1, shift: false, picker: false },
  ] as const)("$mode sidebar, $count projects, shift=$shift: picker=$picker", async (scenario) => {
    using stack = new DisposableStack();
    const previousSettings = getClientSettings();
    stack.defer(() => __setClientSettingsForTests(previousSettings));
    __setClientSettingsForTests({
      ...DEFAULT_CLIENT_SETTINGS,
      boringSidebarEnabled: scenario.mode === "boring",
    });
    const projects = [project("active"), project("idle")].slice(0, scenario.count);
    const currentProjectRef = {
      environmentId: projects[0]!.environmentId,
      projectId: projects[0]!.id,
    };
    const startThread = vi.fn().mockResolvedValue(null);
    vi.mocked(useProjects).mockReturnValue(projects);
    vi.mocked(useHandleNewThread).mockReturnValue({
      activeDraftThread: null,
      activeThread: null,
      defaultProjectRef: currentProjectRef,
      handleNewThread: startThread,
      routeDraftId: null,
      routeThreadRef: null,
    });
    vi.mocked(openCommandPalette).mockClear();
    await using screen = await render(
      <SidebarProvider>
        <Sidebar routeReveal={{ observedKey: null, pendingKey: null }} />
      </SidebarProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "New thread", exact: true }), {
      modifiers: scenario.shift ? ["Shift"] : [],
    });

    if (scenario.picker) {
      expect(openCommandPalette).toHaveBeenCalledExactlyOnceWith({ open: "new-thread-in" });
      expect(startThread).not.toHaveBeenCalled();
    } else {
      expect(openCommandPalette).not.toHaveBeenCalled();
      expect(startThread).toHaveBeenCalledExactlyOnceWith(currentProjectRef);
    }
  });
});

describe("the Agents shelf", () => {
  it("lists an agent thread under its own header and folds it away", async () => {
    using stack = new DisposableStack();
    const previousSettings = getClientSettings();
    stack.defer(() => __setClientSettingsForTests(previousSettings));
    __setClientSettingsForTests({ ...DEFAULT_CLIENT_SETTINGS });
    window.localStorage.removeItem("t3code:sidebar:agents-expanded");
    vi.mocked(useProjects).mockReturnValue([project("active")]);
    vi.mocked(useHandleNewThread).mockReturnValue({
      activeDraftThread: null,
      activeThread: null,
      defaultProjectRef: null,
      handleNewThread: vi.fn().mockResolvedValue(null),
      routeDraftId: null,
      routeThreadRef: null,
    });
    const parentId = ThreadId.make("thread-parent");
    const parent = makeThreadFixture({
      id: parentId,
      environmentId: EnvironmentId.make("local"),
      projectId: ProjectId.make("active"),
      title: "Parent",
    });
    const agent = makeThreadFixture({
      id: ThreadId.make("thread-agent"),
      environmentId: EnvironmentId.make("local"),
      projectId: ProjectId.make("active"),
      title: "Agent",
      lineage: {
        rootThreadId: parentId,
        parentThreadId: parentId,
        relationshipToParent: "subagent",
      },
    });
    vi.mocked(useQuietThreadShells).mockReturnValue([parent, agent]);

    await using screen = await render(
      <SidebarProvider>
        <Sidebar routeReveal={{ observedKey: null, pendingKey: null }} />
      </SidebarProvider>,
    );

    const toggle = screen.getByTestId("sidebar-agents-shelf-toggle");
    const row = screen.getByTestId("sidebar-row-slim");
    await expect.element(toggle).toHaveAttribute("aria-expanded", "false");
    expect(row.query()).toBeNull();

    await userEvent.click(toggle);
    await expect.element(toggle).toHaveAttribute("aria-expanded", "true");
    await vi.waitFor(() => expect(row.element().textContent).toContain("Agent"));
  });
});

describe("the Idle shelf", () => {
  it("folds the threads no agent is working on away", async () => {
    using stack = new DisposableStack();
    const previousSettings = getClientSettings();
    stack.defer(() => __setClientSettingsForTests(previousSettings));
    __setClientSettingsForTests({ ...DEFAULT_CLIENT_SETTINGS });
    window.localStorage.removeItem("t3code:sidebar:idle-expanded");
    vi.mocked(useProjects).mockReturnValue([project("active")]);
    vi.mocked(useHandleNewThread).mockReturnValue({
      activeDraftThread: null,
      activeThread: null,
      defaultProjectRef: null,
      handleNewThread: vi.fn().mockResolvedValue(null),
      routeDraftId: null,
      routeThreadRef: null,
    });
    vi.mocked(useQuietThreadShells).mockReturnValue([
      makeThreadFixture({
        id: ThreadId.make("thread-idle"),
        environmentId: EnvironmentId.make("local"),
        projectId: ProjectId.make("active"),
        title: "Idle thread",
      }),
    ]);

    await using screen = await render(
      <SidebarProvider>
        <Sidebar routeReveal={{ observedKey: null, pendingKey: null }} />
      </SidebarProvider>,
    );

    const toggle = screen.getByTestId("sidebar-idle-shelf-toggle");
    const row = screen.getByTestId("sidebar-row-card");
    await expect.element(toggle).toHaveAttribute("aria-expanded", "true");
    await vi.waitFor(() => expect(row.element().textContent).toContain("Idle thread"));

    await userEvent.click(toggle);
    await expect.element(toggle).toHaveAttribute("aria-expanded", "false");
    await vi.waitFor(() => expect(row.query()).toBeNull());
  });
});

describe("the project folder button", () => {
  it("folds and unfolds every project folder", async () => {
    using stack = new DisposableStack();
    const previousSettings = getClientSettings();
    stack.defer(() => __setClientSettingsForTests(previousSettings));
    __setClientSettingsForTests({ ...DEFAULT_CLIENT_SETTINGS, boringSidebarEnabled: true });
    vi.mocked(useProjects).mockReturnValue([project("active"), project("idle")]);
    vi.mocked(useHandleNewThread).mockReturnValue({
      activeDraftThread: null,
      activeThread: null,
      defaultProjectRef: null,
      handleNewThread: vi.fn().mockResolvedValue(null),
      routeDraftId: null,
      routeThreadRef: null,
    });
    vi.mocked(useQuietThreadShells).mockReturnValue([
      makeThreadFixture({
        id: ThreadId.make("thread-active"),
        environmentId: EnvironmentId.make("local"),
        projectId: ProjectId.make("active"),
        title: "Active thread",
      }),
      makeThreadFixture({
        id: ThreadId.make("thread-idle"),
        environmentId: EnvironmentId.make("local"),
        projectId: ProjectId.make("idle"),
        title: "Idle thread",
      }),
    ]);

    await using screen = await render(
      <SidebarProvider>
        <Sidebar routeReveal={{ observedKey: null, pendingKey: null }} />
      </SidebarProvider>,
    );

    const folders = () => screen.getByTestId("sidebar-project-folder").elements();
    const expanded = () =>
      folders().map((folder) => folder.querySelector("button")?.getAttribute("aria-expanded"));
    await vi.waitFor(() => expect(folders().length).toBe(2));
    expect(expanded()).toEqual(["true", "true"]);

    await userEvent.click(screen.getByRole("button", { name: "Collapse all projects" }));
    await vi.waitFor(() => expect(expanded()).toEqual(["false", "false"]));

    await userEvent.click(screen.getByRole("button", { name: "Expand all projects" }));
    await vi.waitFor(() => expect(expanded()).toEqual(["true", "true"]));
  });
});
