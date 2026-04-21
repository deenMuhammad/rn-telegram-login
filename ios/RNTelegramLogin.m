#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(RNTelegramLogin, NSObject)

RCT_EXTERN_METHOD(
  configure:(NSString *)clientId
  redirectUri:(NSString *)redirectUri
  scopes:(NSArray<NSString *> *)scopes
  fallbackScheme:(nullable NSString *)fallbackScheme
  resolve:(RCTPromiseResolveBlock)resolve
  reject:(RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
  login:(RCTPromiseResolveBlock)resolve
  reject:(RCTPromiseRejectBlock)reject
)

RCT_EXTERN_METHOD(
  handleUrl:(NSString *)url
)

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

@end
