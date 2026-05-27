package com.bitbond.app.status;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class ForegroundPackageFilter {
    private static final Set<String> BLOCKED_EXACT_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.miui.home",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.launcher",
            "com.sec.android.app.launcher",
            "com.samsung.android.app.launcher",
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.oneplus.launcher",
            "net.oneplus.launcher",
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.mi.android.globallauncher",
            "com.motorola.launcher3",
            "com.lenovo.launcher",
            "com.lge.launcher2",
            "com.lge.launcher3",
            "com.htc.launcher",
            "com.asus.launcher",
            "com.sonymobile.home",
            "com.teslacoilsw.launcher",
            "app.lawnchair",
            "ch.deletescape.lawnchair.plah",
            "com.microsoft.launcher",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.google.android.inputmethod.pinyin",
            "com.google.android.apps.inputmethod.hindi",
            "com.google.android.apps.inputmethod.zhuyin",
            "com.google.android.apps.inputmethod.cantonese",
            "com.baidu.input",
            "com.sohu.inputmethod.sogou",
            "com.iflytek.inputmethod",
            "com.touchtype.swiftkey",
            "com.microsoft.swiftkey",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "com.miui.inputmethod",
            "com.miui.securitycenter",
            "com.miui.securityadd",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.oppo.safe",
            "com.iqoo.secure",
            "com.vivo.permissionmanager",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.samsung.android.sm",
            "com.samsung.android.sm_cn",
            "com.samsung.android.lool",
            "com.lenovo.safecenter",
            "com.meizu.safe",
            "com.realme.securitycheck",
            "com.transsion.phonemaster"));

    private static final String[] BLOCKED_PREFIXES = {
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.sohu.inputmethod",
            "com.iflytek.inputmethod",
            "com.sec.android.inputmethod",
            "com.miui.securitycenter",
            "com.miui.securityadd",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.lenovo.safecenter"
    };

    private ForegroundPackageFilter() {
    }

    static boolean isSelectableForegroundPackage(String packageName, String ownPackageName) {
        if (packageName == null) {
            return false;
        }

        String trimmedPackageName = packageName.trim();
        if (trimmedPackageName.isEmpty()) {
            return false;
        }

        if (isOwnPackage(trimmedPackageName, ownPackageName)) {
            return false;
        }

        String normalizedPackageName = trimmedPackageName.toLowerCase(Locale.US);
        if (BLOCKED_EXACT_PACKAGES.contains(normalizedPackageName)) {
            return false;
        }

        for (String blockedPrefix : BLOCKED_PREFIXES) {
            if (matchesPackagePrefix(normalizedPackageName, blockedPrefix)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isOwnPackage(String packageName, String ownPackageName) {
        return ownPackageName != null && packageName.equals(ownPackageName.trim());
    }

    private static boolean matchesPackagePrefix(String packageName, String blockedPrefix) {
        return packageName.equals(blockedPrefix) || packageName.startsWith(blockedPrefix + ".");
    }
}
