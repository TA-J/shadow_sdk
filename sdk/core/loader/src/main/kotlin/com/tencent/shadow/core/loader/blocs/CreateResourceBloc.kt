/*
 * Tencent is pleased to support the open source community by making Tencent Shadow available.
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.tencent.shadow.core.loader.blocs

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.webkit.WebView
import java.util.concurrent.CountDownLatch

object CreateResourceBloc {
    fun create(archiveFilePath: String, hostAppContext: Context): Resources {
        triggerWebViewHookResources(hostAppContext)

        val packageManager = hostAppContext.packageManager
        val applicationInfo = ApplicationInfo()
        val hostApplicationInfo = hostAppContext.applicationInfo
        applicationInfo.packageName = hostApplicationInfo.packageName
        applicationInfo.uid = hostApplicationInfo.uid

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fillApplicationInfoForNewerApi(applicationInfo, hostApplicationInfo, archiveFilePath)
        } else {
            fillApplicationInfoForLowerApi(applicationInfo, hostApplicationInfo, archiveFilePath)
        }

        try {
            val pluginResource = packageManager.getResourcesForApplication(applicationInfo)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pluginResource
            } else {
                val hostResources = hostAppContext.resources
                MixResources(pluginResource, hostResources)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }

    }

    /**
     * WebView?????????????????????????????????Resources????????????webview.apk???
     * ??????WebView??????????????????????????????
     *
     * ?????????????????????????????????Resources????????????apk???
     * ?????????????????????????????????????????????????????????????????????Resources???????????????????????????apk?????????
     */
    private fun triggerWebViewHookResources(hostAppContext: Context) {
        //????????????context???????????????WebView?????????WebView??????????????????sharedLibraryFiles??????webview.apk????????????
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                WebView(hostAppContext)
            } catch (ignored: Exception) {
                // API 26????????????No WebView installed
            }
            latch.countDown()
        }
        latch.await()
    }

    private fun fillApplicationInfoForNewerApi(
        applicationInfo: ApplicationInfo,
        hostApplicationInfo: ApplicationInfo,
        pluginApkPath: String
    ) {
        /**
         * ????????????sourceDir???sharedLibraryFiles????????????apk????????????Resources?????????
         * ??????????????????id????????????0x7f???????????????????????????????????????id?????????
         * ??????????????????apk????????????sharedLibraryFiles??????????????????????????????id?????????0x7f???
         * ????????????????????????????????????????????????0x30???????????????sharedLibraryFiles??????apk???
         * ??????id?????????????????????0x80??????????????????
         *
         * ??????????????????????????????????????????????????????????????????????????????????????????
         * ?????????????????????????????????0x7f????????????????????????????????????????????????????????????????????????id?????????
         * ???????????????apk????????????sharedLibraryFiles??????
         *
         * ???????????????sharedLibraryFiles??????????????????????????????WebView???????????????
         * ??????????????????API?????????webview.apk
         */
        applicationInfo.publicSourceDir = hostApplicationInfo.publicSourceDir
        applicationInfo.sourceDir = hostApplicationInfo.sourceDir

        // hostSharedLibraryFiles????????????webview????????????api?????????webview.apk
        val hostSharedLibraryFiles = hostApplicationInfo.sharedLibraryFiles
        val otherApksAddToResources =
            if (hostSharedLibraryFiles == null)
                arrayOf(pluginApkPath)
            else
                arrayOf(
                    *hostSharedLibraryFiles,
                    pluginApkPath
                )

        applicationInfo.sharedLibraryFiles = otherApksAddToResources
    }

    /**
     * API 25??????????????????????????????????????????
     */
    private fun fillApplicationInfoForLowerApi(
        applicationInfo: ApplicationInfo,
        hostApplicationInfo: ApplicationInfo,
        pluginApkPath: String
    ) {
        applicationInfo.publicSourceDir = pluginApkPath
        applicationInfo.sourceDir = pluginApkPath
        applicationInfo.sharedLibraryFiles = hostApplicationInfo.sharedLibraryFiles
    }
}

/**
 * ???API 25?????????????????????sharedLibraryFiles?????????getResourcesForApplication????????????????????????
 * ?????????addAssetPath?????????????????????CreateResourceTest??????????????????
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@TargetApi(25)
private class MixResources(
    private val mainResources: Resources,
    private val sharedResources: Resources
) : Resources(mainResources.assets, mainResources.displayMetrics, mainResources.configuration) {

    private var beforeInitDone = false
    private var updateConfigurationCalledInInit = false

    /**
     * ??????????????????Resources?????????????????????updateConfiguration?????????
     * ??????mainResources?????????????????????
     */
    init {
        if (updateConfigurationCalledInInit) {
            updateConfiguration(mainResources.configuration, mainResources.displayMetrics)
        }
        beforeInitDone = true
    }

    private fun <R> tryMainThenShared(function: (res: Resources) -> R) = try {
        function(mainResources)
    } catch (e: NotFoundException) {
        function(sharedResources)
    }

    override fun getText(id: Int) = tryMainThenShared { it.getText(id) }

    override fun getText(id: Int, def: CharSequence?) = tryMainThenShared { it.getText(id, def) }

    override fun getQuantityText(id: Int, quantity: Int) =
        tryMainThenShared { it.getQuantityText(id, quantity) }

    override fun getString(id: Int) =
        tryMainThenShared { it.getString(id) }

    override fun getString(id: Int, vararg formatArgs: Any?) =
        tryMainThenShared { it.getString(id, formatArgs) }


    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?) =
        tryMainThenShared { it.getQuantityString(id, quantity, *formatArgs) }

    override fun getQuantityString(id: Int, quantity: Int) =
        tryMainThenShared {
            it.getQuantityString(id, quantity)
        }

    override fun getTextArray(id: Int) =
        tryMainThenShared {
            it.getTextArray(id)
        }

    override fun getStringArray(id: Int) =
        tryMainThenShared {
            it.getStringArray(id)
        }

    override fun getIntArray(id: Int) =
        tryMainThenShared {
            it.getIntArray(id)
        }

    override fun obtainTypedArray(id: Int) =
        tryMainThenShared {
            it.obtainTypedArray(id)
        }

    override fun getDimension(id: Int) =
        tryMainThenShared {
            it.getDimension(id)
        }

    override fun getDimensionPixelOffset(id: Int) =
        tryMainThenShared {
            it.getDimensionPixelOffset(id)
        }

    override fun getDimensionPixelSize(id: Int) =
        tryMainThenShared {
            it.getDimensionPixelSize(id)
        }

    override fun getFraction(id: Int, base: Int, pbase: Int) =
        tryMainThenShared {
            it.getFraction(id, base, pbase)
        }

    override fun getDrawable(id: Int) =
        tryMainThenShared {
            it.getDrawable(id)
        }

    override fun getDrawable(id: Int, theme: Theme?) =
        tryMainThenShared {
            it.getDrawable(id, theme)
        }

    override fun getDrawableForDensity(id: Int, density: Int) =
        tryMainThenShared {
            it.getDrawableForDensity(id, density)
        }

    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?) =
        tryMainThenShared {
            it.getDrawableForDensity(id, density, theme)
        }

    override fun getMovie(id: Int) =
        tryMainThenShared {
            it.getMovie(id)
        }

    override fun getColor(id: Int) =
        tryMainThenShared {
            it.getColor(id)
        }

    override fun getColor(id: Int, theme: Theme?) =
        tryMainThenShared {
            it.getColor(id, theme)
        }

    override fun getColorStateList(id: Int) =
        tryMainThenShared {
            it.getColorStateList(id)
        }

    override fun getColorStateList(id: Int, theme: Theme?) =
        tryMainThenShared {
            it.getColorStateList(id, theme)
        }

    override fun getBoolean(id: Int) =
        tryMainThenShared {
            it.getBoolean(id)
        }

    override fun getInteger(id: Int) =
        tryMainThenShared {
            it.getInteger(id)
        }

    override fun getLayout(id: Int) =
        tryMainThenShared {
            it.getLayout(id)
        }

    override fun getAnimation(id: Int) =
        tryMainThenShared {
            it.getAnimation(id)
        }

    override fun getXml(id: Int) =
        tryMainThenShared {
            it.getXml(id)
        }

    override fun openRawResource(id: Int) =
        tryMainThenShared {
            it.openRawResource(id)
        }

    override fun openRawResource(id: Int, value: TypedValue?) =
        tryMainThenShared {
            it.openRawResource(id, value)
        }

    override fun openRawResourceFd(id: Int) =
        tryMainThenShared {
            it.openRawResourceFd(id)
        }

    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) =
        tryMainThenShared {
            it.getValue(id, outValue, resolveRefs)
        }

    override fun getValue(name: String?, outValue: TypedValue?, resolveRefs: Boolean) =
        tryMainThenShared {
            it.getValue(name, outValue, resolveRefs)
        }

    override fun getValueForDensity(
        id: Int,
        density: Int,
        outValue: TypedValue?,
        resolveRefs: Boolean
    ) =
        tryMainThenShared {
            it.getValueForDensity(id, density, outValue, resolveRefs)
        }

    override fun obtainAttributes(set: AttributeSet?, attrs: IntArray?) =
        tryMainThenShared {
            it.obtainAttributes(set, attrs)
        }

    override fun updateConfiguration(config: Configuration?, metrics: DisplayMetrics?) {
        if (beforeInitDone) {
            tryMainThenShared {
                it.updateConfiguration(config, metrics)
            }
        }
    }

    override fun getDisplayMetrics() =
        tryMainThenShared {
            it.getDisplayMetrics()
        }

    override fun getConfiguration() =
        tryMainThenShared {
            it.getConfiguration()
        }

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?) =
        tryMainThenShared {
            it.getIdentifier(name, defType, defPackage)
        }

    override fun getResourceName(resid: Int) =
        tryMainThenShared {
            it.getResourceName(resid)
        }

    override fun getResourcePackageName(resid: Int) =
        tryMainThenShared {
            it.getResourcePackageName(resid)
        }

    override fun getResourceTypeName(resid: Int) =
        tryMainThenShared {
            it.getResourceTypeName(resid)
        }

    override fun getResourceEntryName(resid: Int) =
        tryMainThenShared {
            it.getResourceEntryName(resid)
        }

    override fun parseBundleExtras(parser: XmlResourceParser?, outBundle: Bundle?) =
        tryMainThenShared {
            it.parseBundleExtras(parser, outBundle)
        }

    override fun parseBundleExtra(tagName: String?, attrs: AttributeSet?, outBundle: Bundle?) =
        tryMainThenShared {
            it.parseBundleExtra(tagName, attrs, outBundle)
        }
}