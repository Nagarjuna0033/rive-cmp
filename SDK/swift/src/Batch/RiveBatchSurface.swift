//
//  RiveBatchSurface.swift
//  RiveRuntime
//
//  Created for batched rendering support.
//

import Foundation
import MetalKit
import SwiftUI

#if canImport(UIKit) || RIVE_MAC_CATALYST
import UIKit
#else
import AppKit
#endif

// MARK: - SwiftUI Container

/// A SwiftUI view that renders multiple Rive items on a single shared Metal surface.
///
/// Wrap your `RiveBatchItem` views inside `RiveBatchSurface`. The surface provides a named
/// coordinate space (`"RiveBatchSurface"`) and passes a `RiveBatchCoordinator` to children
/// via `@EnvironmentObject`.
///
/// Example:
/// ```swift
/// RiveBatchSurface {
///     ScrollView {
///         LazyVStack {
///             ForEach(riveInstances) { item in
///                 RiveBatchItem(rive: item.rive)
///                     .frame(height: 200)
///             }
///         }
///     }
/// }
/// ```
@_spi(RiveExperimental)
public struct RiveBatchSurface<Content: View>: View {
    @StateObject private var coordinator = RiveBatchCoordinator()
    private let content: () -> Content

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        ZStack {
            BatchMTKViewRepresentable(coordinator: coordinator)
            content()
        }
        .coordinateSpace(name: "RiveBatchSurface")
        .environmentObject(coordinator)
    }
}

// MARK: - UIViewRepresentable Bridge

#if canImport(UIKit) || RIVE_MAC_CATALYST
private struct BatchMTKViewRepresentable: UIViewRepresentable {
    let coordinator: RiveBatchCoordinator

    func makeUIView(context: Context) -> BatchMTKView {
        BatchMTKView(coordinator: coordinator)
    }

    func updateUIView(_ uiView: BatchMTKView, context: Context) {}
}
#else
private struct BatchMTKViewRepresentable: NSViewRepresentable {
    let coordinator: RiveBatchCoordinator

    func makeNSView(context: Context) -> BatchMTKView {
        BatchMTKView(coordinator: coordinator)
    }

    func updateNSView(_ nsView: BatchMTKView, context: Context) {}
}
#endif

// MARK: - BatchMTKView

/// A native view that hosts a single `MTKView` for batched Rive rendering.
///
/// Mirrors the `RiveUIView` pattern but draws all items registered with the coordinator
/// in a single render pass.
@_spi(RiveExperimental)
public class BatchMTKView: NativeView, MTKViewDelegate, ScaleProvider {
    private let coordinator: RiveBatchCoordinator
    private var mtkView: MTKView?
    private var renderer: Renderer?
    private var setupTask: Task<Void, Never>?

    private var displayLink: DisplayLink? {
        didSet {
            oldValue?.invalidate()
        }
    }

    private var lastTimestamp: TimeInterval?

    // MARK: ScaleProvider

    var nativeScale: CGFloat? {
#if canImport(UIKit) || RIVE_MAC_CATALYST
    #if os(visionOS)
        return nil
    #else
        return window?.windowScene?.screen.nativeScale
    #endif
#else
        return nil
#endif
    }

    var displayScale: CGFloat {
#if canImport(UIKit) || RIVE_MAC_CATALYST
        return traitCollection.displayScale
#else
        return NSScreen.main?.backingScaleFactor ?? 1
#endif
    }

    // MARK: - Init

    init(coordinator: RiveBatchCoordinator) {
        self.coordinator = coordinator
        super.init(frame: .zero)

        #if canImport(UIKit) || RIVE_MAC_CATALYST
        backgroundColor = .clear
        isUserInteractionEnabled = false
        #else
        layer?.backgroundColor = NSColor.clear.cgColor
        #endif

        setupTask = Task { @MainActor [weak self] in
            guard let self else { return }
            await self.setupView()
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init?(coder:) is not implemented")
    }

    // MARK: - View Lifecycle

    #if !os(macOS) || RIVE_MAC_CATALYST
    public override func didMoveToWindow() {
        updateDisplayLink()
    }
    #else
    public override func viewDidMoveToWindow() {
        updateDisplayLink()
    }
    #endif

    @MainActor
    private func setupView() async {
        guard mtkView == nil,
              let device = await MetalDevice.shared.defaultDevice()?.value
        else { return }

        let mtkView = MTKView(frame: bounds, device: device)
        mtkView.delegate = self
        mtkView.isPaused = true
        mtkView.enableSetNeedsDisplay = true
        mtkView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        #if canImport(UIKit) || RIVE_MAC_CATALYST
        mtkView.backgroundColor = .clear
        #else
        mtkView.layer?.backgroundColor = NSColor.clear.cgColor
        #endif
        self.mtkView = mtkView
        addSubview(mtkView)

        mtkView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            mtkView.leadingAnchor.constraint(equalTo: leadingAnchor),
            mtkView.trailingAnchor.constraint(equalTo: trailingAnchor),
            mtkView.topAnchor.constraint(equalTo: topAnchor),
            mtkView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        updateDisplayLink()
    }

    // MARK: - MTKViewDelegate

    public func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    public func draw(in view: MTKView) {
        guard let renderer else { return }
        let drawableSize = view.drawableSize

        // Advance all state machines
        let now = CACurrentMediaTime()
        let delta: TimeInterval
        if let last = lastTimestamp {
            delta = now - last
        } else {
            delta = 0
        }
        lastTimestamp = now

        for (_, descriptor) in coordinator.items {
            descriptor.rive.stateMachine.advance(by: delta)
        }

        // Build batch configurations and draw
        let _ = coordinator.withBatchConfigurations(scaleProvider: self) { pointer, count in
            autoreleasepool {
                guard let device = view.device,
                      let currentDrawable = view.currentDrawable
                else { return }

                renderer.drawBatchConfigurations(
                    pointer,
                    count: UInt(count),
                    surfaceWidth: UInt32(drawableSize.width),
                    surfaceHeight: UInt32(drawableSize.height),
                    surfaceClearColor: 0x00000000,
                    to: currentDrawable.texture,
                    from: device
                ) { commandBuffer in
                    commandBuffer.present(currentDrawable)
                    commandBuffer.commit()
                } onError: { error in
                    // Batch rendering errors are non-fatal; items may have been unregistered
                }
            }
        }
    }

    // MARK: - Display Link

    private func updateDisplayLink() {
        guard window != nil else {
            displayLink = nil
            return
        }

        // Create renderer from the first registered Rive's worker dependencies
        if renderer == nil, let firstRive = coordinator.items.values.first?.rive {
            let deps = firstRive.file.worker.dependencies.workerService.dependencies
            renderer = Renderer(commandQueue: deps.commandQueue, renderContext: deps.renderContext)
        }

        #if !os(macOS) || RIVE_MAC_CATALYST
        let link = DefaultDisplayLink(host: self) { [weak self] in
            self?.tick()
        }
        #else
        let link: DisplayLink
        if #available(macOS 14, *) {
            link = DefaultDisplayLink(host: self) { [weak self] in
                self?.tick()
            }
        } else {
            // Fallback: use MTKView timer-based rendering
            mtkView?.isPaused = false
            mtkView?.enableSetNeedsDisplay = false
            return
        }
        #endif

        link.isPaused = false
        displayLink = link
    }

    private func tick() {
        // Lazily create renderer when first item is registered
        if renderer == nil, let firstRive = coordinator.items.values.first?.rive {
            let deps = firstRive.file.worker.dependencies.workerService.dependencies
            renderer = Renderer(commandQueue: deps.commandQueue, renderContext: deps.renderContext)
        }

        guard !coordinator.items.isEmpty else { return }

        #if !os(macOS) || RIVE_MAC_CATALYST
        mtkView?.setNeedsDisplay()
        #else
        mtkView?.needsDisplay = true
        #endif
    }
}
