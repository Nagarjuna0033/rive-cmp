//
//  RiveBatchCoordinator.swift
//  RiveRuntime
//
//  Created for batched rendering support.
//

import Foundation
import Combine

/// Describes a single item's Rive instance and its viewport position within the batch surface.
@_spi(RiveExperimental)
@MainActor
public final class BatchItemDescriptor {
    /// The Rive instance to render.
    public let rive: Rive
    /// The viewport frame in pixels (relative to the batch surface's drawable coordinate space).
    public var viewportFrame: CGRect

    public init(rive: Rive, viewportFrame: CGRect) {
        self.rive = rive
        self.viewportFrame = viewportFrame
    }
}

/// Coordinates multiple Rive items for batched rendering on a single shared surface.
///
/// Items register and unregister themselves as they appear/disappear. On each frame,
/// the batch surface calls `fillBatchConfigurations` to build the array of
/// `BatchRendererConfiguration` structs passed to the renderer.
@_spi(RiveExperimental)
@MainActor
public class RiveBatchCoordinator: ObservableObject {
    /// Registered items keyed by an arbitrary hashable identifier.
    private(set) var items: [AnyHashable: BatchItemDescriptor] = [:]

    /// Pre-allocated configuration array. Only reallocated when item count changes.
    private var cachedConfigurations: [BatchRendererConfiguration] = []

    public init() {}

    /// Registers or updates a batch item descriptor.
    public func register(key: AnyHashable, descriptor: BatchItemDescriptor) {
        let isNew = items[key] == nil
        items[key] = descriptor
        if isNew {
            cachedConfigurations.append(BatchRendererConfiguration())
        }
    }

    /// Removes a batch item.
    public func unregister(key: AnyHashable) {
        guard items.removeValue(forKey: key) != nil else { return }
        if cachedConfigurations.count > items.count {
            cachedConfigurations.removeLast()
        }
    }

    /// Fills the cached configuration array for the current frame and calls the provided closure
    /// with a pointer to the contiguous buffer.
    ///
    /// - Parameters:
    ///   - scaleProvider: Provides display scale for layout calculations.
    ///   - body: Closure receiving the buffer pointer and count. Called synchronously.
    /// - Returns: The Rive instances corresponding to each configuration (for advancing state machines).
    func withBatchConfigurations(
        scaleProvider: ScaleProvider,
        body: (UnsafePointer<BatchRendererConfiguration>, Int) -> Void
    ) -> [Rive] {
        let count = items.count
        guard count > 0 else { return [] }

        var rives: [Rive] = []
        rives.reserveCapacity(count)

        var index = 0
        for (_, descriptor) in items {
            let rive = descriptor.rive
            let frame = descriptor.viewportFrame
            let fitBridge = rive.fit.bridged(from: scaleProvider)

            cachedConfigurations[index] = BatchRendererConfiguration(
                configuration: RendererConfiguration(
                    artboardHandle: rive.artboard.artboardHandle,
                    stateMachineHandle: rive.stateMachine.stateMachineHandle,
                    fit: fitBridge.fit,
                    alignment: fitBridge.alignment,
                    size: frame.size,
                    pixelFormat: MTLRiveColorPixelFormat(),
                    layoutScale: fitBridge.scaleFactor,
                    color: rive.backgroundColor.argbValue
                ),
                viewportX: frame.origin.x,
                viewportY: frame.origin.y,
                viewportWidth: frame.size.width,
                viewportHeight: frame.size.height
            )
            rives.append(rive)
            index += 1
        }

        cachedConfigurations.withUnsafeBufferPointer { buffer in
            if let baseAddress = buffer.baseAddress {
                body(baseAddress, count)
            }
        }

        return rives
    }
}
