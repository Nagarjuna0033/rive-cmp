//
//  _RiveUIRenderer.m
//  RiveRuntime
//
//  Created by David Skuza on 9/9/25.
//  Copyright © 2025 Rive. All rights reserved.
//

#import "Renderer.h"
#import <RiveRuntime/RiveExperimental.h>
#import <RiveRuntime/RiveCommandQueue.h>
#import <RiveRuntime/RiveRuntime-Swift.h>
#import <RiveRuntime/RiveRenderContext.h>
#include <memory>
#include <vector>
#include "rive/command_server.hpp"
#include "rive/renderer/metal/render_context_metal_impl.h"
#include "rive/renderer/rive_renderer.hpp"
#include "rive/animation/state_machine_instance.hpp"

NS_ASSUME_NONNULL_BEGIN

static rive::Fit RiveConfigurationFitCppValue(RiveConfigurationFit fit)
{
    switch (fit)
    {
        case RiveConfigurationFitFill:
            return rive::Fit::fill;
        case RiveConfigurationFitContain:
            return rive::Fit::contain;
        case RiveConfigurationFitCover:
            return rive::Fit::cover;
        case RiveConfigurationFitFitWidth:
            return rive::Fit::fitWidth;
        case RiveConfigurationFitFitHeight:
            return rive::Fit::fitHeight;
        case RiveConfigurationFitNone:
            return rive::Fit::none;
        case RiveConfigurationFitScaleDown:
            return rive::Fit::scaleDown;
        case RiveConfigurationFitLayout:
            return rive::Fit::layout;
    }
}

static rive::Alignment RiveConfigurationAlignmentCppValue(
    RiveConfigurationAlignment alignment)
{
    switch (alignment)
    {
        case RiveConfigurationAlignmentTopLeft:
            return rive::Alignment::topLeft;
        case RiveConfigurationAlignmentTopCenter:
            return rive::Alignment::topCenter;
        case RiveConfigurationAlignmentTopRight:
            return rive::Alignment::topRight;
        case RiveConfigurationAlignmentCenterLeft:
            return rive::Alignment::centerLeft;
        case RiveConfigurationAlignmentCenter:
            return rive::Alignment::center;
        case RiveConfigurationAlignmentCenterRight:
            return rive::Alignment::centerRight;
        case RiveConfigurationAlignmentBottomLeft:
            return rive::Alignment::bottomLeft;
        case RiveConfigurationAlignmentBottomCenter:
            return rive::Alignment::bottomCenter;
        case RiveConfigurationAlignmentBottomRight:
            return rive::Alignment::bottomRight;
    }
}

@implementation Renderer
{
    id<RiveCommandQueueProtocol> _commandQueue;
    rive::rcp<rive::gpu::RenderTargetMetal> _renderTarget;
    RiveRenderContext* _renderContext;
}

- (instancetype)initWithCommandQueue:(id<RiveCommandQueueProtocol>)commandQueue
                       renderContext:(nonnull RiveRenderContext*)renderContext
{
    if (self = [super init])
    {
        _commandQueue = commandQueue;
        _renderContext = renderContext;
    }
    return self;
}

- (void)dealloc
{
    _renderTarget = nullptr;
}

- (rive::rcp<rive::gpu::RenderTargetMetal>)renderTarget
{
    // Does this need a lock, in case this renderer gets dealloc'd before a
    // frame draws?
    return _renderTarget;
}

- (void)setRenderTarget:(rive::rcp<rive::gpu::RenderTargetMetal>)renderTarget
{
    _renderTarget = renderTarget;
}

- (RiveRenderContext*)renderContext
{
    return _renderContext;
}

- (void)drawConfiguration:(RendererConfiguration)configuration
                toTexture:(id<MTLTexture>)texture
               fromDevice:(id<MTLDevice>)device
                 finalize:(nullable void (^)(id<MTLCommandBuffer>))finalize
                  onError:(nullable void (^)(NSError* _Nonnull))onError
{
    BOOL (^CGSizeWithinRange)(CGSize, CGSize) =
        ^BOOL(CGSize size, CGSize maxSize) {
          BOOL isWidthValid = size.width > 0 && size.width <= maxSize.width;
          BOOL isHeightValid = size.height > 0 && size.height <= maxSize.height;
          return isWidthValid && isHeightValid;
        };

    // CGSizeWithinRange checks for width > 0 && height > 0,
    // so negative-width, 0-width, negative-height, 0-height,
    // and > max texture size are all accounted for.
    if (!CGSizeWithinRange(configuration.size,
                           CGSizeMaximum2DTextureSize(device)))
    {
        NSError* invalidSize = [NSError
            errorWithDomain:@"app.rive.renderer"
                       code:RendererErrorInvalidSize
                   userInfo:@{
                       NSLocalizedDescriptionKey : [NSString
                           stringWithFormat:@"Cannot draw size {%f, %f}",
                                            configuration.size.width,
                                            configuration.size.height]
                   }];
        if (onError)
        {
            onError(invalidSize);
        }
    }

    __weak Renderer* weakSelf = self;
    [_commandQueue
            draw:[_commandQueue createDrawKey]
        callback:^(void* cppServer) {
          __strong Renderer* strongSelf = weakSelf;
          if (!strongSelf)
          {
              NSError* invalidRenderer =
                  [NSError errorWithDomain:@"app.rive.renderer"
                                      code:RendererErrorInvalidRenderer
                                  userInfo:@{
                                      NSLocalizedDescriptionKey :
                                          @"Invalid renderer for drawing."
                                  }];
              if (onError)
              {
                  onError(invalidRenderer);
              }
              return;
          }
          auto server = static_cast<rive::CommandServer*>(cppServer);
          auto artboard = server->getArtboardInstance(
              reinterpret_cast<rive::ArtboardHandle>(
                  configuration.artboardHandle));
          if (artboard == nullptr)
          {
              NSError* invalidArtboard = [NSError
                  errorWithDomain:@"app.rive.renderer"
                             code:RendererErrorInvalidArtboard
                         userInfo:@{
                             @"artboard" : @(configuration.artboardHandle),
                             NSLocalizedDescriptionKey :
                                 @"Attempted to draw with invalid artboard."
                         }];
              if (onError)
              {
                  onError(invalidArtboard);
              }
              return;
          }

          if (artboard->didChange() == false)
          {
              return;
          }

          auto stateMachine = server->getStateMachineInstance(
              reinterpret_cast<rive::StateMachineHandle>(
                  configuration.stateMachineHandle));
          if (stateMachine == nullptr)
          {
              NSError* invalidStateMachine =
                  [NSError errorWithDomain:@"app.rive.renderer"
                                      code:RendererErrorInvalidStateMachine
                                  userInfo:@{
                                      @"stateMachine" :
                                          @(configuration.stateMachineHandle),
                                      NSLocalizedDescriptionKey :
                                          @"Attempted to draw with invalid "
                                          @"state machine."
                                  }];
              if (onError)
              {
                  onError(invalidStateMachine);
              }
              return;
          }

          auto riveContext =
              static_cast<rive::gpu::RenderContext*>(server->factory());

          auto metalContext =
              riveContext
                  ->static_impl_cast<rive::gpu::RenderContextMetalImpl>();
          auto renderTarget = strongSelf.renderTarget;
          if (renderTarget == nullptr ||
              (renderTarget->width() != configuration.size.width ||
               renderTarget->height() != configuration.size.height))
          {
              renderTarget = metalContext->makeRenderTarget(
                  MTLRiveColorPixelFormat(),
                  (uint32_t)configuration.size.width,
                  (uint32_t)configuration.size.height);
              strongSelf.renderTarget = renderTarget;
          }
          renderTarget->setTargetTexture(texture);

          riveContext->beginFrame(rive::gpu::RenderContext::FrameDescriptor{
              .renderTargetWidth = renderTarget->width(),
              .renderTargetHeight = renderTarget->height(),
              .loadAction = rive::gpu::LoadAction::clear,
              .clearColor = configuration.color});

          auto renderer = rive::RiveRenderer(riveContext);
          renderer.align(
              RiveConfigurationFitCppValue(configuration.fit),
              RiveConfigurationAlignmentCppValue(configuration.alignment),
              rive::AABB(
                  0.0f, 0.0f, renderTarget->width(), renderTarget->height()),
              artboard->bounds(),
              configuration.layoutScale);

          artboard->draw(&renderer);

          @autoreleasepool
          {
              id<MTLCommandBuffer> commandBuffer =
                  [strongSelf.renderContext newCommandBuffer];
              riveContext->flush(
                  {.renderTarget = strongSelf.renderTarget.get(),
                   .externalCommandBuffer = (__bridge void*)commandBuffer});

              if (finalize)
              {
                  finalize(commandBuffer);
              }
          }
        }];
}

- (void)drawBatchConfigurations:(const BatchRendererConfiguration*)configurations
                          count:(NSUInteger)count
                   surfaceWidth:(uint32_t)surfaceWidth
                  surfaceHeight:(uint32_t)surfaceHeight
              surfaceClearColor:(uint32_t)surfaceClearColor
                      toTexture:(id<MTLTexture>)texture
                     fromDevice:(id<MTLDevice>)device
                       finalize:(nullable void (^)(id<MTLCommandBuffer>))finalize
                        onError:(nullable void (^)(NSError* _Nonnull))onError
{
    if (count == 0)
    {
        return;
    }

    // Copy configurations to heap so the async callback can safely access them.
    auto heapConfigs = std::make_shared<std::vector<BatchRendererConfiguration>>(
        configurations, configurations + count);

    __weak Renderer* weakSelf = self;
    [_commandQueue
            draw:[_commandQueue createDrawKey]
        callback:^(void* cppServer) {
          __strong Renderer* strongSelf = weakSelf;
          if (!strongSelf)
          {
              if (onError)
              {
                  onError([NSError
                      errorWithDomain:@"app.rive.renderer"
                                 code:RendererErrorInvalidRenderer
                             userInfo:@{
                                 NSLocalizedDescriptionKey :
                                     @"Invalid renderer for batch drawing."
                             }]);
              }
              return;
          }

          auto server = static_cast<rive::CommandServer*>(cppServer);
          auto riveContext =
              static_cast<rive::gpu::RenderContext*>(server->factory());
          auto metalContext =
              riveContext
                  ->static_impl_cast<rive::gpu::RenderContextMetalImpl>();

          // Reuse or create render target for the shared surface size.
          auto renderTarget = strongSelf.renderTarget;
          if (renderTarget == nullptr ||
              (renderTarget->width() != surfaceWidth ||
               renderTarget->height() != surfaceHeight))
          {
              renderTarget = metalContext->makeRenderTarget(
                  MTLRiveColorPixelFormat(), surfaceWidth, surfaceHeight);
              strongSelf.renderTarget = renderTarget;
          }
          renderTarget->setTargetTexture(texture);

          riveContext->beginFrame(rive::gpu::RenderContext::FrameDescriptor{
              .renderTargetWidth = renderTarget->width(),
              .renderTargetHeight = renderTarget->height(),
              .loadAction = rive::gpu::LoadAction::clear,
              .clearColor = surfaceClearColor});

          auto renderer = rive::RiveRenderer(riveContext);

          for (const auto& batchConfig : *heapConfigs)
          {
              const auto& config = batchConfig.configuration;
              auto artboard = server->getArtboardInstance(
                  reinterpret_cast<rive::ArtboardHandle>(
                      config.artboardHandle));
              if (artboard == nullptr)
              {
                  continue;
              }

              auto stateMachine = server->getStateMachineInstance(
                  reinterpret_cast<rive::StateMachineHandle>(
                      config.stateMachineHandle));
              if (stateMachine == nullptr)
              {
                  continue;
              }

              renderer.save();
              renderer.align(
                  RiveConfigurationFitCppValue(config.fit),
                  RiveConfigurationAlignmentCppValue(config.alignment),
                  rive::AABB(batchConfig.viewportX,
                             batchConfig.viewportY,
                             batchConfig.viewportX + batchConfig.viewportWidth,
                             batchConfig.viewportY +
                                 batchConfig.viewportHeight),
                  artboard->bounds(),
                  config.layoutScale);
              artboard->draw(&renderer);
              renderer.restore();
          }

          @autoreleasepool
          {
              id<MTLCommandBuffer> commandBuffer =
                  [strongSelf.renderContext newCommandBuffer];
              riveContext->flush(
                  {.renderTarget = strongSelf.renderTarget.get(),
                   .externalCommandBuffer = (__bridge void*)commandBuffer});

              if (finalize)
              {
                  finalize(commandBuffer);
              }
          }
        }];
}

@end

NS_ASSUME_NONNULL_END
