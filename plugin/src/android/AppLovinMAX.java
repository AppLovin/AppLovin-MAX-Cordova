package com.applovin.cordova;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxAdView;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkSettings;
import com.applovin.sdk.AppLovinSdkUtils;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.cordova.PluginResult.Status.ERROR;
import static org.apache.cordova.PluginResult.Status.OK;

/**
 * Created by Thomas So on January 1 2021
 */
public class AppLovinMAX
        extends CordovaPlugin
        implements MaxAdListener, MaxAdViewAdListener, MaxRewardedAdListener
{
    private static final String SDK_TAG = "AppLovinSdk";
    private static final String TAG     = "AppLovinMAX";

    // Parent Fields
    private AppLovinSdk              sdk;
    private boolean                  isPluginInitialized;
    private boolean                  isSdkInitialized;
    private AppLovinSdkConfiguration sdkConfiguration;

    // Store these values if pub attempts to set it before initializing
    private String       userIdToSet;
    private List<String> testDeviceAdvertisingIdsToSet;
    private Boolean      verboseLoggingToSet;

    // Fullscreen Ad Fields
    private final Map<String, MaxInterstitialAd> mInterstitials = new HashMap<>( 2 );
    private final Map<String, MaxRewardedAd>     mRewardedAds   = new HashMap<>( 2 );

    // Banner Fields
    private final Map<String, MaxAdView>   mAdViews                    = new HashMap<>( 2 );
    private final Map<String, MaxAdFormat> mAdViewAdFormats            = new HashMap<>( 2 );
    private final Map<String, String>      mAdViewPositions            = new HashMap<>( 2 );
    private final Map<String, MaxAdFormat> mVerticalAdViewFormats      = new HashMap<>( 2 );
    private final List<String>             mAdUnitIdsToShowAfterCreate = new ArrayList<>( 2 );

    private Activity getCurrentActivity() { return cordova.getActivity(); }

    public AppLovinMAX() { }

    @Override
    public void initialize(final CordovaInterface cordova, final CordovaWebView webView)
    {
        super.initialize( cordova, webView );
    }

    private void initialize(final String pluginVersion, final String sdkKey, final CallbackContext callbackContext) throws JSONException
    {
        Context context = cordova.getContext();

        // Check if Activity is available
        Activity currentActivity = getCurrentActivity();
        if ( currentActivity == null ) throw new IllegalStateException( "No Activity found" );

        // Guard against running init logic multiple times
        if ( isPluginInitialized )
        {
            callbackContext.success( getInitializationMessage( context ) );
            return;
        }

        isPluginInitialized = true;

        d( "Initializing AppLovin MAX Cordova v" + pluginVersion + "..." );

        // If SDK key passed in is empty, check Android Manifest
        String sdkKeyToUse = sdkKey;
        if ( TextUtils.isEmpty( sdkKey ) )
        {
            try
            {
                PackageManager packageManager = context.getPackageManager();
                String packageName = context.getPackageName();
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo( packageName, PackageManager.GET_META_DATA );
                Bundle metaData = applicationInfo.metaData;

                sdkKeyToUse = metaData.getString( "applovin.sdk.key", "" );
            }
            catch ( Throwable th )
            {
                e( "Unable to retrieve SDK key from Android Manifest: " + th );
            }

            if ( TextUtils.isEmpty( sdkKeyToUse ) )
            {
                throw new IllegalStateException( "Unable to initialize AppLovin SDK - no SDK key provided and not found in Android Manifest!" );
            }
        }

        // Initialize SDK
        sdk = AppLovinSdk.getInstance( sdkKeyToUse, new AppLovinSdkSettings( context ), currentActivity );
        sdk.setPluginVersion( "Cordova-" + pluginVersion );
        sdk.setMediationProvider( AppLovinMediationProvider.MAX );

        // Set user id if needed
        if ( !TextUtils.isEmpty( userIdToSet ) )
        {
            sdk.setUserIdentifier( userIdToSet );
            userIdToSet = null;
        }

        // Set test device ids if needed
        if ( testDeviceAdvertisingIdsToSet != null )
        {
            sdk.getSettings().setTestDeviceAdvertisingIds( testDeviceAdvertisingIdsToSet );
            testDeviceAdvertisingIdsToSet = null;
        }

        // Set verbose logging state if needed
        if ( verboseLoggingToSet != null )
        {
            sdk.getSettings().setVerboseLogging( verboseLoggingToSet );
            verboseLoggingToSet = null;
        }

        sdk.initializeSdk( configuration -> {
            d( "SDK initialized" );

            sdkConfiguration = configuration;
            isSdkInitialized = true;

            // Enable orientation change listener, so that the position can be updated for vertical banners.
            new OrientationEventListener( getCurrentActivity() )
            {
                @Override
                public void onOrientationChanged(final int orientation)
                {
                    for ( final Map.Entry<String, MaxAdFormat> adUnitFormats : mVerticalAdViewFormats.entrySet() )
                    {
                        positionAdView( adUnitFormats.getKey(), adUnitFormats.getValue() );
                    }
                }
            }.enable();

            try
            {
                callbackContext.success( getInitializationMessage( context ) );
            }
            catch ( Throwable ignored ) {}
        } );
    }

    private JSONObject getInitializationMessage(final Context context) throws JSONException
    {
        JSONObject message = new JSONObject();

        if ( sdkConfiguration != null )
        {
            message.put( "consentDialogState", sdkConfiguration.getConsentDialogState().ordinal() );
            message.put( "countryCode", sdkConfiguration.getCountryCode() );
        }
        else
        {
            message.put( "consentDialogState", AppLovinSdkConfiguration.ConsentDialogState.UNKNOWN.ordinal() );
        }

        message.put( "hasUserConsent", AppLovinPrivacySettings.hasUserConsent( context ) );
        message.put( "isAgeRestrictedUser", AppLovinPrivacySettings.isAgeRestrictedUser( context ) );
        message.put( "isDoNotSell", AppLovinPrivacySettings.isDoNotSell( context ) );
        message.put( "isTablet", AppLovinSdkUtils.isTablet( context ) );

        return message;
    }

    private boolean isInitialized()
    {
        return isPluginInitialized && isSdkInitialized;
    }

    // General Public API

    public void showMediationDebugger(final CallbackContext callbackContext)
    {
        if ( sdk == null )
        {
            Log.e( "[" + TAG + "]", "Failed to show mediation debugger - please ensure the AppLovin MAX Cordova Plugin has been initialized by calling 'AppLovinMAX.initialize(...);'!" );
            return;
        }

        sdk.showMediationDebugger();

        callbackContext.success();
    }

    public void getConsentDialogState(final CallbackContext callbackContext)
    {
        if ( !isInitialized() )
        {
            PluginResult result = new PluginResult( OK, AppLovinSdkConfiguration.ConsentDialogState.UNKNOWN.ordinal() );
            callbackContext.sendPluginResult( result );

            return;
        }

        PluginResult result = new PluginResult( OK, sdkConfiguration.getConsentDialogState().ordinal() );
        callbackContext.sendPluginResult( result );
    }

    public void setHasUserConsent(final boolean hasUserConsent, final CallbackContext callbackContext)
    {
        AppLovinPrivacySettings.setHasUserConsent( hasUserConsent, getCurrentActivity() );
        callbackContext.success();
    }

    public void hasUserConsent(final CallbackContext callbackContext)
    {
        PluginResult result = new PluginResult( OK, AppLovinPrivacySettings.hasUserConsent( getCurrentActivity() ) );
        callbackContext.sendPluginResult( result );
    }

    public void setIsAgeRestrictedUser(final boolean isAgeRestrictedUser, final CallbackContext callbackContext)
    {
        AppLovinPrivacySettings.setIsAgeRestrictedUser( isAgeRestrictedUser, getCurrentActivity() );
        callbackContext.success();
    }

    public void isAgeRestrictedUser(final CallbackContext callbackContext)
    {
        PluginResult result = new PluginResult( OK, AppLovinPrivacySettings.isAgeRestrictedUser( getCurrentActivity() ) );
        callbackContext.sendPluginResult( result );
    }

    public void setDoNotSell(final boolean doNotSell, final CallbackContext callbackContext)
    {
        AppLovinPrivacySettings.setDoNotSell( doNotSell, getCurrentActivity() );
        callbackContext.success();
    }

    public void isDoNotSell(final CallbackContext callbackContext)
    {
        PluginResult result = new PluginResult( OK, AppLovinPrivacySettings.isDoNotSell( getCurrentActivity() ) );
        callbackContext.sendPluginResult( result );
    }

    public void setUserId(final String userId, final CallbackContext callbackContext)
    {
        if ( isPluginInitialized )
        {
            sdk.setUserIdentifier( userId );
            userIdToSet = null;
        }
        else
        {
            userIdToSet = userId;
        }

        callbackContext.success();
    }

    public void setMuted(final boolean muted, final CallbackContext callbackContext)
    {
        if ( !isPluginInitialized )
        {
            callbackContext.sendPluginResult( new PluginResult( ERROR ) );
            return;
        }

        sdk.getSettings().setMuted( muted );

        callbackContext.success();
    }

    public void setVerboseLogging(final boolean verboseLoggingEnabled, final CallbackContext callbackContext)
    {
        if ( isPluginInitialized )
        {
            sdk.getSettings().setVerboseLogging( verboseLoggingEnabled );
            verboseLoggingToSet = null;
        }
        else
        {
            verboseLoggingToSet = verboseLoggingEnabled;
        }

        callbackContext.success();
    }

    public void setTestDeviceAdvertisingIds(final List<String> advertisingIds, final CallbackContext callbackContext)
    {
        if ( isPluginInitialized )
        {
            sdk.getSettings().setTestDeviceAdvertisingIds( advertisingIds );
            testDeviceAdvertisingIdsToSet = null;
        }
        else
        {
            testDeviceAdvertisingIdsToSet = advertisingIds;
        }

        callbackContext.success();
    }

    // EVENT TRACKING

    public void trackEvent(final String event, final JSONObject parameters, final CallbackContext callbackContext) throws JSONException
    {
        Map<String, String> parametersToUse = new HashMap<>();
        if ( parameters != null )
        {
            parametersToUse = AppLovinSdkUtils.toMap( parameters );
        }

        sdk.getEventService().trackEvent( event, parametersToUse );

        callbackContext.success();
    }

    // BANNERS

    public void createBanner(final String adUnitId, final String bannerPosition, final CallbackContext callbackContext)
    {
        createAdView( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), bannerPosition, callbackContext );
    }

    public void setBannerBackgroundColor(final String adUnitId, final String hexColorCode, final CallbackContext callbackContext)
    {
        setAdViewBackgroundColor( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), hexColorCode, callbackContext );
    }

    public void setBannerPlacement(final String adUnitId, final String placement, final CallbackContext callbackContext)
    {
        setAdViewPlacement( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), placement, callbackContext );
    }

    public void updateBannerPosition(final String adUnitId, final String bannerPosition, final CallbackContext callbackContext)
    {
        updateAdViewPosition( adUnitId, bannerPosition, getDeviceSpecificBannerAdViewAdFormat(), callbackContext );
    }

    public void setBannerExtraParameter(final String adUnitId, final String key, final String value, final CallbackContext callbackContext)
    {
        setAdViewExtraParameters( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), key, value, callbackContext );
    }

    public void showBanner(final String adUnitId, final CallbackContext callbackContext)
    {
        showAdView( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), callbackContext );
    }

    public void hideBanner(final String adUnitId, final CallbackContext callbackContext)
    {
        hideAdView( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), callbackContext );
    }

    public void destroyBanner(final String adUnitId, final CallbackContext callbackContext)
    {
        destroyAdView( adUnitId, getDeviceSpecificBannerAdViewAdFormat(), callbackContext );
    }

    // MRECS

    public void createMRec(final String adUnitId, final String mrecPosition, final CallbackContext callbackContext)
    {
        createAdView( adUnitId, MaxAdFormat.MREC, mrecPosition, callbackContext );
    }

    public void setMRecPlacement(final String adUnitId, final String placement, final CallbackContext callbackContext)
    {
        setAdViewPlacement( adUnitId, MaxAdFormat.MREC, placement, callbackContext );
    }

    public void updateMRecPosition(final String adUnitId, final String mrecPosition, final CallbackContext callbackContext)
    {
        updateAdViewPosition( adUnitId, mrecPosition, MaxAdFormat.MREC, callbackContext );
    }

    public void showMRec(final String adUnitId, final CallbackContext callbackContext)
    {
        showAdView( adUnitId, MaxAdFormat.MREC, callbackContext );
    }

    public void hideMRec(final String adUnitId, final CallbackContext callbackContext)
    {
        hideAdView( adUnitId, MaxAdFormat.MREC, callbackContext );
    }

    public void destroyMRec(final String adUnitId, final CallbackContext callbackContext)
    {
        destroyAdView( adUnitId, MaxAdFormat.MREC, callbackContext );
    }

    // INTERSTITIALS

    public void loadInterstitial(final String adUnitId, final CallbackContext callbackContext)
    {
        MaxInterstitialAd interstitial = retrieveInterstitial( adUnitId );
        interstitial.loadAd();
    }

    public void showInterstitial(final String adUnitId, final String placement, final CallbackContext callbackContext)
    {
        MaxInterstitialAd interstitial = retrieveInterstitial( adUnitId );
        interstitial.showAd( placement );
    }

    public void setInterstitialExtraParameter(final String adUnitId, final String key, final String value, final CallbackContext callbackContext)
    {
        MaxInterstitialAd interstitial = retrieveInterstitial( adUnitId );
        interstitial.setExtraParameter( key, value );
    }

    // REWARDED

    public void loadRewardedAd(final String adUnitId, final CallbackContext callbackContext)
    {
        MaxRewardedAd rewardedAd = retrieveRewardedAd( adUnitId );
        rewardedAd.loadAd();
    }

    public boolean isRewardedAdReady(final String adUnitId, final CallbackContext callbackContext)
    {
        MaxRewardedAd rewardedAd = retrieveRewardedAd( adUnitId );
        return rewardedAd.isReady();
    }

    public void showRewardedAd(final String adUnitId, final String placement, final CallbackContext callbackContext)
    {
        MaxRewardedAd rewardedAd = retrieveRewardedAd( adUnitId );
        rewardedAd.showAd( placement );
    }

    public void setRewardedAdExtraParameter(final String adUnitId, final String key, final String value, final CallbackContext callbackContext)
    {
        MaxRewardedAd rewardedAd = retrieveRewardedAd( adUnitId );
        rewardedAd.setExtraParameter( key, value );
    }

    // AD CALLBACKS

    @Override
    public void onAdLoaded(MaxAd ad)
    {
        String name;
        MaxAdFormat adFormat = ad.getFormat();
        if ( MaxAdFormat.BANNER == adFormat || MaxAdFormat.LEADER == adFormat || MaxAdFormat.MREC == adFormat )
        {
            name = ( MaxAdFormat.MREC == adFormat ) ? "OnMRecAdLoadedEvent" : "OnBannerAdLoadedEvent";

            String adViewPosition = mAdViewPositions.get( ad.getAdUnitId() );
            if ( !TextUtils.isEmpty( adViewPosition ) )
            {
                // Only position ad if not native UI component
                positionAdView( ad );
            }

            // Do not auto-refresh by default if the ad view is not showing yet (e.g. first load during app launch and publisher does not automatically show banner upon load success)
            // We will resume auto-refresh in {@link #showBanner(String)}.
            MaxAdView adView = retrieveAdView( ad.getAdUnitId(), adFormat );
            if ( adView != null && adView.getVisibility() != View.VISIBLE )
            {
                adView.stopAutoRefresh();
            }
        }
        else if ( MaxAdFormat.INTERSTITIAL == adFormat )
        {
            name = "OnInterstitialLoadedEvent";
        }
        else if ( MaxAdFormat.REWARDED == adFormat )
        {
            name = "OnRewardedAdLoadedEvent";
        }
        else
        {
            logInvalidAdFormat( adFormat );
            return;
        }

        fireWindowEvent( name, getAdInfo( ad ) );
    }

    @Override
    public void onAdLoadFailed(final String adUnitId, final MaxError error)
    {
        if ( TextUtils.isEmpty( adUnitId ) )
        {
            logStackTrace( new IllegalArgumentException( "adUnitId cannot be null" ) );
            return;
        }

        String name;
        if ( mAdViews.containsKey( adUnitId ) )
        {
            name = ( MaxAdFormat.MREC == mAdViewAdFormats.get( adUnitId ) ) ? "OnMRecAdLoadFailedEvent" : "OnBannerAdLoadFailedEvent";
        }
        else if ( mInterstitials.containsKey( adUnitId ) )
        {
            name = "OnInterstitialLoadFailedEvent";
        }
        else if ( mRewardedAds.containsKey( adUnitId ) )
        {
            name = "OnRewardedAdLoadFailedEvent";
        }
        else
        {
            logStackTrace( new IllegalStateException( "invalid adUnitId: " + adUnitId ) );
            return;
        }

        try
        {
            JSONObject params = new JSONObject();
            params.put( "adUnitId", adUnitId );
            params.put( "errorCode", Integer.toString( error.getCode() ) );
            // TODO: Add "code", "message", and "adLoadFailureInfo"
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onAdClicked(final MaxAd ad)
    {
        final MaxAdFormat adFormat = ad.getFormat();
        final String name;
        if ( MaxAdFormat.BANNER == adFormat || MaxAdFormat.LEADER == adFormat )
        {
            name = "OnBannerAdClickedEvent";
        }
        else if ( MaxAdFormat.MREC == adFormat )
        {
            name = "OnMRecAdClickedEvent";
        }
        else if ( MaxAdFormat.INTERSTITIAL == adFormat )
        {
            name = "OnInterstitialClickedEvent";
        }
        else if ( MaxAdFormat.REWARDED == adFormat )
        {
            name = "OnRewardedAdClickedEvent";
        }
        else
        {
            logInvalidAdFormat( adFormat );
            return;
        }

        fireWindowEvent( name, getAdInfo( ad ) );
    }

    @Override
    public void onAdDisplayed(final MaxAd ad)
    {
        // BMLs do not support [DISPLAY] events
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.INTERSTITIAL && adFormat != MaxAdFormat.REWARDED ) return;

        final String name;
        if ( MaxAdFormat.INTERSTITIAL == adFormat )
        {
            name = "OnInterstitialDisplayedEvent";
        }
        else // REWARDED
        {
            name = "OnRewardedAdDisplayedEvent";
        }

        fireWindowEvent( name, getAdInfo( ad ) );
    }

    @Override
    public void onAdDisplayFailed(final MaxAd ad, final MaxError error)
    {
        // BMLs do not support [DISPLAY] events
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.INTERSTITIAL && adFormat != MaxAdFormat.REWARDED ) return;

        final String name;
        if ( MaxAdFormat.INTERSTITIAL == adFormat )
        {
            name = "OnInterstitialAdFailedToDisplayEvent";
        }
        else // REWARDED
        {
            name = "OnRewardedAdFailedToDisplayEvent";
        }

        try
        {
            JSONObject params = getAdInfo( ad );
            params.put( "errorCode", Integer.toString( error.getCode() ) );
            // TODO: Add "code", "message"
            fireWindowEvent( name, params );
        }
        catch ( Throwable ignored ) { }
    }

    @Override
    public void onAdHidden(final MaxAd ad)
    {
        // BMLs do not support [HIDDEN] events
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.INTERSTITIAL && adFormat != MaxAdFormat.REWARDED ) return;

        String name;
        if ( MaxAdFormat.INTERSTITIAL == adFormat )
        {
            name = "OnInterstitialHiddenEvent";
        }
        else // REWARDED
        {
            name = "OnRewardedAdHiddenEvent";
        }

        fireWindowEvent( name, getAdInfo( ad ) );
    }

    @Override
    public void onAdExpanded(final MaxAd ad)
    {
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.BANNER && adFormat != MaxAdFormat.LEADER && adFormat != MaxAdFormat.MREC )
        {
            logInvalidAdFormat( adFormat );
            return;
        }

        fireWindowEvent( ( MaxAdFormat.MREC == adFormat ) ? "OnMRecAdExpandedEvent" : "OnBannerAdExpandedEvent", getAdInfo( ad ) );
    }

    @Override
    public void onAdCollapsed(final MaxAd ad)
    {
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.BANNER && adFormat != MaxAdFormat.LEADER && adFormat != MaxAdFormat.MREC )
        {
            logInvalidAdFormat( adFormat );
            return;
        }

        fireWindowEvent( ( MaxAdFormat.MREC == adFormat ) ? "OnMRecAdCollapsedEvent" : "OnBannerAdCollapsedEvent", getAdInfo( ad ) );
    }

    @Override
    public void onRewardedVideoCompleted(final MaxAd ad)
    {
        // This event is not forwarded
    }

    @Override
    public void onRewardedVideoStarted(final MaxAd ad)
    {
        // This event is not forwarded
    }

    @Override
    public void onUserRewarded(final MaxAd ad, final MaxReward reward)
    {
        final MaxAdFormat adFormat = ad.getFormat();
        if ( adFormat != MaxAdFormat.REWARDED )
        {
            logInvalidAdFormat( adFormat );
            return;
        }

        final String rewardLabel = reward != null ? reward.getLabel() : "";
        final int rewardAmount = reward != null ? reward.getAmount() : 0;

        try
        {
            JSONObject params = getAdInfo( ad );
            params.put( "rewardLabel", rewardLabel );
            params.put( "rewardAmount", rewardAmount );
            fireWindowEvent( "OnRewardedAdReceivedRewardEvent", params );
        }
        catch ( Throwable ignored ) { }
    }

    // INTERNAL METHODS

    private void createAdView(final String adUnitId, final MaxAdFormat adFormat, final String adViewPosition, final CallbackContext callbackContext)
    {
        // Run on main thread to ensure there are no concurrency issues with other ad view methods
        getCurrentActivity().runOnUiThread( () -> {

            d( "Creating " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\" and position: \"" + adViewPosition + "\"" );

            // Retrieve ad view from the map
            final MaxAdView adView = retrieveAdView( adUnitId, adFormat, adViewPosition );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            adView.setVisibility( View.GONE );

            if ( adView.getParent() == null )
            {
                final Activity currentActivity = getCurrentActivity();
                final RelativeLayout relativeLayout = new RelativeLayout( currentActivity );
                currentActivity.addContentView( relativeLayout, new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT,
                                                                                               LinearLayout.LayoutParams.MATCH_PARENT ) );
                relativeLayout.addView( adView );

                // Position ad view immediately so if publisher sets color before ad loads, it will not be the size of the screen
                mAdViewAdFormats.put( adUnitId, adFormat );
                positionAdView( adUnitId, adFormat );
            }

            adView.loadAd();

            // The publisher may have requested to show the banner before it was created. Now that the banner is created, show it.
            if ( mAdUnitIdsToShowAfterCreate.contains( adUnitId ) )
            {
                showAdView( adUnitId, adFormat, null );
                mAdUnitIdsToShowAfterCreate.remove( adUnitId );
            }

            callbackContext.success();
        } );
    }

    private void setAdViewPlacement(final String adUnitId, final MaxAdFormat adFormat, final String placement, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Setting placement \"" + placement + "\" for " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\"" );

            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            adView.setPlacement( placement );

            callbackContext.success();
        } );
    }

    private void updateAdViewPosition(final String adUnitId, final String adViewPosition, final MaxAdFormat adFormat, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Updating " + adFormat.getLabel() + " position to \"" + adViewPosition + "\" for ad unit id \"" + adUnitId + "\"" );

            // Retrieve ad view from the map
            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            // Check if the previous position is same as the new position. If so, no need to update the position again.
            final String previousPosition = mAdViewPositions.get( adUnitId );
            if ( adViewPosition == null || adViewPosition.equals( previousPosition ) ) return;

            mAdViewPositions.put( adUnitId, adViewPosition );
            positionAdView( adUnitId, adFormat );

            callbackContext.success();
        } );
    }

    private void showAdView(final String adUnitId, final MaxAdFormat adFormat, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Showing " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\"" );

            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist for ad unit id " + adUnitId );

                // The adView has not yet been created. Store the ad unit ID, so that it can be displayed once the banner has been created.
                mAdUnitIdsToShowAfterCreate.add( adUnitId );
                return;
            }

            adView.setVisibility( View.VISIBLE );
            adView.startAutoRefresh();

            callbackContext.success();
        } );
    }

    private void hideAdView(final String adUnitId, final MaxAdFormat adFormat, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Hiding " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\"" );
            mAdUnitIdsToShowAfterCreate.remove( adUnitId );

            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            adView.setVisibility( View.GONE );
            adView.stopAutoRefresh();

            callbackContext.success();
        } );
    }

    private void destroyAdView(final String adUnitId, final MaxAdFormat adFormat, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Destroying " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\"" );

            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            final ViewParent parent = adView.getParent();
            if ( parent instanceof ViewGroup )
            {
                ( (ViewGroup) parent ).removeView( adView );
            }

            adView.setListener( null );
            adView.destroy();

            mAdViews.remove( adUnitId );
            mAdViewAdFormats.remove( adUnitId );
            mAdViewPositions.remove( adUnitId );
            mVerticalAdViewFormats.remove( adUnitId );

            callbackContext.success();
        } );
    }

    private void setAdViewBackgroundColor(final String adUnitId, final MaxAdFormat adFormat, final String hexColorCode, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Setting " + adFormat.getLabel() + " with ad unit id \"" + adUnitId + "\" to color: " + hexColorCode );

            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            adView.setBackgroundColor( Color.parseColor( hexColorCode ) );

            callbackContext.success();
        } );
    }

    private void setAdViewExtraParameters(final String adUnitId, final MaxAdFormat adFormat, final String key, final String value, final CallbackContext callbackContext)
    {
        getCurrentActivity().runOnUiThread( () -> {

            d( "Setting " + adFormat.getLabel() + " extra with key: \"" + key + "\" value: " + value );

            // Retrieve ad view from the map
            final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
            if ( adView == null )
            {
                e( adFormat.getLabel() + " does not exist" );
                return;
            }

            adView.setExtraParameter( key, value );

            // Handle local changes as needed
            if ( "force_banner".equalsIgnoreCase( key ) && MaxAdFormat.MREC != adFormat )
            {
                final MaxAdFormat forcedAdFormat;

                boolean shouldForceBanner = Boolean.parseBoolean( value );
                if ( shouldForceBanner )
                {
                    forcedAdFormat = MaxAdFormat.BANNER;
                }
                else
                {
                    forcedAdFormat = getDeviceSpecificBannerAdViewAdFormat();
                }

                mAdViewAdFormats.put( adUnitId, forcedAdFormat );
                positionAdView( adUnitId, forcedAdFormat );
            }

            callbackContext.success();
        } );
    }

    private void logInvalidAdFormat(MaxAdFormat adFormat)
    {
        logStackTrace( new IllegalStateException( "invalid ad format: " + adFormat ) );
    }

    private void logStackTrace(Exception e)
    {
        e( Log.getStackTraceString( e ) );
    }

    public static void d(final String message)
    {
        final String fullMessage = "[" + TAG + "] " + message;
        Log.d( SDK_TAG, fullMessage );
    }

    public static void e(final String message)
    {
        final String fullMessage = "[" + TAG + "] " + message;
        Log.e( SDK_TAG, fullMessage );
    }

    private MaxInterstitialAd retrieveInterstitial(String adUnitId)
    {
        MaxInterstitialAd result = mInterstitials.get( adUnitId );
        if ( result == null )
        {
            result = new MaxInterstitialAd( adUnitId, sdk, getCurrentActivity() );
            result.setListener( this );

            mInterstitials.put( adUnitId, result );
        }

        return result;
    }

    private MaxRewardedAd retrieveRewardedAd(String adUnitId)
    {
        MaxRewardedAd result = mRewardedAds.get( adUnitId );
        if ( result == null )
        {
            result = MaxRewardedAd.getInstance( adUnitId, sdk, getCurrentActivity() );
            result.setListener( this );

            mRewardedAds.put( adUnitId, result );
        }

        return result;
    }

    private MaxAdView retrieveAdView(String adUnitId, MaxAdFormat adFormat)
    {
        return retrieveAdView( adUnitId, adFormat, null );
    }

    public MaxAdView retrieveAdView(String adUnitId, MaxAdFormat adFormat, String adViewPosition)
    {
        MaxAdView result = mAdViews.get( adUnitId );
        if ( result == null && adViewPosition != null )
        {
            result = new MaxAdView( adUnitId, adFormat, sdk, getCurrentActivity() );
            result.setListener( this );

            mAdViews.put( adUnitId, result );
            mAdViewPositions.put( adUnitId, adViewPosition );
        }

        return result;
    }

    private void positionAdView(MaxAd ad)
    {
        positionAdView( ad.getAdUnitId(), ad.getFormat() );
    }

    private void positionAdView(String adUnitId, MaxAdFormat adFormat)
    {
        final MaxAdView adView = retrieveAdView( adUnitId, adFormat );
        if ( adView == null )
        {
            e( adFormat.getLabel() + " does not exist" );
            return;
        }

        final String adViewPosition = mAdViewPositions.get( adUnitId );
        final RelativeLayout relativeLayout = (RelativeLayout) adView.getParent();
        if ( relativeLayout == null )
        {
            e( adFormat.getLabel() + "'s parent does not exist" );
            return;
        }

        // Size the ad
        final AdViewSize adViewSize = getAdViewSize( adFormat );
        final int width = AppLovinSdkUtils.dpToPx( getCurrentActivity(), adViewSize.widthDp );
        final int height = AppLovinSdkUtils.dpToPx( getCurrentActivity(), adViewSize.heightDp );

        final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) adView.getLayoutParams();
        params.height = height;
        adView.setLayoutParams( params );

        // Parse gravity
        int gravity = 0;

        // Reset rotation, translation and margins so that the banner can be positioned again
        adView.setRotation( 0 );
        adView.setTranslationX( 0 );
        params.setMargins( 0, 0, 0, 0 );
        mVerticalAdViewFormats.remove( adUnitId );

        if ( "centered".equalsIgnoreCase( adViewPosition ) )
        {
            gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        }
        else
        {
            // Figure out vertical params
            if ( adViewPosition.contains( "top" ) )
            {
                gravity = Gravity.TOP;
            }
            else if ( adViewPosition.contains( "bottom" ) )
            {
                gravity = Gravity.BOTTOM;
            }

            // Figure out horizontal params
            if ( adViewPosition.contains( "center" ) )
            {
                gravity |= Gravity.CENTER_HORIZONTAL;
                params.width = ( MaxAdFormat.MREC == adFormat ) ? width : RelativeLayout.LayoutParams.MATCH_PARENT; // Stretch width if banner

                // Check if the publisher wants the ad view to be vertical and update the position accordingly ('CenterLeft' or 'CenterRight').
                final boolean containsLeft = adViewPosition.contains( "left" );
                final boolean containsRight = adViewPosition.contains( "right" );
                if ( containsLeft || containsRight )
                {
                    // First, center the ad view in the view.
                    gravity |= Gravity.CENTER_VERTICAL;

                    // For banners, set the width to the height of the screen to span the ad across the screen after it is rotated.
                    // Android by default clips a view bounds if it goes over the size of the screen. We can overcome it by setting negative margins to match our required size.
                    if ( MaxAdFormat.MREC == adFormat )
                    {
                        gravity |= adViewPosition.contains( "left" ) ? Gravity.LEFT : Gravity.RIGHT;
                    }
                    else
                    {
                        /* Align the center of the view such that when rotated it snaps into place.
                         *
                         *                  +---+---+-------+
                         *                  |   |           |
                         *                  |   |           |
                         *                  |   |           |
                         *                  |   |           |
                         *                  |   |           |
                         *                  |   |           |
                         *    +-------------+---+-----------+--+
                         *    |             | + |   +       |  |
                         *    +-------------+---+-----------+--+
                         *                  |   |           |
                         *                  | ^ |   ^       |
                         *                  | +-----+       |
                         *                  Translation     |
                         *                  |   |           |
                         *                  |   |           |
                         *                  +---+-----------+
                         */
                        final Rect windowRect = new Rect();
                        relativeLayout.getWindowVisibleDisplayFrame( windowRect );

                        final int windowWidth = windowRect.width();
                        final int windowHeight = windowRect.height();
                        final int longSide = Math.max( windowWidth, windowHeight );
                        final int shortSide = Math.min( windowWidth, windowHeight );
                        final int margin = ( longSide - shortSide ) / 2;
                        params.setMargins( -margin, 0, -margin, 0 );

                        // The view is now at the center of the screen and so is it's pivot point. Move its center such that when rotated, it snaps into the vertical position we need.
                        final int translationRaw = ( windowWidth / 2 ) - ( height / 2 );
                        final int translationX = containsLeft ? -translationRaw : translationRaw;
                        adView.setTranslationX( translationX );

                        // We have the view's center in the correct position. Now rotate it to snap into place.
                        adView.setRotation( 270 );

                        // Store the ad view with format, so that it can be updated when the orientation changes.
                        mVerticalAdViewFormats.put( adUnitId, adFormat );
                    }

                    // Hack alert: For the rotation and translation to be applied correctly, need to set the background color (Unity only, similar to what we do in Cross Promo).
                    relativeLayout.setBackgroundColor( Color.TRANSPARENT );
                }
            }
            else
            {
                params.width = width;

                if ( adViewPosition.contains( "left" ) )
                {
                    gravity |= Gravity.LEFT;
                }
                else if ( adViewPosition.contains( "right" ) )
                {
                    gravity |= Gravity.RIGHT;
                }
            }
        }

        relativeLayout.setGravity( gravity );
    }

    // Utility Methods

    private MaxAdFormat getDeviceSpecificBannerAdViewAdFormat()
    {
        return getDeviceSpecificBannerAdViewAdFormat( getCurrentActivity() );
    }

    public static MaxAdFormat getDeviceSpecificBannerAdViewAdFormat(final Context context)
    {
        return AppLovinSdkUtils.isTablet( context ) ? MaxAdFormat.LEADER : MaxAdFormat.BANNER;
    }

    protected static class AdViewSize
    {
        public final int widthDp;
        public final int heightDp;

        private AdViewSize(final int widthDp, final int heightDp)
        {
            this.widthDp = widthDp;
            this.heightDp = heightDp;
        }

    }

    public static AdViewSize getAdViewSize(final MaxAdFormat format)
    {
        if ( MaxAdFormat.LEADER == format )
        {
            return new AdViewSize( 728, 90 );
        }
        else if ( MaxAdFormat.BANNER == format )
        {
            return new AdViewSize( 320, 50 );
        }
        else if ( MaxAdFormat.MREC == format )
        {
            return new AdViewSize( 300, 250 );
        }
        else
        {
            throw new IllegalArgumentException( "Invalid ad format" );
        }
    }

    private JSONObject getAdInfo(final MaxAd ad)
    {
        JSONObject adInfo = null;

        try
        {
            adInfo = new JSONObject();
            adInfo.put( "adUnitId", ad.getAdUnitId() );
            adInfo.put( "creativeId", !TextUtils.isEmpty( ad.getCreativeId() ) ? ad.getCreativeId() : "" );
            adInfo.put( "networkName", ad.getNetworkName() );
            adInfo.put( "placement", !TextUtils.isEmpty( ad.getPlacement() ) ? ad.getPlacement() : "" );
            adInfo.put( "revenue", ad.getRevenue() );
        }
        catch ( JSONException ignored ) { }

        return adInfo;
    }

    // React Native Bridge

    private void fireWindowEvent(final String name, final JSONObject params)
    {
        getCurrentActivity().runOnUiThread( () -> webView.loadUrl( "javascript:cordova.fireWindowEvent('" + name + "', " + params.toString() + ");" ) );
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
    {
        if ( "initialize".equalsIgnoreCase( action ) )
        {
            String pluginVersion = args.getString( 0 );
            String sdkKey = args.getString( 1 );
            initialize( pluginVersion, sdkKey, callbackContext );
        }
        else if ( "showMediationDebugger".equalsIgnoreCase( action ) )
        {
            showMediationDebugger( callbackContext );
        }
        else if ( "getConsentDialogState".equalsIgnoreCase( action ) )
        {
            getConsentDialogState( callbackContext );
        }
        else if ( "setHasUserConsent".equalsIgnoreCase( action ) )
        {
            boolean hasUserConsent = args.getBoolean( 0 );
            setHasUserConsent( hasUserConsent, callbackContext );
        }
        else if ( "hasUserConsent".equalsIgnoreCase( action ) )
        {
            hasUserConsent( callbackContext );
        }
        else if ( "setIsAgeRestrictedUser".equalsIgnoreCase( action ) )
        {
            boolean isAgeRestrictedUser = args.getBoolean( 0 );
            setIsAgeRestrictedUser( isAgeRestrictedUser, callbackContext );
        }
        else if ( "isAgeRestrictedUser".equalsIgnoreCase( action ) )
        {
            isAgeRestrictedUser( callbackContext );
        }
        else if ( "setDoNotSell".equalsIgnoreCase( action ) )
        {
            boolean isDoNotSell = args.getBoolean( 0 );
            setDoNotSell( isDoNotSell, callbackContext );
        }
        else if ( "isDoNotSell".equalsIgnoreCase( action ) )
        {
            isDoNotSell( callbackContext );
        }
        else if ( "setUserId".equalsIgnoreCase( action ) )
        {
            String userId = args.getString( 0 );
            setUserId( userId, callbackContext );
        }
        else if ( "setMuted".equalsIgnoreCase( action ) )
        {
            boolean isMuted = args.getBoolean( 0 );
            setMuted( isMuted, callbackContext );
        }
        else if ( "setVerboseLogging".equalsIgnoreCase( action ) )
        {
            boolean isVerboseLogging = args.getBoolean( 0 );
            setVerboseLogging( isVerboseLogging, callbackContext );
        }
        else if ( "setTestDeviceAdvertisingIds".equalsIgnoreCase( action ) )
        {
            JSONArray testDeviceAdvertisingIds = args.getJSONArray( 0 );
            List<String> testDeviceAdvertisingIdsList = new ArrayList<>( testDeviceAdvertisingIds.length() );

            for ( int i = 0; i < testDeviceAdvertisingIds.length(); i++ )
            {
                testDeviceAdvertisingIdsList.add( testDeviceAdvertisingIds.getString( 0 ) );
            }

            setTestDeviceAdvertisingIds( testDeviceAdvertisingIdsList, callbackContext );
        }
        else if ( "trackEvent".equalsIgnoreCase( action ) )
        {
            String event = args.getString( 0 );
            JSONObject parameters = args.getJSONObject( 1 );
            trackEvent( event, parameters, callbackContext );
        }
        else if ( "createBanner".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String position = args.getString( 1 );
            createBanner( adUnitId, position, callbackContext );
        }
        else if ( "setBannerBackgroundColor".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String hexColorCode = args.getString( 1 );
            setBannerBackgroundColor( adUnitId, hexColorCode, callbackContext );
        }
        else if ( "setBannerPlacement".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String placement = args.getString( 1 );
            setBannerPlacement( adUnitId, placement, callbackContext );
        }
        else if ( "updateBannerPosition".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String position = args.getString( 1 );
            updateBannerPosition( adUnitId, position, callbackContext );
        }
        else if ( "setBannerExtraParameter".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String key = args.getString( 1 );
            String value = args.getString( 2 );
            setBannerExtraParameter( adUnitId, key, value, callbackContext );
        }
        else if ( "showBanner".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            showBanner( adUnitId, callbackContext );
        }
        else if ( "hideBanner".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            hideBanner( adUnitId, callbackContext );
        }
        else if ( "destroyBanner".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            destroyBanner( adUnitId, callbackContext );
        }
        else if ( "createMRec".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String position = args.getString( 1 );
            createMRec( adUnitId, position, callbackContext );
        }
        else if ( "setMRecPlacement".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String placement = args.getString( 1 );
            setMRecPlacement( adUnitId, placement, callbackContext );
        }
        else if ( "updateMRecPosition".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String position = args.getString( 1 );
            updateMRecPosition( adUnitId, position, callbackContext );
        }
        else if ( "showMRec".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            showMRec( adUnitId, callbackContext );
        }
        else if ( "hideMRec".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            hideMRec( adUnitId, callbackContext );
        }
        else if ( "destroyMRec".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            destroyMRec( adUnitId, callbackContext );
        }
        else if ( "loadInterstitial".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            loadInterstitial( adUnitId, callbackContext );
        }
        else if ( "showInterstitial".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String placement = args.optString( 1 );
            showInterstitial( adUnitId, placement, callbackContext );
        }
        else if ( "setInterstitialExtraParameter".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String key = args.getString( 1 );
            String value = args.getString( 2 );
            setInterstitialExtraParameter( adUnitId, key, value, callbackContext );
        }
        else if ( "loadRewardedAd".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            loadRewardedAd( adUnitId, callbackContext );
        }
        else if ( "showRewardedAd".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String placement = args.optString( 1 );
            showRewardedAd( adUnitId, placement, callbackContext );
        }
        else if ( "setRewardedAdExtraParameter".equalsIgnoreCase( action ) )
        {
            String adUnitId = args.getString( 0 );
            String key = args.getString( 1 );
            String value = args.getString( 2 );
            setRewardedAdExtraParameter( adUnitId, key, value, callbackContext );
        }
        else
        {
            // Action not recognized
            return false;
        }

        return true;
    }
}
