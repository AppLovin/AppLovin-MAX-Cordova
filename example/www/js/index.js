document.addEventListener('deviceready', onDeviceReady, false);

var AppLovinMAX;

var INTER_AD_UNIT_ID;
var REWARDED_AD_UNIT_ID;
var BANNER_AD_UNIT_ID;
var MREC_AD_UNIT_ID;

if (window.cordova.platformId.toUpperCase() === 'IOS') {
    INTER_AD_UNIT_ID = 'YOUR_IOS_INTER_AD_UNIT_ID';
    REWARDED_AD_UNIT_ID = 'YOUR_IOS_REWARDED_AD_UNIT_ID';
    BANNER_AD_UNIT_ID = 'YOUR_IOS_BANNER_AD_UNIT_ID';
    MREC_AD_UNIT_ID = 'YOUR_IOS_MREC_AD_UNIT_ID';
} else {
    // Assume Android
    INTER_AD_UNIT_ID = 'YOUR_ANDROID_INTER_AD_UNIT_ID';
    REWARDED_AD_UNIT_ID = 'YOUR_ANDROID_REWARDED_AD_UNIT_ID';
    BANNER_AD_UNIT_ID = 'YOUR_ANDROID_BANNER_AD_UNIT_ID';
    MREC_AD_UNIT_ID = 'YOUR_ANDROID_MREC_AD_UNIT_ID';
}

var SDK_KEY = "YOUR_SDK_KEY";

var mediationDebuggerButton = document.querySelector('#med_debugger_button');
var interButton = document.querySelector('#inter_button');
var rewardedAdButton = document.querySelector('#rewarded_ad_button');
var bannerAdButton = document.querySelector('#banner_ad_button');
var mrecAdButton = document.querySelector('#mrec_ad_button');

function onDeviceReady() {
    console.log('Running cordova-' + cordova.platformId + 'v' + cordova.version);

    // Disable buttons until SDK is initialized
    mediationDebuggerButton.disabled = true;
    interButton.disabled = true;
    rewardedAdButton.disabled = true;
    bannerAdButton.disabled = true;
    mrecAdButton.disabled = true;

    // 3rd-party plugins are loaded now
    AppLovinMAX = cordova.require('cordova-plugin-applovin-max.AppLovinMAX');
    AppLovinMAX.setVerboseLogging(true);
    AppLovinMAX.initialize(SDK_KEY, function (configuration) {
        mediationDebuggerButton.disabled = false;

        initializeInterstitialAds();
        initializeRewardedAds();
        initializeBannerAds();
        initializeMRecAds();
    });
}

function showMediationDebugger() {
    AppLovinMAX.showMediationDebugger();
}

function onInterButtonClicked() {
    if (AppLovinMAX.isInterstitialReady(INTER_AD_UNIT_ID)) {
        AppLovinMAX.showInterstitial(INTER_AD_UNIT_ID);
    } else {
        loadInterstitial();
    }
}

function initializeInterstitialAds() {
    window.addEventListener('OnInterstitialLoadedEvent', function (adInfo) {
        interButton.innerHTML = 'Show Interstitial';
        interButton.disabled = false;
    });

    window.addEventListener('OnInterstitialLoadFailedEvent', function (adInfo) {
        interButton.innerHTML = 'Load Interstitial';
        interButton.disabled = false;
    });
    window.addEventListener('OnInterstitialClickedEvent', function (adInfo) {});
    window.addEventListener('OnInterstitialDisplayedEvent', function (adInfo) {});
    window.addEventListener('OnInterstitialAdFailedToDisplayEvent', function (adInfo) {
        interButton.innerHTML = 'Load Interstitial';
        interButton.disabled = false;
    });
    window.addEventListener('OnInterstitialHiddenEvent', function (adInfo) {
        interButton.innerHTML = 'Load Interstitial';
        interButton.disabled = false;
    });

    // Load the first interstitial
    loadInterstitial();
}

function loadInterstitial() {
    interButton.innerHTML = 'Loading Interstitial...';
    interButton.disabled = true;
    AppLovinMAX.loadInterstitial(INTER_AD_UNIT_ID);
}

function onRewardedAdButtonClicked() {
    if (AppLovinMAX.isRewardedAdReady(REWARDED_AD_UNIT_ID)) {
        AppLovinMAX.showRewardedAd(REWARDED_AD_UNIT_ID);
    } else {
        loadRewardedAd();
    }
}

function initializeRewardedAds() {
    window.addEventListener('OnRewardedAdLoadedEvent', function (adInfo) {
        rewardedAdButton.innerHTML = 'Show Rewarded Ad';
        rewardedAdButton.disabled = false;
    });

    window.addEventListener('OnRewardedAdLoadFailedEvent', function (adInfo) {
        rewardedAdButton.innerHTML = 'Load Rewarded Ad';
        rewardedAdButton.disabled = false;
    });
    window.addEventListener('OnRewardedAdClickedEvent', function (adInfo) {});
    window.addEventListener('OnRewardedAdDisplayedEvent', function (adInfo) {});
    window.addEventListener('OnRewardedAdAdFailedToDisplayEvent', function (adInfo) {
        rewardedAdButton.innerHTML = 'Load Rewarded Ad';
        rewardedAdButton.disabled = false;
    });
    window.addEventListener('OnRewardedAdHiddenEvent', function (adInfo) {
        rewardedAdButton.innerHTML = 'Load Rewarded Ad';
        rewardedAdButton.disabled = false;
    });
    window.addEventListener('OnRewardedAdReceivedRewardEvent', function (adInfo) {
        // Rewarded ad was displayed and user should receive the reward
    });

    // Load the first rewarded ad
    loadRewardedAd();
}

function loadRewardedAd() {
    rewardedAdButton.innerHTML = 'Loading Rewarded Ad...';
    rewardedAdButton.disabled = true;
    AppLovinMAX.loadRewardedAd(REWARDED_AD_UNIT_ID);
}

function initializeBannerAds() {
    window.addEventListener('OnBannerAdLoadedEvent', function (adInfo) {
        bannerAdButton.innerHTML = 'Showing Banner Ad...';
        AppLovinMAX.showBanner(BANNER_AD_UNIT_ID);
    });
    window.addEventListener('OnBannerAdLoadFailedEvent', function (adInfo) {
        bannerAdButton.innerHTML = 'Banner Ad Failed to Load';
    });
    window.addEventListener('OnBannerAdClickedEvent', function (adInfo) {});
    window.addEventListener('OnBannerAdCollapsedEvent', function (adInfo) {});
    window.addEventListener('OnBannerAdExpandedEvent', function (adInfo) {});

    // Banners are automatically sized to 320x50 on phones and 728x90 on tablets
    // You may use the utility method `AppLovinMAX.isTablet()` to help with view sizing adjustments
    AppLovinMAX.createBanner(BANNER_AD_UNIT_ID, AppLovinMAX.AdViewPosition.BOTTOM_CENTER);

    // Set background or background color for banners to be fully functional
    // In this case we are setting it to black - PLEASE USE HEX STRINGS ONLY
    AppLovinMAX.setBannerBackgroundColor(BANNER_AD_UNIT_ID, '#000000');
}

function initializeMRecAds() {
    window.addEventListener('OnMRecAdLoadedEvent', function (adInfo) {
        mrecAdButton.innerHTML = 'Showing MREC Ad...';
        AppLovinMAX.showMRec(MREC_AD_UNIT_ID);
    });
    window.addEventListener('OnMRecAdLoadFailedEvent', function (adInfo) {
        rewardedAdButton.innerHTML = 'MREC Ad Failed to Load';
    });
    window.addEventListener('OnMRecAdClickedEvent', function (adInfo) {});
    window.addEventListener('OnMRecAdCollapsedEvent', function (adInfo) {});
    window.addEventListener('OnMRecAdExpandedEvent', function (adInfo) {});

    // Banners are automatically sized to 320x50 on phones and 728x90 on tablets
    // You may use the utility method `AppLovinMAX.isTablet()` to help with view sizing adjustments
    AppLovinMAX.createBanner(MREC_AD_UNIT_ID, AppLovinMAX.AdViewPosition.CENTERED);
}
