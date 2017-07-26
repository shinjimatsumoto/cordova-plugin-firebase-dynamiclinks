#import <Cordova/CDV.h>
#import "AppDelegate.h"

@import Firebase;
@import GoogleSignIn;

@interface FirebaseDynamicLinksPlugin : CDVPlugin<GIDSignInDelegate, GIDSignInUIDelegate>

- (void)onDynamicLink:(CDVInvokedUrlCommand *)command;

@property (nonatomic, copy) NSString *dynamicLinkCallbackId;
@property (nonatomic, assign) BOOL isSigningIn;
@property NSDictionary* cachedInvitation;

@end
