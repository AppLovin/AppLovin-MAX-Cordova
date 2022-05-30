## Versions

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
