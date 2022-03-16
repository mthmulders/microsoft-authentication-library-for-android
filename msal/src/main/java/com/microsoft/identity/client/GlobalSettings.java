//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
package com.microsoft.identity.client;

import static com.microsoft.identity.client.GlobalSettingsConfigurationFactory.initializeGlobalConfiguration;
import static com.microsoft.identity.client.exception.MsalClientException.SAPCA_USE_WITH_MULTI_POLICY_B2C;
import static com.microsoft.identity.client.internal.MsalUtils.validateNonNullArgument;

import android.content.Context;

import androidx.annotation.NonNull;

import com.microsoft.identity.client.configuration.AccountMode;
import com.microsoft.identity.client.exception.MsalClientException;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.common.java.authorities.AzureActiveDirectoryB2CAuthority;
import com.microsoft.identity.common.logging.Logger;

import java.io.File;

/**
 * Class used to initialize global configurations for the library.
 */
public class GlobalSettings {
    private static final String TAG = GlobalSettings.class.getSimpleName();
    public static final String NO_GLOBAL_SETTINGS_WARNING = "Global settings have not been initialized before the creation of this PCA Configuration.";
    public static final String GLOBAL_INIT_AFTER_PCA = "pca_created_before_global";
    public static final String GLOBAL_INIT_AFTER_PCA_ERROR_MESSAGE = "Global initialization was attempted after a PublicClientApplicationConfiguration instance was already created. Please initialize global settings before any PublicClientApplicationConfiguration instance is created.";

    private static GlobalSettingsConfiguration mGlobalSettingsConfiguration;
    private static boolean pcaCreated = false;
    private static boolean mGlobalSettingsInitialized = false;
    private static Object mGlobalSettingsLock = new Object();

    /**
     * Load the global configuration file using the context, resource id of the configuration file, and a listener.
     *
     * @param context Context of the app.
     * @param configFileResourceId Resource Id for the configuration file.
     * @param listener Handles success and error messages.
     */
    public static void loadGlobalConfigurationFile(@NonNull final Context context,
                                                   final int configFileResourceId,
                                                   @NonNull final GlobalSettingsListener listener) {
        validateNonNullArgument(context, PublicClientApplication.NONNULL_CONSTANTS.CONTEXT);
        validateNonNullArgument(listener, PublicClientApplication.NONNULL_CONSTANTS.LISTENER);
        runOnBackground(new Runnable() {
            @Override
            public void run() {
                synchronized (mGlobalSettingsLock) {
                    setGlobalConfiguration(
                            initializeGlobalConfiguration(context, configFileResourceId),
                            listener
                    );
                }
            }
        });
    }

    /**
     * Load the global configuration file using the configuration file and a listener.
     *
     * @param configFile Configuration file.
     * @param listener Handles success and error messages.
     */
    public static void loadGlobalConfigurationFile(@NonNull final File configFile,
                                                   @NonNull final GlobalSettingsListener listener) {
        validateNonNullArgument(listener, PublicClientApplication.NONNULL_CONSTANTS.LISTENER);
        runOnBackground(new Runnable() {
            @Override
            public void run() {
                synchronized (mGlobalSettingsLock) {
                    setGlobalConfiguration(
                            initializeGlobalConfiguration(configFile),
                            listener
                    );
                }
            }
        });
    }

    private static void setGlobalConfiguration(@NonNull final GlobalSettingsConfiguration globalConfiguration,
                                               @NonNull final GlobalSettingsListener listener) {
        if (pcaCreated) {
            listener.onError(new MsalClientException(GLOBAL_INIT_AFTER_PCA,
                    GLOBAL_INIT_AFTER_PCA_ERROR_MESSAGE));
        }

        try {
            validateAccountModeConfiguration(globalConfiguration);
        } catch (final MsalClientException e) {
            listener.onError(e);
            return;
        }

        mGlobalSettingsConfiguration = globalConfiguration;
        mGlobalSettingsInitialized = true;

        listener.onSuccess("Global configuration initialized.");
    }

    private static void runOnBackground(@NonNull final Runnable runnable) {
        new Thread(runnable).start();
    }

    private static void validateAccountModeConfiguration(@NonNull final GlobalSettingsConfiguration config) throws MsalClientException {
        if (config.getAccountMode() == AccountMode.SINGLE
                && null != config.getDefaultAuthority()
                && config.getDefaultAuthority() instanceof AzureActiveDirectoryB2CAuthority) {
            Logger.warn(
                    TAG,
                    "Warning! B2C applications should use MultipleAccountPublicClientApplication. "
                            + "Use of SingleAccount mode with multiple IEF policies is unsupported."
            );

            if (config.getAuthorities().size() > 1) {
                throw new MsalClientException(SAPCA_USE_WITH_MULTI_POLICY_B2C);
            }
        }
    }

    protected static GlobalSettingsConfiguration getGlobalSettingsConfiguration() {
        return mGlobalSettingsConfiguration;
    }

    protected static boolean isGlobalSettingsInitialized() {
        return mGlobalSettingsInitialized;
    }

    protected static void pcaHasBeenInitiated() {
        pcaCreated = true;
    }

    protected static Object getGlobalSettingsLock() {
        return mGlobalSettingsLock;
    }

    public interface GlobalSettingsListener {
        /**
         * Invoked if the global settings are initialized successfully.
         *
         * @param message A message showing successful initialization.
         */
        void onSuccess(@NonNull final String message);

        /**
         * Invoked if an error is encountered during the creation of the global configuration.
         *
         * @param exception Error exception.
         */
        void onError(@NonNull final MsalException exception);
    }
}
