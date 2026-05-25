//
//  RiveBatchItem.swift
//  RiveRuntime
//
//  Created for batched rendering support.
//

import Foundation
import SwiftUI

/// A SwiftUI view that registers a Rive instance with the batch coordinator for
/// rendering on the shared surface.
///
/// Place `RiveBatchItem` inside a `RiveBatchSurface`. The item uses `GeometryReader`
/// and the parent's named coordinate space to report its position. The item itself
/// is transparent — all drawing happens on the shared `BatchMTKView`.
///
/// Example:
/// ```swift
/// RiveBatchItem(rive: myRive)
///     .frame(width: 200, height: 200)
/// ```
@_spi(RiveExperimental)
public struct RiveBatchItem: View {
    let rive: Rive
    let key: AnyHashable

    @EnvironmentObject private var coordinator: RiveBatchCoordinator

    /// Creates a batch item with an explicit key.
    ///
    /// - Parameters:
    ///   - key: A unique hashable key identifying this item in the batch.
    ///   - rive: The Rive instance to render.
    public init(key: AnyHashable, rive: Rive) {
        self.key = key
        self.rive = rive
    }

    /// Creates a batch item using the Rive instance's identity as key.
    ///
    /// - Parameter rive: The Rive instance to render.
    public init(rive: Rive) {
        self.key = AnyHashable(ObjectIdentifier(rive))
        self.rive = rive
    }

    public var body: some View {
        GeometryReader { geometry in
            SwiftUI.Color.clear
                .onAppear {
                    registerWithFrame(geometry.frame(in: .named("RiveBatchSurface")))
                }
                .onDisappear {
                    coordinator.unregister(key: key)
                }
                .modifier(FrameChangeModifier(
                    frame: geometry.frame(in: .named("RiveBatchSurface")),
                    onChange: { registerWithFrame($0) }
                ))
        }
    }

    // MARK: - Private

    private func registerWithFrame(_ frame: CGRect) {
        // Convert from points to pixels by multiplying by display scale.
        // The batch surface's MTKView drawableSize is in pixels.
        #if os(visionOS)
        let scale: CGFloat = 1.0
        #elseif canImport(UIKit) || RIVE_MAC_CATALYST
        let scale = UIScreen.main.scale
        #else
        let scale = NSScreen.main?.backingScaleFactor ?? 1.0
        #endif

        let pixelFrame = CGRect(
            x: frame.origin.x * scale,
            y: frame.origin.y * scale,
            width: frame.size.width * scale,
            height: frame.size.height * scale
        )

        coordinator.register(
            key: key,
            descriptor: BatchItemDescriptor(rive: rive, viewportFrame: pixelFrame)
        )
    }
}

// MARK: - FrameChangeModifier

/// A ViewModifier that bridges the iOS 14 and iOS 17 `onChange` APIs
/// to avoid deprecation warnings while maintaining backward compatibility.
private struct FrameChangeModifier: ViewModifier {
    let frame: CGRect
    let onChange: (CGRect) -> Void

    func body(content: Content) -> some View {
        if #available(iOS 17, macOS 14, tvOS 17, visionOS 1, *) {
            content.onChange(of: frame) { _, newFrame in
                onChange(newFrame)
            }
        } else {
            content.onChange(of: frame) { newFrame in
                onChange(newFrame)
            }
        }
    }
}
