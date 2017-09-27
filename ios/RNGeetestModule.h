#import <React/RCTBridgeModule.h>
#import <GTFramework/GTFramework.h>

@interface RNGeetestModule : NSObject <RCTBridgeModule, GTManageDelegate>

@property (nonatomic, strong) GTManager *manager;

@end
