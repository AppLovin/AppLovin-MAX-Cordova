//
//  AppLovinMAX.m
//  AppLovin MAX Cordova Plugin
//
//  Created by Thomas So on 1/1/21.
//  Copyright © 2021 AppLovin. All rights reserved.
//

#import "AppLovinMAX.h"

#define ROOT_VIEW_CONTROLLER (self.viewController)
#define DEVICE_SPECIFIC_ADVIEW_AD_FORMAT ([[UIDevice currentDevice] userInterfaceIdiom] == UIUserInterfaceIdiomPad) ? MAAdFormat.leader : MAAdFormat.banner

// Internal
@interface UIColor (ALUtils)
+ (nullable UIColor *)al_colorWithHexString:(NSString *)hexString;
@end

@interface NSNumber (ALUtils)
+ (NSNumber *)al_numberWithString:(NSString *)string;
@end

@interface NSDictionary (ALUtils)
- (BOOL)al_containsValueForKey:(id)key;
@end

@interface NSString (ALUtils)
@property (nonatomic, assign, readonly, getter=al_isValidString) BOOL al_validString;
@end

@interface AppLovinMAX()<MAAdDelegate, MAAdViewAdDelegate, MARewardedAdDelegate>

// Parent Fields
@property (nonatomic,  weak) ALSdk *sdk;
@property (nonatomic, assign, getter=isPluginInitialized) BOOL pluginInitialized;
@property (nonatomic, assign, getter=isSDKInitialized) BOOL sdkInitialized;
@property (nonatomic, strong) ALSdkConfiguration *sdkConfiguration;

// Store these values if pub attempts to set it before initializing
@property (nonatomic,   copy, nullable) NSString *userIdentifierToSet;
@property (nonatomic, strong, nullable) NSArray<NSString *> *testDeviceIdentifiersToSet;
@property (nonatomic, strong, nullable) NSNumber *verboseLoggingToSet;

// Fullscreen Ad Fields
@property (nonatomic, strong) NSMutableDictionary<NSString *, MAInterstitialAd *> *interstitials;
@property (nonatomic, strong) NSMutableDictionary<NSString *, MARewardedAd *> *rewardedAds;

// Banner Fields
@property (nonatomic, strong) NSMutableDictionary<NSString *, MAAdView *> *adViews;
@property (nonatomic, strong) NSMutableDictionary<NSString *, MAAdFormat *> *adViewAdFormats;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSString *> *adViewPositions;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSArray<NSLayoutConstraint *> *> *adViewConstraints;
@property (nonatomic, strong) NSMutableArray<NSString *> *adUnitIdentifiersToShowAfterCreate;
@property (nonatomic, strong) UIView *safeAreaBackground;
@property (nonatomic, strong, nullable) UIColor *publisherBannerBackgroundColor;

@end

@implementation AppLovinMAX
static NSString *const SDK_TAG = @"AppLovinSdk";
static NSString *const TAG = @"AppLovinMAX";

- (void)pluginInitialize
{
    [super pluginInitialize];
    
    self.interstitials = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.rewardedAds = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.adViews = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.adViewAdFormats = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.adViewPositions = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.adViewConstraints = [NSMutableDictionary dictionaryWithCapacity: 2];
    self.adUnitIdentifiersToShowAfterCreate = [NSMutableArray arrayWithCapacity: 2];
    
    self.safeAreaBackground = [[UIView alloc] init];
    self.safeAreaBackground.hidden = YES;
    self.safeAreaBackground.backgroundColor = UIColor.clearColor;
    self.safeAreaBackground.translatesAutoresizingMaskIntoConstraints = NO;
    self.safeAreaBackground.userInteractionEnabled = NO;
    [ROOT_VIEW_CONTROLLER.view addSubview: self.safeAreaBackground];
}

- (BOOL)isInitialized
{
    return [self isPluginInitialized] && [self isSDKInitialized];
}

- (void)initialize:(CDVInvokedUrlCommand *)command
{
    // Guard against running init logic multiple times
    if ( [self isPluginInitialized] )
    {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsDictionary: [self initializationMessage]];
        [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
        
        return;
    }
    
    self.pluginInitialized = YES;
    
    NSString *pluginVersion = [command argumentAtIndex: 0];
    NSString *sdkKey = [command argumentAtIndex: 1];
    
    [self log: @"Initializing AppLovin MAX Cordova v%@...", pluginVersion];
    
    // If SDK key passed in is empty, check Info.plist
    if ( ![sdkKey al_isValidString] )
    {
        if ( [[NSBundle mainBundle].infoDictionary al_containsValueForKey: @"AppLovinSdkKey"] )
        {
            sdkKey = [NSBundle mainBundle].infoDictionary[@"AppLovinSdkKey"];
        }
        else
        {
            [NSException raise: NSInternalInconsistencyException
                        format: @"Unable to initialize AppLovin SDK - no SDK key provided and not found in Info.plist!"];
        }
    }
    
    // Initialize SDK
    self.sdk = [ALSdk sharedWithKey: sdkKey];
    [self.sdk setPluginVersion: [@"Cordova-" stringByAppendingString: pluginVersion]];
    [self.sdk setMediationProvider: ALMediationProviderMAX];
    
    // Set user id if needed
    if ( [self.userIdentifierToSet al_isValidString] )
    {
        self.sdk.userIdentifier = self.userIdentifierToSet;
        self.userIdentifierToSet = nil;
    }
    
    // Set test device ids if needed
    if ( self.testDeviceIdentifiersToSet )
    {
        self.sdk.settings.testDeviceAdvertisingIdentifiers = self.testDeviceIdentifiersToSet;
        self.testDeviceIdentifiersToSet = nil;
    }
    
    // Set verbose logging state if needed
    if ( self.verboseLoggingToSet )
    {
        self.sdk.settings.isVerboseLogging = self.verboseLoggingToSet.boolValue;
        self.verboseLoggingToSet = nil;
    }
    
    [self.sdk initializeSdkWithCompletionHandler:^(ALSdkConfiguration *configuration)
     {
        [self log: @"SDK initialized"];
        
        self.sdkConfiguration = configuration;
        self.sdkInitialized = YES;
        
        CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsDictionary: [self initializationMessage]];
        [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
    }];
}

- (NSDictionary<NSString *, id> *)initializationMessage
{
    NSMutableDictionary<NSString *, id> *message = [NSMutableDictionary dictionaryWithCapacity: 4];
    
    if ( self.sdkConfiguration )
    {
        message[@"consentDialogState"] = @(self.sdkConfiguration.consentDialogState);
        message[@"countryCode"] = self.sdkConfiguration.countryCode;
    }
    else
    {
        message[@"consentDialogState"] = @(ALConsentDialogStateUnknown);
    }
    
    message[@"hasUserConsent"] = @([ALPrivacySettings hasUserConsent]);
    message[@"isAgeRestrictedUser"] = @([ALPrivacySettings isAgeRestrictedUser]);
    message[@"isDoNotSell"] = @([ALPrivacySettings isDoNotSell]);
    message[@"isTablet"] = @([self isTablet]);
    
    return message;
}

#pragma mark - General Public API

- (BOOL)isTablet
{
    return [UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPad;
}

- (void)showMediationDebugger:(CDVInvokedUrlCommand *)command
{
    if ( !_sdk )
    {
        [self log: @"Failed to show mediation debugger - please ensure the AppLovin MAX Unity Plugin has been initialized by calling 'AppLovinMAX.initialize(...);'!"];
        [self sendErrorPluginResultForCommand: command];
        
        return;
    }
    
    [self.sdk showMediationDebugger];
    
    [self sendOKPluginResultForCommand: command];
}

- (void/*BOOL*/)getConsentDialogState:(CDVInvokedUrlCommand *)command
{
    if ( ![self isInitialized] )
    {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                 messageAsNSInteger: ALConsentDialogStateUnknown];
        [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
        
        return;
    }
    
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                             messageAsNSInteger: self.sdkConfiguration.consentDialogState];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

- (void)setHasUserConsent:(CDVInvokedUrlCommand *)command
{
    BOOL hasUserConsent = ((NSNumber *)[command argumentAtIndex: 0]).boolValue;
    [ALPrivacySettings setHasUserConsent: hasUserConsent];
    
    [self sendOKPluginResultForCommand: command];
}

- (void/*BOOL*/)hasUserConsent:(CDVInvokedUrlCommand *)command
{
    [self sendOKPluginResultForCommand: command withBool: [ALPrivacySettings hasUserConsent]];
}

- (void)setIsAgeRestrictedUser:(CDVInvokedUrlCommand *)command
{
    BOOL isAgeRestrictedUser = ((NSNumber *)[command argumentAtIndex: 0]).boolValue;
    [ALPrivacySettings setIsAgeRestrictedUser: isAgeRestrictedUser];
    
    [self sendOKPluginResultForCommand: command];
}

- (void/*BOOL*/)isAgeRestrictedUser:(CDVInvokedUrlCommand *)command
{
    [self sendOKPluginResultForCommand: command withBool: [ALPrivacySettings isAgeRestrictedUser]];
}

- (void)setDoNotSell:(CDVInvokedUrlCommand *)command
{
    BOOL doNotSell = ((NSNumber *)[command argumentAtIndex: 0]).boolValue;
    [ALPrivacySettings setDoNotSell: doNotSell];
    
    [self sendOKPluginResultForCommand: command];
}

- (void/*BOOL*/)isDoNotSell:(CDVInvokedUrlCommand *)command
{
    [self sendOKPluginResultForCommand: command withBool: [ALPrivacySettings isDoNotSell]];
}

- (void)setUserId:(CDVInvokedUrlCommand *)command
{
    NSString *userId = [command argumentAtIndex: 0];
    
    if ( [self isPluginInitialized] )
    {
        self.sdk.userIdentifier = userId;
        self.userIdentifierToSet = nil;
    }
    else
    {
        self.userIdentifierToSet = userId;
    }
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setMuted:(CDVInvokedUrlCommand *)command
{
    if ( ![self isPluginInitialized] )
    {
        [self sendErrorPluginResultForCommand: command];
        return;
    }
    
    BOOL muted = ((NSNumber *)[command argumentAtIndex: 0]).boolValue;
    self.sdk.settings.muted = muted;
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setVerboseLogging:(CDVInvokedUrlCommand *)command
{
    BOOL enabled = ((NSNumber *)[command argumentAtIndex: 0]).boolValue;
    
    if ( [self isPluginInitialized] )
    {
        self.sdk.settings.isVerboseLogging = enabled;
        self.verboseLoggingToSet = nil;
    }
    else
    {
        self.verboseLoggingToSet = @(enabled);
    }
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setTestDeviceAdvertisingIds:(CDVInvokedUrlCommand *)command
{
    NSArray<NSString *> *testDeviceAdvertisingIds = [command argumentAtIndex: 0];
    
    if ( [self isPluginInitialized] )
    {
        self.sdk.settings.testDeviceAdvertisingIdentifiers = testDeviceAdvertisingIds;
        self.testDeviceIdentifiersToSet = nil;
    }
    else
    {
        self.testDeviceIdentifiersToSet = testDeviceAdvertisingIds;
    }
    
    [self sendOKPluginResultForCommand: command];
}

#pragma mark - Event Tracking

- (void)trackEvent:(CDVInvokedUrlCommand *)command
{
    NSString *event = [command argumentAtIndex: 0];
    NSDictionary<NSString *, id> *parameters = [command argumentAtIndex: 1];
    
    [self.sdk.eventService trackEvent: event parameters: parameters];
    
    [self sendOKPluginResultForCommand: command];
}

#pragma mark - Banners

- (void)createBanner:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *bannerPosition = [command argumentAtIndex: 1];
    [self createAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT atPosition: bannerPosition command: command];
}

- (void)setBannerBackgroundColor:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *hexColorCode = [command argumentAtIndex: 1];
    [self setAdViewBackgroundColorForAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT hexColorCode: hexColorCode command: command];
}

- (void)setBannerPlacement:(CDVInvokedUrlCommand *)command
{
    NSString *placement = [command argumentAtIndex: 0];
    NSString *adUnitIdentifier = [command argumentAtIndex: 1];
    [self setAdViewPlacement: placement forAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT command: command];
}

- (void)updateBannerPosition:(CDVInvokedUrlCommand *)command
{
    NSString *bannerPosition = [command argumentAtIndex: 0];
    NSString *adUnitIdentifier = [command argumentAtIndex: 1];
    [self updateAdViewPosition: bannerPosition forAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT command: command];
}

- (void)setBannerExtraParameter:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *key = [command argumentAtIndex: 1];
    NSString *value = [command argumentAtIndex: 2];
    [self setAdViewExtraParameterForAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT key: key value: value command: command];
}

- (void)showBanner:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self showAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT command: command];
}

- (void)hideBanner:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self hideAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT command: command];
}

- (void)destroyBanner:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self destroyAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: DEVICE_SPECIFIC_ADVIEW_AD_FORMAT command: command];
}

#pragma mark - MRECs

- (void)createMRec:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *mrecPosition = [command argumentAtIndex: 1];
    [self createAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec atPosition: mrecPosition command: command];
}

- (void)setMRecPlacement:(CDVInvokedUrlCommand *)command
{
    NSString *placement = [command argumentAtIndex: 0];
    NSString *adUnitIdentifier = [command argumentAtIndex: 1];
    [self setAdViewPlacement: placement forAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec command: command];
}

- (void)updateMRecPosition:(CDVInvokedUrlCommand *)command
{
    NSString *mrecPosition = [command argumentAtIndex: 0];
    NSString *adUnitIdentifier = [command argumentAtIndex: 1];
    [self updateAdViewPosition: mrecPosition forAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec command: command];
}

- (void)showMRec:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self showAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec command: command];
}

- (void)hideMRec:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self hideAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec command: command];
}

- (void)destroyMRec:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    [self destroyAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: MAAdFormat.mrec command: command];
}

#pragma mark - Interstitials

- (void)loadInterstitial:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    MAInterstitialAd *interstitial = [self retrieveInterstitialForAdUnitIdentifier: adUnitIdentifier];
    [interstitial loadAd];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)showInterstitial:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *placement = [command argumentAtIndex: 1];
    MAInterstitialAd *interstitial = [self retrieveInterstitialForAdUnitIdentifier: adUnitIdentifier];
    [interstitial showAdForPlacement: placement];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setInterstitialExtraParameter:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *key = [command argumentAtIndex: 1];
    NSString *value = [command argumentAtIndex: 2];
    MAInterstitialAd *interstitial = [self retrieveInterstitialForAdUnitIdentifier: adUnitIdentifier];
    [interstitial setExtraParameterForKey: key value: value];
    
    [self sendOKPluginResultForCommand: command];
}

#pragma mark - Rewarded

- (void)loadRewardedAd:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    MARewardedAd *rewardedAd = [self retrieveRewardedAdForAdUnitIdentifier: adUnitIdentifier];
    [rewardedAd loadAd];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)showRewardedAd:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *placement = [command argumentAtIndex: 1];
    MARewardedAd *rewardedAd = [self retrieveRewardedAdForAdUnitIdentifier: adUnitIdentifier];
    [rewardedAd showAdForPlacement: placement];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setRewardedAdExtraParameter:(CDVInvokedUrlCommand *)command
{
    NSString *adUnitIdentifier = [command argumentAtIndex: 0];
    NSString *key = [command argumentAtIndex: 1];
    NSString *value = [command argumentAtIndex: 2];
    MARewardedAd *rewardedAd = [self retrieveRewardedAdForAdUnitIdentifier: adUnitIdentifier];
    [rewardedAd setExtraParameterForKey: key value: value];
    
    [self sendOKPluginResultForCommand: command];
}

#pragma mark - Ad Callbacks

- (void)didLoadAd:(MAAd *)ad
{
    NSString *name;
    MAAdFormat *adFormat = ad.format;
    if ( MAAdFormat.banner == adFormat || MAAdFormat.leader == adFormat || MAAdFormat.mrec == adFormat )
    {
        MAAdView *adView = [self retrieveAdViewForAdUnitIdentifier: ad.adUnitIdentifier adFormat: adFormat];
        // An ad is now being shown, enable user interaction.
        adView.userInteractionEnabled = YES;
        
        name = ( MAAdFormat.mrec == adFormat ) ? @"OnMRecAdLoadedEvent" : @"OnBannerAdLoadedEvent";
        [self positionAdViewForAd: ad];
        
        // Do not auto-refresh by default if the ad view is not showing yet (e.g. first load during app launch and publisher does not automatically show banner upon load success)
        // We will resume auto-refresh in -[MAUnityAdManager showBannerWithAdUnitIdentifier:].
        if ( adView && [adView isHidden] )
        {
            [adView stopAutoRefresh];
        }
    }
    else if ( MAAdFormat.interstitial == adFormat )
    {
        name = @"OnInterstitialLoadedEvent";
    }
    else if ( MAAdFormat.rewarded == adFormat )
    {
        name = @"OnRewardedAdLoadedEvent";
    }
    else
    {
        [self logInvalidAdFormat: adFormat];
        return;
    }
    
    [self fireWindowEventWithName: name body: [self adInfoForAd: ad]];
}

- (void)didFailToLoadAdForAdUnitIdentifier:(NSString *)adUnitIdentifier withError:(MAError *)error
{
    if ( !adUnitIdentifier )
    {
        [self log: @"adUnitIdentifier cannot be nil from %@", [NSThread callStackSymbols]];
        return;
    }
    
    NSString *name;
    if ( self.adViews[adUnitIdentifier] )
    {
        name = ( MAAdFormat.mrec == self.adViewAdFormats[adUnitIdentifier] ) ? @"OnMRecAdLoadFailedEvent" : @"OnBannerAdLoadFailedEvent";
    }
    else if ( self.interstitials[adUnitIdentifier] )
    {
        name = @"OnInterstitialLoadFailedEvent";
    }
    else if ( self.rewardedAds[adUnitIdentifier] )
    {
        name = @"OnRewardedAdLoadFailedEvent";
    }
    else
    {
        [self log: @"invalid adUnitId from %@", [NSThread callStackSymbols]];
        return;
    }
    
    // TODO: Add "code", "message", and "adLoadFailureInfo"
    NSString *errorCodeStr = [@(error.code) stringValue];
    [self fireWindowEventWithName: name body: @{@"adUnitId" : adUnitIdentifier,
                                                @"errorCode" : errorCodeStr}];
}

- (void)didClickAd:(MAAd *)ad
{
    NSString *name;
    MAAdFormat *adFormat = ad.format;
    if ( MAAdFormat.banner == adFormat || MAAdFormat.leader == adFormat )
    {
        name = @"OnBannerAdClickedEvent";
    }
    else if ( MAAdFormat.mrec == adFormat )
    {
        name = @"OnMRecAdClickedEvent";
    }
    else if ( MAAdFormat.interstitial == adFormat )
    {
        name = @"OnInterstitialClickedEvent";
    }
    else if ( MAAdFormat.rewarded == adFormat )
    {
        name = @"OnRewardedAdClickedEvent";
    }
    else
    {
        [self logInvalidAdFormat: adFormat];
        return;
    }
    
    [self fireWindowEventWithName: name body: [self adInfoForAd: ad]];
}

- (void)didDisplayAd:(MAAd *)ad
{
    // BMLs do not support [DISPLAY] events in Unity
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.interstitial && adFormat != MAAdFormat.rewarded ) return;
    
    NSString *name;
    if ( MAAdFormat.interstitial == adFormat )
    {
        name = @"OnInterstitialDisplayedEvent";
    }
    else // REWARDED
    {
        name = @"OnRewardedAdDisplayedEvent";
    }
    
    [self fireWindowEventWithName: name body: [self adInfoForAd: ad]];
}

- (void)didFailToDisplayAd:(MAAd *)ad withError:(MAError *)error
{
    // BMLs do not support [DISPLAY] events in Unity
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.interstitial && adFormat != MAAdFormat.rewarded ) return;
    
    NSString *name;
    if ( MAAdFormat.interstitial == adFormat )
    {
        name = @"OnInterstitialAdFailedToDisplayEvent";
    }
    else // REWARDED
    {
        name = @"OnRewardedAdFailedToDisplayEvent";
    }
    
    // TODO: Add "code", "message"
    NSMutableDictionary *body = [@{@"errorCode" : @(error.code)} mutableCopy];
    [body addEntriesFromDictionary: [self adInfoForAd: ad]];
    
    [self fireWindowEventWithName: name body: body];
}

- (void)didHideAd:(MAAd *)ad
{
    // BMLs do not support [HIDDEN] events in Unity
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.interstitial && adFormat != MAAdFormat.rewarded ) return;
    
    NSString *name;
    if ( MAAdFormat.interstitial == adFormat )
    {
        name = @"OnInterstitialHiddenEvent";
    }
    else // REWARDED
    {
        name = @"OnRewardedAdHiddenEvent";
    }
    
    [self fireWindowEventWithName: name body: [self adInfoForAd: ad]];
}

- (void)didExpandAd:(MAAd *)ad
{
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.banner && adFormat != MAAdFormat.leader && adFormat != MAAdFormat.mrec )
    {
        [self logInvalidAdFormat: adFormat];
        return;
    }
    
    [self fireWindowEventWithName: ( MAAdFormat.mrec == adFormat ) ? @"OnMRecAdExpandedEvent" : @"OnBannerAdExpandedEvent"
                             body: [self adInfoForAd: ad]];
}

- (void)didCollapseAd:(MAAd *)ad
{
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.banner && adFormat != MAAdFormat.leader && adFormat != MAAdFormat.mrec )
    {
        [self logInvalidAdFormat: adFormat];
        return;
    }
    
    [self fireWindowEventWithName: ( MAAdFormat.mrec == adFormat ) ? @"OnMRecAdCollapsedEvent" : @"OnBannerAdCollapsedEvent"
                             body: [self adInfoForAd: ad]];
}

- (void)didCompleteRewardedVideoForAd:(MAAd *)ad
{
    // This event is not forwarded
}

- (void)didStartRewardedVideoForAd:(MAAd *)ad
{
    // This event is not forwarded
}

- (void)didRewardUserForAd:(MAAd *)ad withReward:(MAReward *)reward
{
    MAAdFormat *adFormat = ad.format;
    if ( adFormat != MAAdFormat.rewarded )
    {
        [self logInvalidAdFormat: adFormat];
        return;
    }
    
    NSString *rewardLabel = reward ? reward.label : @"";
    NSInteger rewardAmountInt = reward ? reward.amount : 0;
    NSString *rewardAmount = [@(rewardAmountInt) stringValue];
    
    NSMutableDictionary *body = [@{@"rewardLabel": rewardLabel,
                                   @"rewardAmount": rewardAmount} mutableCopy];
    [body addEntriesFromDictionary: [self adInfoForAd: ad]];
    
    [self fireWindowEventWithName: @"OnRewardedAdReceivedRewardEvent" body: body];
}

#pragma mark - Internal Methods

- (void)createAdViewWithAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat atPosition:(NSString *)adViewPosition command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Creating %@ with ad unit identifier \"%@\" and position: \"%@\"", adFormat, adUnitIdentifier, adViewPosition];
    
    // Retrieve ad view from the map
    MAAdView *adView = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat atPosition: adViewPosition];
    adView.hidden = YES;
    self.safeAreaBackground.hidden = YES;
    
    // Position ad view immediately so if publisher sets color before ad loads, it will not be the size of the screen
    self.adViewAdFormats[adUnitIdentifier] = adFormat;
    [self positionAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    
    [adView loadAd];
    
    // The publisher may have requested to show the banner before it was created. Now that the banner is created, show it.
    if ( [self.adUnitIdentifiersToShowAfterCreate containsObject: adUnitIdentifier] )
    {
        [self showAdViewWithAdUnitIdentifier: adUnitIdentifier adFormat: adFormat command: nil];
        [self.adUnitIdentifiersToShowAfterCreate removeObject: adUnitIdentifier];
    }
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setAdViewBackgroundColorForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat hexColorCode:(NSString *)hexColorCode command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Setting %@ with ad unit identifier \"%@\" to color: \"%@\"", adFormat, adUnitIdentifier, hexColorCode];
    
    // In some cases, black color may get redrawn on each frame update, resulting in an undesired flicker
    UIColor *convertedColor;
    if ( [hexColorCode containsString: @"FF000000"] )
    {
        convertedColor = [UIColor al_colorWithHexString: @"FF000001"];
    }
    else
    {
        convertedColor = [UIColor al_colorWithHexString: hexColorCode];
    }
    
    MAAdView *view = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    self.publisherBannerBackgroundColor = convertedColor;
    self.safeAreaBackground.backgroundColor = view.backgroundColor = convertedColor;
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setAdViewPlacement:(nullable NSString *)placement forAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Setting placement \"%@\" for \"%@\" with ad unit identifier \"%@\"", placement, adFormat, adUnitIdentifier];
    
    MAAdView *adView = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    adView.placement = placement;
    
    [self sendOKPluginResultForCommand: command];
}

- (void)updateAdViewPosition:(NSString *)adViewPosition forAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat command:(CDVInvokedUrlCommand *)command
{
    // Check if the previous position is same as the new position. If so, no need to update the position again.
    NSString *previousPosition = self.adViewPositions[adUnitIdentifier];
    if ( !adViewPosition || [adViewPosition isEqualToString: previousPosition] ) return;
    
    self.adViewPositions[adUnitIdentifier] = adViewPosition;
    [self positionAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)setAdViewExtraParameterForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat key:(NSString *)key value:(nullable NSString *)value command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Setting %@ extra with key: \"%@\" value: \"%@\"", adFormat, key, value];
    
    MAAdView *adView = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    [adView setExtraParameterForKey: key value: value];
    
    if (  [@"force_banner" isEqualToString: key] && MAAdFormat.mrec != adFormat )
    {
        // Handle local changes as needed
        MAAdFormat *adFormat;
        
        BOOL shouldForceBanner = [NSNumber al_numberWithString: value].boolValue;
        if ( shouldForceBanner )
        {
            adFormat = MAAdFormat.banner;
        }
        else
        {
            adFormat = DEVICE_SPECIFIC_ADVIEW_AD_FORMAT;
        }
        
        self.adViewAdFormats[adUnitIdentifier] = adFormat;
        [self positionAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    }
    
    [self sendOKPluginResultForCommand: command];
}

- (void)showAdViewWithAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat command:(nullable CDVInvokedUrlCommand *)command
{
    [self log: @"Showing %@ with ad unit identifier \"%@\"", adFormat, adUnitIdentifier];
    
    MAAdView *view = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    if ( !view )
    {
        [self log: @"%@ does not exist for ad unit identifier %@.", adFormat, adUnitIdentifier];
        
        // The adView has not yet been created. Store the ad unit ID, so that it can be displayed once the banner has been created.
        [self.adUnitIdentifiersToShowAfterCreate addObject: adUnitIdentifier];
    }
    
    self.safeAreaBackground.hidden = NO;
    view.hidden = NO;
    
    [view startAutoRefresh];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)hideAdViewWithAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Hiding %@ with ad unit identifier \"%@\"", adFormat, adUnitIdentifier];
    [self.adUnitIdentifiersToShowAfterCreate removeObject: adUnitIdentifier];
    
    MAAdView *view = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    view.hidden = YES;
    self.safeAreaBackground.hidden = YES;
    
    [view stopAutoRefresh];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)destroyAdViewWithAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat command:(CDVInvokedUrlCommand *)command
{
    [self log: @"Destroying %@ with ad unit identifier \"%@\"", adFormat, adUnitIdentifier];
    
    MAAdView *view = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    view.delegate = nil;
    
    [view removeFromSuperview];
    
    [self.adViews removeObjectForKey: adUnitIdentifier];
    [self.adViewPositions removeObjectForKey: adUnitIdentifier];
    [self.adViewAdFormats removeObjectForKey: adUnitIdentifier];
    
    [self sendOKPluginResultForCommand: command];
}

- (void)logInvalidAdFormat:(MAAdFormat *)adFormat
{
    [self log: @"invalid ad format: %@, from %@", adFormat, [NSThread callStackSymbols]];
}

- (void)log:(NSString *)format, ...
{
    va_list valist;
    va_start(valist, format);
    NSString *message = [[NSString alloc] initWithFormat: format arguments: valist];
    va_end(valist);
    
    NSLog(@"[%@] [%@] %@", SDK_TAG, TAG, message);
}

- (MAInterstitialAd *)retrieveInterstitialForAdUnitIdentifier:(NSString *)adUnitIdentifier
{
    MAInterstitialAd *result = self.interstitials[adUnitIdentifier];
    if ( !result )
    {
        result = [[MAInterstitialAd alloc] initWithAdUnitIdentifier: adUnitIdentifier sdk: self.sdk];
        result.delegate = self;
        
        self.interstitials[adUnitIdentifier] = result;
    }
    
    return result;
}

- (MARewardedAd *)retrieveRewardedAdForAdUnitIdentifier:(NSString *)adUnitIdentifier
{
    MARewardedAd *result = self.rewardedAds[adUnitIdentifier];
    if ( !result )
    {
        result = [MARewardedAd sharedWithAdUnitIdentifier: adUnitIdentifier sdk: self.sdk];
        result.delegate = self;
        
        self.rewardedAds[adUnitIdentifier] = result;
    }
    
    return result;
}

- (MAAdView *)retrieveAdViewForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat
{
    return [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat atPosition: nil];
}

- (MAAdView *)retrieveAdViewForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat atPosition:(NSString *)adViewPosition
{
    return [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat atPosition: adViewPosition attach: YES];
}

- (MAAdView *)retrieveAdViewForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat atPosition:(NSString *)adViewPosition attach:(BOOL)attach
{
    MAAdView *result = self.adViews[adUnitIdentifier];
    if ( !result && adViewPosition )
    {
        result = [[MAAdView alloc] initWithAdUnitIdentifier: adUnitIdentifier adFormat: adFormat sdk: self.sdk];
        result.delegate = self;
        result.userInteractionEnabled = NO;
        result.translatesAutoresizingMaskIntoConstraints = NO;
        
        self.adViews[adUnitIdentifier] = result;
        
        // If this is programmatic (non native RN)
        if ( attach )
        {
            self.adViewPositions[adUnitIdentifier] = adViewPosition;
            [ROOT_VIEW_CONTROLLER.view addSubview: result];
        }
    }
    
    return result;
}

- (void)positionAdViewForAd:(MAAd *)ad
{
    [self positionAdViewForAdUnitIdentifier: ad.adUnitIdentifier adFormat: ad.format];
}

- (void)positionAdViewForAdUnitIdentifier:(NSString *)adUnitIdentifier adFormat:(MAAdFormat *)adFormat
{
    MAAdView *adView = [self retrieveAdViewForAdUnitIdentifier: adUnitIdentifier adFormat: adFormat];
    NSString *adViewPosition = self.adViewPositions[adUnitIdentifier];
    
    UIView *superview = adView.superview;
    if ( !superview ) return;
    
    // Deactivate any previous constraints so that the banner can be positioned again.
    NSArray<NSLayoutConstraint *> *activeConstraints = self.adViewConstraints[adUnitIdentifier];
    [NSLayoutConstraint deactivateConstraints: activeConstraints];
    
    // Ensure superview contains the safe area background.
    if ( ![superview.subviews containsObject: self.safeAreaBackground] )
    {
        [self.safeAreaBackground removeFromSuperview];
        [superview insertSubview: self.safeAreaBackground belowSubview: adView];
    }
    
    // Deactivate any previous constraints and reset visibility state so that the safe area background can be positioned again.
    [NSLayoutConstraint deactivateConstraints: self.safeAreaBackground.constraints];
    self.safeAreaBackground.hidden = NO;
    
    CGSize adViewSize = [[self class] adViewSizeForAdFormat: adFormat];
    
    // All positions have constant height
    NSMutableArray<NSLayoutConstraint *> *constraints = [NSMutableArray arrayWithObject: [adView.heightAnchor constraintEqualToConstant: adViewSize.height]];
    
    UILayoutGuide *layoutGuide;
    if ( @available(iOS 11.0, *) )
    {
        layoutGuide = superview.safeAreaLayoutGuide;
    }
    else
    {
        layoutGuide = superview.layoutMarginsGuide;
    }
    
    // If top of bottom center, stretch width of screen
    if ( [adViewPosition isEqual: @"top_center"] || [adViewPosition isEqual: @"bottom_center"] )
    {
        // If publisher actually provided a banner background color, span the banner across the realm
        if ( self.publisherBannerBackgroundColor && adFormat != MAAdFormat.mrec )
        {
            [constraints addObjectsFromArray: @[[self.safeAreaBackground.leftAnchor constraintEqualToAnchor: superview.leftAnchor],
                                                [self.safeAreaBackground.rightAnchor constraintEqualToAnchor: superview.rightAnchor]]];
            
            if ( [adViewPosition isEqual: @"top_center"] )
            {
                [constraints addObjectsFromArray: @[[adView.topAnchor constraintEqualToAnchor: layoutGuide.topAnchor],
                                                    [adView.leftAnchor constraintEqualToAnchor: superview.leftAnchor],
                                                    [adView.rightAnchor constraintEqualToAnchor: superview.rightAnchor]]];
                [constraints addObjectsFromArray: @[[self.safeAreaBackground.topAnchor constraintEqualToAnchor: superview.topAnchor],
                                                    [self.safeAreaBackground.bottomAnchor constraintEqualToAnchor: adView.topAnchor]]];
            }
            else // BottomCenter
            {
                [constraints addObjectsFromArray: @[[adView.bottomAnchor constraintEqualToAnchor: layoutGuide.bottomAnchor],
                                                    [adView.leftAnchor constraintEqualToAnchor: superview.leftAnchor],
                                                    [adView.rightAnchor constraintEqualToAnchor: superview.rightAnchor]]];
                [constraints addObjectsFromArray: @[[self.safeAreaBackground.topAnchor constraintEqualToAnchor: adView.bottomAnchor],
                                                    [self.safeAreaBackground.bottomAnchor constraintEqualToAnchor: superview.bottomAnchor]]];
            }
        }
        // If pub does not have a background color set - we shouldn't span the banner the width of the realm (there might be user-interactable UI on the sides)
        else
        {
            self.safeAreaBackground.hidden = YES;
            
            // Assign constant width of 320 or 728
            [constraints addObject: [adView.widthAnchor constraintEqualToConstant: adViewSize.width]];
            [constraints addObject: [adView.centerXAnchor constraintEqualToAnchor: layoutGuide.centerXAnchor]];
            
            if ( [adViewPosition isEqual: @"top_center"] )
            {
                [constraints addObject: [adView.topAnchor constraintEqualToAnchor: layoutGuide.topAnchor]];
            }
            else // BottomCenter
            {
                [constraints addObject: [adView.bottomAnchor constraintEqualToAnchor: layoutGuide.bottomAnchor]];
            }
        }
    }
    // Otherwise, publisher will likely construct his own views around the adview
    else
    {
        self.safeAreaBackground.hidden = YES;
        
        // Assign constant width of 320 or 728
        [constraints addObject: [adView.widthAnchor constraintEqualToConstant: adViewSize.width]];
        
        if ( [adViewPosition isEqual: @"top_left"] )
        {
            [constraints addObjectsFromArray: @[[adView.topAnchor constraintEqualToAnchor: layoutGuide.topAnchor],
                                                [adView.leftAnchor constraintEqualToAnchor: superview.leftAnchor]]];
        }
        else if ( [adViewPosition isEqual: @"top_right"] )
        {
            [constraints addObjectsFromArray: @[[adView.topAnchor constraintEqualToAnchor: layoutGuide.topAnchor],
                                                [adView.rightAnchor constraintEqualToAnchor: superview.rightAnchor]]];
        }
        else if ( [adViewPosition isEqual: @"centered"] )
        {
            [constraints addObjectsFromArray: @[[adView.centerXAnchor constraintEqualToAnchor: layoutGuide.centerXAnchor],
                                                [adView.centerYAnchor constraintEqualToAnchor: layoutGuide.centerYAnchor]]];
        }
        else if ( [adViewPosition isEqual: @"bottom_left"] )
        {
            [constraints addObjectsFromArray: @[[adView.bottomAnchor constraintEqualToAnchor: layoutGuide.bottomAnchor],
                                                [adView.leftAnchor constraintEqualToAnchor: superview.leftAnchor]]];
        }
        else if ( [adViewPosition isEqual: @"bottom_right"] )
        {
            [constraints addObjectsFromArray: @[[adView.bottomAnchor constraintEqualToAnchor: layoutGuide.bottomAnchor],
                                                [adView.rightAnchor constraintEqualToAnchor: superview.rightAnchor]]];
        }
    }
    
    self.adViewConstraints[adUnitIdentifier] = constraints;
    
    [NSLayoutConstraint activateConstraints: constraints];
}

+ (CGSize)adViewSizeForAdFormat:(MAAdFormat *)adFormat
{
    if ( MAAdFormat.leader == adFormat )
    {
        return CGSizeMake(728.0f, 90.0f);
    }
    else if ( MAAdFormat.banner == adFormat )
    {
        return CGSizeMake(320.0f, 50.0f);
    }
    else if ( MAAdFormat.mrec == adFormat )
    {
        return CGSizeMake(300.0f, 250.0f);
    }
    else
    {
        [NSException raise: NSInvalidArgumentException format: @"Invalid ad format"];
        return CGSizeZero;
    }
}

- (NSDictionary<NSString *, id> *)adInfoForAd:(MAAd *)ad
{
    return @{@"adUnitId" : ad.adUnitIdentifier,
             @"creativeId" : ad.creativeIdentifier ?: @"",
             @"networkName" : ad.networkName,
             @"placement" : ad.placement ?: @"",
             @"revenue" : @(ad.revenue)};
}

#pragma mark - Cordova Event Bridge (via window)

- (void)fireWindowEventWithName:(NSString *)name body:(NSDictionary<NSString *, id> *)body
{
    NSError *error = nil;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject: body options: 0 error: &error];
    
    if ( error )
    {
        [self log: @"Failed to serialize body: %@", body];
        return;
    }
    
    NSString *jsonStr = [[NSString alloc] initWithData: jsonData encoding: NSUTF8StringEncoding];
    NSString *js = [NSString stringWithFormat: @"cordova.fireWindowEvent('%@', %@)", name, jsonStr];
    [self.commandDelegate evalJs: js];
}

- (void)sendOKPluginResultForCommand:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

- (void)sendOKPluginResultForCommand:(CDVInvokedUrlCommand *)command withBool:(BOOL)boolValue
{
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsBool: boolValue];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

- (void)sendErrorPluginResultForCommand:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

- (void)sendErrorPluginResultForCommand:(CDVInvokedUrlCommand *)command withBool:(BOOL)boolValue
{
    CDVPluginResult *result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsBool: boolValue];
    [self.commandDelegate sendPluginResult: result callbackId: command.callbackId];
}

@end
