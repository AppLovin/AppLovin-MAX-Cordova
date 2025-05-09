## Versions

## 2.1.0
    * Depends on Android SDK [v13.2.0](https://github.com/AppLovin/AppLovin-MAX-SDK-Android/releases/tag/release_13_2_0) and iOS SDK [v13.2.0](https://github.com/AppLovin/AppLovin-MAX-SDK-iOS/releases/tag/release_13_2_0).
## 2.0.0
    * Depends on Android SDK [v13.1.0](https://github.com/AppLovin/AppLovin-MAX-SDK-Android/releases/tag/release_13_1_0) and iOS SDK [v13.1.0](https://github.com/AppLovin/AppLovin-MAX-SDK-iOS/releases/tag/release_13_1_0).
    * Replace deprecated `Targeting Data` APIs with `Segment Targeting`.
    * Remove deprecated `ConsentDialogState` APIs.
    * Remove deprecated `isAgeRestrictedUser` APIs.
    * Add a listener for `OnRewardedAdReceivedRewardEvent`.
## 1.1.5
    * Depends on Android SDK [v12.3.1](https://github.com/AppLovin/AppLovin-MAX-SDK-Android/releases/tag/release_12_3_1) and iOS SDK [v12.3.1](https://github.com/AppLovin/AppLovin-MAX-SDK-iOS/releases/tag/release_12_3_1).
## 1.1.4
    * Attempt fix at increased CPU usage on app pause on Android. https://github.com/AppLovin/AppLovin-MAX-Cordova/issues/44
## 1.1.3
    * Fix NPE if the integration attempts to show a banner before it is created.
## 1.1.2
    * Fix `destroyMRec` API by adding the missing argument.
## 1.1.1
    * Fix `plugins.xml` using a deprecated CocoaPods repository.
## 1.1.0
    * Add support for clearing all targeting data. 
## 1.0.10
    * Add support for targeting data. 
## 1.0.9
    * Fix "OnMRecAdExpandedEvent" not being called when MRECs are expanded.
## 1.0.8
    * Remove `NSAdvertisingAttributionReportEndpoint` from `Info.plist`.
## 1.0.7
    * Automatically add `NSAdvertisingAttributionReportEndpoint` to `Info.plist`.
## 1.0.6
    * Add support for latest SDKs v10.3.1 with new callbacks.
## 1.0.5
    * Add support for latest SDKs v10.3.0 with new callbacks.
    * Fallback to SDK key in Android Manifest and Info.plist if not passed programmatically.
## 1.0.4
    * Pass `"countryCode"` in initialization callback.
## 1.0.3
    * Fix crashes for when creative id or placement is nil for an ad.
## 1.0.2
    * Return more data in ad callbacks in addition to `ad.adUnitId` (e.g. `adInfo.creativeId`, `adInfo.networkName`, `adInfo.placement`, `adInfo.revenue`).
## 1.0.1
    * Initial release with support for interstitials, rewarded ads, banners, and MRECs.
