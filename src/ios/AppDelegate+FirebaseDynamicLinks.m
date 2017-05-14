#import "AppDelegate+FirebaseDynamicLinks.h"
#import "FirebaseDynamicLinks.h"
@import Firebase;
@import FirebaseInvites;
@import GoogleSignIn;

@implementation AppDelegate (FirebasePlugin)

// [START openurl]
- (BOOL)application:(nonnull UIApplication *)application
            openURL:(nonnull NSURL *)url
            options:(nonnull NSDictionary<NSString *, id> *)options {
  FirebaseDynamicLinks* dl = [self.viewController getCommandInstance:@"FirebaseDynamicLinks"];

  if ([dl isSigningIn]) {
    dl.isSigningIn = NO;

    return [[GIDSignIn sharedInstance] handleURL:url
             sourceApplication:options[UIApplicationOpenURLOptionsSourceApplicationKey]
                    annotation:options[UIApplicationOpenURLOptionsAnnotationKey]];
  } else {
    return [self application:application
                     openURL:url
           sourceApplication:options[UIApplicationOpenURLOptionsSourceApplicationKey]
                  annotation:options[UIApplicationOpenURLOptionsAnnotationKey]];
  }
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
  sourceApplication:(NSString *)sourceApplication
         annotation:(id)annotation {
  FirebaseDynamicLinks* dl = [self.viewController getCommandInstance:@"FirebaseDynamicLinks"];
  // Handle App Invite requests
  FIRReceivedInvite *invite =
      [FIRInvites handleURL:url sourceApplication:sourceApplication annotation:annotation];
  if (invite) {
    NSString *matchType = (invite.matchType == FIRReceivedInviteMatchTypeWeak) ? @"Weak" : @"Strong";
    [dl sendDynamicLinkData:@{
                             @"deepLink": invite.deepLink,
                             @"invitationId": invite.inviteId,
                             @"matchType": matchType
                           }];
    return YES;
  }

  FIRDynamicLink *dynamicLink =
    [[FIRDynamicLinks dynamicLinks] dynamicLinkFromCustomSchemeURL:url];
  if (dynamicLink) {
      NSString *matchType = (dynamicLink.matchConfidence == FIRDynamicLinkMatchConfidenceWeak) ? @"Weak" : @"Strong";
      [dl sendDynamicLinkData:@{
                               @"deepLink": dynamicLink.url.absoluteString,
                               @"matchType": matchType
                             }];

      return YES;
  }

  if ([dl isSigningIn]) {
    dl.isSigningIn = NO;

    return [[GIDSignIn sharedInstance] handleURL:url
                             sourceApplication:sourceApplication
                                    annotation:annotation];
  } else {
    // call super
    return [self application:application
                     openURL:url
           sourceApplication:sourceApplication
                  annotation:annotation];
  }
}
// [END openurl]

// [START continueuseractivity]
- (BOOL)application:(UIApplication *)application
    continueUserActivity:(NSUserActivity *)userActivity
      restorationHandler:(void (^)(NSArray *))restorationHandler {
    FirebaseDynamicLinks* dl = [self.viewController getCommandInstance:@"FirebaseDynamicLinks"];

    BOOL handled = [[FIRDynamicLinks dynamicLinks]
                     handleUniversalLink:userActivity.webpageURL
                              completion:^(FIRDynamicLink * _Nullable dynamicLink,
                                           NSError * _Nullable error) {

      if (dynamicLink) {
        NSString *matchType = (dynamicLink.matchConfidence == FIRDynamicLinkMatchConfidenceWeak) ? @"Weak" : @"Strong";

        [dl sendDynamicLinkData:@{
           @"deepLink": dynamicLink.url.absoluteString,
           @"matchType": matchType
         }];
      }
  }];

  return handled;
}
// [END continueuseractivity]

@end
