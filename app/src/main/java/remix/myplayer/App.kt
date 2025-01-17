package remix.myplayer

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.bugly.crashreport.CrashReport.UserStrategy
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins
import remix.myplayer.appshortcuts.DynamicShortcutManager
import remix.myplayer.helper.LanguageHelper.onConfigurationChanged
import remix.myplayer.helper.LanguageHelper.saveSystemCurrentLanguage
import remix.myplayer.helper.LanguageHelper.setApplicationLanguage
import remix.myplayer.helper.LanguageHelper.setLocal
import remix.myplayer.misc.cache.DiskCache
import remix.myplayer.theme.ThemeStore
import remix.myplayer.util.SPUtil
import remix.myplayer.util.SPUtil.SETTING_KEY
import remix.myplayer.util.Util
import timber.log.Timber

/**
 * Created by Remix on 16-3-16.
 */
class App : MultiDexApplication(), ActivityLifecycleCallbacks {
  private var foregroundActivityCount = 0

  override fun attachBaseContext(base: Context) {
    saveSystemCurrentLanguage()
    super.attachBaseContext(setLocal(base))
    MultiDex.install(this)
  }

  override fun onCreate() {
    super.onCreate()
    context = this
    setUp()

    // AppShortcut
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      DynamicShortcutManager(this).setUpShortcut()
    }

    // 加载第三方库
    loadLibrary()

    // 处理 RxJava2 取消订阅后，抛出的异常无法捕获，导致程序崩溃
    RxJavaPlugins.setErrorHandler { throwable: Throwable? ->
      Timber.v(throwable)
      CrashReport.postCatchedException(throwable)
    }
    registerActivityLifecycleCallbacks(this)
  }

  private fun setUp() {
    DiskCache.init(this, "lyric")
    setApplicationLanguage(this)
    Completable
        .fromAction {
          ThemeStore.sImmersiveMode = SPUtil
              .getValue(context, SETTING_KEY.NAME, SETTING_KEY.IMMERSIVE_MODE, false)
          ThemeStore.sColoredNavigation = SPUtil.getValue(context, SETTING_KEY.NAME,
              SETTING_KEY.COLOR_NAVIGATION, false)
        }
        .subscribe()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    onConfigurationChanged(applicationContext)
  }

  private fun loadLibrary() {
    // bugly
    val context = applicationContext
    // 获取当前包名
    val packageName = context.packageName
    // 获取当前进程名
    val processName = Util.getProcessName(Process.myPid())
    // 设置是否为上报进程
    val strategy = UserStrategy(context)
    strategy.isUploadProcess = processName == null || processName == packageName
    CrashReport.initCrashReport(this, BuildConfig.BUGLY_APPID, BuildConfig.DEBUG, strategy)
    CrashReport.setIsDevelopmentDevice(this, BuildConfig.DEBUG)

  }

  override fun onLowMemory() {
    super.onLowMemory()
    Timber.v("onLowMemory")
//    Completable
//        .fromAction { Fresco.getImagePipeline().clearMemoryCaches() }
//        .subscribeOn(AndroidSchedulers.mainThread())
//        .subscribe()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    Timber.v("onTrimMemory, %s", level)
    Completable
        .fromAction {
          when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
            }
            TRIM_MEMORY_RUNNING_MODERATE, TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_RUNNING_CRITICAL -> {
            }
            TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_MODERATE, TRIM_MEMORY_COMPLETE -> {
            }
            else -> {
            }
          }
        }
        .subscribeOn(AndroidSchedulers.mainThread())
        .subscribe()
  }

  val isAppForeground: Boolean
    get() = foregroundActivityCount > 0

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {
    foregroundActivityCount++
  }

  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {
    foregroundActivityCount--
  }

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
  override fun onActivityDestroyed(activity: Activity) {}

  companion object {
    @JvmStatic
    lateinit var context: App
      private set

    //是否是googlePlay版本
    val IS_GOOGLEPLAY =
        !BuildConfig.DEBUG && BuildConfig.FLAVOR == "google"
  }
}