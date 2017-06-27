#import <Cordova/CDV.h>
#import "AppDelegate.h"

@import Firebase;
@import GoogleSignIn;

@interface FirebaseDynamicLinksPlugin : CDVPlugin<FIRInviteDelegate, GIDSignInDelegate, GIDSignInUIDelegate>

- (void)onDynamicLink:(CDVInvokedUrlCommand *)command;
- (void)sendInvitation:(CDVInvokedUrlCommand*)command;
- (void)sendDynamicLinkData:(NSDictionary*)data;

@property (nonatomic, copy) NSString *dynamicLinkCallbackId;
@property (nonatomic, assign) BOOL isSigningIn;
@property NSDictionary* cachedInvitation;

@end
