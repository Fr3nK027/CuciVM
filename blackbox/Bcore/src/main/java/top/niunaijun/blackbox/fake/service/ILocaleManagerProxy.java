package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;

/**
 * CuciVM addition. Apps that pick their own language (YouTube...) call LocaleManager.setApplicationLocales
 * with their own package name; the system rejects it for our uid (CHANGE_CONFIGURATION) and the app
 * crashes. Inside a space the request is accepted and ignored; reads are answered for the host package.
 */
public class ILocaleManagerProxy extends BinderInvocationStub {
    private static final String NAME = "locale";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(NAME);
            Class<?> stub = Class.forName("android.app.ILocaleManager$Stub");
            return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().startsWith("setApplicationLocales") || method.getName().startsWith("setOverrideLocaleConfig")) {
            return null;
        }
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }
}
