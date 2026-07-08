import AppKit
import Bonsplit
import GhosttyKit
import Testing
import CmuxTerminalCore
@testable import CmuxTerminal

@MainActor
@Suite struct TerminalSurfacePortalLeaseTests {
    @Test func rearmedHostCannotReclaimAfterReplacementWindowCallback() {
        let surface = makeSurface()
        let pane = PaneID(id: UUID())
        let oldHost = NSObject()
        let newHost = NSObject()
        let oldHostId = ObjectIdentifier(oldHost)
        let newHostId = ObjectIdentifier(newHost)

        #expect(surface.claimPortalHost(
            hostId: oldHostId,
            paneId: pane,
            instanceSerial: 1,
            inWindow: true,
            bounds: CGRect(x: 0, y: 0, width: 400, height: 300),
            reason: "test.initial"
        ))

        #expect(surface.preparePortalHostReplacementIfOwned(
            hostId: oldHostId,
            reason: "test.dismantle"
        ))

        #expect(surface.claimPortalHost(
            hostId: newHostId,
            paneId: pane,
            instanceSerial: 2,
            inWindow: true,
            bounds: CGRect(x: 0, y: 0, width: 400, height: 300),
            reason: "test.newHost"
        ))

        #expect(!surface.claimPortalHost(
            hostId: oldHostId,
            paneId: pane,
            instanceSerial: 1,
            inWindow: true,
            bounds: CGRect(x: 0, y: 0, width: 400, height: 300),
            reason: "test.staleDidMoveToWindow"
        ))
    }

    private func makeSurface() -> TerminalSurface {
        let nativeView = FakeTerminalSurfaceNativeView(frame: NSRect(x: 0, y: 0, width: 800, height: 600))
        let paneHost = FakeTerminalSurfacePaneHost(surfaceView: nativeView)
        return TerminalSurface(
            tabId: UUID(),
            context: GHOSTTY_SURFACE_CONTEXT_SPLIT,
            configTemplate: nil,
            runtimeSpawnPolicy: .pacedSessionRestore,
            dependencies: TerminalSurfaceRuntimeDependencies(
                registry: FakeSurfaceRegistry(),
                engine: FakeTerminalEngine(),
                viewProvider: FakeTerminalSurfaceViewProvider(surfaceView: nativeView, paneHost: paneHost),
                spawnPolicy: FakeSpawnPolicyProvider(),
                byteTee: FakeTerminalByteTee(),
                rendererRealization: FakeRendererRealizationScheduler(),
                hibernationRecorder: FakeHibernationRecorder(),
                runtimeTeardown: TerminalSurfaceRuntimeTeardownCoordinator(),
                restoreSpawnScheduler: TerminalSurfaceRestoreSpawnScheduler(interSpawnDelay: .zero),
                runtimeFilesystem: TerminalSurfaceRuntimeFilesystem(
                    claudeCommandShimTemporaryDirectory: URL(fileURLWithPath: "/tmp/cmux-terminal-tests", isDirectory: true),
                    installClaudeCommandShim: { _, _, _ in nil },
                    isExecutableFile: { _ in false }
                ),
                sessionPortBase: 40_000,
                sessionPortRangeSize: 100,
                scrollbackReplayEnvironmentKey: "CMUX_TEST_SCROLLBACK_REPLAY"
            )
        )
    }
}
