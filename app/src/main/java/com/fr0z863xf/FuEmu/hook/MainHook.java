package com.fr0z863xf.FuEmu.hook;

import static de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.http.X509TrustManagerExtensions;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 3.x 壳: 百度加固 sagittarius v6 (libbaiduprotect.so), application = com.sagittarius.v6.StubApplication。
 *   穿壳时机(dexsql 静态验证): StubApplication.attachBaseContext 内完成
 *   A.n1 native 解密 -> b/p.a 反射 DexPathList.makeInMemoryDexElements 把解密 dex 追加进
 *   当前 PathClassLoader 的 dexElements(不创建新 loader) -> AppInfo.APPNAME 回填 ->
 *   b/g.a(String) Class.forName 创建真实 MainApplication -> b/f 回调链替换
 *   LoadedApk.mApplication/mInitialApplication。
 *   因此 attachBaseContext 的 afterHook 时刻真实类即可解析, 且早于真实 Application.onCreate。
 */
public class MainHook implements IXposedHookLoadPackage {
    private static final String REAL_MAIN_ACTIVITY = "com.fuulea.venus.MainActivity";

    private prefsUtils prefs;
    private Application mApplication;
    private final AtomicBoolean installed = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable  {
        if (!Objects.equals(lpparam.packageName, "com.fuulea.venus.g")) return;
        this.prefs = new prefsUtils();

        //2.x的壳
        try {
            XposedHelpers.findAndHookMethod("com.stub.StubApp", lpparam.classLoader, "onCreate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    XposedBridge.log("[FuEmu]app已加载，开始hook外壳");
                    XposedBridge.log("[FuEmu]com.stub.StubApp->beforeHookedMethod->onCreate: " + param.thisObject);
                    XposedBridge.log("[FuEmu]com.stub.StubApp: " + lpparam.classLoader);
                    installHooks(getClassloader());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] 未找到2.x壳入口 com.stub.StubApp: " + t);
        }

        // 3.x壳
        try {
            XposedHelpers.findAndHookMethod("com.sagittarius.v6.StubApplication", lpparam.classLoader, "attachBaseContext", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    XposedBridge.log("[FuEmu]app已加载，开始hook外壳");
                    XposedBridge.log("[FuEmu]com.sagittarius.v6.StubApplication->afterHookedMethod->attachBaseContext: " + param.thisObject);
                    XposedBridge.log("[FuEmu]com.sagittarius.v6.StubApplication: " + lpparam.classLoader);
                    installHooks(getClassloader());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] 未找到3.x壳入口 com.sagittarius.v6.StubApplication: " + t);
        }
    }

    private void installHooks(ClassLoader finalClassLoader) {
        if (installed.get()) return;
        if (finalClassLoader == null) {
            XposedBridge.log("[FuEmu] finalClassLoader 为空！");
            return;
        }
        installed.set(true);
        XposedBridge.log("[FuEmu]finalClassLoader: " + finalClassLoader);
        XposedBridge.log("[FuEmu]解密完成");
        //XposedBridge.log("[FuEmu]测试Xprefs:device_info" + prefs.getString("device_info", "ERR!"));
        //Class MainActivityC = XposedHelpers.findClass("com.fuulea.venus.MainActivity",finalClassLoader);
        XposedHelpers.findAndHookMethod("com.fuulea.venus.MainActivity", finalClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                XposedBridge.log("[FuEmu]com.fuulea.venus.g.MainActivity->onCreate");
                Toast.makeText((Activity) param.thisObject, "[FuEmu]:模块已加载", Toast.LENGTH_SHORT).show();
            }
        });

        // 关键hook，修改版本和品牌。直接修改常量更方便，无需对具体的获取函数进行hook
        // 实际上没几个是真正需要的:)
        XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build$VERSION", finalClassLoader), "RELEASE", prefs.getString("android_version", Build.VERSION.RELEASE));
        XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"BRAND",prefs.getString("brand", Build.BRAND));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"MODEL",prefs.getString("model", Build.MODEL));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"PRODUCT",prefs.getString("product", Build.PRODUCT));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"MANUFACTURER",prefs.getString("manufacturer", Build.MANUFACTURER));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"HARDWARE",prefs.getString("hardware", Build.HARDWARE));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"FINGERPRINT",prefs.getString("fingerprint", Build.FINGERPRINT));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"DISPLAY",prefs.getString("display", Build.DISPLAY));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"BOARD",prefs.getString("board", Build.BOARD));
        XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"DEVICE",prefs.getString("device_info", Build.DEVICE));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"SERIAL",prefs.getString("serial_number", Build.SERIAL));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"BOOTLOADER",prefs.getString("bootloader", Build.BOOTLOADER));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"HOST",prefs.getString("host", Build.HOST));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"ID",prefs.getString("id", Build.ID));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"TAGS",prefs.getString("tags", Build.TAGS));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"TYPE",prefs.getString("type", Build.TYPE));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"USER",prefs.getString("user", Build.USER));
        //XposedHelpers.setStaticLongField(XposedHelpers.findClass("android.os.Build", finalClassLoader),"TIME", Long.parseLong(prefs.getString("time", String.valueOf(Build.TIME))));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build$VERSION", finalClassLoader),"CODENAME",prefs.getString("codename", Build.VERSION.CODENAME));
        //XposedHelpers.setStaticObjectField(XposedHelpers.findClass("android.os.Build$VERSION", finalClassLoader),"INCREMENTAL",prefs.getString("incremental", Build.VERSION.INCREMENTAL));
        //XposedHelpers.setStaticIntField(XposedHelpers.findClass("android.os.Build$VERSION", finalClassLoader),"SDK_INT", Integer.parseInt(prefs.getString("sdk_int", String.valueOf(Build.VERSION.SDK_INT))));
        //XposedHelpers.setStaticIntField(XposedHelpers.findClass("android.os.Build$VERSION", finalClassLoader),"SDK", Integer.parseInt(prefs.getString("sdk_int", String.valueOf(Build.VERSION.SDK_INT))));

        // Hook for Baseband version (基带版本)
//                XposedHelpers.findAndHookMethod("android.os.Build", finalClassLoader, "getRadioVersion", new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        super.afterHookedMethod(param);
//                        String baseband = prefs.getString("baseband", null);
//                        if (baseband != null) {
//                            param.setResult(baseband);
//                            XposedBridge.log("[FuEmu] Hooked getRadioVersion to: " + baseband);
//                        }
//                    }
//                });

        // Hook for Kernel version (内核版本)
//                XposedHelpers.findAndHookMethod("java.lang.System", finalClassLoader, "getProperty", String.class, new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        super.afterHookedMethod(param);
//                        if (param.args[0] != null && param.args[0].equals("os.version")) {
//                            String kernelVersion = prefs.getString("kernel_version", null);
//                            if (kernelVersion != null) {
//                                param.setResult(kernelVersion);
//                                XposedBridge.log("[FuEmu] Hooked os.version to: " + kernelVersion);
//                            }
//                        }
//                    }
//                });

        // Hook for Android ID + 蓝牙设备名(3.x expo DeviceModule, SDK<=31 走 bluetooth_name)
        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", finalClassLoader, "getString", ContentResolver.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (param.args[1] == null) return;
                String key = (String) param.args[1];
                if (key.equals("android_id")) {
                    String androidId = prefs.getString("android_id", null);
                    if (androidId != null) {
                        param.setResult(androidId);
                        XposedBridge.log("[FuEmu] Hooked android_id to: " + androidId);
                    }
                } else if (key.equals("bluetooth_name")) {
                    String deviceName = prefs.getString("device_name", null);
                    if (deviceName != null && !deviceName.isEmpty()) {
                        param.setResult(deviceName);
                        XposedBridge.log("[FuEmu] Hooked bluetooth_name to: " + deviceName);
                    }
                }
            }
        });

        // 3.x expo DeviceModule 设备名(SDK>=32 走 Settings.Global device_name)
        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings.Global", finalClassLoader, "getString", ContentResolver.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if ("device_name".equals(param.args[1])) {
                        String deviceName = prefs.getString("device_name", null);
                        if (deviceName != null && !deviceName.isEmpty()) {
                            param.setResult(deviceName);
                            XposedBridge.log("[FuEmu] Hooked device_name to: " + deviceName);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] Hook Settings.Global->getString 失败: " + t);
        }


        // 关键hook，修改设备名称、管控环境、设备型号
        // 2.x: RNDeviceInfo
        try {
            XposedHelpers.findAndHookMethod("com.learnium.RNDeviceInfo.RNDeviceModule",finalClassLoader,"getDeviceNameSync", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    XposedBridge.log("[FuEmu]com.learnium.RNDeviceInfo.RNDeviceModule->getDeviceNameSync");
                    param.setResult(prefs.getString("device_name", "Oops"));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] RNDeviceModule->getDeviceNameSync 不存在(3.x?): " + t);
        }


        /*
        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure",finalClassLoader,"getString", ContentResolver.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[FuEmu]android.provider.Settings.Secure->getString");
                if (param.args[1].equals("bluetooth_name") || param.args[1].equals("device_name"))  param.setResult(prefs.getString("device_name", Build.DEVICE));
            }
        });

         */
        // 2.x: 管控环境
        try {
            XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.packages.governance.SystemGovernance",finalClassLoader,"getCurrentGovernanceName", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    XposedBridge.log("[FuEmu]com.fuulea.venus.reactNative.packages.governance.SystemGovernance->getCurrentGovernanceName");
                    param.setResult(prefs.getString("governance_environment", "HEM"));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] SystemGovernance->getCurrentGovernanceName 不存在(3.x?): " + t);
        }
        /*
        XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.packages.GovernanceModule",finalClassLoader,"getGovernanceName", Bundle.class, new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("[FuEmu]com.fuulea.venus.reactNative.packages.GovernanceModule->getGovernanceName");
                param.setResult(prefs.getString("governance_environment", "HEM"));
            }
        });

         */
        /*Class<?> GovC = XposedHelpers.findClass("com.fuulea.venus.reactNative.packages.governance.IGovernancePlugin", finalClassLoader);
        XposedBridge.hookAllMethods(GovC,"getName", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                XposedBridge.log("[FuEmu]com.fuulea.venus.reactNative.packages.governance.IGovernancePlugin->getName");
                param.setResult(prefs.getString("governance_environment", "HEM"));
            }
        });

         */
        // 2.x: 序列号
        try {
            XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.packages.governance.SystemGovernance",finalClassLoader,"getSN", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    XposedBridge.log("[FuEmu]com.fuulea.venus.reactNative.packages.governance.SystemGovernance->getSN");
                    param.setResult(prefs.getString("serial_number", "unspecified"));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] SystemGovernance->getSN 不存在(3.x?): " + t);
        }
        // 3.x: 直接hook框架
        try {
            final String sn = prefs.getString("serial_number", null);
            if (sn != null && !sn.isEmpty()) {
                XC_MethodReplacement snHook = new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return sn;
                    }
                };
                Class<?> telephonyManager = XposedHelpers.findClass("android.telephony.TelephonyManager", finalClassLoader);
                XposedBridge.hookAllMethods(telephonyManager, "getSimSerialNumber", snHook);
                XposedBridge.hookAllMethods(telephonyManager, "getDeviceId", snHook);
                XposedBridge.hookAllMethods(telephonyManager, "getImei", snHook);
                XposedBridge.hookAllMethods(telephonyManager, "getMeid", snHook);
                XposedHelpers.findAndHookMethod("android.os.Build", finalClassLoader, "getSerial", snHook);
                XposedBridge.log("[FuEmu] 3.x SN hooks 已挂: getSimSerialNumber/getDeviceId/getImei/getMeid/Build.getSerial -> " + sn);
            }
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] 3.x SN hooks 失败: " + t);
        }

        //绕过证书验证，方便抓包
//                try {
//                    XposedHelpers.findAndHookMethod("javax.net.ssl.X509TrustManager",finalClassLoader, "checkServerTrusted", java.security.cert.X509Certificate[].class, String.class, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                            XposedBridge.log("[FuEmu][Util] Universal TrustManager->checkServerTrusted called. Bypassing check.");
//                            param.setResult(null);
//                        }
//                    });
//                    XposedBridge.log("[FuEmu][Util] Universal javax.net.ssl.X509TrustManager->checkServerTrusted hooked.");
//                } catch (Throwable e) {
//                    XposedBridge.log("[FuEmu][Util] Error hooking universal javax.net.ssl.X509TrustManager: " + e.getMessage());
//                }

        try {
            XposedHelpers.findAndHookMethod("javax.net.ssl.SSLContext", finalClassLoader, "init", KeyManager[].class, TrustManager[].class, SecureRandom.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[FuEmu][Util][SSLContext] SSLContext.init(KeyManager[], TrustManager[], SecureRandom) called.");
                            param.args[1] = new TrustManager[]{ new NBTrustManager() };
                        }
                    }
            );
            XposedBridge.log("[FuEmu][Util] SSLContext hooks have been set up successfully.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking SSLContext.init: " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookConstructor(SSLSocketFactory.class, String.class, KeyStore.class, String.class, KeyStore.class,SecureRandom.class, new XC_MethodHook() {});
        } catch (Throwable ignored) {

        }


        try {
            XposedHelpers.findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", finalClassLoader, "checkServerTrusted",X509Certificate[].class, String.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String,String) called. Bypassing check.");
                    return new ArrayList<X509Certificate>();
                }
            });
            XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String,String) hooked.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String,String): " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", finalClassLoader, "checkServerTrusted", X509Certificate[].class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert[],String) called. Bypassing check.");
                    return null;
                }
            });
            XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String) hooked.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String): " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", finalClassLoader, "checkServerTrusted", X509Certificate[].class, String.class, SSLSession.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert[],String,SSLSession) called. Bypassing check.");
                    return new ArrayList<X509Certificate>();
                }
            });
            XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String,SSLSession) hooked.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking Conscrypt TrustManagerImpl->checkServerTrusted(Cert,String,SSLSession): " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", finalClassLoader, "checkTrusted", X509Certificate[].class, String.class, SSLSession.class, SSLParameters.class, boolean.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkTrusted(Cert[],String,SSLSession,SSLParameters,boolean) called. Bypassing check.");
                    return new ArrayList<X509Certificate>();
                }
            });
            XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkTrusted(Cert,String,SSLSession...) hooked.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking Conscrypt TrustManagerImpl->checkTrusted(Cert,String,SSLSession...): " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", finalClassLoader, "checkTrusted", X509Certificate[].class, byte[].class, byte[].class, String.class, String.class, boolean.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkTrusted(Cert,byte,byte,String,String,boolean) called. Bypassing check.");
                    return new ArrayList<X509Certificate>();
                }
            });
            XposedBridge.log("[FuEmu][Util] Conscrypt TrustManagerImpl->checkTrusted(Cert,byte,byte...) hooked.");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking Conscrypt TrustManagerImpl->checkTrusted(Cert,byte,byte...): " + e.getMessage());
        }



        try {
            XposedHelpers.findAndHookMethod(X509TrustManagerExtensions.class, "checkServerTrusted", X509Certificate[].class, String.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]X509TrustManagerExtensions->checkServerTrusted called");
                    return new ArrayList<X509Certificate>();
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util] Error hooking X509TrustManagerExtensions->checkServerTrusted: " + e.getMessage());
        }


        XposedHelpers.findAndHookMethod("android.security.net.config.NetworkSecurityTrustManager", finalClassLoader, "checkPins", List.class, DO_NOTHING);



        try {
            XposedHelpers.findAndHookMethod("okhttp3.CertificatePinner", finalClassLoader, "check$okhttp", String.class, List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,List) called");
                    return null;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,List) hooked");
        } catch(Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,List) not found");
        }

        try {
            XposedHelpers.findAndHookMethod("okhttp3.CertificatePinner",finalClassLoader,"check$okhttp", String.class, "kotlin.jvm.functions.Function0", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,Function0) called");
                    return null;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,Function0) hooked");
        } catch(Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check$okhttp(String,Function0) not found");
        }

        try {
            XposedHelpers.findAndHookMethod("okhttp3.CertificatePinner", finalClassLoader, "check", String.class, List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,List) called");
                    return null;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,List) hooked");
        } catch(Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,List) not found");
        }

        // 3.x okhttp4: 新增 check(String, Certificate[]) 重载
        try {
            XposedHelpers.findAndHookMethod("okhttp3.CertificatePinner", finalClassLoader, "check", String.class, X509Certificate[].class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,Certificate[]) called");
                    return null;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,Certificate[]) hooked");
        } catch(Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.CertificatePinner->check(String,Certificate[]) not found");
        }

        try {
            XposedHelpers.findAndHookMethod("okhttp3.internal.tls.OkHostnameVerifier", finalClassLoader, "verify", String.class, X509Certificate.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,X509Certificate) called");
                    return true;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,X509Certificate) hooked");
        } catch(Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,X509Certificate) not found");
        }

        try {
            XposedHelpers.findAndHookMethod("okhttp3.internal.tls.OkHostnameVerifier", finalClassLoader, "verify", String.class, SSLSession.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,SSLSession) called");
                    return true;
                }
            });
            XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,SSLSession) hooked");
        } catch (Throwable e) {
            XposedBridge.log("[FuEmu][Util]okhttp3.internal.tls.OkHostnameVerifier->verify(String,SSLSession) not found");
        }


        if(prefs.getBoolean("utils_set_UseDeveloperSupport_true", false)) {
            // 2.x: RNHost
            try {
                XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.RNHost", finalClassLoader, "getUseDeveloperSupport", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
                XposedBridge.log("[FuEmu][Util]com.fuulea.venus.reactNative.RNHost->getUseDeveloperSupport() set to true");
            } catch (Throwable e) {
                XposedBridge.log("[FuEmu][Util]com.fuulea.venus.reactNative.RNHost->getUseDeveloperSupport() not found(3.x?)");
            }
            // 3.x: ReactHost 由 expo.modules.ExpoReactHostFactory.getDefaultReactHost 构建, 第8参 useDeveloperSupport
            try {
                XposedHelpers.findAndHookMethod("expo.modules.ExpoReactHostFactory", finalClassLoader, "getDefaultReactHost",
                        Context.class, List.class, String.class, String.class, String.class,
                        "kotlin.jvm.functions.Function0", "com.facebook.react.runtime.JSRuntimeFactory",
                        boolean.class, "com.facebook.react.runtime.BindingsInstaller",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);
                                param.args[7] = true;
                                XposedBridge.log("[FuEmu][Util]expo.modules.ExpoReactHostFactory->getDefaultReactHost useDeveloperSupport=true");
                            }
                        });
                XposedBridge.log("[FuEmu][Util]expo.modules.ExpoReactHostFactory->getDefaultReactHost hooked");
            } catch (Throwable e) {
                XposedBridge.log("[FuEmu][Util]expo.modules.ExpoReactHostFactory->getDefaultReactHost not found(2.x?)");
            }
            //rn debug需要悬浮窗，但manifest未声明
//                    XposedHelpers.findAndHookMethod("android.provider.Settings", finalClassLoader, "canDrawOverlays", Context.class, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                            XposedBridge.log("[FuEmu][Util]android.provider.Settings->canDrawOverlays() called, permission granted");
//                            param.setResult(true);
//                        }
//                    });
        }


        if(prefs.getBoolean("utils_enableWebviewDebugging", false)) {
            // 2.x: NativeAbilityModule
            try {
                XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.packages.NativeAbilityModule", finalClassLoader, "enableWebviewDebugging",  new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            } catch (Throwable e) {
                XposedBridge.log("[FuEmu][Util]NativeAbilityModule->enableWebviewDebugging not found(3.x?)");
            }
            // 3.x: RNCWebViewManager
            try {
                Class<?> wvm = XposedHelpers.findClass("com.reactnativecommunity.webview.RNCWebViewManager", finalClassLoader);
                XposedBridge.hookAllMethods(wvm, "setWebviewDebuggingEnabled", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        if (param.args.length >= 2) {
                            param.args[param.args.length - 1] = true;
                        }
                    }
                });
                XposedBridge.log("[FuEmu][Util]RNCWebViewManager->setWebviewDebuggingEnabled hooked");
            } catch (Throwable e) {
                XposedBridge.log("[FuEmu][Util]RNCWebViewManager->setWebviewDebuggingEnabled not found(2.x?)");
            }
        }


        if(prefs.getBoolean("utils_set_isWifiProxy_false", false)) {
            // 2.x: NativeAbilityModule.isWifiProxy
            try {
                XposedHelpers.findAndHookMethod("com.fuulea.venus.reactNative.packages.NativeAbilityModule", finalClassLoader, "isWifiProxy", "com.facebook.react.bridge.Promise", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        XposedHelpers.callMethod(param.args[0], "resolve", false);
                        return null;
                    }
                });
            } catch (Throwable e) {
                XposedBridge.log("[FuEmu][Util]NativeAbilityModule->isWifiProxy not found(3.x?)");
            }
            // 3.x: 代理检测走 System.getProperty("http.proxyHost")
            XposedHelpers.findAndHookMethod("java.lang.System", finalClassLoader, "getProperty", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    String key = (String) param.args[0];
                    if (("http.proxyHost".equals(key) || "https.proxyHost".equals(key) || "socksProxyHost".equals(key)) && param.getResult() != null) {
                        param.setResult(null);
                        XposedBridge.log("[FuEmu][Util]System->getProperty(" + key + ") -> null (隐藏代理)");
                    }
                }
            });
        }

        //移除bugly (2.x)
        try {
            XposedHelpers.findAndHookMethod("com.fuulea.venus.MainApplication", finalClassLoader, "initBugly", "android.app.Application", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu] MainApplication->initBugly 不存在(3.x?): " + t);
        }


        hookRNLoader(finalClassLoader);

    }

    private void hookRNLoader(ClassLoader finalClassLoader) {
        // 2.x only
        try {
        XposedHelpers.findAndHookMethod("com.facebook.react.bridge.CatalystInstanceImpl", finalClassLoader, "loadScriptFromAssets", AssetManager.class, String.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String assetUrl = (String) param.args[1];
                String fileName = new File(assetUrl).getName();
                XposedBridge.log("[FuEmu][RN-Needle] CatalystInstanceImpl->loadScriptFromAssets() loading " + assetUrl);

                boolean injectEnabled = prefs.getBoolean("rn_inject_enable", false);
                boolean patchEnabled = prefs.getBoolean("rn_patch_enable", false);

                if (!injectEnabled && !patchEnabled) {
                    return;
                }

                AssetManager am = (AssetManager) param.args[0];
                boolean loadSynchronously = (boolean) param.args[2];
                String bundleString;
                try (InputStream is = am.open(assetUrl.startsWith("assets://") ? assetUrl.substring("assets://".length()) : assetUrl);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[1024 * 16];
                    int n;
                    while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n);
                    bundleString = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                } catch (Throwable t) {
                    XposedBridge.log("[FuEmu][RN-Needle] Failed to load " + assetUrl + ": " + t);
                    return;
                }

                boolean modified = false;

                // Handle Injection
                if (injectEnabled) {
                    String injectRulesJson = prefs.getString("rn_inject_rules", "[]");
                    try {
                        JSONArray rules = new JSONArray(injectRulesJson);
                        for (int i = 0; i < rules.length(); i++) {
                            JSONObject rule = rules.getJSONObject(i);
                            if (rule.optBoolean("enabled", false) && fileName.equals(rule.optString("filename", ""))) {
                                String codeToInject = rule.optString("code", "");
                                if (!codeToInject.isEmpty()) {
                                    bundleString = buildInjectedBundle(bundleString, codeToInject, true);
                                    modified = true;
                                    XposedBridge.log("[FuEmu][RN-Needle] Injected code into " + assetUrl);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        XposedBridge.log("[FuEmu][RN-Needle] Failed to parse inject rules: " + e);
                    }
                }

                // Handle Patching
                if (patchEnabled) {
                    String patchRulesJson = prefs.getString("rn_patch_rules", "[]");
                    try {
                        JSONArray rules = new JSONArray(patchRulesJson);
                        for (int i = 0; i < rules.length(); i++) {
                            JSONObject rule = rules.getJSONObject(i);
                            if (rule.optBoolean("enabled", false) && fileName.equals(rule.optString("filename", ""))) {
                                String regex = rule.optString("regex", "");
                                String replacement = rule.optString("replacement", "");
                                if (!regex.isEmpty()) {
                                    bundleString = buildPatchedBundle(bundleString, regex, replacement);
                                    modified = true;
                                    XposedBridge.log("[FuEmu][RN-Needle] Patched " + assetUrl + " with regex: " + regex);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        XposedBridge.log("[FuEmu][RN-Needle] Failed to parse patch rules: " + e);
                    }
                }

                if (!modified) {
                    return; // No changes, proceed with original method
                }

                Application app = mApplication;
                if (app == null) {
                    try {
                        getClassloader();
                        app = mApplication;
                    } catch (Throwable ignored) {
                    }
                }
                if (app == null) {
                    XposedBridge.log("[FuEmu][RN-Needle] No Application context, skip modified bundle");
                    return;
                }

                File f;
                try {
                    f = File.createTempFile(fileName, ".mod", app.getCacheDir());
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(bundleString.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[FuEmu][RN-Needle] Failed to save modified bundle: " + t);
                    return;
                }

                try {
                    Method jniLoadScriptFromFile = XposedHelpers.findMethodExact(param.thisObject.getClass(), "jniLoadScriptFromFile", String.class, String.class, boolean.class);
                    jniLoadScriptFromFile.setAccessible(true);
                    jniLoadScriptFromFile.invoke(param.thisObject, f.getAbsolutePath(), f.getAbsolutePath(), loadSynchronously);
                    XposedBridge.log("[FuEmu][RN-Needle] Success invoking jniLoadScriptFromFile with modified bundle: " + f.getAbsolutePath());
                    param.setResult(null); // Prevent original loadScriptFromAssets from running
                } catch (Throwable t) {
                    XposedBridge.log("[FuEmu][RN-Needle] Failed to invoke jniLoadScriptFromFile: " + t);
                }
            }
        });
        } catch (Throwable t) {
            XposedBridge.log("[FuEmu][RN-Needle] CatalystInstanceImpl 不存在(3.x?): " + t);
        }
    }

    private static String buildInjectedBundle(String original, String payload, boolean prepend) {
        if (payload == null || payload.isEmpty()) return original != null ? original : "";
        if (original == null) original = "";
        if (prepend) {
            StringBuilder sb = new StringBuilder(payload.length() + original.length() + 32);
            sb.append(payload);
            if (!payload.endsWith("\n")) sb.append('\n');
            sb.append(original);
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(original.length() + payload.length() + 32);
            sb.append(original);
            if (!original.endsWith("\n")) sb.append('\n');
            sb.append(payload);
            return sb.toString();
        }
    }

    private static String buildPatchedBundle(String original, String regex, String replacement) {
        if (regex == null || regex.isEmpty()) return original != null ? original : "";
        if (original == null) original = "";
        if (replacement == null) replacement = "";
        try {
            return original.replaceAll(regex, Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
            XposedBridge.log("[FuEmu][RN-Needle] Error during regex replace: " + e.getMessage());
            return original;
        }
    }

    public ClassLoader getClassloader() {
        ClassLoader resultClassloader = null;
        Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
        Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
        Object mBoundApplication = XposedHelpers.getObjectField(currentActivityThread, "mBoundApplication");
        Object loadedApkInfo = XposedHelpers.getObjectField(mBoundApplication, "info");
        this.mApplication = (Application) XposedHelpers.getObjectField(loadedApkInfo, "mApplication");
        resultClassloader = mApplication.getClassLoader();
        return resultClassloader;
    }


    private class NBTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkClientTrusted called");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkServerTrusted called");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkClientTrusted called");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkServerTrusted called");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkClientTrusted called");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            XposedBridge.log("[FuEmu][Util]NBTrustManager->checkServerTrusted called");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
