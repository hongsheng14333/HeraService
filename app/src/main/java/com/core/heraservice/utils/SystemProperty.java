package com.core.heraservice.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SystemProperty {
	public static void set(String key, String val) {
		try {
			Class<?> c = Class.forName("android.os.SystemProperties");
			Method set = c.getMethod("set", String.class,String.class);
			set.invoke(c, key,val);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public static String get(String key, String defValue) {
		try {
			Class<?> cls = Class.forName("android.os.SystemProperties");
			Method method = cls.getMethod("get", String.class, String.class);
			return (String) method.invoke(cls, key, defValue);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return defValue;
	}
}
